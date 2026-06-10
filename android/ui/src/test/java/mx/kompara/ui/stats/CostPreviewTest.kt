package mx.kompara.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/** Live cost-per-km computation for [CostPreview] (B-040 req 4). */
class CostPreviewTest {

    @Test
    fun `gas cost per km is gas price over rendimiento plus maintenance`() {
        // 24.50 MXN/L ÷ 14 km/L = 1.75 $/km fuel; + 0.80 maintenance = 2.55 $/km.
        val r = CostPreview.compute(
            isEv = false,
            rendimientoKmPerLitre = 14.0,
            gasPricePerLitreMxn = 24.5,
            kwhPer100Km = 0.0,
            costPerKwhMxn = 0.0,
            maintenancePerKmMxn = 0.8,
        )
        assertEquals(1.75, r.energyPerKmMxn, 0.001)
        assertEquals(2.55, r.marginalCostPerKmMxn, 0.001)
        // Sample trip: 120 fare, 12 km → cost 12*2.55 = 30.6 → net 89.4.
        assertEquals(12.0 * 2.55, r.sampleCostMxn, 0.001)
        assertEquals(120.0 - 12.0 * 2.55, r.sampleNetMxn, 0.001)
    }

    @Test
    fun `EV cost per km uses kWh consumption and tariff`() {
        // 18 kWh/100km ÷ 100 × 3.50 $/kWh = 0.63 $/km energy; + 0.50 maintenance = 1.13 $/km.
        val r = CostPreview.compute(
            isEv = true,
            rendimientoKmPerLitre = 0.0,
            gasPricePerLitreMxn = 0.0,
            kwhPer100Km = 18.0,
            costPerKwhMxn = 3.5,
            maintenancePerKmMxn = 0.5,
        )
        assertEquals(0.63, r.energyPerKmMxn, 0.001)
        assertEquals(1.13, r.marginalCostPerKmMxn, 0.001)
    }

    @Test
    fun `zero or unknown rendimiento does not divide by zero`() {
        val r = CostPreview.compute(
            isEv = false,
            rendimientoKmPerLitre = 0.0,
            gasPricePerLitreMxn = 24.5,
            kwhPer100Km = 0.0,
            costPerKwhMxn = 0.0,
            maintenancePerKmMxn = 0.0,
        )
        assertEquals(0.0, r.energyPerKmMxn, 0.0)
        assertEquals(0.0, r.marginalCostPerKmMxn, 0.0)
        assertEquals(CostPreview.SAMPLE_FARE_MXN, r.sampleNetMxn, 0.0)
    }

    @Test
    fun `string parser tolerates commas, blanks and garbage`() {
        assertEquals(1.75, "1,75".toDoubleOrZero(), 0.001)
        assertEquals(0.0, "".toDoubleOrZero(), 0.0)
        assertEquals(0.0, "abc".toDoubleOrZero(), 0.0)
        assertEquals(24.5, " 24.5 ".toDoubleOrZero(), 0.001)
    }
}
