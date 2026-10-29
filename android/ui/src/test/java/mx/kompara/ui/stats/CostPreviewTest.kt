package mx.kompara.ui.stats

import mx.kompara.data.accounting.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class CostPreviewTest {
    private val gas = CostProfileInputs.from(RunningCostInputs.TYPICAL)
    @Test fun `formula and sample use full decimal inputs`() {
        val result = CostPreview.compute(gas)!!
        assertEquals("$2.84", mx.kompara.ui.format.Formatters.formatMxn(result.perKm))
        assertEquals("$34", mx.kompara.ui.format.Formatters.formatMxnWhole(result.sampleCost))
        assertEquals(34.10, result.sampleCost.toDouble(), 1e-12)
        assertEquals(24.5 / 12 + .8, result.perKm.toDouble(), 1e-14)
    }
    @Test fun `gas limits include four and forty but empty garbage and out of range are unavailable`() {
        for (value in listOf("", "0", "3.99", "40.01", "-12", "NaN", "1e9")) assertNull(value, CostPreview.compute(gas.copy(rendimientoKmPerLitre = value)))
        for (value in listOf("4", "12,5", "40")) assertNotNull(value, CostPreview.compute(gas.copy(rendimientoKmPerLitre = value)))
        assertNull(CostPreview.compute(gas.copy(maintenancePerKmMxn = "bad")))
    }
    @Test fun `electric and direct modes preserve inactive inputs and a legitimate zero`() {
        val electric = gas.copy(isEv = true, kwhPer100Km = "18", costPerKwhMxn = "3,50", maintenancePerKmMxn = "0.5")
        assertEquals(1.13, CostPreview.compute(electric)!!.perKm.toDouble(), 1e-12)
        assertEquals(electric.runningCosts(), CostProfileInputs.from(electric.runningCosts()!!).runningCosts())
        val direct = electric.copy(direct = true, directPerKm = "$0")
        assertEquals(BigDecimal.ZERO, CostPreview.compute(direct)!!.perKm)
        assertEquals(BigDecimal.ZERO, CostPreview.compute(direct)!!.sampleCost)
        assertNull(CostPreview.compute(direct.copy(directPerKm = "")))
    }
    @Test fun `localized inputs never conflate grouping decimal zero and absence`() {
        assertEquals(BigDecimal("6.50"), costDecimal(" $6,50 "))
        assertEquals(BigDecimal.ZERO, costDecimal("0"))
        for (v in listOf("", "abc", "1,500.00", "-1", "1.2.3", "Infinity")) assertNull(v, costDecimal(v))
        assertEquals(gas, CostProfileInputs.decode(gas.encode()))
        assertEquals(BigDecimal("1500"), carCostDecimal("$1,500"))
        assertEquals(BigDecimal("1500.50"), carCostDecimal("1,500.50"))
        assertEquals(BigDecimal("1.50"), carCostDecimal("1,50"))
        assertEquals(BigDecimal("0.500"), carCostDecimal("0,500"))
    }
}
