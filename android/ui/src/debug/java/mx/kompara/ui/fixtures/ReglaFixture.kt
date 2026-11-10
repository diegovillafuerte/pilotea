package mx.kompara.ui.fixtures

import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.RateBasis
import mx.kompara.metrics.CounterOffers
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.TripOffer
import mx.kompara.metrics.VerdictLevel
import mx.kompara.metrics.rate
import mx.kompara.ui.components.*
import java.math.BigDecimal
import java.math.MathContext

/** Frozen offer states from the v3 kit; fixture values never enter production. */
fun V3Fixture.regla(frame: String): ReglaModel {
    require(frame !in setOf("C6", "C11", "C13")) { "$frame has no verdict plate" }
    val name = when (frame) {
        "C2", "C9", "N9" -> "amarillo"; "C3" -> "rojo"; "C7" -> "rojo_negativo"; else -> "verde"
    }
    val goal = goal.toBigDecimal()
    val minimum = minimum.toBigDecimal()
    val rate = requireNotNull(chip(name).ratePerKm)
    val band = when (frame) {
        "C4", "N10" -> ReglaBand.Unavailable(ReglaUnavailable.FARE)
        "C5" -> ReglaBand.Unavailable(ReglaUnavailable.TIME)
        else -> ReglaBand.Rated(when {
            rate >= goal -> ReglaPosition.ABOVE
            rate >= minimum -> ReglaPosition.MEETS
            else -> ReglaPosition.BELOW
        }, rate)
    }
    return ReglaModel(band, if (frame == "C5") ReglaUnit.HOUR else ReglaUnit.KM)
}

/**
 * The v4 kit's chip frames (part-s.html): each offer evaluated by the engine with the kit's cost
 * (24.50 ÷ 12 + 0.80 per km) and limits ($8 / $6 neto, $11 / $9 bruto); bids carry their counter-offers.
 */
fun reglaFrame(frame: String): ReglaModel {
    val cost = BigDecimal("24.50").divide(BigDecimal(12), MathContext.DECIMAL128).add(BigDecimal("0.80"))
    val basis = if (frame == "S10") RateBasis.GROSS else RateBasis.NET
    val limits = if (basis == RateBasis.GROSS) PlatformThreshold(11.0, 280.0, 9.0, 230.0) else PlatformThreshold(8.0, 200.0, 6.0, 150.0)
    val offer = when (frame) {
        "S1", "S7", "S10" -> TripOffer("UBER", 148.0, 2.0, 6.0, 10.0, 18.0)
        "S2" -> TripOffer("UBER", 120.0, 2.0, 5.0, 10.0, 22.0)
        "S3" -> TripOffer("UBER", 62.0, 5.2, 12.0, 7.1, 18.0)
        "S4" -> TripOffer("UBER", null, 1.5, 4.0, 6.0, 15.0)
        "S5" -> TripOffer("DIDI", 90.05, 2.0, 8.0, 20.9, 36.0)
        "S9" -> TripOffer("DIDI", 96.0, 1.2, 4.0, 9.2, 19.0)
        else -> error("$frame has no chip")
    }
    val metrics = NetProfitEngine().evaluate(offer, cost, limits, PreferredMetric.IPK, basis)
    val position = when (metrics.verdict.level) {
        VerdictLevel.GREEN -> ReglaPosition.ABOVE
        VerdictLevel.YELLOW -> ReglaPosition.MEETS
        VerdictLevel.RED -> ReglaPosition.BELOW
        VerdictLevel.UNKNOWN -> null
    }
    val band = position?.let { ReglaBand.Rated(it, requireNotNull(metrics.rate(PreferredMetric.IPK, basis))) }
        ?: ReglaBand.Unavailable(ReglaUnavailable.FARE)
    val counters = if (offer.platform == "DIDI") CounterOffers.of(metrics, limits.minPerKmMxn.toBigDecimal(),
        limits.redPerKmMxn.toBigDecimal(), PreferredMetric.IPK, basis) else null
    return ReglaModel(band, ReglaUnit.KM, basis, counters)
}
