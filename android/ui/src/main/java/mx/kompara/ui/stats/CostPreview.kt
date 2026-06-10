package mx.kompara.ui.stats

import mx.kompara.metrics.CostProfile

/**
 * Live preview math for the cost-profile editor (B-040 requirement 4): from the raw editor inputs,
 * derive the headline "tu costo por km: $X.XX" and show its effect on a fixed sample trip, so the
 * driver sees the consequence of each keystroke before saving.
 *
 * Pure and Android-free. Reuses [CostProfile.fuelCostPerKmFrom] so the editor and the engine agree
 * on how rendimiento × gas price becomes $/km. EV mode swaps the fuel term for kWh × $/kWh ÷ 100
 * (efficiency entered as kWh per 100 km, the way EV range is quoted in MX).
 */
object CostPreview {

    /** A representative trip used to illustrate the net effect; the design's sample card. */
    const val SAMPLE_FARE_MXN: Double = 120.0
    const val SAMPLE_KM: Double = 12.0

    /**
     * @param isEv when true, fuel cost is computed from [kwhPer100Km] × [costPerKwhMxn]; otherwise from
     *   [rendimientoKmPerLitre] (km/L) and [gasPricePerLitreMxn].
     */
    fun compute(
        isEv: Boolean,
        rendimientoKmPerLitre: Double,
        gasPricePerLitreMxn: Double,
        kwhPer100Km: Double,
        costPerKwhMxn: Double,
        maintenancePerKmMxn: Double,
    ): CostPreviewResult {
        val energyPerKm = if (isEv) {
            evCostPerKm(kwhPer100Km, costPerKwhMxn)
        } else {
            CostProfile.fuelCostPerKmFrom(rendimientoKmPerLitre, gasPricePerLitreMxn)
        }
        val marginalPerKm = (energyPerKm + maintenancePerKmMxn).coerceAtLeast(0.0)

        val sampleCost = SAMPLE_KM * marginalPerKm
        val sampleNet = SAMPLE_FARE_MXN - sampleCost
        return CostPreviewResult(
            energyPerKmMxn = energyPerKm,
            maintenancePerKmMxn = maintenancePerKmMxn.coerceAtLeast(0.0),
            marginalCostPerKmMxn = marginalPerKm,
            sampleFareMxn = SAMPLE_FARE_MXN,
            sampleKm = SAMPLE_KM,
            sampleCostMxn = sampleCost,
            sampleNetMxn = sampleNet,
        )
    }

    /**
     * EV energy cost per km from consumption (kWh per 100 km) and tariff (MXN/kWh).
     * Cost/km = kWh/100km ÷ 100 × $/kWh. Non-positive consumption ⇒ 0 (don't penalise unknown).
     */
    fun evCostPerKm(kwhPer100Km: Double, costPerKwhMxn: Double): Double =
        if (kwhPer100Km > 0.0) (kwhPer100Km / 100.0) * costPerKwhMxn else 0.0
}

/**
 * The live-preview output. [marginalCostPerKmMxn] is the headline figure; the sample-trip fields
 * illustrate it on [CostPreview.SAMPLE_FARE_MXN] over [CostPreview.SAMPLE_KM] km.
 */
data class CostPreviewResult(
    val energyPerKmMxn: Double,
    val maintenancePerKmMxn: Double,
    val marginalCostPerKmMxn: Double,
    val sampleFareMxn: Double,
    val sampleKm: Double,
    val sampleCostMxn: Double,
    val sampleNetMxn: Double,
)
