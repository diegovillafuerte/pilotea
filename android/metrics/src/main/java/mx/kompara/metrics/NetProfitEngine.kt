package mx.kompara.metrics

import mx.kompara.data.accounting.MONEY_CONTEXT
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.RateBasis
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Complete legs only; no unit substitution, commission guess or fixed car allocation. The verdict
 * judges the rate in [RateBasis] (net or gross) against limits kept in that basis; only a
 * DRIVER_PAYMENT fare is judged in either basis, and a missing cost blocks only a NET verdict.
 */
class NetProfitEngine @Inject constructor() {
    fun evaluate(offer: TripOffer, costProfile: CostProfile, threshold: PlatformThreshold,
        preferredMetric: PreferredMetric = PreferredMetric.DEFAULT, rateBasis: RateBasis = RateBasis.DEFAULT): OfferMetrics {
        val fuel = costProfile.fuelCostPerKm.takeIf { it.isFinite() && it >= 0 }?.toBigDecimal()
        val wear = costProfile.maintenancePerKm.takeIf { it.isFinite() && it >= 0 }?.toBigDecimal()
        return evaluate(offer, fuel?.let { wear?.add(it) }, threshold, preferredMetric, rateBasis)
    }

    fun evaluate(offer: TripOffer, costPerKm: BigDecimal?, threshold: PlatformThreshold,
        preferredMetric: PreferredMetric = PreferredMetric.DEFAULT, rateBasis: RateBasis = RateBasis.DEFAULT): OfferMetrics {
        fun valid(value: Double?) = value?.takeIf { it.isFinite() && it >= 0 }?.toBigDecimal()
        val fields = linkedMapOf("fareMxn" to valid(offer.fareMxn), "pickupKm" to valid(offer.pickupKm),
            "pickupMin" to valid(offer.pickupMin), "tripKm" to valid(offer.tripKm), "tripMin" to valid(offer.tripMin))
        val missing = fields.filterValues { it == null }.keys.toList()
        fun sum(a: String, b: String): BigDecimal? = fields[a]?.let { x -> fields[b]?.add(x) }
        fun rate(value: BigDecimal?, denominator: BigDecimal?): BigDecimal? =
            if (value != null && denominator != null && denominator.signum() > 0) value.divide(denominator, MONEY_CONTEXT) else null
        val km = sum("pickupKm", "tripKm")
        val minutes = sum("pickupMin", "tripMin")
        val gross = fields["fareMxn"]
        val cost = costPerKm?.takeIf { it.signum() >= 0 }
        val driverPayment = offer.paymentBasis == "DRIVER_PAYMENT"
        val net = if (driverPayment && cost != null && km != null) gross?.subtract(cost.multiply(km)) else null
        fun judged(value: BigDecimal?, unit: PreferredMetric): BigDecimal? = value?.let { amount ->
            (if (unit == PreferredMetric.IPK) km else minutes)?.takeIf { it.signum() > 0 }?.let { judgedRate(amount, it, unit) }
        }
        val perKm = judged(net, PreferredMetric.IPK)
        val perMin = rate(net, minutes)
        val perHour = judged(net, PreferredMetric.IPH)
        val grossPerKm = judged(gross, PreferredMetric.IPK)
        val grossPerHour = judged(gross, PreferredMetric.IPH)
        val judgeNet = rateBasis == RateBasis.NET
        val judgedKm = if (judgeNet) perKm else grossPerKm?.takeIf { driverPayment }
        val judgedHour = if (judgeNet) perHour else grossPerHour?.takeIf { driverPayment }
        val kmLevel = classify(judgedKm, threshold.minPerKmMxn, threshold.redPerKmMxn)
        val hourLevel = classify(judgedHour, threshold.minPerHourMxn, threshold.redPerHourMxn)
        val chosen = if (preferredMetric == PreferredMetric.IPK) kmLevel else hourLevel
        val reason = when {
            gross == null -> "FARE"
            !driverPayment -> "PAYMENT_BASIS"
            (km == null && (judgeNet || preferredMetric == PreferredMetric.IPK)) ||
                (preferredMetric == PreferredMetric.IPK && km != null && km.signum() <= 0) -> "DISTANCE"
            judgeNet && cost == null -> "COST"
            preferredMetric == PreferredMetric.IPH && (minutes == null || minutes.signum() <= 0) -> "TIME"
            chosen == null -> "LIMITS"
            else -> null
        }
        val verdict = Verdict(level = chosen ?: VerdictLevel.UNKNOWN, unavailableReason = reason,
            netPerKm = perKm?.toDouble(), netPerHourEquivalent = perHour?.toDouble(), netProfitMxn = net?.toDouble(),
            grossPerKm = grossPerKm?.toDouble(), missingInputs = missing, netPerKmLevel = kmLevel,
            netPerHourLevel = hourLevel, preferredMetric = preferredMetric, rateBasis = rateBasis)
        return OfferMetrics(gross?.toDouble(), net?.toDouble(), km?.toDouble(), minutes?.toDouble(),
            grossPerKm?.toDouble(), rate(gross, minutes)?.toDouble(), perKm?.toDouble(), perMin?.toDouble(),
            perHour?.toDouble(), verdict, net?.toPlainString(), perKm?.toPlainString(), perHour?.toPlainString(),
            grossPerHour?.toDouble())
    }

    private fun classify(value: BigDecimal?, goal: Double, minimum: Double): VerdictLevel? {
        if (value == null || !goal.isFinite() || !minimum.isFinite() || minimum < 0 || minimum >= goal) return null
        return when {
            value >= goal.toBigDecimal() -> VerdictLevel.GREEN
            value >= minimum.toBigDecimal() -> VerdictLevel.YELLOW
            else -> VerdictLevel.RED
        }
    }
}

/**
 * The rate the engine judges: [amount] (the net or the fare) per km of a positive [denominator], or
 * per hour of [denominator] minutes. [CounterOffers] checks its fares through this same calculation.
 */
internal fun judgedRate(amount: BigDecimal, denominator: BigDecimal, unit: PreferredMetric): BigDecimal =
    (if (unit == PreferredMetric.IPK) amount else amount.multiply(BigDecimal(60))).divide(denominator, MONEY_CONTEXT)
