package mx.kompara.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mx.kompara.data.accounting.*
import mx.kompara.data.settings.CostProfileRepository
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
)

/** Row edits stay in a restored draft; only a successful save changes capture's profile. */
@HiltViewModel
class CostProfileViewModel @Inject constructor(
    private val costs: CostProfileRepository,
    private val recomputer: RollupRecomputer,
    private val clock: AppClock,
    private val zone: ZoneId,
    private val handle: SavedStateHandle,
) : ViewModel() {
    private val mutable = MutableStateFlow(CostEditorState())
    val state = mutable.asStateFlow()
    init { load() }
    fun load() { viewModelScope.launch {
        try {
            val versions = costs.runningVersions()
            val active = CostSchedule(versions).runningAt(clock.nowMs()) ?: error("Missing costs")
            val saved = CostProfileInputs.from(active.inputs)
            mutable.value = CostEditorState(saved, handle.get<ArrayList<String>>("costDraft")?.let(CostProfileInputs::decode) ?: saved,
                handle.get<String>("costField")?.let(CostField::valueOf), active.confirmation == CostConfirmation.TYPICAL,
                confirmHistory = handle["costHistory"] ?: false,
                historyFrom = versions.filter { it.inputs.mode == RunningCostMode.GAS && it.effectiveFrom < clock.nowMs() }
                    .minOfOrNull { it.effectiveFrom }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() })
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
                mutable.value = s.copy(saved = s.draft, field = null, typical = false, saving = false, finished = finish, confirmHistory = false)
            } catch (e: Exception) { if (e is CancellationException) throw e
                mutable.value = state.value.copy(saving = false, error = "No pudimos guardar tus costos. Inténtalo otra vez.") }
        }
    }
}
