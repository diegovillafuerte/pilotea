package mx.kompara.metrics

import mx.kompara.data.db.entity.CostProfileEntity

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

    /** Null-safe convenience: a null entity (no profile saved yet) maps to [CostProfile.ZERO]. */
    fun toCostProfileOrZero(entity: CostProfileEntity?): CostProfile =
        entity?.let { toCostProfile(it) } ?: CostProfile.ZERO
}
