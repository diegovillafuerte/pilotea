package mx.kompara.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.kompara.data.db.entity.CostProfileEntity
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.metrics.rollup.RollupRecomputer
import javax.inject.Inject

/**
 * Backs the cost-profile editor (B-040 req 4): rendimiento + gas price (or EV kWh), maintenance,
 * insurance and rent per period, a live "tu costo por km: $X.XX" preview, and persistence.
 *
 * The editable fields are held as **strings** so the text fields can show exactly what the driver
 * typed (and an empty field reads as 0, not "0.00"); [CostProfileInputs.toDoubleOrZero] parses them
 * for the live [CostPreview] on every keystroke. Saving derives the resolved $/km and persists via
 * [CostProfileRepository], then kicks off a historical rollup recompute so past net views reflect
 * the new costs (acceptance criterion).
 */
@HiltViewModel
class CostProfileViewModel @Inject constructor(
    private val costProfileRepository: CostProfileRepository,
    private val rollupRecomputer: RollupRecomputer,
    private val clock: AppClock,
) : ViewModel() {

    private val _inputs = MutableStateFlow(CostProfileInputs())
    val inputs: StateFlow<CostProfileInputs> = _inputs.asStateFlow()

    private val _saved = MutableStateFlow(false)
    /** Flips true after a successful save; the screen can show a confirmation / navigate back. */
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        load()
    }

    /** The live preview for the current inputs. Recomputed by the screen on each [inputs] emission. */
    fun preview(inputs: CostProfileInputs): CostPreviewResult = CostPreview.compute(
        isEv = inputs.isEv,
        rendimientoKmPerLitre = inputs.rendimientoKmPerLitre.toDoubleOrZero(),
        gasPricePerLitreMxn = inputs.gasPricePerLitreMxn.toDoubleOrZero(),
        kwhPer100Km = inputs.kwhPer100Km.toDoubleOrZero(),
        costPerKwhMxn = inputs.costPerKwhMxn.toDoubleOrZero(),
        maintenancePerKmMxn = inputs.maintenancePerKmMxn.toDoubleOrZero(),
    )

    fun onEvToggled(isEv: Boolean) = _inputs.update { it.copy(isEv = isEv) }
    fun onRendimientoChanged(v: String) = _inputs.update { it.copy(rendimientoKmPerLitre = v) }
    fun onGasPriceChanged(v: String) = _inputs.update { it.copy(gasPricePerLitreMxn = v) }
    fun onKwhPer100Changed(v: String) = _inputs.update { it.copy(kwhPer100Km = v) }
    fun onCostPerKwhChanged(v: String) = _inputs.update { it.copy(costPerKwhMxn = v) }
    fun onMaintenanceChanged(v: String) = _inputs.update { it.copy(maintenancePerKmMxn = v) }
    fun onInsuranceChanged(v: String) = _inputs.update { it.copy(insurancePerDayMxn = v) }
    fun onRentChanged(v: String) = _inputs.update { it.copy(rentPerDayMxn = v) }

    /** Persist the profile and recompute historical net. Idempotent; safe to call repeatedly. */
    fun save() {
        val inputs = _inputs.value
        viewModelScope.launch {
            val preview = preview(inputs)
            costProfileRepository.save(
                CostProfileEntity(
                    updatedAt = clock.nowMs(),
                    // Persist the resolved $/km directly (covers EV, which the entity has no field
                    // for) plus the gas inputs so the gas editor round-trips. See techdebt note.
                    fuelPerKmMxn = preview.energyPerKmMxn,
                    maintenancePerKmMxn = inputs.maintenancePerKmMxn.toDoubleOrZero(),
                    insurancePerDayMxn = inputs.insurancePerDayMxn.toDoubleOrZero(),
                    rentPerDayMxn = inputs.rentPerDayMxn.toDoubleOrZero(),
                    rendimientoKmPerLitre = if (inputs.isEv) 0.0 else inputs.rendimientoKmPerLitre.toDoubleOrZero(),
                    gasPricePerLitreMxn = if (inputs.isEv) 0.0 else inputs.gasPricePerLitreMxn.toDoubleOrZero(),
                ),
            )
            // Recompute past net views with the new costs (acceptance criterion). Best-effort and
            // wide so older weeks update too; failures here must not block the save UX.
            runCatching { rollupRecomputer.recompute(windowDays = HISTORY_RECOMPUTE_DAYS) }
            _saved.value = true
        }
    }

    private fun load() {
        viewModelScope.launch {
            val existing = costProfileRepository.get() ?: return@launch
            val isEv = existing.rendimientoKmPerLitre <= 0.0 && existing.gasPricePerLitreMxn <= 0.0 &&
                existing.fuelPerKmMxn > 0.0
            _inputs.value = CostProfileInputs(
                isEv = isEv,
                rendimientoKmPerLitre = existing.rendimientoKmPerLitre.editText(),
                gasPricePerLitreMxn = existing.gasPricePerLitreMxn.editText(),
                // EV inputs aren't stored separately; show the resolved $/km can't be reversed, so
                // leave the EV consumption fields blank for the driver to re-enter if they edit.
                kwhPer100Km = "",
                costPerKwhMxn = "",
                maintenancePerKmMxn = existing.maintenancePerKmMxn.editText(),
                insurancePerDayMxn = existing.insurancePerDayMxn.editText(),
                rentPerDayMxn = existing.rentPerDayMxn.editText(),
            )
        }
    }

    companion object {
        /** Recompute window on save: ~2 years, enough to cover all on-device captured history. */
        const val HISTORY_RECOMPUTE_DAYS: Long = 730L
    }
}

/** Editable cost-profile fields, held as strings to match the text inputs verbatim. */
data class CostProfileInputs(
    val isEv: Boolean = false,
    val rendimientoKmPerLitre: String = "",
    val gasPricePerLitreMxn: String = "",
    val kwhPer100Km: String = "",
    val costPerKwhMxn: String = "",
    val maintenancePerKmMxn: String = "",
    val insurancePerDayMxn: String = "",
    val rentPerDayMxn: String = "",
)

/** Parse a user-typed number; blank/garbage ⇒ 0.0 (an empty field means "not set", never crash). */
internal fun String.toDoubleOrZero(): Double = trim().replace(',', '.').toDoubleOrNull() ?: 0.0

/** Render a stored double for a text field: 0 ⇒ blank (so a fresh field isn't pre-filled "0.0"). */
private fun Double.editText(): String = if (this <= 0.0) "" else this.toString()
