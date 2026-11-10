package mx.kompara.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mx.kompara.data.settings.MonthlyIncentiveRepository
import mx.kompara.metrics.imss.ImssMonth
import mx.kompara.metrics.imss.ImssMonthRepository
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** [view] is null while loading or after an [error]. */
data class ImssUiState(
    val month: YearMonth,
    val today: LocalDate,
    val imss: ImssMonth? = null,
    val view: ImssPresentation? = imss?.let(ImssPresentation::of),
    val error: Boolean = false,
) {
    val title: String get() = ImssPresentation.title(month, today)
    /** The forward arrow stops at the current month. */
    val canNext: Boolean get() = month < YearMonth.from(today)
}

/** M4: the one amount per app and month the driver types, staged until Guardar. */
data class IncentiveSheet(val platform: String, val month: YearMonth, val saved: Int?, val draft: String,
    val saving: Boolean = false, val error: Boolean = false, val current: Boolean = true) {
    val amount: Int? get() = draft.toIntOrNull()
}

/** IMSS (plan 4.7): the month's amount per app toward the need, the rule (M3) and the typed incentives (M4). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ImssViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val months: ImssMonthRepository,
    private val incentives: MonthlyIncentiveRepository,
    private val clock: AppClock,
    private val zone: ZoneId,
) : ViewModel() {
    /** "rule" (M3) or "incentives:<PLATFORM>" (M4). */
    val sheet = saved.getStateFlow<String?>("imssSheet", null)
    private val draft = saved.getStateFlow("imssDraft", "")
    private val saving = MutableStateFlow(false)
    private val saveError = MutableStateFlow(false)
    private val selected = saved.getStateFlow<String?>("imssMonth", null)
    private val refresh = MutableStateFlow(0)

    private fun today() = Instant.ofEpochMilli(clock.nowMs()).atZone(zone).toLocalDate()
    private fun month(selected: String?, today: LocalDate): YearMonth =
        selected?.let(YearMonth::parse)?.takeIf { it <= YearMonth.from(today) } ?: YearMonth.from(today)

    val uiState: StateFlow<ImssUiState> = combine(selected, refresh) { month, _ -> month }.flatMapLatest { choice ->
        // "Now" moves by the minute: a trip read after it counts at the next tick, and the days left follow midnight.
        clock.moments().map { it.truncatedTo(ChronoUnit.MINUTES) }.distinctUntilChanged().flatMapLatest { now ->
            months.observe(month(choice, now.atZone(zone).toLocalDate()), now, zone)
        }.map { ImssUiState(it.month, it.today, it) }
            .onStart { today().let { emit(ImssUiState(month(choice, it), it)) } }
            .catch { today().let { day -> emit(ImssUiState(month(choice, day), day, error = true)) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), today().let { ImssUiState(month(selected.value, it), it) })

    val incentiveSheet: StateFlow<IncentiveSheet?> = combine(sheet, draft, saving, saveError, uiState) { sheet, draft, busy, failed, state ->
        sheet?.takeIf { it.startsWith(INCENTIVES) }?.removePrefix(INCENTIVES)?.let { platform ->
            IncentiveSheet(platform, state.month, state.imss?.apps?.firstOrNull { it.platform == platform }?.incentives, draft, busy, failed,
                current = !state.canNext)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun step(offset: Long) {
        val state = uiState.value
        val next = state.month.plusMonths(offset)
        if (next > YearMonth.from(state.today)) return
        saved["imssMonth"] = next.takeIf { it < YearMonth.from(state.today) }?.toString()
    }

    fun openRule() { saved["imssSheet"] = RULE }

    fun openIncentives(platform: String) {
        val typed = uiState.value.imss?.apps?.firstOrNull { it.platform == platform }?.incentives
        saveError.value = false
        saved["imssDraft"] = typed?.toString().orEmpty()
        saved["imssSheet"] = INCENTIVES + platform
    }

    /** Whole pesos only. */
    fun editDraft(text: String) { if (!saving.value) { saved["imssDraft"] = text.filter(Char::isDigit).take(7); saveError.value = false } }

    fun closeSheet() { if (!saving.value) { saved["imssSheet"] = null; saved["imssDraft"] = ""; saveError.value = false } }

    fun saveIncentives() { incentiveSheet.value?.let { sheet -> sheet.amount?.let { amount -> write { incentives.set(sheet.platform, sheet.month, amount, clock.nowMs()) } } } }

    fun removeIncentives() { incentiveSheet.value?.takeIf { it.saved != null }?.let { sheet -> write { incentives.remove(sheet.platform, sheet.month) } } }

    private fun write(action: suspend () -> Unit) {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            try { action(); saving.value = false; closeSheet() }
            catch (e: Exception) { if (e is CancellationException) throw e; saveError.value = true; saving.value = false }
        }
    }

    fun retry() { refresh.value++ }

    companion object {
        const val RULE = "rule"
        const val INCENTIVES = "incentives:"
    }
}
