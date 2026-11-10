package mx.kompara.metrics

import mx.kompara.data.accounting.CarCostVersion
import mx.kompara.data.accounting.CostSchedule
import mx.kompara.data.accounting.PeriodWindow
import java.math.BigDecimal
import java.time.DayOfWeek

/**
 * The car as a weekly bill (v4 plan 4.5): the weekly amount, whole, against the week's
 * contribution. Nothing is charged per day and no figure subtracts it; it is only drawn on the week.
 */
data class CarWeek(
    /** The weekly car amount in force that week; null when none is set (no amount, removed, or zero). */
    val bill: BigDecimal?,
    /** Monday … Sunday contribution (fares − gasoline and wear); null for a day not yet elapsed. */
    val days: List<BigDecimal?>,
) {
    val earned: BigDecimal get() = days.fold(BigDecimal.ZERO) { sum, day -> sum.add(day ?: BigDecimal.ZERO) }

    /**
     * "Cubierto el martes": the first day from which the week's running total never falls below the
     * bill again; null while the running total is under it, even after it once reached it.
     */
    val coveredOn: DayOfWeek? get() {
        val bill = bill ?: return null
        var total = BigDecimal.ZERO
        var from: DayOfWeek? = null
        days.forEachIndexed { i, day ->
            total = total.add(day ?: BigDecimal.ZERO)
            from = if (total < bill) null else from ?: DayOfWeek.of(i + 1)
        }
        return from
    }

    /** "Te faltan" / "Te faltaron": bill − earned, while not covered. */
    val missing: BigDecimal? get() = bill?.takeIf { coveredOn == null }?.subtract(earned)

    /** "Tuyo": earned − bill, once covered; never negative. */
    val yours: BigDecimal? get() = bill?.takeIf { coveredOn != null }?.let { earned.subtract(it) }

    companion object {
        /** The amount in force on the week's last elapsed day applies to the whole week. */
        fun of(car: List<CarCostVersion>, week: PeriodWindow, days: List<BigDecimal?>): CarWeek =
            CarWeek(CostSchedule(emptyList(), car).carAt(week.elapsedEnd)?.weeklyMxn?.takeIf { it.signum() > 0 }, days)
    }
}
