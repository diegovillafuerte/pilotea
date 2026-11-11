package mx.kompara.metrics

import mx.kompara.data.accounting.Contribution
import mx.kompara.data.accounting.MONEY_CONTEXT
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.RateBasis
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Comparar's hour blocks, by the trip's local start hour: [fromHour] inclusive to [untilHour] exclusive. */
enum class HourBlock(val fromHour: Int, val untilHour: Int) {
    H6_10(6, 10), H10_14(10, 14), H14_18(14, 18), H18_22(18, 22), H22_6(22, 6);

    companion object {
        fun of(hour: Int): HourBlock = entries.first {
            if (it.fromHour < it.untilHour) hour >= it.fromHour && hour < it.untilHour else hour >= it.fromHour || hour < it.untilHour
        }
    }
}

/**
 * One group of taken trips. Rates are ratios of sums. Per hour uses only the trips with a known
 * duration ([minutes], [timedFare], [timedNet]), so a trip without one never skews the hour rate.
 */
data class SliceTotals(
    val trips: Int,
    val fare: BigDecimal,
    /** Σ (fare − cost per km × km); null when any trip's cost is unknown (the ledger's rule). */
    val net: BigDecimal?,
    val km: BigDecimal,
    val minutes: BigDecimal,
    val timedFare: BigDecimal,
    val timedNet: BigDecimal?,
) {
    /** A group needs [DriverSlices.MIN_TRIPS_PER_GROUP] trips to be drawn. */
    val drawable: Boolean get() = trips >= DriverSlices.MIN_TRIPS_PER_GROUP

    fun rate(unit: PreferredMetric, basis: RateBasis): BigDecimal? {
        val (amount, denominator) = if (unit == PreferredMetric.IPK) (if (basis == RateBasis.NET) net else fare) to km
            else (if (basis == RateBasis.NET) timedNet else timedFare)?.multiply(BigDecimal(60)) to minutes
        return if (amount == null || denominator.signum() <= 0) null else amount.divide(denominator, MONEY_CONTEXT)
    }
}

/** The driver's own taken trips of the window, compared with each other (Comparar · Tú, K1/K2). */
data class DriverSlices(
    val from: LocalDate,
    val to: LocalDate,
    val all: SliceTotals,
    val byApp: Map<String, SliceTotals>,
    /** Monday first; only weekdays with trips. */
    val byWeekday: Map<DayOfWeek, SliceTotals>,
    /** In [HourBlock] order; only blocks with trips. */
    val byHourBlock: Map<HourBlock, SliceTotals>,
) {
    val trips: Int get() = all.trips
    /** Under [MIN_TRIPS_TO_COMPARE] trips the screen shows "Aún juntamos tus viajes" (K2). */
    val ready: Boolean get() = trips >= MIN_TRIPS_TO_COMPARE

    companion object {
        const val WINDOW_DAYS = 28L
        const val MIN_TRIPS_TO_COMPARE = 30
        const val MIN_TRIPS_PER_GROUP = 3

        /** The 28 days before [today] ("últimas 4 semanas"); today stays out until it is complete. */
        fun window(today: LocalDate): ClosedRange<LocalDate> = today.minusDays(WINDOW_DAYS)..today.minusDays(1)

        /** [contributions]: the ledger's taken trips of the window (PeriodMoney.contributions). */
        fun of(from: LocalDate, to: LocalDate, contributions: List<Contribution>, zone: ZoneId): DriverSlices {
            val rows = contributions.filter { it.record.payment != null && it.record.km != null && it.record.startedAt != null }
            fun start(row: Contribution) = Instant.ofEpochMilli(row.record.startedAt!!).atZone(zone)
            return DriverSlices(from, to, totals(rows), rows.groupBy { it.record.app }.mapValues { totals(it.value) },
                rows.groupBy { start(it).dayOfWeek }.toSortedMap().mapValues { totals(it.value) },
                rows.groupBy { HourBlock.of(start(it).hour) }.toSortedMap().mapValues { totals(it.value) })
        }

        private fun totals(rows: List<Contribution>): SliceTotals {
            val timed = rows.filter { it.record.minutes != null }
            fun List<Contribution>.sum(value: (Contribution) -> BigDecimal) = fold(BigDecimal.ZERO) { s, row -> s.add(value(row)) }
            fun List<Contribution>.net() = if (any { it.tripContribution == null }) null else sum { it.tripContribution!! }
            return SliceTotals(rows.sumOf { it.record.trips }, rows.sum { it.record.payment!! }, rows.net(), rows.sum { it.record.km!! },
                timed.sum { it.record.minutes!! }, timed.sum { it.record.payment!! }, timed.net())
        }
    }
}
