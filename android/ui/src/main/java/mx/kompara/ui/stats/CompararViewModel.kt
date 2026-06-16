package mx.kompara.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mx.kompara.billing.Capability
import mx.kompara.billing.GateState
import mx.kompara.billing.TierGatekeeper
import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.data.model.Platform
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.sync.aggregate.BenchmarksRepository
import mx.kompara.sync.aggregate.PercentileRepository
import mx.kompara.ui.paywall.GateFunnel
import javax.inject.Inject

/**
 * Backs the Comparar tab (S-024) — a weekly benchmarking hub: the driver's blended value per metric
 * vs. each platform's city average and vs. their percentile among all drivers.
 *
 * Reactive over the weekly-aggregate table, the chosen week, the driver's city, the city benchmarks,
 * and the driver's percentiles (computed against the combined `all` population — B-085), mirroring
 * [InicioDashboardViewModel]'s percentile wiring. The pure assembly lives in [ComparisonBuilder] so
 * the blend, per-platform N/A, and standing pick are unit-tested without Room or the gatekeeper.
 *
 * Premium gate (B-050): the tab is [Capability.COMPARE]. The screen renders the percentile hero FREE
 * as the tease and gates the benchmark table + opportunities behind the PaywallGate; [gateState] is
 * the single source of truth and [gateFunnel] records gate impressions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CompararViewModel @Inject constructor(
    aggregateDao: AggregateDao,
    benchmarksRepository: BenchmarksRepository,
    percentileRepository: PercentileRepository,
    settingsRepository: SettingsRepository,
    tierGatekeeper: TierGatekeeper,
    val gateFunnel: GateFunnel,
) : ViewModel() {

    /** The week the picker is showing; null ⇒ default to the newest week with data. */
    private val selectedWeek = MutableStateFlow<String?>(null)

    val gateState: StateFlow<GateState> =
        tierGatekeeper.gateFor(Capability.COMPARE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GateState.LOCKED)

    val uiState: StateFlow<CompareUiState> = combine(
        aggregateDao.observeWeekly(),
        selectedWeek,
        settingsRepository.settings.map { it.city }.distinctUntilChanged(),
    ) { rows, week, city -> Triple(rows, week, city) }
        .flatMapLatest { (rows, week, city) ->
            val weeks = CompareState.availableWeeks(rows)
            // Default to the newest week with data; keep the explicit choice if it still has data.
            val resolvedWeek = week?.takeIf { it in weeks } ?: weeks.firstOrNull()
            val weekRows = resolvedWeek?.let { CompareState.rowsForWeek(rows, it) } ?: emptyList()
            val platforms = PlatformSelection.platformsWithData(weekRows)
            if (resolvedWeek == null || platforms.isEmpty()) {
                flowOf(CompareUiState(loading = false, availableWeeks = weeks, data = null))
            } else {
                val blended = PeriodStats.fromWeekly(weekRows, platform = null)
                combine(
                    benchmarksRepository.observe(city),
                    percentileRepository.observe(
                        city,
                        BenchmarksRepository.ALL_PLATFORM,
                        MetricPercentiles.comparisonMetricValues(blended),
                    ),
                ) { stats, percentiles ->
                    // The driver's app mix drives the platform-mix opportunity. Use TRIPS per
                    // platform (per-platform) — captured hoursOnline is replicated across the
                    // week's platform-rows, so it can't say which app the driver leaned on.
                    val platformMix = weekRows.mapNotNull { row ->
                        runCatching { Platform.valueOf(row.platform) }.getOrNull()
                            ?.let { it to row.totalTrips.toDouble() }
                    }.toMap()
                    val comparison = ComparisonBuilder.build(
                        weekStart = resolvedWeek,
                        weekRows = weekRows,
                        platforms = platforms,
                        platformStats = stats,
                        percentilesByMetric = MetricPercentiles.byMetric(percentiles),
                        opportunities = ComparisonOpportunities.build(blended, stats, platformMix),
                    )
                    CompareUiState(
                        loading = false,
                        availableWeeks = weeks,
                        data = CompareUiData(weekStart = resolvedWeek, comparison = comparison),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompareUiState.LOADING)

    /** Switch the week the picker shows (ISO Monday). */
    fun selectWeek(weekStart: String) {
        selectedWeek.value = weekStart
    }
}

/** Render state for the Comparar tab. */
data class CompareUiState(
    val loading: Boolean,
    /** Every week with data, newest first — what the week picker offers (empty ⇒ no data at all). */
    val availableWeeks: List<String>,
    /** The resolved comparison for the shown week; null while loading or when the week has no data. */
    val data: CompareUiData?,
) {
    companion object {
        val LOADING = CompareUiState(loading = true, availableWeeks = emptyList(), data = null)
    }
}
