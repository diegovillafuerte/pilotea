package mx.kompara.metrics

import mx.kompara.data.db.entity.CostProfileEntity
import mx.kompara.data.accounting.RunningCostVersion
import mx.kompara.data.accounting.RunningCostMode

/**
 * Bridges `:data`'s persisted [CostProfileEntity] to the engine's pure [CostProfile]. Lives in
 * `:metrics` (not `:data`) because `:metrics` depends on `:data`, never the other way round — so
 * the Room entity can't reference the engine model.
 */
object CostProfileMapper {

    /**
     * Map a stored entity to a [CostProfile].
     *
     * Fuel $/km: prefer the explicit [CostProfileEntity.fuelPerKmMxn]; when it's 0 but the driver
     * supplied rendimiento + gas price, derive it via [CostProfile.fuelCostPerKmFrom]. Daily fixed
     * costs fold insurance and rent together (both are per-day already).
     */
    fun toCostProfile(entity: CostProfileEntity): CostProfile {
        val fuelPerKm = if (entity.fuelPerKmMxn > 0.0) {
            entity.fuelPerKmMxn
        } else {
            CostProfile.fuelCostPerKmFrom(
                rendimientoKmPerLitre = entity.rendimientoKmPerLitre,
                gasPricePerLitreMxn = entity.gasPricePerLitreMxn,
            )
        }
        return CostProfile(
            fuelCostPerKm = fuelPerKm,
            maintenancePerKm = entity.maintenancePerKmMxn,
            dailyFixedCosts = entity.insurancePerDayMxn + entity.rentPerDayMxn,
            workDaysPerWeek = entity.workDaysPerWeek,
        )
    }

    /** Decimal version adapter. Invalid inputs stay unavailable rather than becoming free costs. */
    fun toCostProfile(version: RunningCostVersion): CostProfile? {
        val rate = version.inputs.costPerKm ?: return null
        val wear = if (version.inputs.mode == RunningCostMode.DIRECT) 0.0 else version.inputs.wearPerKm.toDouble()
        return CostProfile(rate.toDouble() - wear, wear)
    }

    /** Compatibility name: absence now uses [CostProfile.TYPICAL], never an optimistic zero. */
    fun toCostProfileOrZero(entity: CostProfileEntity?): CostProfile =
        entity?.let { toCostProfile(it) } ?: CostProfile.TYPICAL
}
