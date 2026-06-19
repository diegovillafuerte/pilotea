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
import mx.kompara.billing.Capability
import mx.kompara.billing.TierGatekeeper
import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.data.db.dao.OfferDao
import mx.kompara.data.db.dao.TripDao
import mx.kompara.data.db.entity.AggregateSource
import mx.kompara.data.db.entity.CostProfileEntity
import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.db.entity.TripEntity
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.data.settings.Settings
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.CostProfileMapper
import mx.kompara.metrics.recommendation.BestHourFinder
import mx.kompara.metrics.recommendation.CrossPlatformRate
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
    private val offerDao: OfferDao,
    private val tripDao: TripDao,
    settingsRepository: SettingsRepository,
    costProfileRepository: CostProfileRepository,
    percentileRepository: PercentileRepository,
    tierGatekeeper: TierGatekeeper,
    /** Exposed so the dashboard's [mx.kompara.ui.paywall.PaywallGate] can record gate impressions. */
    val gateFunnel: mx.kompara.ui.paywall.GateFunnel,
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
        settingsRepository.settings.map { it.city }.distinctUntilChanged(),
        // B-050: the single source of truth for gating — entitlement + debug + remote kill switch.
        tierGatekeeper.gateFor(Capability.BENCHMARKS),
    ) { inputs, city, gate ->
        Triple(inputs, city, gate)
    }.flatMapLatest { (inputs, city, gate) ->
        val platform = inputs.platform
        if (platform == null || platform == Platform.UNKNOWN) {
            // No concrete platform (e.g. "Todas") -> no percentiles.
            flowOf(PercentilesUiState(byMetric = emptyMap(), gateState = gate))
        } else {
            percentileRepository
                .observe(city, platform.name.lowercase(), MetricPercentiles.metricValues(inputs.period))
                .map { results -> PercentilesUiState(MetricPercentiles.byMetric(results), gateState = gate) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PercentilesUiState.EMPTY,
    )

    /**
     * The current week's captured offers (verdicts + outcomes) for the recommendation rules (B-048),
     * recomputed as offers resolve. Window is the Monday-anchored current week, matching the dashboard.
     */
    private val weekOffers: StateFlow<List<OfferEntity>> = run {
        val now = clock.nowMs()
        offerDao.observeSeenBetween(weekClock.weekStartMs(now), weekClock.weekEndMs(now))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The current week's completed trips, for the best-hours rule (B-048). */
    private val weekTrips: StateFlow<List<TripEntity>> = run {
        val now = clock.nowMs()
        tripDao.observeCompletedStartedBetween(weekClock.weekStartMs(now), weekClock.weekEndMs(now))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The "Consejos" section (B-048): runs the pure [RecommendationsBuilder] over the week's stats,
     * the city percentiles, the captured offers/trips and the cross-platform net $/km, then flags the
     * premium gate from [Capability.RECOMMENDATIONS]. Re-derives whenever any input changes.
     */
    private val recommendations: StateFlow<RecommendationsUiState> = combine(
        baseState,
        percentiles,
        weekOffers,
        weekTrips,
        combine(
            aggregateDao.observeWeekly(),
            costProfileRepository.profile,
            settingsRepository.settings.map { it.city }.distinctUntilChanged(),
            tierGatekeeper.gateFor(Capability.RECOMMENDATIONS),
        ) { weekly, profile, city, gate -> RecInputs(weekly, profile, city, gate) },
    ) { base, pct, offers, trips, rec ->
        val marginalCostPerKm = CostProfileMapper.toCostProfileOrZero(rec.profile).marginalCostPerKm
        val bestHour = BestHourFinder(weekClock.zone, marginalCostPerKm).best(trips)
        val recs = RecommendationsBuilder.build(
            period = base.period,
            // Percentile-dependent rules use POPULATION data, so they gate on the BENCHMARKS gate, NOT
            // the (own-data) RECOMMENDATIONS gate — otherwise a premium-but-unverified driver, whose
            // RECOMMENDATIONS stays unlocked but whose BENCHMARKS is NEEDS_VERIFICATION, would leak the
            // standing through Consejos, outside the PaywallGate tease (PR-E, codex review).
            percentiles = if (pct.gateState.isUnlocked) pct.byMetric.values.toList() else emptyList(),
            cityLabel = rec.city.displayName,
            streakWeeks = base.streak.weeks,
            weeklyNetGoalMxn = if (base.goal.hasGoal) base.goal.goalMxn else null,
            offers = offers,
            bestHour = bestHour,
            crossPlatform = if (rec.gate.isUnlocked) {
                crossPlatformRates(rec.weekly, weekClock.weekStartIso(clock.nowMs()))
            } else {
                emptyList()
            },
        )
        RecommendationsUiState(recommendations = recs, locked = rec.gate.isLocked)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecommendationsUiState.EMPTY,
    )

    val uiState: StateFlow<InicioUiState> = combine(baseState, percentiles, recommendations) { base, pct, recs ->
        base.copy(percentiles = pct, recommendations = recs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InicioUiState.LOADING,
    )

    /**
     * Per-platform net $/km for the current captured week, for the cross-platform tip. Built from each
     * captured platform-row's [WeeklyAggregateEntity.earningsPerKm] regardless of the chip selection,
     * since the tip is about *choosing between* platforms. Only platforms with a positive rate are
     * included; fewer than two ⇒ the rule guards itself out.
     */
    private fun crossPlatformRates(
        allWeekly: List<WeeklyAggregateEntity>,
        currentWeekStart: String,
    ): List<CrossPlatformRate> =
        allWeekly
            .filter { it.weekStart == currentWeekStart && it.source == AggregateSource.CAPTURED.name }
            .mapNotNull { row ->
                row.earningsPerKm?.takeIf { it > 0.0 }?.let { CrossPlatformRate(row.platform, it) }
            }

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
    /** The "Consejos" section's top-3 recommendations + gate (B-048); empty when no rule fires. */
    val recommendations: RecommendationsUiState = RecommendationsUiState.EMPTY,
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
            recommendations = RecommendationsUiState.EMPTY,
        )
    }
}

/**
 * The bundled inputs for the [InicioDashboardViewModel] recommendations recompute that aren't already
 * in the base/percentile flows — the full weekly table (for cross-platform rates), the cost profile
 * (for the best-hour net), the city (for percentile-praise copy) and the recommendations gate.
 * Bundled so the outer `combine` stays within arity and a recompute fires when any one changes.
 */
private data class RecInputs(
    val weekly: List<WeeklyAggregateEntity>,
    val profile: CostProfileEntity?,
    val city: mx.kompara.data.model.City,
    val gate: mx.kompara.billing.GateState,
)
