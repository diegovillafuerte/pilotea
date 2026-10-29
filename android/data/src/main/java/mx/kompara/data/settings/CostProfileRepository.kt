package mx.kompara.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import mx.kompara.data.accounting.*
import mx.kompara.data.db.entity.CostVersionEntity
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
    /** Compatibility projection of the effective version; a future edit cannot change today's chip. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val profile: Flow<CostProfileEntity?> = combine(dao.observe(), dao.observeVersions()) { legacy, rows ->
        legacy to rows.map { it.toDomain() }
    }.onStart { ensureTypicalVersion() }.transformLatest { (legacy, versions) ->
        while (true) {
            val now = System.currentTimeMillis()
            emit(project(legacy, CostSchedule(versions).runningAt(now)))
            val next = versions.map { it.effectiveFrom }.filter { it > now }.minOrNull() ?: break
            delay((next - now).coerceAtLeast(1))
        }
    }

    /** One-shot read of the active profile. */
    suspend fun get(at: Long = System.currentTimeMillis()): CostProfileEntity? {
        ensureTypicalVersion()
        return project(dao.get(), CostSchedule(dao.versions().map { it.toDomain() }).runningAt(at))
    }

    private fun project(legacy: CostProfileEntity?, version: RunningCostVersion?): CostProfileEntity? {
        if (version == null || version.confirmation == CostConfirmation.TYPICAL) return null
        val inputs = version.inputs
        val rate = inputs.costPerKm ?: return null
        val wear = if (inputs.mode == RunningCostMode.DIRECT) java.math.BigDecimal.ZERO else inputs.wearPerKm
        return CostProfileEntity(updatedAt = version.recordedAt, fuelPerKmMxn = rate.subtract(wear).toDouble(),
            maintenancePerKmMxn = wear.toDouble(), insurancePerDayMxn = legacy?.insurancePerDayMxn ?: 0.0,
            rentPerDayMxn = legacy?.rentPerDayMxn ?: 0.0, rendimientoKmPerLitre = inputs.kmPerLitre?.takeIf { inputs.mode == RunningCostMode.GAS || inputs.mode == RunningCostMode.LEGACY }?.toDouble() ?: 0.0,
            gasPricePerLitreMxn = inputs.fuelPricePerLitre?.takeIf { inputs.mode == RunningCostMode.GAS || inputs.mode == RunningCostMode.LEGACY }?.toDouble() ?: 0.0,
            workDaysPerWeek = legacy?.workDaysPerWeek ?: CostProfileEntity.DEFAULT_WORK_DAYS_PER_WEEK)
    }

    val versions: Flow<List<CostVersionEntity>> = dao.observeVersions().onStart { ensureTypicalVersion() }

    suspend fun runningVersions(): List<RunningCostVersion> {
        ensureTypicalVersion()
        return dao.versions().map { it.toDomain() }
    }

    private suspend fun ensureTypicalVersion() {
        val now = System.currentTimeMillis()
        dao.initializeVersion(CostVersionEntity.from(RunningCostVersion(0, now, now,
            RunningCostInputs.TYPICAL, CostConfirmation.TYPICAL)))
    }

    /** Appends an effective version and updates the old caller projection in one transaction. */
    suspend fun saveRunningCosts(
        inputs: RunningCostInputs,
        effectiveFrom: Long,
        recordedAt: Long,
        confirmation: CostConfirmation = CostConfirmation.CONFIRMED,
        legacyInsurancePerDay: Double? = null,
        legacyRentPerDay: Double? = null,
        correctEfficiency: Boolean = false,
    ): Long {
        val rate = requireNotNull(inputs.costPerKm) { "Incomplete running costs" }
        val old = dao.get()
        val profile = CostProfileEntity(
            updatedAt = recordedAt,
            fuelPerKmMxn = if (inputs.mode == RunningCostMode.DIRECT) rate.toDouble() else rate.subtract(inputs.wearPerKm).toDouble(),
            maintenancePerKmMxn = if (inputs.mode == RunningCostMode.DIRECT) 0.0 else inputs.wearPerKm.toDouble(),
            insurancePerDayMxn = legacyInsurancePerDay ?: old?.insurancePerDayMxn ?: 0.0,
            rentPerDayMxn = legacyRentPerDay ?: old?.rentPerDayMxn ?: 0.0,
            rendimientoKmPerLitre = inputs.kmPerLitre?.takeIf { inputs.mode == RunningCostMode.GAS || inputs.mode == RunningCostMode.LEGACY }?.toDouble() ?: 0.0,
            gasPricePerLitreMxn = inputs.fuelPricePerLitre?.takeIf { inputs.mode == RunningCostMode.GAS || inputs.mode == RunningCostMode.LEGACY }?.toDouble() ?: 0.0,
            workDaysPerWeek = old?.workDaysPerWeek ?: CostProfileEntity.DEFAULT_WORK_DAYS_PER_WEEK,
        )
        return dao.saveVersion(profile, CostVersionEntity.from(RunningCostVersion(0, effectiveFrom, recordedAt,
            inputs, confirmation, if (confirmation == CostConfirmation.TYPICAL) null else recordedAt)), correctEfficiency)
    }

    /** Persist [profile] as the single active cost profile (replaces any existing one). */
    suspend fun save(profile: CostProfileEntity) {
        val fuel = if (profile.fuelPerKmMxn > 0) profile.fuelPerKmMxn else if (profile.rendimientoKmPerLitre > 0)
            profile.gasPricePerLitreMxn / profile.rendimientoKmPerLitre else profile.fuelPerKmMxn
        val inputs = RunningCostInputs(RunningCostMode.LEGACY, profile.maintenancePerKmMxn.decimal(), directPerKm = fuel.decimal())
        requireNotNull(inputs.costPerKm)
        dao.saveVersion(profile.copy(id = CostProfileEntity.SINGLETON_ID), CostVersionEntity.from(
            RunningCostVersion(0, profile.updatedAt, profile.updatedAt, inputs, CostConfirmation.LEGACY_CONFIRMED, profile.updatedAt)))
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
        val inputs = RunningCostInputs(RunningCostMode.GAS, maintenancePerKmMxn.decimal(),
            gasPricePerLitreMxn.decimal(), rendimientoKmPerLitre.decimal())
        val rate = requireNotNull(inputs.costPerKm)
        val profile = CostProfileEntity(updatedAt = updatedAt,
            fuelPerKmMxn = rate.subtract(inputs.wearPerKm).toDouble(), maintenancePerKmMxn = maintenancePerKmMxn,
            insurancePerDayMxn = insurancePerDayMxn, rentPerDayMxn = rentPerDayMxn,
            rendimientoKmPerLitre = rendimientoKmPerLitre, gasPricePerLitreMxn = gasPricePerLitreMxn,
            workDaysPerWeek = workDaysPerWeek)
        dao.saveVersion(profile, CostVersionEntity.from(RunningCostVersion(0, updatedAt, updatedAt,
            inputs, CostConfirmation.CONFIRMED, updatedAt)))
    }
}
