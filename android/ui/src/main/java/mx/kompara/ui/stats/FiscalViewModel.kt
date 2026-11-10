package mx.kompara.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mx.kompara.billing.Capability
import mx.kompara.billing.TierGatekeeper
import mx.kompara.data.accounting.PeriodWindow
import mx.kompara.metrics.PeriodMoneyRepository
import mx.kompara.sync.fiscal.FiscalConfigRepository
import mx.kompara.ui.fiscal.FiscalReportExporter
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** Ganancias Mes and IMSS share the selected month, source snapshot and as-of clock. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FiscalViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val selection: SelectedPeriod,
    private val snapshots: TodaySnapshots,
    repository: PeriodMoneyRepository,
    gatekeeper: TierGatekeeper,
    config: FiscalConfigRepository,
    clock: AppClock,
    zone: ZoneId,
    private val exporter: FiscalReportExporter,
) : ViewModel() {
    val sheet = saved.getStateFlow<String?>("monthSheet", null)
    private val exporting = MutableStateFlow(false)
    private val exportError = MutableStateFlow(false)
    private val initial = SelectedPeriod.resolveMonth(null, Instant.ofEpochMilli(clock.nowMs()).atZone(zone).toLocalDate())
    val exportGate = gatekeeper.gateFor(Capability.FISCAL).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), mx.kompara.billing.GateState.LOCKED)
    // The existing server DTO has no legally validated income rule/source. A fresh fetch cannot enable E5.
    val regulatoryEnabled = config.regulatoryEnabled
    private val data = combine(selection.month, snapshots.state, gatekeeper.gateFor(Capability.HISTORY)) { selected, today, gate -> Triple(selected, today, gate) }
        .flatMapLatest { (selected, today, gate) ->
            val window = today.getOrNull()?.today?.money?.window
                ?: return@flatMapLatest flowOf(FiscalUiState(initial, loading = false, error = true))
            val month = SelectedPeriod.resolveMonth(selected, window.start)
            repository.observeMonth(PeriodWindow(month.atDay(1), month.atEndOfMonth(), window.asOf, window.zone))
                .map { FiscalUiState(month, loading = false, period = MonthPresentation(it, premium = !gate.isLocked)) }
                .onStart { emit(FiscalUiState(month)) }
                .catch { emit(FiscalUiState(month, loading = false, error = true)) }
        }.scan(FiscalUiState(initial)) { previous, next ->
            if (next.loading && previous.month == next.month && previous.period != null) previous else next
        }
    val uiState = combine(data, exporting, exportError) { state, busy, failed ->
        state.copy(exporting = busy, exportError = failed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FiscalUiState(initial))

    fun selectMonth(month: YearMonth) {
        val today = uiState.value.period?.today ?: return
        if (month > YearMonth.from(today)) return
        viewModelScope.launch { selection.selectMonth(month, today); closeSheet() }
    }
    fun step(offset: Long) = selectMonth(uiState.value.month.plusMonths(offset))
    fun selectComparisonWeek() {
        val month = uiState.value.period ?: return
        viewModelScope.launch { selection.select(SelectedPeriod.lastClosedWeek(month.month, month.today)) }
    }
    fun openSheet(value: String) {
        if (value != "weeks" || uiState.value.period?.locked == false) saved["monthSheet"] = value
    }
    fun closeSheet() { saved["monthSheet"] = null }
    fun retry() = snapshots.retry()
    fun exportPdf() {
        val state = uiState.value
        if (exporting.value || !state.canExport || exportGate.value.isLocked) return
        val period = state.period ?: return
        exporting.value = true
        exportError.value = false
        viewModelScope.launch {
            try { exportError.value = !exporter.exportRecorded(period) }
            catch (_: Exception) { exportError.value = true }
            finally { exporting.value = false }
        }
    }
}

data class FiscalUiState(
    val month: YearMonth,
    val loading: Boolean = true,
    val period: MonthPresentation? = null,
    val error: Boolean = false,
    val offline: Boolean = false,
    val exporting: Boolean = false,
    val exportError: Boolean = false,
) {
    val payments get() = period?.let { ImssTracker.payments(it.money) }.orEmpty()
    val isEmpty get() = payments.isEmpty()
    val canExport get() = !loading && !error && !isEmpty
}
