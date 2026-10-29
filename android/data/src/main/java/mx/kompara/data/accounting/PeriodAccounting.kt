package mx.kompara.data.accounting

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Sources describe evidence, not whether an entire week has been verified. */
enum class MoneySource { CAPTURED_ESTIMATE, PARSED_STATEMENT, USER_CORRECTED_IMPORT, SERVER_CONFIRMED_STATEMENT }
enum class PaymentBasis { DRIVER_PAYMENT, UNCONFIRMED }
enum class MoneyLifecycle { TAKEN_ESTIMATE, IN_PROGRESS, ITEM_CONFIRMED, UNRESOLVED, NOT_TAKEN, CANCELLED }
enum class MoneyField { PAYMENT, DISTANCE, TRIPS, HOURS }

data class Coverage(val start: LocalDate, val endInclusive: LocalDate, val dated: Boolean = true) {
    init { require(!endInclusive.isBefore(start)) }
    fun overlaps(other: Coverage) = start <= other.endInclusive && other.start <= endInclusive
    fun contains(other: Coverage) = start <= other.start && endInclusive >= other.endInclusive
}

data class IncomeRecord(
    val id: String,
    val app: String,
    val coverage: Coverage,
    val source: MoneySource,
    val payment: BigDecimal?,
    /** Includes pickup. Null means incomplete distance, not a free ride. */
    val km: BigDecimal?,
    val trips: Int,
    val startedAt: Long? = null,
    val minutes: BigDecimal? = null,
    val lifecycle: MoneyLifecycle = MoneyLifecycle.TAKEN_ESTIMATE,
    val inProgress: Int = if (lifecycle == MoneyLifecycle.IN_PROGRESS) 1 else 0,
    val paymentBasis: PaymentBasis = PaymentBasis.DRIVER_PAYMENT,
    val costVersionId: Long? = null,
    val costVersionLocked: Boolean = false,
    val fieldSources: Map<MoneyField, String> = MoneyField.entries.associateWith { id },
    /** A declared aggregate contribution whose raw fields are unavailable (e.g. B6 fixture). */
    val declaredContribution: BigDecimal? = null,
    val tripsKnown: Boolean = true,
) {
    init { require(trips >= 0 && inProgress in 0..trips); require(km == null || km.signum() >= 0) }
    val eligible: Boolean get() = lifecycle in setOf(MoneyLifecycle.TAKEN_ESTIMATE, MoneyLifecycle.IN_PROGRESS, MoneyLifecycle.ITEM_CONFIRMED)
}

data class Reconciliation(val capturedId: String, val statementId: String, val fields: Set<MoneyField>)

data class Selection(val records: List<IncomeRecord>, val excluded: List<IncomeRecord>)

/** Keep source records intact. Payment replacement needs matching app/period AND complete work coverage. */
object AccountingSourceSelector {
    fun select(records: List<IncomeRecord>, decisions: List<Reconciliation> = emptyList()): Selection {
        val unique = records.groupBy { it.id }.map { (_, copies) ->
            require(copies.distinct().size == 1) { "Conflicting source identity" }
            copies.first()
        }
        val selected = unique.sortedBy { it.id }.filter { it.source == MoneySource.CAPTURED_ESTIMATE }.toMutableList()
        val excluded = mutableListOf<IncomeRecord>()
        for (statement in unique.sortedBy { it.id }.filter { it.source != MoneySource.CAPTURED_ESTIMATE }) {
            val overlaps = selected.filter { it.app == statement.app && it.coverage.overlaps(statement.coverage) }
            val decision = decisions.singleOrNull { it.statementId == statement.id }
            if (overlaps.isEmpty()) {
                selected += statement
            } else if (decision == null) {
                excluded += statement
            } else {
                val captured = overlaps.singleOrNull { it.id == decision.capturedId }
                require(captured != null && overlaps.size == 1) { "Reconcile one explicit coverage at a time" }
                require(captured.coverage.start == statement.coverage.start && captured.coverage.endInclusive == statement.coverage.endInclusive)
                require(decision.fields.isNotEmpty())
                val paymentChanged = MoneyField.PAYMENT in decision.fields
                val kmChanged = MoneyField.DISTANCE in decision.fields
                val sameWork = captured.trips == statement.trips
                // Never divide full-statement payment by the distance of a captured subset (H5b).
                val distance = if (kmChanged) statement.km else if (paymentChanged && !sameWork) null else captured.km
                val tripsChanged = MoneyField.TRIPS in decision.fields || !sameWork && (paymentChanged || kmChanged)
                val provenance = captured.fieldSources + decision.fields.associateWith { statement.id } +
                    (if (tripsChanged) mapOf(MoneyField.TRIPS to statement.id) else emptyMap())
                selected.remove(captured)
                selected += captured.copy(
                    id = "${captured.id}+${statement.id}",
                    source = statement.source,
                    coverage = captured.coverage.copy(dated = captured.coverage.dated && statement.coverage.dated),
                    payment = if (paymentChanged) statement.payment else if (kmChanged && !sameWork) null else captured.payment,
                    paymentBasis = if (paymentChanged) statement.paymentBasis else captured.paymentBasis,
                    km = distance,
                    trips = if (tripsChanged) statement.trips else captured.trips,
                    minutes = if (MoneyField.HOURS in decision.fields && (sameWork || paymentChanged)) statement.minutes
                        else if (!sameWork && (paymentChanged || kmChanged || MoneyField.HOURS in decision.fields)) null else captured.minutes,
                    inProgress = captured.inProgress,
                    fieldSources = provenance,
                    declaredContribution = null,
                )
                excluded += captured
            }
        }
        return Selection(selected, excluded)
    }
}

data class PeriodWindow(val start: LocalDate, val endInclusive: LocalDate, val asOf: Instant, val zone: ZoneId) {
    init { require(endInclusive >= start) }
    val elapsedEnd: LocalDate get() = minOf(endInclusive, asOf.atZone(zone).toLocalDate())
    val coverage: Coverage get() = Coverage(start, endInclusive)
}

data class TimeInterval(val start: Long, val end: Long?)
data class CarCharge(val date: LocalDate, val versionId: Long, val amount: BigDecimal)
data class Contribution(
    val record: IncomeRecord,
    val runningCostVersion: RunningCostVersion?,
    val runningExpense: BigDecimal?,
    val tripContribution: BigDecimal?,
) {
    val ratePerKm: BigDecimal? get() = record.km?.let { tripContribution?.per(it) }
    val ratePerHour: BigDecimal? get() = record.minutes?.let { tripContribution?.multiply(BigDecimal(60))?.per(it) }
    val unknownHistoricalCost: Boolean get() = runningCostVersion == null && record.declaredContribution == null
}

data class AppContribution(
    val payment: BigDecimal?, val runningExpense: BigDecimal?, val tripContribution: BigDecimal?,
    val km: BigDecimal?, val trips: Int,
) {
    /** Fixed car cost is never allocated to apps. */
    val ratePerKm: BigDecimal? get() = km?.let { tripContribution?.per(it) }
}

data class PeriodMoney(
    val window: PeriodWindow,
    val contributions: List<Contribution>,
    val excluded: List<IncomeRecord>,
    val carCharges: List<CarCharge>,
    val payment: BigDecimal?,
    val runningExpense: BigDecimal?,
    val tripContribution: BigDecimal?,
    val carCost: BigDecimal,
    val periodResult: BigDecimal?,
    val hoursOnline: BigDecimal,
    val byApp: Map<String, AppContribution>,
    /** Labelled payment scope excluded from the net ledger, never distributed into day bars. */
    val paymentOnly: List<Contribution> = emptyList(),
) {
    val paymentScopeTotal: BigDecimal? get() = (contributions + paymentOnly).map { it.record.payment }.sumKnown()
    val trips: Int get() = contributions.sumOf { it.record.trips }
    val inProgress: Int get() = contributions.sumOf { it.record.inProgress }
    val carConfigured: Boolean get() = carCharges.isNotEmpty()
    val sources: Set<MoneySource> get() = contributions.map { it.record.source }.toSet()
    val unknownHistoricalCost: Boolean get() = contributions.any { it.unknownHistoricalCost }
    /** Only show a rounding explanation when the displayed child sum actually differs. */
    fun roundingResidual(children: List<PeriodMoney>): BigDecimal? {
        val total = periodResult ?: return null
        val displayed = children.map { it.periodResult?.setScale(0, RoundingMode.HALF_UP) ?: return null }
        return total.setScale(0, RoundingMode.HALF_UP).subtract(displayed.sumDecimal())
    }
}

/** One period-money calculation for day/week/month and per-app running-cost contributions. */
object PeriodAccounting {
    fun contribution(record: IncomeRecord, costs: CostSchedule, zone: ZoneId): Contribution {
        val version = if (record.costVersionLocked || record.costVersionId != null) costs.accountingVersion(record.costVersionId)
        else {
            val instant = record.startedAt ?: record.coverage.start.atStartOfDay(zone).toInstant().toEpochMilli()
            val atStart = costs.runningAt(instant)
            // An undated aggregate cannot be spread across cost changes without dated distance.
            val end = record.coverage.endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            if (record.startedAt == null && costs.running.any { it.effectiveFrom > instant && it.effectiveFrom < end }) null else atStart
        }
        val expense = record.km?.let { version?.inputs?.costPerKm?.multiply(it) }
        val value = if (record.paymentBasis != PaymentBasis.DRIVER_PAYMENT) null
            else record.declaredContribution ?: record.payment?.let { pay -> expense?.let { pay.subtract(it) } }
        return Contribution(record, version, expense, value)
    }

    fun calculate(
        window: PeriodWindow,
        records: List<IncomeRecord>,
        costs: CostSchedule,
        shifts: List<TimeInterval> = emptyList(),
        decisions: List<Reconciliation> = emptyList(),
        statementSelections: List<StatementSelection> = emptyList(),
    ): PeriodMoney {
        val selection = if (statementSelections.isEmpty()) AccountingSourceSelector.select(records, decisions)
            else selectStatements(records, statementSelections, window)
        val included = selection.records.filter {
            it.eligible && it.coverage.start >= window.start && it.coverage.endInclusive <= window.elapsedEnd &&
                (it.startedAt == null || it.startedAt <= window.asOf.toEpochMilli())
        }
        val evaluated = included.map { contribution(it, costs, window.zone) }
        val paymentOnly = evaluated.filter { it.record.source != MoneySource.CAPTURED_ESTIMATE && it.tripContribution == null }
        val contributions = evaluated - paymentOnly.toSet()
        val incompleteReplacement = paymentOnly.any { payment ->
            statementSelections.any { it.statementId == payment.record.id } && selection.excluded.any {
                it.source == MoneySource.CAPTURED_ESTIMATE && it.app == payment.record.app && it.coverage.overlaps(payment.record.coverage)
            }
        }
        val charges = generateSequence(window.start) { it.plusDays(1) }.takeWhile { it <= window.elapsedEnd }.mapNotNull { date ->
            costs.carAt(date)?.let { version -> version.weeklyMxn?.let {
                CarCharge(date, version.id, it.divide(BigDecimal(7), MONEY_CONTEXT))
            } }
        }.toList()
        val car = charges.map { it.amount }.sumDecimal()
        val payment = contributions.map { it.record.payment }.sumKnown()
        val running = contributions.map { it.runningExpense }.sumKnown()
        val contribution = contributions.map { it.tripContribution }.sumKnown()
        val byApp = contributions.groupBy { it.record.app }.mapValues { (_, rows) ->
            AppContribution(rows.map { it.record.payment }.sumKnown(), rows.map { it.runningExpense }.sumKnown(),
                rows.map { it.tripContribution }.sumKnown(), rows.map { it.record.km }.sumKnown(), rows.sumOf { it.record.trips })
        }
        return PeriodMoney(window, contributions, selection.excluded + (selection.records - included.toSet()), charges,
            payment, running, contribution, car,
            if (incompleteReplacement || contributions.isEmpty() && paymentOnly.isNotEmpty()) null else contribution?.subtract(car),
            onlineHours(window, shifts), byApp, paymentOnly)
    }

    /** Union of intervals, clipped to local calendar bounds and the SAME as-of as the money. */
    fun onlineHours(window: PeriodWindow, shifts: List<TimeInterval>): BigDecimal {
        val start = window.start.atStartOfDay(window.zone).toInstant().toEpochMilli()
        val end = minOf(window.endInclusive.plusDays(1).atStartOfDay(window.zone).toInstant().toEpochMilli(), window.asOf.toEpochMilli())
        val clipped = shifts.map { maxOf(start, it.start) to minOf(end, it.end ?: end) }.filter { it.second > it.first }.sortedBy { it.first }
        var total = 0L
        var previousEnd = start
        for ((from, until) in clipped) {
            if (until > previousEnd) total += until - maxOf(from, previousEnd)
            previousEnd = maxOf(previousEnd, until)
        }
        return BigDecimal(total).divide(BigDecimal(3_600_000), MONEY_CONTEXT)
    }
}

private fun List<BigDecimal>.sumDecimal(): BigDecimal = fold(BigDecimal.ZERO, BigDecimal::add)
private fun List<BigDecimal?>.sumKnown(): BigDecimal? = if (any { it == null }) null else filterNotNull().sumDecimal()
