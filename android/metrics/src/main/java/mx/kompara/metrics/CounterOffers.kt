package mx.kompara.metrics

import mx.kompara.data.accounting.MONEY_CONTEXT
import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.RateBasis
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pon Tu Precio (v4 plan 4.4): the smallest whole-peso fare that the engine itself rates at or above
 * the minimum and the goal. Derived as per km: NET `L × km + running expense`, GROSS `L × km`; per
 * hour: NET `L × minutes ÷ 60 + running expense`, GROSS `L × minutes ÷ 60` (running expense =
 * fare − net), rounded up to the peso, then checked through [judgedRate] and moved a peso at a time
 * until it reaches and a peso less does not. Each is null when the offer already meets that limit.
 */
data class CounterOffers(val toMinimum: Int?, val toGoal: Int?) {
    companion object {
        /** Null when the fare, the unit's denominator or (in NET) the offer's net is unknown. */
        fun of(metrics: OfferMetrics, goal: BigDecimal, minimum: BigDecimal, unit: PreferredMetric, basis: RateBasis): CounterOffers? {
            val fare = metrics.grossMxn?.toBigDecimal() ?: return null
            val expense = if (basis == RateBasis.GROSS) BigDecimal.ZERO
                else fare.subtract(metrics.contributionDecimal?.toBigDecimalOrNull() ?: return null)
            val denominator = (if (unit == PreferredMetric.IPK) metrics.totalKm else metrics.totalMin)
                ?.toBigDecimal()?.takeIf { it.signum() > 0 } ?: return null
            fun reaches(at: BigDecimal, limit: BigDecimal) = judgedRate(at.subtract(expense), denominator, unit) >= limit
            fun reach(limit: BigDecimal): Int? {
                if (reaches(fare, limit)) return null
                val times = limit.multiply(denominator)
                val exact = (if (unit == PreferredMetric.IPK) times else times.divide(BigDecimal(60), MONEY_CONTEXT)).add(expense)
                var peso = exact.setScale(0, RoundingMode.CEILING)
                while (!reaches(peso, limit)) peso += BigDecimal.ONE
                while (reaches(peso - BigDecimal.ONE, limit)) peso -= BigDecimal.ONE
                return peso.intValueExact()
            }
            return CounterOffers(reach(minimum), reach(goal))
        }
    }
}

/** From the row's frozen goal, minimum, unit and basis; null without a frozen evaluation. */
fun OfferEntity.counterOffers(): CounterOffers? {
    val saved = evaluation ?: return null
    return CounterOffers.of(recordedMetrics(), saved.goal.toBigDecimal(), saved.minimum.toBigDecimal(),
        PreferredMetric.valueOf(saved.unit), RateBasis.fromName(saved.rateBasis))
}
