package mx.kompara.metrics

import mx.kompara.data.db.entity.CostProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CostProfileMapperTest {

    @Test
    fun `explicit fuel per km wins over derivation`() {
        val entity = CostProfileEntity(
            updatedAt = 0L,
            fuelPerKmMxn = 2.0,
            maintenancePerKmMxn = 0.5,
            insurancePerDayMxn = 50.0,
            rentPerDayMxn = 200.0,
            rendimientoKmPerLitre = 14.0,
            gasPricePerLitreMxn = 24.5,
        )
        val profile = CostProfileMapper.toCostProfile(entity)
        assertEquals(2.0, profile.fuelCostPerKm, 1e-9) // explicit, not 24.5/14
        assertEquals(0.5, profile.maintenancePerKm, 1e-9)
        assertEquals(250.0, profile.dailyFixedCosts, 1e-9) // 50 + 200
    }

    @Test
    fun `derives fuel per km when explicit value is zero`() {
        val entity = CostProfileEntity(
            updatedAt = 0L,
            fuelPerKmMxn = 0.0,
            maintenancePerKmMxn = 0.5,
            insurancePerDayMxn = 0.0,
            rentPerDayMxn = 0.0,
            rendimientoKmPerLitre = 14.0,
            gasPricePerLitreMxn = 24.5,
        )
        val profile = CostProfileMapper.toCostProfile(entity)
        assertEquals(24.5 / 14.0, profile.fuelCostPerKm, 1e-9)
    }

    @Test
    fun `carries work days through`() {
        val entity = CostProfileEntity(
            updatedAt = 0L,
            fuelPerKmMxn = 1.0,
            maintenancePerKmMxn = 0.0,
            insurancePerDayMxn = 0.0,
            rentPerDayMxn = 0.0,
            workDaysPerWeek = 5,
        )
        assertEquals(5, CostProfileMapper.toCostProfile(entity).workDaysPerWeek)
    }

    @Test
    fun `null entity maps to ZERO profile`() {
        assertEquals(CostProfile.ZERO, CostProfileMapper.toCostProfileOrZero(null))
    }

    @Test
    fun `non-null entity maps through toCostProfileOrZero`() {
        val entity = CostProfileEntity(
            updatedAt = 0L,
            fuelPerKmMxn = 1.5,
            maintenancePerKmMxn = 0.5,
            insurancePerDayMxn = 0.0,
            rentPerDayMxn = 0.0,
        )
        assertEquals(2.0, CostProfileMapper.toCostProfileOrZero(entity).marginalCostPerKm, 1e-9)
    }
}
