package mx.kompara.ui.stats

import mx.kompara.data.db.entity.DailyAggregateEntity
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform
import mx.kompara.data.accounting.*

/**
 * The five headline metrics for one period (a week or a day), folded to a single platform or summed
 * across "Todas". Pure data — the screens format it through [mx.kompara.ui.format.Formatters].
 *
 * Rates ($/viaje, $/km, $/hora, viajes/hora) are recomputed from the summed totals rather than
 * averaged from the per-platform rows: averaging pre-divided rates would weight a platform with one
 * trip the same as one with fifty. Acceptance rate is the only exception — it has no shared
 * denominator with earnings, so it's a trips-unweighted blend across the selected rows (documented
 * approximation; the honest per-offer rate would need the raw offer counts, which the rollup folds
 * away). A rate is null when its denominator is zero, exactly like the rollup.
 */
data class PeriodStats(
    val netEarningsMxn: Double,
    val grossEarningsMxn: Double,
    val totalTrips: Int,
    val totalKm: Double,
    val hoursOnline: Double,
    val earningsPerTrip: Double?,
    val earningsPerKm: Double?,
    val earningsPerHour: Double?,
    val tripsPerHour: Double?,
    val acceptanceRate: Double?,
    val runningCostKnown: Boolean = true,
) {
    /** True when this period carried no completed trips at all (drives the empty/first-run state). */
    val isEmpty: Boolean get() = totalTrips == 0 && netEarningsMxn == 0.0 && hoursOnline == 0.0

    companion object {
        /** V3 consumers share this complete ledger; no screen-specific net formula. */
        fun calculate(window: PeriodWindow, records: List<IncomeRecord>, costs: CostSchedule,
            shifts: List<TimeInterval> = emptyList(), decisions: List<Reconciliation> = emptyList()): PeriodMoney =
            PeriodAccounting.calculate(window, records, costs, shifts, decisions)

        /** A zeroed period — what the dashboard shows before any data is captured. */
        val EMPTY = PeriodStats(
            netEarningsMxn = 0.0,
            grossEarningsMxn = 0.0,
            totalTrips = 0,
            totalKm = 0.0,
            hoursOnline = 0.0,
            earningsPerTrip = null,
            earningsPerKm = null,
            earningsPerHour = null,
            tripsPerHour = null,
            acceptanceRate = null,
        )

        /**
         * Fold weekly rows for the selected [platform] (null ⇒ "Todas", sum every platform) into one
         * [PeriodStats]. Hours are summed straight across platforms because the rollup already
         * attributes the same (platform-agnostic) online hours to every platform with trips that
         * week — so summing them would double-count. We therefore take the **max** hours across the
         * selected rows, which equals the true online hours for the period (every row carries the same
         * value when present). See [foldRows].
         */
        fun fromWeekly(rows: List<WeeklyAggregateEntity>, platform: Platform?): PeriodStats =
            foldRows(
                rows.map { it.toRow() }.selectSources().filterByPlatform(platform),
            )

        /** Daily twin of [fromWeekly] for a *single* day bucket (hours replicated per platform-row). */
        fun fromDaily(rows: List<DailyAggregateEntity>, platform: Platform?): PeriodStats =
            foldRows(
                rows.map { it.toRow() }.selectSources().filterByPlatform(platform),
            )

        /**
         * Fold a **month** of daily rows (many day buckets) into one [PeriodStats]. Same totals as
         * [foldRows] — net/gross/trips/km summed across all selected rows — but the online-hours fold
         * differs: within one day the platform-rows replicate the same hours (so max-per-day is the
         * day's true online hours), and the month's hours is the **sum of each day's hours**. Taking a
         * single max across the whole month (as [fromDaily] does) would undercount to a single day and
         * inflate $/hora and viajes/hora. Rates are recomputed from these corrected monthly totals.
         */
        fun fromDailyMonth(rows: List<DailyAggregateEntity>, platform: Platform?): PeriodStats {
            val selected = rows.map { it.toRow() }.selectSources().filterByPlatform(platform)
            if (selected.isEmpty()) return EMPTY
            val monthlyHours = selected.groupBy { it.day }.values.sumOf { dayRows -> dayRows.maxOf { it.hours } }
            return foldRows(selected, hoursOverride = monthlyHours)
        }

        // Legacy aggregates have no field-selection evidence. Captured stays selected per app/period.
        private fun List<AggRow>.selectSources(): List<AggRow> = groupBy { it.platform to it.period }.values.map { candidates ->
            candidates.firstOrNull { it.source == "CAPTURED" } ?: candidates.first()
        }

        private fun List<AggRow>.filterByPlatform(platform: Platform?): List<AggRow> =
            if (platform == null) this else filter { it.platform == platform.name }

        private fun foldRows(rows: List<AggRow>, hoursOverride: Double? = null): PeriodStats {
            if (rows.isEmpty()) return EMPTY
            val known = rows.all { it.runningCostKnown }
            val net = rows.fold(java.math.BigDecimal.ZERO) { total, row -> total.add(row.exactNet ?: row.net.decimal()) }.toDouble()
            val gross = rows.sumOf { it.gross }
            val trips = rows.sumOf { it.trips }
            val km = rows.sumOf { it.km }
            // Hours are platform-agnostic and replicated per platform-row in the rollup; within one day
            // bucket the period's true online hours is the max, not the sum (summing would multiply by
            // platform count). [hoursOverride] carries the correct cross-day sum for monthly folds.
            val hours = hoursOverride ?: rows.maxOf { it.hours }
            val acceptances = rows.mapNotNull { it.acceptanceRate }
            return PeriodStats(
                netEarningsMxn = net,
                grossEarningsMxn = gross,
                totalTrips = trips,
                totalKm = km,
                hoursOnline = hours,
                earningsPerTrip = if (!known || trips == 0) null else net / trips,
                earningsPerKm = if (!known || km <= 0.0) null else net / km,
                earningsPerHour = if (!known || hours <= 0.0) null else net / hours,
                tripsPerHour = if (hours <= 0.0) null else trips / hours,
                acceptanceRate = if (acceptances.isEmpty()) null else acceptances.average(),
                runningCostKnown = known,
            )
        }
    }
}

/**
 * The platform-tagged subset of aggregate fields [PeriodStats] folds. Internal glue. [day] is the ISO
 * date for daily rows (used to group hours per-day in the monthly fold) and null for weekly rows.
 */
private data class AggRow(
    val platform: String,
    val net: Double,
    val gross: Double,
    val trips: Int,
    val km: Double,
    val hours: Double,
    val acceptanceRate: Double?,
    val day: String? = null,
    val period: String,
    val source: String,
    val runningCostKnown: Boolean,
    val exactNet: java.math.BigDecimal?,
)

private fun WeeklyAggregateEntity.toRow() = AggRow(
    period = weekStart,
    platform = platform,
    source = source,
    runningCostKnown = runningCostKnown,
    exactNet = contributionDecimal?.toBigDecimal(),
    net = netEarningsMxn,
    gross = grossEarningsMxn,
    trips = totalTrips,
    km = totalKm,
    hours = hoursOnline,
    acceptanceRate = acceptanceRate,
)

private fun DailyAggregateEntity.toRow() = AggRow(
    period = day,
    platform = platform,
    source = source,
    runningCostKnown = runningCostKnown,
    exactNet = contributionDecimal?.toBigDecimal(),
    net = netEarningsMxn,
    gross = grossEarningsMxn,
    trips = totalTrips,
    km = totalKm,
    hours = hoursOnline,
    acceptanceRate = acceptanceRate,
    day = day,
)

/** The ledger basis follows configured elapsed car charges, including on days without trips. */
fun PeriodMoney.deductionBasis(): String = if (carConfigured) "ya restamos ${energy()}, desgaste y coche"
    else "ya restamos ${energy()} y desgaste"
