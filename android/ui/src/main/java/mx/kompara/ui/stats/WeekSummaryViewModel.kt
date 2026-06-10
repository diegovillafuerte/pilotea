package mx.kompara.ui.stats

import androidx.lifecycle.SavedStateHandle
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
import mx.kompara.billing.EntitlementRepository
import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.data.db.entity.AggregateSource
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.sync.aggregate.PercentileRepository
import javax.inject.Inject

/**
 * Backs the week-summary screen reached by tapping a history week (B-040 req 3): the same five
 * MetricCards as Inicio, but for the chosen [ARG_WEEK_START] (ISO Monday), with platform chips. B-046
 * adds the percentile bars/badges on those cards when benchmarks are available.
 *
 * Reactive over the weekly table so an import or recompute of that exact week updates the summary.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeekSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    aggregateDao: AggregateDao,
    settingsRepository: SettingsRepository,
    percentileRepository: PercentileRepository,
    entitlementRepository: EntitlementRepository,
) : ViewModel() {

    private val weekStart: String = savedStateHandle.get<String>(ARG_WEEK_START).orEmpty()
    private val selectedPlatform = MutableStateFlow<Platform?>(null)

    private val baseState: StateFlow<WeekSummaryUiState> = combine(
        aggregateDao.observeWeekly().map { rows -> rowsForWeek(rows) },
        selectedPlatform,
    ) { rows, selected ->
        buildState(rows, selected)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WeekSummaryUiState.loading(weekStart),
    )

    /**
     * The percentile overlay (B-046) for this week's resolved platform + metric values. Same shape as
     * the Inicio dashboard: only a concrete platform benchmarks; "Todas" shows none.
     */
    private val percentiles: StateFlow<PercentilesUiState> = combine(
        baseState.map { PercentileInputs(it.selectedPlatform, it.period) }.distinctUntilChanged(),
        settingsRepository.settings.map { it.city to it.debugPremium }.distinctUntilChanged(),
        entitlementRepository.capabilities.distinctUntilChanged(),
    ) { inputs, cityDebug, caps ->
        Triple(inputs, cityDebug, caps)
    }.flatMapLatest { (inputs, cityDebug, caps) ->
        val (city, debugPremium) = cityDebug
        val locked = !(caps.canSeeBenchmarks || debugPremium)
        val platform = inputs.platform
        if (platform == null || platform == Platform.UNKNOWN) {
            flowOf(PercentilesUiState(byMetric = emptyMap(), locked = locked))
        } else {
            percentileRepository
                .observe(city, platform.name.lowercase(), MetricPercentiles.metricValues(inputs.period))
                .map { results -> PercentilesUiState(MetricPercentiles.byMetric(results), locked) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PercentilesUiState.EMPTY,
    )

    val uiState: StateFlow<WeekSummaryUiState> = combine(baseState, percentiles) { base, pct ->
        base.copy(percentiles = pct)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WeekSummaryUiState.loading(weekStart),
    )

    fun selectPlatform(platform: Platform?) {
        selectedPlatform.value = platform
    }

    private fun rowsForWeek(rows: List<WeeklyAggregateEntity>): List<WeeklyAggregateEntity> {
        val forWeek = rows.filter { it.weekStart == weekStart }
        // Prefer imported (realized) rows when present; otherwise the captured estimate.
        val imported = forWeek.filter { it.source == AggregateSource.IMPORTED.name }
        return if (imported.isNotEmpty()) imported else forWeek.filter { it.source == AggregateSource.CAPTURED.name }
    }

    private fun buildState(
        rows: List<WeeklyAggregateEntity>,
        selected: Platform?,
    ): WeekSummaryUiState {
        val chips = PlatformSelection.chips(rows)
        val resolved = PlatformSelection.resolve(rows, selected)
        val source = if (rows.any { it.source == AggregateSource.IMPORTED.name }) {
            WeekSourceBadge.IMPORTADO
        } else {
            WeekSourceBadge.CAPTURADO
        }
        return WeekSummaryUiState(
            loading = false,
            weekStart = weekStart,
            source = source,
            period = PeriodStats.fromWeekly(rows, resolved),
            chips = chips,
            selectedPlatform = resolved,
            hasData = rows.isNotEmpty(),
        )
    }

    companion object {
        /** Nav argument key: the ISO Monday (yyyy-MM-dd) of the week to summarise. */
        const val ARG_WEEK_START = "weekStart"
    }
}

/** Render state for the week-summary screen. */
data class WeekSummaryUiState(
    val loading: Boolean,
    val weekStart: String,
    val source: WeekSourceBadge,
    val period: PeriodStats,
    val chips: List<Platform?>,
    val selectedPlatform: Platform?,
    val hasData: Boolean,
    /** Per-card percentile standings + gate (B-046); empty until benchmarks are cached. */
    val percentiles: PercentilesUiState = PercentilesUiState.EMPTY,
) {
    companion object {
        fun loading(weekStart: String) = WeekSummaryUiState(
            loading = true,
            weekStart = weekStart,
            source = WeekSourceBadge.CAPTURADO,
            period = PeriodStats.EMPTY,
            chips = emptyList(),
            selectedPlatform = null,
            hasData = false,
            percentiles = PercentilesUiState.EMPTY,
        )
    }
}
