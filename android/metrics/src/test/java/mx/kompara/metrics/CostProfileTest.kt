package mx.kompara.metrics

import org.junit.Assert.assertEquals
import org.junit.Test

class CostProfileTest {

    @Test
    fun `fuel cost per km is gas price divided by rendimiento`() {
        // 24.5 MXN/L at 14 km/L -> 1.75 MXN/km
        assertEquals(1.75, CostProfile.fuelCostPerKmFrom(14.0, 24.5), 1e-9)
    }

    @Test
    fun `non-positive rendimiento yields zero fuel cost not infinity`() {
        assertEquals(0.0, CostProfile.fuelCostPerKmFrom(0.0, 24.5), 1e-9)
        assertEquals(0.0, CostProfile.fuelCostPerKmFrom(-5.0, 24.5), 1e-9)
    }

    @Test
    fun `marginal cost per km is fuel plus maintenance`() {
        val p = CostProfile(fuelCostPerKm = 1.75, maintenancePerKm = 0.65)
        assertEquals(2.40, p.marginalCostPerKm, 1e-9)
    }

    @Test
    fun `ZERO profile has no marginal cost`() {
        assertEquals(0.0, CostProfile.ZERO.marginalCostPerKm, 1e-9)
    }

    @Test
    fun `default work week is six days`() {
        assertEquals(6, CostProfile.DEFAULT_WORK_DAYS_PER_WEEK)
        assertEquals(6, CostProfile(1.0, 0.0).workDaysPerWeek)
    }
}
