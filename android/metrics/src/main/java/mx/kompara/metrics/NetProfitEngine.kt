package mx.kompara.metrics

import mx.kompara.data.db.entity.CostProfileEntity
import mx.kompara.data.settings.PlatformThreshold
import javax.inject.Inject

/**
 * Turns a raw offer (gross fare, distance, time) into net economics and a traffic-light
 * [Verdict], applying the driver's [CostProfileEntity] and per-platform [PlatformThreshold].
 *
 * Pure and deterministic — no Android, no IO — so it is fully unit-testable. Per-day fixed
 * costs (insurance, rent/financing) are not amortized per offer here; only the marginal
 * per-km costs (fuel + maintenance) reduce a single offer's net.
 */
class NetProfitEngine @Inject constructor() {

    fun evaluate(
        grossMxn: Double,
        distanceKm: Double,
        durationMin: Double,
        costProfile: CostProfileEntity?,
        threshold: PlatformThreshold,
    ): OfferMetrics {
        val perKmCost = costProfile?.let { it.fuelPerKmMxn + it.maintenancePerKmMxn } ?: 0.0
        val net = grossMxn - perKmCost * distanceKm

        val perKm = if (distanceKm > 0.0) net / distanceKm else 0.0
        val perHour = if (durationMin > 0.0) net / (durationMin / 60.0) else 0.0

        val verdict = verdictFor(perKm, perHour, threshold)
        return OfferMetrics(netMxn = net, perKmMxn = perKm, perHourMxn = perHour, verdict = verdict)
    }

    private fun verdictFor(
        perKm: Double,
        perHour: Double,
        threshold: PlatformThreshold,
    ): Verdict {
        val kmOk = perKm >= threshold.minPerKmMxn
        val hourOk = perHour >= threshold.minPerHourMxn
        return when {
            kmOk && hourOk -> Verdict.GOOD
            kmOk || hourOk -> Verdict.MARGINAL
            else -> Verdict.BAD
        }
    }
}
