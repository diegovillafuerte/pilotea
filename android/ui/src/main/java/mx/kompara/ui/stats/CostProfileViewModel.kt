package mx.kompara.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mx.kompara.data.accounting.*
import mx.kompara.data.settings.CarCostRepository
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.data.settings.RateBasis
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.ui.format.Formatters
import mx.kompara.metrics.rollup.RollupRecomputer
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class CostEditorState(
    val saved: CostProfileInputs? = null,
    val draft: CostProfileInputs? = null,
    val field: CostField? = null,
    val typical: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val confirmHistory: Boolean = false,
    val historyFrom: String? = null,
    val finished: Boolean = false,
    /** What the semáforo shows (J3's choice, the same setting as J2's "Muestra"). */
    val basis: RateBasis = RateBasis.DEFAULT,
    val switching: Boolean = false,
    /** Said under the choice once it is applied: "Tu meta y tu mínimo pasan a $11.00 y $9.00." */
    val basisResult: String? = null,
    /** J3's car row: "$1,500 a la semana", the weekly amount in force today. */
    val car: String = "Sin agregar",
)

/** Row edits stay in a restored draft; only a successful save changes capture's profile. */
@HiltViewModel
class CostProfileViewModel @Inject constructor(
    private val costs: CostProfileRepository,
    private val recomputer: RollupRecomputer,
    private val clock: AppClock,
    private val zone: ZoneId,
    private val handle: SavedStateHandle,
    private val settings: SettingsRepository,
    car: CarCostRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(CostEditorState())
    val state = mutable.asStateFlow()
    init {
        load()
        viewModelScope.launch { settings.settings.collect { prefs -> mutable.update { it.copy(basis = prefs.rateBasis) } } }
        viewModelScope.launch { car.versions.collect { rows ->
            val weekly = CostSchedule(emptyList(), rows.map { it.toDomain() }).carAt(Instant.ofEpochMilli(clock.nowMs()).atZone(zone).toLocalDate())?.weeklyMxn
            mutable.update { it.copy(car = weekly?.let { amount -> "${Formatters.formatMxnWhole(amount)} a la semana" } ?: "Sin agregar") }
        } }
    }
    fun load() { viewModelScope.launch {
        try {
            val versions = costs.runningVersions()
            val active = CostSchedule(versions).runningAt(clock.nowMs()) ?: error("Missing costs")
            val saved = CostProfileInputs.from(active.inputs)
            mutable.update { it.copy(saved = saved, draft = handle.get<ArrayList<String>>("costDraft")?.let(CostProfileInputs::decode) ?: saved,
                field = handle.get<String>("costField")?.let(CostField::valueOf), typical = active.confirmation == CostConfirmation.TYPICAL,
                error = null, confirmHistory = handle["costHistory"] ?: false,
                historyFrom = versions.filter { it.inputs.mode == RunningCostMode.GAS && it.effectiveFrom < clock.nowMs() }
                    .minOfOrNull { it.effectiveFrom }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() }) }
        } catch (e: Exception) { if (e is CancellationException) throw e
            mutable.value = state.value.copy(error = "No pudimos cargar tus costos. Inténtalo otra vez.") }
    } }
    fun open(field: CostField) {
        if (state.value.saving) return
        val saved = state.value.saved ?: return
        handle["costField"] = field.name
        edit(saved.copy(direct = if (field == CostField.DIRECT) true else saved.direct))
        mutable.value = state.value.copy(field = field, error = null)
    }
    fun edit(draft: CostProfileInputs) {
        if (state.value.saving) return
        handle["costDraft"] = draft.encode()
        mutable.value = state.value.copy(draft = draft, error = null)
    }
    fun dismiss() {
        if (state.value.saving) return
        handle.remove<String>("costField"); handle.remove<ArrayList<String>>("costDraft"); handle["costHistory"] = false
        mutable.value = state.value.copy(field = null, draft = state.value.saved, error = null, confirmHistory = false)
    }
    fun dismissHistory() { handle["costHistory"] = false; mutable.value = state.value.copy(confirmHistory = false) }
    fun save(finish: Boolean = false, applyHistory: Boolean? = null) {
        val s = state.value
        if (s.saving) return
        val inputs = s.draft?.runningCosts()
        if (inputs == null) { mutable.value = s.copy(error = "Revisa este número"); return }
        val efficiencyChanged = inputs.mode == RunningCostMode.GAS && s.saved?.direct == false && s.saved.isEv == false &&
            costDecimal(s.saved.rendimientoKmPerLitre)?.compareTo(inputs.kmPerLitre) != 0
        if (efficiencyChanged && s.historyFrom != null && applyHistory == null) {
            handle["costHistory"] = true; mutable.value = s.copy(confirmHistory = true); return
        }
        val correct = efficiencyChanged && applyHistory == true
        mutable.value = s.copy(saving = true, error = null, confirmHistory = false)
        viewModelScope.launch {
            try {
                val now = clock.nowMs()
                recomputer.editCosts(if (correct) java.time.LocalDate.parse(s.historyFrom!!) else Instant.ofEpochMilli(now).atZone(zone).toLocalDate()) {
                    costs.saveRunningCosts(inputs, now, now, correctEfficiency = correct)
                }
                handle.remove<String>("costField"); handle.remove<ArrayList<String>>("costDraft"); handle["costHistory"] = false
                mutable.update { it.copy(saved = s.draft, field = null, typical = false, saving = false, finished = finish, confirmHistory = false) }
            } catch (e: Exception) { if (e is CancellationException) throw e
                mutable.value = state.value.copy(saving = false, error = "No pudimos guardar tus costos. Inténtalo otra vez.") }
        }
    }
    /**
     * J3's "El semáforo muestra": applied at once. The limits are converted with the saved cost per km
     * in the same transaction (WP-A), and the new pair is said under the choice.
     */
    fun switchBasis(basis: RateBasis) {
        val s = state.value
        val cost = s.saved?.runningCosts()?.costPerKm ?: return
        if (s.switching || basis == s.basis) return
        mutable.update { it.copy(switching = true, error = null) }
        viewModelScope.launch {
            try {
                settings.switchRateBasis(basis, cost, ThresholdEditor.HOUR_STEP)
                val prefs = settings.settings.first()
                val limits = LimitDraft.from(prefs.effectiveThreshold, prefs.preferredMetric, prefs.rateBasis)
                mutable.update { it.copy(switching = false, basis = prefs.rateBasis, basisResult = "Tu meta y tu mínimo pasan a " +
                    "${ThresholdEditor.amount(limits.pair.goal, limits.unit)} y ${ThresholdEditor.amount(limits.pair.minimum, limits.unit)}.") }
            } catch (e: Exception) { if (e is CancellationException) throw e
                mutable.update { it.copy(switching = false, error = "No pudimos guardar el ajuste. Inténtalo otra vez.") } }
        }
    }
}
