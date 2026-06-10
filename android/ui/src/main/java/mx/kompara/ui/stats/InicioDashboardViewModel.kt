package mx.kompara.ui.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.data.db.entity.CostProfileEntity
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.data.settings.Settings
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.ui.onboarding.AccessibilitySettings
import mx.kompara.ui.onboarding.ServiceWatchdog
import mx.kompara.ui.onboarding.WatchdogState
import javax.inject.Inject

/**
 * Backs the Inicio dashboard (B-040 req 1): this week's net header, streak, weekly-goal progress,
 * the five metric cards for the selected platform, the platform chips, the data-completeness hint,
 * the watchdog banner, and the first-run cost-profile nudge.
 *
 * Reactive end-to-end: it combines the captured weekly aggregates (B-039), settings (goal), and the
 * cost profile so the screen updates the instant any of them change. All selection/aggregation math
 * lives in the pure [PeriodStats]/[PlatformSelection]/[GoalProgress] helpers so it's unit-tested
 * without a real database.
 */
@HiltViewModel
class InicioDashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aggregateDao: AggregateDao,
    settingsRepository: SettingsRepository,
    costProfileRepository: CostProfileRepository,
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

    val uiState: StateFlow<InicioUiState> = combine(
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
        )
    }
}
