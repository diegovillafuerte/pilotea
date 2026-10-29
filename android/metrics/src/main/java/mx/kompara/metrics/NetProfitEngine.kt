package mx.kompara.metrics

import mx.kompara.data.accounting.MONEY_CONTEXT
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import java.math.BigDecimal
import javax.inject.Inject

/** Complete legs only; no unit substitution, commission guess or fixed car allocation. */
class NetProfitEngine @Inject constructor() {
    fun evaluate(offer: TripOffer, costProfile: CostProfile, threshold: PlatformThreshold,
        preferredMetric: PreferredMetric = PreferredMetric.DEFAULT): OfferMetrics {
        val fuel = costProfile.fuelCostPerKm.takeIf { it.isFinite() && it >= 0 }?.toBigDecimal()
        val wear = costProfile.maintenancePerKm.takeIf { it.isFinite() && it >= 0 }?.toBigDecimal()
        return evaluate(offer, fuel?.let { wear?.add(it) }, threshold, preferredMetric)
    }

    fun evaluate(offer: TripOffer, costPerKm: BigDecimal?, threshold: PlatformThreshold,
        preferredMetric: PreferredMetric = PreferredMetric.DEFAULT): OfferMetrics {
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
        val net = if (offer.paymentBasis == "DRIVER_PAYMENT" && cost != null && km != null) gross?.subtract(cost.multiply(km)) else null
        val perKm = rate(net, km)
        val perMin = rate(net, minutes)
        val perHour = rate(net?.multiply(BigDecimal(60)), minutes)
        val kmLevel = classify(perKm, threshold.minPerKmMxn, threshold.redPerKmMxn)
        val hourLevel = classify(perHour, threshold.minPerHourMxn, threshold.redPerHourMxn)
        val chosen = if (preferredMetric == PreferredMetric.IPK) kmLevel else hourLevel
        val reason = when {
            gross == null -> "FARE"
            offer.paymentBasis != "DRIVER_PAYMENT" -> "PAYMENT_BASIS"
            km == null || (preferredMetric == PreferredMetric.IPK && km.signum() <= 0) -> "DISTANCE"
            cost == null -> "COST"
            preferredMetric == PreferredMetric.IPH && (minutes == null || minutes.signum() <= 0) -> "TIME"
            chosen == null -> "LIMITS"
            else -> null
        }
        val grossPerKm = rate(gross, km)
        val verdict = Verdict(level = chosen ?: VerdictLevel.UNKNOWN, unavailableReason = reason,
            netPerKm = perKm?.toDouble(), netPerHourEquivalent = perHour?.toDouble(), netProfitMxn = net?.toDouble(),
            grossPerKm = grossPerKm?.toDouble(), missingInputs = missing, netPerKmLevel = kmLevel,
            netPerHourLevel = hourLevel, preferredMetric = preferredMetric)
        return OfferMetrics(gross?.toDouble(), net?.toDouble(), km?.toDouble(), minutes?.toDouble(),
            grossPerKm?.toDouble(), rate(gross, minutes)?.toDouble(), perKm?.toDouble(), perMin?.toDouble(),
            perHour?.toDouble(), verdict, net?.toPlainString(), perKm?.toPlainString(), perHour?.toPlainString())
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
