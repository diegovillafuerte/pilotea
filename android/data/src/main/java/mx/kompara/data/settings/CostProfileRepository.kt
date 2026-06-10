package mx.kompara.data.settings

import kotlinx.coroutines.flow.Flow
import mx.kompara.data.db.dao.CostProfileDao
import mx.kompara.data.db.entity.CostProfileEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flow-based persistence for the single active cost profile, backed by Room ([CostProfileDao]).
 *
 * The metrics engine consumes the profile, so changes need to flow through reactively — the
 * exposed [profile] re-emits whenever the driver edits their costs, which is what lets verdicts
 * update instantly (B-032 acceptance criterion "cost profile changes reflect instantly").
 */
@Singleton
class CostProfileRepository @Inject constructor(
    private val dao: CostProfileDao,
) {
    /** The active cost profile, or null until the driver saves one. Re-emits on every change. */
    val profile: Flow<CostProfileEntity?> = dao.observe()

    /** One-shot read of the active profile. */
    suspend fun get(): CostProfileEntity? = dao.get()

    /** Persist [profile] as the single active cost profile (replaces any existing one). */
    suspend fun save(profile: CostProfileEntity) {
        dao.upsert(profile.copy(id = CostProfileEntity.SINGLETON_ID))
    }

    /**
     * Convenience builder: persist a profile, deriving fuel $/km from rendimiento × gas price.
     *
     * @param updatedAt epoch millis (caller supplies the clock so this stays testable)
     * @param rendimientoKmPerLitre vehicle efficiency in km/L
     * @param gasPricePerLitreMxn fuel price in MXN/L
     */
    suspend fun saveFromInputs(
        updatedAt: Long,
        rendimientoKmPerLitre: Double,
        gasPricePerLitreMxn: Double,
        maintenancePerKmMxn: Double,
        insurancePerDayMxn: Double,
        rentPerDayMxn: Double,
        workDaysPerWeek: Int = CostProfileEntity.DEFAULT_WORK_DAYS_PER_WEEK,
    ) {
        val fuelPerKm = if (rendimientoKmPerLitre > 0.0) {
            gasPricePerLitreMxn / rendimientoKmPerLitre
        } else {
            0.0
        }
        save(
            CostProfileEntity(
                updatedAt = updatedAt,
                fuelPerKmMxn = fuelPerKm,
                maintenancePerKmMxn = maintenancePerKmMxn,
                insurancePerDayMxn = insurancePerDayMxn,
                rentPerDayMxn = rentPerDayMxn,
                rendimientoKmPerLitre = rendimientoKmPerLitre,
                gasPricePerLitreMxn = gasPricePerLitreMxn,
                workDaysPerWeek = workDaysPerWeek,
            ),
        )
    }
}
