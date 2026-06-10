package mx.kompara.metrics

/**
 * The per-offer economics the engine computes: net earnings and the normalized rates that the
 * thresholds are compared against. All money in MXN.
 */
data class OfferMetrics(
    val netMxn: Double,
    val perKmMxn: Double,
    val perHourMxn: Double,
    val verdict: Verdict,
)
