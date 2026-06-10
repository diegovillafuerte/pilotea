package mx.kompara.ui.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import mx.kompara.data.db.entity.CostProfileEntity
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.data.settings.Settings
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.sync.aggregate.PercentileRepository
import mx.kompara.ui.onboarding.AccessibilitySettings
import mx.kompara.ui.onboarding.ServiceWatchdog
import mx.kompara.ui.onboarding.WatchdogState
import javax.inject.Inject

/**
 * Backs the Inicio dashboard (B-040 req 1): this week's net header, streak, weekly-goal progress,
 * the five metric cards for the selected platform, the platform chips, the data-completeness hint,
 * the watchdog banner, and the first-run cost-profile nudge. B-046 adds the percentile bars/badges
 * on the metric cards when benchmarks are available.
 *
 * Reactive end-to-end: it combines the captured weekly aggregates (B-039), settings (goal), and the
 * cost profile so the screen updates the instant any of them change. All selection/aggregation math
 * lives in the pure [PeriodStats]/[PlatformSelection]/[GoalProgress] helpers so it's unit-tested
 * without a real database.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InicioDashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aggregateDao: AggregateDao,
    settingsRepository: SettingsRepository,
    costProfileRepository: CostProfileRepository,
    percentileRepository: PercentileRepository,
    entitlementRepository: EntitlementRepository,
    watchdog: ServiceWatchdog,
    private val weekClock: WeekClock,
    private val clock: AppClock,
) : ViewModel() {

    /** The driver's platform-chip choice; null ⇒ "Todas". Reset to null when it loses its data. */
    private val selectedPlatform = MutableStateFlow<Platform?>(null)

    val watchdogState: StateFlow<WatchdogState> = watchdog.bannerState

    /** One-tap re-enable from the watchdog banner: open Accessibility settings (mirrors B-036). */
    fun reEnableReader() {
        AccessibilitySettings.open(context)
    }

    private val baseState: StateFlow<InicioUiState> = combine(
        aggregateDao.observeWeekly(),
        settingsRepository.settings,
        costProfileRepository.profile,
        selectedPlatform,
    ) { weeklyRows, settings, profile, selected ->
        buildState(weeklyRows, settings, profile, selected)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InicioUiState.LOADING,
    )

    /**
     * The percentile overlay (B-046): for the resolved platform + the period's metric values, the
     * per-card standings, plus the [PercentilesUiState.locked] gate. Re-derives whenever the base
     * state's relevant inputs, the city, the entitlement, or the debug override change. "Todas" has no
     * single platform to benchmark against, so percentiles only show for a concrete platform.
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
            // No concrete platform (e.g. "Todas") -> no percentiles.
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

    val uiState: StateFlow<InicioUiState> = combine(baseState, percentiles) { base, pct ->
        base.copy(percentiles = pct)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InicioUiState.LOADING,
    )

    /** Switch the platform chip (null = "Todas"). */
    fun selectPlatform(platform: Platform?) {
        selectedPlatform.value = platform
    }

    private fun buildState(
        weeklyRows: List<WeeklyAggregateEntity>,
        settings: Settings,
        profile: CostProfileEntity?,
        selected: Platform?,
    ): InicioUiState = InicioStats.forCurrentWeek(
        allWeekly = weeklyRows,
        currentWeekStart = weekClock.weekStartIso(clock.nowMs()),
        weeklyNetGoalMxn = settings.weeklyNetGoalMxn,
        selectedPlatform = selected,
        costProfileSet = profile != null,
    )
}

/** Immutable render state for the Inicio dashboard. */
data class InicioUiState(
    val loading: Boolean,
    /** True once at least one trip is captured this week — otherwise show the empty state. */
    val hasData: Boolean,
    val period: PeriodStats,
    /** Platform chips to render (empty when 0/1 platform has data). null entry = "Todas". */
    val chips: List<Platform?>,
    /** The platform actually shown (null = "Todas"). */
    val selectedPlatform: Platform?,
    val goal: GoalProgress,
    val streak: StreakDisplay,
    val completeness: CompletenessHint,
    /** False ⇒ show the "configura tus costos" first-run nudge. */
    val costProfileSet: Boolean,
    /** Per-card percentile standings + gate (B-046); empty until benchmarks are cached. */
    val percentiles: PercentilesUiState = PercentilesUiState.EMPTY,
) {
    companion object {
        /** Initial state while the first DB/settings emission is pending. */
        val LOADING = InicioUiState(
            loading = true,
            hasData = false,
            period = PeriodStats.EMPTY,
            chips = emptyList(),
            selectedPlatform = null,
            goal = GoalProgress.of(null, 0.0),
            streak = StreakDisplay(0),
            completeness = CompletenessHint.NONE,
            costProfileSet = true,
            percentiles = PercentilesUiState.EMPTY,
        )
    }
}
