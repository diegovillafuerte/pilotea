package mx.kompara.metrics

import androidx.room.withTransaction
import kotlinx.coroutines.flow.map
import mx.kompara.data.accounting.*
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.rollup.capturedIncomeRecords
import mx.kompara.data.settings.CarCostRepository
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.metrics.compare.ConfirmedStatement
import mx.kompara.metrics.compare.confirmedStatement
import javax.inject.Inject
import javax.inject.Singleton

/** One raw-source, cost and as-of snapshot for the v3 period screen owners. */
@Singleton
class PeriodMoneyRepository @Inject constructor(
    private val database: KomparaDatabase,
    private val runningCosts: CostProfileRepository,
    private val carCosts: CarCostRepository,
) {
    suspend fun snapshot(window: PeriodWindow): PeriodMoney = daySnapshot(window).money

    fun observeMonth(window: PeriodWindow): kotlinx.coroutines.flow.Flow<MonthMoneySnapshot> =
        database.invalidationTracker.createFlow("offers", "trips", "shifts", "running_cost_versions",
            "car_cost_versions", "weekly_aggregates", "import_statements", "import_selections").map { monthSnapshot(window) }

    suspend fun monthSnapshot(window: PeriodWindow): MonthMoneySnapshot = database.withTransaction {
        val captured = daySnapshot(window)
        val costs = CostSchedule(runningCosts.runningVersions(), carCosts.get())
        val history = database.aggregateDao().allWeekly()
        val statements = database.importDao().statements().map { it.income() } + history.filter { it.source == "IMPORTED" }.mapNotNull { row ->
            val start = runCatching { java.time.LocalDate.parse(row.weekStart) }.getOrNull() ?: return@mapNotNull null
            IncomeRecord("legacy-import:${row.platform}:${row.weekStart}", row.platform,
                Coverage(start, start.plusDays(6), dated = false), MoneySource.PARSED_STATEMENT,
                row.grossEarningsMxn.takeIf { it.isFinite() }?.toBigDecimal(), null, row.totalTrips,
                paymentBasis = PaymentBasis.UNCONFIRMED)
        }
        monthMoneySnapshot(window, captured.money.contributions.map { it.record } + statements, costs,
            captured.shifts.map { TimeInterval(it.startedAt, it.endedAt) },
            history.mapNotNull { runCatching { java.time.YearMonth.from(java.time.LocalDate.parse(it.weekStart)) }.getOrNull() } + statements.map { java.time.YearMonth.from(it.coverage.start) },
            captured.offers.isNotEmpty(), database.importDao().selections().map { it.domain() })
    }

    /** Week, days, source records and cost versions are read atomically, at the caller's clock. */
    fun observeWeek(window: PeriodWindow): kotlinx.coroutines.flow.Flow<WeekMoneySnapshot> =
        database.invalidationTracker.createFlow("offers", "trips", "shifts", "running_cost_versions",
            "car_cost_versions", "weekly_aggregates", "import_statements", "import_selections").map { weekSnapshot(window) }

    suspend fun weekSnapshot(window: PeriodWindow): WeekMoneySnapshot = database.withTransaction {
        val captured = daySnapshot(window)
        val costs = CostSchedule(runningCosts.runningVersions(), carCosts.get())
        val rows = database.aggregateDao().allWeekly()
        // Legacy imports have no field coverage/payment-basis evidence. Preserve their payment as
        // an explicitly unconfirmed, undated scope; never substitute net/km or infer verification.
        val localStatements = database.importDao().statements().filter { it.start == window.start.toString() }
        val statements = localStatements.map { it.income() } + rows.filter { it.weekStart == window.start.toString() && it.source == "IMPORTED" }.map { row ->
            IncomeRecord("legacy-import:${row.platform}:${row.weekStart}", row.platform,
                Coverage(window.start, window.endInclusive, dated = false), MoneySource.PARSED_STATEMENT,
                row.grossEarningsMxn.takeIf { it.isFinite() }?.toBigDecimal(), null, row.totalTrips,
                paymentBasis = PaymentBasis.UNCONFIRMED)
        }
        val records = captured.money.contributions.map { it.record } + statements
        val shifts = captured.shifts.map { TimeInterval(it.startedAt, it.endedAt) }
        val week = PeriodAccounting.calculate(window, records, costs, shifts,
            statementSelections = database.importDao().selections().map { it.domain() })
        val days = (0L..6L).map { offset ->
            val date = window.start.plusDays(offset)
            if (date > window.elapsedEnd) null else PeriodAccounting.calculate(
                PeriodWindow(date, date, window.asOf, window.zone), records.filter { it.coverage.dated }, costs, shifts)
        }
        WeekMoneySnapshot(week, days, rows, statements, captured.offers.isNotEmpty(),
            database.importDao().statements().map { java.time.LocalDate.parse(it.start) }, captured.money,
            database.importDao().selections().map { "statement:${it.statementId}" }.filter { id ->
                (week.contributions + week.paymentOnly).any { it.record.id == id }
            }.toSet(), localStatements.mapNotNull { it.confirmedStatement() },
            CarWeek.of(costs.car, window, days.map { it?.tripContribution }))
    }

    /** Rows, presentations and money come from one transaction and one as-of instant. */
    suspend fun daySnapshot(window: PeriodWindow): DayMoneySnapshot = database.withTransaction {
        val from = window.start.atStartOfDay(window.zone).toInstant().toEpochMilli()
        val until = minOf(window.endInclusive.plusDays(1).atStartOfDay(window.zone).toInstant().toEpochMilli(), window.asOf.toEpochMilli() + 1)
        val trips = database.tripDao().startedBetween(from, until)
        val visibleOffers = database.offerDao().seenBetween(from, until)
        val offers = database.offerDao().withTripEvidence(visibleOffers, trips)
        val records = capturedIncomeRecords(trips, offers, window.asOf.toEpochMilli(), window.zone)
        val shifts = database.shiftDao().overlapping(from, until)
        val costs = CostSchedule(runningCosts.runningVersions(), carCosts.get())
        DayMoneySnapshot(PeriodAccounting.calculate(window, records, costs,
            shifts.map { TimeInterval(it.startedAt, it.endedAt) }), visibleOffers, trips, shifts,
            database.offerPresentationDao().seenBetween(from, until), costs.runningAt(window.asOf.toEpochMilli()), costs.running)
    }

    /** Comparar · Tú: the ledger's taken trips of the 28 days before [asOf]'s day, by app, weekday and hour block. */
    fun observeSlices(asOf: java.time.Instant, zone: java.time.ZoneId): kotlinx.coroutines.flow.Flow<DriverSlices> =
        database.invalidationTracker.createFlow("offers", "trips", "running_cost_versions").map {
            val window = DriverSlices.window(asOf.atZone(zone).toLocalDate())
            val money = daySnapshot(PeriodWindow(window.start, window.endInclusive, asOf, zone)).money
            DriverSlices.of(window.start, window.endInclusive, money.contributions, zone)
        }

    fun observeToday(asOf: java.time.Instant, zone: java.time.ZoneId): kotlinx.coroutines.flow.Flow<TodayMoneySnapshot> =
        database.invalidationTracker.createFlow(
            "offers", "trips", "shifts", "offer_presentations", "running_cost_versions", "car_cost_versions").map {
            database.withTransaction {
                val date = asOf.atZone(zone).toLocalDate()
                val today = daySnapshot(PeriodWindow(date, date, asOf, zone))
                val yesterday = daySnapshot(PeriodWindow(date.minusDays(1), date.minusDays(1), asOf, zone))
                TodayMoneySnapshot(today, yesterday, database.offerDao().hasSeenBefore(asOf.toEpochMilli() + 1))
            }
        }

}

/** A trip can start just after midnight even when its accepted offer was seen in the prior bucket. */
internal suspend fun mx.kompara.data.db.dao.OfferDao.withTripEvidence(
    offers: List<mx.kompara.data.db.entity.OfferEntity>,
    trips: List<mx.kompara.data.db.entity.TripEntity>,
): List<mx.kompara.data.db.entity.OfferEntity> {
    val present = offers.map { it.id }.toSet()
    val missing = trips.mapNotNull { it.offerId }.distinct().filter { it !in present }
    return offers + missing.mapNotNull { findById(it) }
}

/** Frozen data shared by Hoy and its dated drill-in. No fixture or display-rounding inputs. */
data class DayMoneySnapshot(
    val money: PeriodMoney,
    val offers: List<mx.kompara.data.db.entity.OfferEntity>,
    val trips: List<mx.kompara.data.db.entity.TripEntity>,
    val shifts: List<mx.kompara.data.db.entity.ShiftEntity>,
    val presentations: List<mx.kompara.data.db.entity.OfferPresentationEntity>,
    val activeCost: RunningCostVersion?,
    val runningCosts: List<RunningCostVersion> = listOfNotNull(activeCost),
)
data class TodayMoneySnapshot(val today: DayMoneySnapshot, val yesterday: DayMoneySnapshot, val hasHistory: Boolean)

data class WeekMoneySnapshot(
    val money: PeriodMoney,
    val days: List<PeriodMoney?>,
    val history: List<mx.kompara.data.db.entity.WeeklyAggregateEntity> = emptyList(),
    /** Typed source seam for S16. Legacy rows never qualify for a payment comparison. */
    val statements: List<IncomeRecord> = emptyList(),
    val hasOffers: Boolean = false,
    val importedWeeks: List<java.time.LocalDate> = emptyList(),
    val capturedMoney: PeriodMoney? = null,
    val selectedStatements: Set<String> = emptySet(),
    /** Authoritative proof read in the same transaction as the period's money. */
    val confirmedStatements: List<ConfirmedStatement> = emptyList(),
    /** The week's car bill against its per-day contribution (v4 Semana); bill null when no car cost. */
    val car: CarWeek = CarWeek(null, days.map { it?.tripContribution }),
)

data class MonthMoneySnapshot(
    val money: PeriodMoney,
    val weeks: List<PeriodMoney>,
    val unallocated: List<IncomeRecord> = emptyList(),
    val history: List<java.time.YearMonth> = emptyList(),
    val hasOffers: Boolean = false,
)

/** Undated summaries retain their week scope. Only calendar car costs are divided across dates. */
fun monthMoneySnapshot(window: PeriodWindow, records: List<IncomeRecord>, costs: CostSchedule,
    shifts: List<TimeInterval> = emptyList(), history: List<java.time.YearMonth> = emptyList(),
    hasOffers: Boolean = false, choices: List<StatementSelection> = emptyList()): MonthMoneySnapshot {
    val relevant = records.filter { it.coverage.overlaps(window.coverage) }
    val unallocated = relevant.filter { !it.coverage.dated && !window.coverage.contains(it.coverage) }
    // Keep undated income out of the dated net even if a legacy import happens to carry km.
    val scoped = (relevant - unallocated.toSet()).map {
        if (!it.coverage.dated) it.copy(km = null, declaredContribution = null) else it
    }
    val money = PeriodAccounting.calculate(window, scoped, costs, shifts, statementSelections = choices)
    val weeks = generateSequence(window.start) { date ->
        date.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
    }.takeWhile { it <= window.elapsedEnd }.map { start ->
        val end = minOf(start.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)), window.elapsedEnd)
        PeriodAccounting.calculate(window.copy(start = start, endInclusive = end), scoped.filter { it.coverage.dated }, costs, shifts)
    }.toList()
    return MonthMoneySnapshot(money, weeks, unallocated, history, hasOffers)
}
