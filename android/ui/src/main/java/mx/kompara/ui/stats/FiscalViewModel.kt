package mx.kompara.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.kompara.billing.Capability
import mx.kompara.billing.GateState
import mx.kompara.billing.TierGatekeeper
import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.fiscal.FiscalCalculator
import mx.kompara.metrics.fiscal.FiscalMonthSummary
import mx.kompara.metrics.imss.PlatformImssStatus
import mx.kompara.sync.fiscal.FiscalConfig
import mx.kompara.sync.fiscal.FiscalConfigRepository
import mx.kompara.ui.fiscal.FiscalAggregateMapper
import mx.kompara.ui.fiscal.FiscalReportExporter
import mx.kompara.ui.paywall.GateFunnel
import java.time.YearMonth
import javax.inject.Inject

/**
 * Backs the Fiscal tab's IMSS threshold tracker (B-051).
 *
 * Reactive end-to-end: it combines the selected month, the month's daily + weekly rollup rows (queried
 * by range so a month change re-queries via [flatMapLatest]), the remote-or-default fiscal config, and
 * the driver's enabled platforms, then folds them through the pure [ImssTracker]/[ImssCalculator] so
 * the screen renders nothing but state. Fires a best-effort [FiscalConfigRepository.refresh] on init
 * so a fresh year's threshold is picked up; a failed fetch keeps the cached/default value (never
 * blocks the UI).
 *
 * The month picker offers the current month plus [PAST_MONTHS] past months ([selectMonthOffset]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FiscalViewModel @Inject constructor(
    private val aggregateDao: AggregateDao,
    settingsRepository: SettingsRepository,
    fiscalConfigRepository: FiscalConfigRepository,
    tierGatekeeper: TierGatekeeper,
    /** Exposed so the screen's [mx.kompara.ui.paywall.PaywallGate] can record gate impressions. */
    val gateFunnel: GateFunnel,
    private val fiscalMonth: FiscalMonth,
    private val clock: AppClock,
    private val reportExporter: FiscalReportExporter,
) : ViewModel() {

    /** Months-ago offset for the picker: 0 = current, 1 = last month, … up to [PAST_MONTHS]. */
    private val monthOffset = MutableStateFlow(0)

    /** Whether the fiscal summary shows the YTD totals (true) or just the month (false). */
    private val ytdView = MutableStateFlow(false)

    /** Pure fiscal calculator (B-052); stateless. */
    private val fiscalCalculator = FiscalCalculator()

    /** True while an export is in flight (disables the button, shows progress). */
    private val exporting = MutableStateFlow(false)

    /** B-050: the Fiscal/IMSS tab is premium. Gating flows through the [TierGatekeeper]. */
    val gateState: StateFlow<GateState> =
        tierGatekeeper.gateFor(Capability.FISCAL)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GateState.LOCKED)

    init {
        // Best-effort: pick up a fresh threshold this session; never throws, no-ops within TTL.
        viewModelScope.launch { runCatching { fiscalConfigRepository.refresh() } }
    }

    val uiState: StateFlow<FiscalUiState> =
        combine(
            monthOffset,
            settingsRepository.settings,
            fiscalConfigRepository.observe(),
            ytdView,
            exporting,
        ) { offset, settings, config, ytd, isExporting ->
            UiInputs(offset, settings.enabledPlatforms, config, ytd, isExporting)
        }
            .flatMapLatest { inputs ->
                val month = fiscalMonth.monthAt(clock.nowMs(), inputs.offset)
                // YTD window: Jan 1 of the month's year through the month end (for the YTD fiscal totals).
                val ytdStartDay = YearMonth.of(month.year, 1).atDay(1).toString()
                val ytdEndDay = fiscalMonth.monthEndDay(month)
                val (weekRangeStart, weekRangeEnd) = FiscalAggregateMapper.weekRange(month)
                combine(
                    // Month rows drive the IMSS tracker + the month's fiscal line.
                    aggregateDao.observeDailyInRange(fiscalMonth.monthStartDay(month), fiscalMonth.monthEndDay(month)),
                    aggregateDao.observeWeeklyInRange(weekRangeStart, weekRangeEnd),
                    // YTD daily rows (Jan→month) drive the YTD fiscal totals.
                    aggregateDao.observeDailyInRange(ytdStartDay, ytdEndDay),
                ) { daily, weekly, ytdDaily ->
                    buildState(month, inputs, daily, weekly, ytdDaily)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = FiscalUiState.LOADING,
            )

    /** Pick a month by offset (0 = current). Clamped to the supported window. */
    fun selectMonthOffset(offset: Int) {
        monthOffset.value = offset.coerceIn(0, PAST_MONTHS)
    }

    /** Toggle the fiscal summary between the month view and the year-to-date view. */
    fun setYtdView(ytd: Boolean) {
        ytdView.value = ytd
    }

    /**
     * Export the currently-shown month as a PDF and fire the share chooser (B-052). No-ops while an
     * export is already in flight or when there's nothing to export (the screen hides the button then).
     */
    fun exportPdf() {
        if (exporting.value) return
        val state = uiState.value
        val summary = state.fiscalSummary ?: return
        if (summary.isEmpty) return
        exporting.value = true
        viewModelScope.launch {
            try {
                reportExporter.export(
                    summary = summary,
                    monthLabel = state.monthLabel,
                    imssStatuses = state.sections,
                    driverName = null, // driver name not captured yet — header omits it.
                )
            } finally {
                exporting.value = false
            }
        }
    }

    private fun buildState(
        month: YearMonth,
        inputs: UiInputs,
        daily: List<mx.kompara.data.db.entity.DailyAggregateEntity>,
        weekly: List<mx.kompara.data.db.entity.WeeklyAggregateEntity>,
        ytdDaily: List<mx.kompara.data.db.entity.DailyAggregateEntity>,
    ): FiscalUiState {
        val config = inputs.config
        val today = fiscalMonth.today(clock.nowMs())
        val sections = ImssTracker.sectionsFor(
            month = month,
            thresholdMxn = config.imssMonthlyThresholdMxn,
            daily = daily,
            weekly = weekly,
            today = today,
            enabledPlatforms = inputs.enabledPlatforms,
        )

        // Fiscal summary (B-052): month rows (daily wins, weekly pro-rated) + YTD daily rows.
        val monthInputs = FiscalAggregateMapper.toInputs(month, daily, weekly)
        val ytdInputs = ytdDaily.map {
            mx.kompara.metrics.fiscal.FiscalMonthInput(it.platform, it.day, it.grossEarningsMxn, it.netEarningsMxn)
        }
        val fiscalSummary = fiscalCalculator.summarize(month, monthInputs, ytdInputs)

        return FiscalUiState(
            loading = false,
            monthOffset = inputs.offset,
            monthLabel = fiscalMonth.label(month),
            availableMonthOffsets = (0..PAST_MONTHS).toList(),
            monthLabels = (0..PAST_MONTHS).map { fiscalMonth.label(fiscalMonth.monthAt(clock.nowMs(), it)) },
            thresholdMxn = config.imssMonthlyThresholdMxn,
            usingDefaultConfig = config.isDefault,
            sections = sections,
            fiscalSummary = fiscalSummary,
            ytdView = inputs.ytdView,
            exporting = inputs.exporting,
        )
    }

    /** The five reactive inputs folded into one carrier so [combine] stays under the 5-arg arity. */
    private data class UiInputs(
        val offset: Int,
        val enabledPlatforms: Set<mx.kompara.data.model.Platform>,
        val config: FiscalConfig,
        val ytdView: Boolean,
        val exporting: Boolean,
    )

    companion object {
        /** How many past months the picker offers (in addition to the current month). */
        const val PAST_MONTHS = 5
    }
}

/** Immutable render state for the Fiscal tab. */
data class FiscalUiState(
    val loading: Boolean,
    val monthOffset: Int,
    val monthLabel: String,
    /** Offsets the picker can select (0..PAST_MONTHS). */
    val availableMonthOffsets: List<Int>,
    /** Spanish labels parallel to [availableMonthOffsets]. */
    val monthLabels: List<String>,
    val thresholdMxn: Double,
    /** True when the threshold is the bundled default (no successful remote fetch yet). */
    val usingDefaultConfig: Boolean,
    val sections: List<PlatformImssStatus>,
    /** The B-052 fiscal-withholding summary for the month (+ YTD), or null while loading. */
    val fiscalSummary: FiscalMonthSummary? = null,
    /** Whether the fiscal section shows the YTD totals (true) or the month (false). */
    val ytdView: Boolean = false,
    /** True while a PDF export is in flight (disables the export button). */
    val exporting: Boolean = false,
) {
    /** No enabled platform has any data for the selected month (IMSS section). */
    val isEmpty: Boolean get() = sections.isEmpty()

    /** True when the fiscal summary has at least one platform line to export. */
    val canExport: Boolean get() = fiscalSummary?.isEmpty == false

    companion object {
        val LOADING = FiscalUiState(
            loading = true,
            monthOffset = 0,
            monthLabel = "",
            availableMonthOffsets = listOf(0),
            monthLabels = listOf(""),
            thresholdMxn = 0.0,
            usingDefaultConfig = true,
            sections = emptyList(),
            fiscalSummary = null,
            ytdView = false,
            exporting = false,
        )
    }
}
