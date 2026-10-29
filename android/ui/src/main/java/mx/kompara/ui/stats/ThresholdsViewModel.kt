package mx.kompara.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mx.kompara.data.accounting.CostSchedule
import mx.kompara.data.settings.*
import mx.kompara.ui.components.ReglaModel
import javax.inject.Inject

data class ThresholdsUiState(
    val draft: LimitDraft? = null,
    val saved: LimitDraft? = null,
    val initial: LimitDraft? = null,
    val preview: ReglaModel? = null,
    val touched: LimitField = LimitField.MINIMUM,
    val error: String? = null,
    val saving: Boolean = false,
    val confirmExit: Boolean = false,
    val result: String? = null,
) {
    val dirty get() = draft != null && saved != null && ThresholdEditor.dirty(draft, saved)
    val canSave get() = dirty && !saving && listOf(draft!!.km, draft.hour).all { it.goal.isNotBlank() && it.minimum.isNotBlank() }
    val canReset get() = draft != null && initial != null && !ThresholdEditor.same(draft.pair, initial.copy(metric = draft.metric).pair)
}

enum class LimitsBack { HIDE_KEYBOARD, CONFIRM, CLOSE }
fun limitsBack(imeVisible: Boolean, dirty: Boolean) = when { imeVisible -> LimitsBack.HIDE_KEYBOARD; dirty -> LimitsBack.CONFIRM; else -> LimitsBack.CLOSE }

@HiltViewModel
class ThresholdsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val costs: CostProfileRepository,
    private val clock: AppClock,
    private val handle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(ThresholdsUiState())
    val state = _state.asStateFlow()
    private var original = PlatformThreshold.DEFAULT
    private var valid: LimitDraft? = null
    private var cost: java.math.BigDecimal? = null
    private var reset = handle.get<Boolean>("reset") ?: false
    init { load() }
    fun load() { viewModelScope.launch {
        try {
            val prefs = settings.settings.first()
            original = handle.get<ArrayList<String>>("original")?.let { PlatformThreshold(it[0].toDouble(), it[1].toDouble(), it[2].toDouble(), it[3].toDouble()) } ?: prefs.effectiveThreshold
            handle["original"] = arrayListOf(original.minPerKmMxn.toString(), original.minPerHourMxn.toString(), original.redPerKmMxn.toString(), original.redPerHourMxn.toString())
            val baseline = handle.get<ArrayList<String>>("saved")?.let(LimitDraft::decode) ?: LimitDraft.from(prefs.effectiveThreshold, prefs.preferredMetric)
            val draft = handle.get<ArrayList<String>>("draft")?.let(LimitDraft::decode) ?: baseline
            val initial = LimitDraft.from(PlatformThreshold.DEFAULT, draft.metric)
            val restoredValid = handle.get<ArrayList<String>>("valid")?.let(LimitDraft::decode) ?: baseline
            valid = restoredValid.copy(
                km = restoredValid.km.takeIf { ThresholdEditor.valid(it, PreferredMetric.IPK) } ?: initial.km,
                hour = restoredValid.hour.takeIf { ThresholdEditor.valid(it, PreferredMetric.IPH) } ?: initial.hour,
                metric = draft.metric,
            )
            cost = CostSchedule(costs.runningVersions()).runningAt(clock.nowMs())?.inputs?.costPerKm
            handle["saved"] = baseline.encode(); handle["draft"] = draft.encode(); handle["valid"] = valid!!.encode()
            _state.value = ThresholdsUiState(draft, baseline, initial, preview = ThresholdEditor.preview(valid!!, cost), touched = handle.get<String>("touched")?.let(LimitField::valueOf) ?: LimitField.MINIMUM,
                confirmExit = handle["confirm"] ?: false)
        } catch (e: Exception) { if (e is CancellationException) throw e; _state.value = state.value.copy(error = "No pudimos cargar tus límites. Inténtalo otra vez.") }
    } }
    private fun update(draft: LimitDraft) {
        val last = valid ?: return
        valid = last.copy(
            km = if (ThresholdEditor.valid(draft.km, PreferredMetric.IPK)) draft.km else last.km,
            hour = if (ThresholdEditor.valid(draft.hour, PreferredMetric.IPH)) draft.hour else last.hour,
            metric = draft.metric,
        )
        _state.value = state.value.copy(draft = draft, preview = ThresholdEditor.preview(valid!!, cost), error = null)
        handle["saved"] = state.value.saved!!.encode(); handle["draft"] = draft.encode(); handle["valid"] = valid!!.encode()
        handle["reset"] = reset; handle["touched"] = state.value.touched.name
    }
    fun edit(field: LimitField, text: String) {
        if (state.value.saving) return
        val draft = state.value.draft ?: return
        reset = false; _state.value = state.value.copy(touched = field); update(draft.edit(field, text))
    }
    fun step(field: LimitField, increase: Boolean) {
        val draft = state.value.draft ?: return
        edit(field, ThresholdEditor.step(if (field == LimitField.GOAL) draft.pair.goal else draft.pair.minimum, draft.metric, increase))
    }
    fun select(metric: PreferredMetric) { if (!state.value.saving) state.value.draft?.let { update(it.copy(metric = metric)) } }
    fun reset() {
        if (state.value.saving) return
        val draft = state.value.draft ?: return
        reset = true; update(draft.withPair(state.value.initial!!.copy(metric = draft.metric).pair))
    }
    fun confirmExit(show: Boolean) { handle["confirm"] = show; _state.value = state.value.copy(confirmExit = show) }
    fun save() {
        val s = state.value
        if (!s.canSave) return
        val draft = s.draft!!
        // Validate both drafts, selecting the invalid unit rather than silently throwing it away.
        val invalid = listOf(draft.metric, if (draft.metric == PreferredMetric.IPK) PreferredMetric.IPH else PreferredMetric.IPK)
            .firstOrNull { !ThresholdEditor.valid(draft.copy(metric = it).pair, it) }
        if (invalid != null) {
            val d = draft.copy(metric = invalid)
            update(d); confirmExit(false)
            _state.value = state.value.copy(error = ThresholdEditor.error(d, s.touched)); return
        }
        _state.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                settings.saveLimits(draft.thresholdPreserving(s.saved!!, original), draft.metric)
                val message = ThresholdEditor.savedMessage(draft, s.saved!!, reset)
                _state.value = state.value.copy(saving = false, saved = draft, confirmExit = false, result = message)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value = state.value.copy(saving = false, confirmExit = false, error = "No pudimos guardar tus límites. Inténtalo otra vez.")
            }
        }
    }
}
