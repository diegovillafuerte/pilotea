package mx.kompara.data.settings

import mx.kompara.data.accounting.MONEY_CONTEXT
import mx.kompara.data.accounting.decimal
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Moves the driver's limits between [RateBasis] values (v4 plan 4.2). Per km: ± the cost per km,
 * rounded to the nearest $0.50. Per hour: each limit × (its per-km limit after ÷ before), rounded
 * to the per-hour editor's step. A pair that rounds to equal raises the goal one step, so the goal
 * stays above the minimum; nothing goes below zero.
 */
object LimitConversion {
    /** The per-km limit editor's step and the rounding unit of a converted per-km limit. */
    val KM_STEP = BigDecimal("0.50")

    fun convert(threshold: PlatformThreshold, from: RateBasis, to: RateBasis, costPerKm: BigDecimal,
        hourStep: BigDecimal): PlatformThreshold {
        if (from == to) return threshold
        val delta = if (to == RateBasis.GROSS) costPerKm else costPerKm.negate()
        val goalKmBefore = threshold.minPerKmMxn.decimal()
        val minKmBefore = threshold.redPerKmMxn.decimal()
        val (goalKm, minKm) = pair(goalKmBefore.add(delta), minKmBefore.add(delta), KM_STEP)
        val goalRatio = ratio(goalKm, goalKmBefore) ?: BigDecimal.ONE
        val minRatio = ratio(minKm, minKmBefore) ?: goalRatio
        val (goalHour, minHour) = pair(threshold.minPerHourMxn.decimal().multiply(goalRatio),
            threshold.redPerHourMxn.decimal().multiply(minRatio), hourStep)
        return PlatformThreshold(goalKm.toDouble(), goalHour.toDouble(), minKm.toDouble(), minHour.toDouble())
    }

    private fun ratio(after: BigDecimal, before: BigDecimal) =
        if (before.signum() > 0) after.divide(before, MONEY_CONTEXT) else null

    private fun pair(goal: BigDecimal, minimum: BigDecimal, step: BigDecimal): Pair<BigDecimal, BigDecimal> {
        val g = round(goal, step)
        val m = round(minimum, step)
        return (if (g > m) g else m.add(step)) to m
    }

    private fun round(value: BigDecimal, step: BigDecimal): BigDecimal =
        value.max(BigDecimal.ZERO).divide(step, 0, RoundingMode.HALF_UP).multiply(step)
}
