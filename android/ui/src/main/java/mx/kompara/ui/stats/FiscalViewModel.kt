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
import mx.kompara.metrics.imss.PlatformImssStatus
import mx.kompara.sync.fiscal.FiscalConfig
import mx.kompara.sync.fiscal.FiscalConfigRepository
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
) : ViewModel() {

    /** Months-ago offset for the picker: 0 = current, 1 = last month, … up to [PAST_MONTHS]. */
    private val monthOffset = MutableStateFlow(0)

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
        ) { offset, settings, config -> Triple(offset, settings, config) }
            .flatMapLatest { (offset, settings, config) ->
                val month = fiscalMonth.monthAt(clock.nowMs(), offset)
                combine(
                    aggregateDao.observeDailyInRange(
                        fiscalMonth.monthStartDay(month),
                        fiscalMonth.monthEndDay(month),
                    ),
                    aggregateDao.observeWeeklyInRange(
                        fiscalMonth.weekRangeStart(month),
                        fiscalMonth.weekRangeEnd(month),
                    ),
                ) { daily, weekly ->
                    buildState(month, offset, config, settings.enabledPlatforms, daily, weekly)
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

    private fun buildState(
        month: YearMonth,
        offset: Int,
        config: FiscalConfig,
        enabledPlatforms: Set<mx.kompara.data.model.Platform>,
        daily: List<mx.kompara.data.db.entity.DailyAggregateEntity>,
        weekly: List<mx.kompara.data.db.entity.WeeklyAggregateEntity>,
    ): FiscalUiState {
        val today = fiscalMonth.today(clock.nowMs())
        val sections = ImssTracker.sectionsFor(
            month = month,
            thresholdMxn = config.imssMonthlyThresholdMxn,
            daily = daily,
            weekly = weekly,
            today = today,
            enabledPlatforms = enabledPlatforms,
        )
        return FiscalUiState(
            loading = false,
            monthOffset = offset,
            monthLabel = fiscalMonth.label(month),
            availableMonthOffsets = (0..PAST_MONTHS).toList(),
            monthLabels = (0..PAST_MONTHS).map { fiscalMonth.label(fiscalMonth.monthAt(clock.nowMs(), it)) },
            thresholdMxn = config.imssMonthlyThresholdMxn,
            usingDefaultConfig = config.isDefault,
            sections = sections,
        )
    }

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
) {
    /** No enabled platform has any data for the selected month. */
    val isEmpty: Boolean get() = sections.isEmpty()

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
        )
    }
}
