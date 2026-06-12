package mx.kompara.metrics

import mx.kompara.data.settings.PlatformThreshold
import javax.inject.Inject

/**
 * Turns a raw [TripOffer] into net economics and a traffic-light [Verdict], applying the driver's
 * [CostProfile] and a per-platform [PlatformThreshold].
 *
 * Pure and deterministic — no Android, no IO — so it is fully unit-testable.
 *
 * ## What "distance" and "time" mean
 * Total distance = pickup leg + trip leg; total time = pickup ETA + trip duration. The dead km/min
 * spent reaching the rider burn real fuel and real time, so folding them into the denominators is
 * what makes the net rate honest (and matches how Uber's own gross badge counts). Only the
 * **marginal** per-km cost (fuel + maintenance) reduces a single offer's net — daily fixed costs
 * (insurance, rent) are a shift-level concern, not a per-offer one.
 *
 * ## Graceful degradation
 * Real captures are lossy. The engine judges with whatever it has:
 *  - missing fare → it cannot value the offer at all → RED, and every dependent rate is null.
 *  - missing a distance/time leg → the corresponding rate is null and that floor is simply not
 *    tested; the verdict is decided on the floor(s) that *can* be tested, and capped at YELLOW
 *    because the read is provisional.
 *  - both legs of one dimension missing → that floor can't be tested.
 * Whatever was absent is listed in [Verdict.missingInputs].
 */
class NetProfitEngine @Inject constructor() {

    /**
     * Evaluate [offer] against [costProfile] and [threshold] into a full [OfferMetrics] breakdown.
     */
    fun evaluate(
        offer: TripOffer,
        costProfile: CostProfile,
        threshold: PlatformThreshold,
    ): OfferMetrics {
        val missing = mutableListOf<String>()
        if (offer.fareMxn == null) missing.add("fareMxn")
        if (offer.pickupKm == null) missing.add("pickupKm")
        if (offer.pickupMin == null) missing.add("pickupMin")
        if (offer.tripKm == null) missing.add("tripKm")
        if (offer.tripMin == null) missing.add("tripMin")

        // Total legs. Sum the parts that are present; null only when *both* parts are absent, so a
        // half-known total (e.g. trip km but no pickup km) still yields a usable — if slightly
        // optimistic — denominator.
        val totalKm = sumOrNull(offer.pickupKm, offer.tripKm)
        val totalMin = sumOrNull(offer.pickupMin, offer.tripMin)

        val gross = offer.fareMxn
        val marginalCost = totalKm?.let { it * costProfile.marginalCostPerKm }
        val net = if (gross != null) gross - (marginalCost ?: 0.0) else null

        val grossPerKm = rate(gross, totalKm)
        val grossPerMin = rate(gross, totalMin)
        val netPerKm = rate(net, totalKm)
        val netPerMin = rate(net, totalMin)
        val netPerHour = if (net != null && totalMin != null && totalMin > 0.0) {
            net / (totalMin / 60.0)
        } else {
            null
        }

        val level = decideLevel(
            netPerKm = netPerKm,
            netPerHour = netPerHour,
            fareKnown = gross != null,
            hasMissingInputs = missing.isNotEmpty(),
            threshold = threshold,
        )

        val verdict = Verdict(
            level = level,
            netPerKm = netPerKm,
            netPerHourEquivalent = netPerHour,
            netProfitMxn = net,
            grossPerKm = grossPerKm,
            missingInputs = missing.toList(),
        )

        return OfferMetrics(
            grossMxn = gross,
            netMxn = net,
            totalKm = totalKm,
            totalMin = totalMin,
            grossPerKm = grossPerKm,
            grossPerMin = grossPerMin,
            netPerKm = netPerKm,
            netPerMin = netPerMin,
            netPerHour = netPerHour,
            verdict = verdict,
        )
    }

    /**
     * Decide the traffic-light level from two-tier floors.
     *
     * Each testable metric classifies independently: at/above its green floor → GREEN, below its
     * red floor → RED, in between → YELLOW. The verdict is GREEN only when every testable metric
     * is GREEN, RED only when every testable metric is RED (or nothing is testable), and YELLOW
     * for any mix — so one strong metric never makes a confident GREEN, and one weak metric never
     * sinks an otherwise-good offer to RED. When the fare is missing nothing can be judged → RED.
     * When any input was missing the result is capped at YELLOW (a provisional read is never a
     * confident GREEN).
     */
    private fun decideLevel(
        netPerKm: Double?,
        netPerHour: Double?,
        fareKnown: Boolean,
        hasMissingInputs: Boolean,
        threshold: PlatformThreshold,
    ): VerdictLevel {
        if (!fareKnown) return VerdictLevel.RED

        val levels = listOfNotNull(
            metricLevel(netPerKm, green = threshold.minPerKmMxn, red = threshold.redPerKmMxn),
            metricLevel(netPerHour, green = threshold.minPerHourMxn, red = threshold.redPerHourMxn),
        )
        if (levels.isEmpty()) return VerdictLevel.RED

        val base = when {
            levels.all { it == VerdictLevel.GREEN } -> VerdictLevel.GREEN
            levels.all { it == VerdictLevel.RED } -> VerdictLevel.RED
            else -> VerdictLevel.YELLOW
        }

        // Partial data is never a confident GREEN.
        return if (hasMissingInputs && base == VerdictLevel.GREEN) VerdictLevel.YELLOW else base
    }

    /**
     * Classify one metric against its two floors, or null when the rate itself is unknown
     * (untestable). The red floor is clamped to the green floor so a misconfigured pair
     * (red > green) can never produce a band where RED outranks GREEN.
     */
    private fun metricLevel(value: Double?, green: Double, red: Double): VerdictLevel? {
        if (value == null) return null
        val redFloor = minOf(red, green)
        return when {
            value >= green -> VerdictLevel.GREEN
            value >= redFloor -> VerdictLevel.YELLOW
            else -> VerdictLevel.RED
        }
    }

    /** Sum two optional legs; null only when both are absent. A present part counts; absent ⇒ 0. */
    private fun sumOrNull(a: Double?, b: Double?): Double? =
        if (a == null && b == null) null else (a ?: 0.0) + (b ?: 0.0)

    /** numerator / denominator, or null if either is unknown or the denominator is non-positive. */
    private fun rate(numerator: Double?, denominator: Double?): Double? =
        if (numerator != null && denominator != null && denominator > 0.0) {
            numerator / denominator
        } else {
            null
        }
}
