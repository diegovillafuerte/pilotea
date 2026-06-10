package mx.kompara.ui.stats

import mx.kompara.data.db.entity.AggregateSource
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform
import mx.kompara.data.rollup.StreakCalculator

/**
 * The pure core of [InicioDashboardViewModel.buildState] (B-040 req 1): given the full weekly table,
 * the current week-start, the goal, the platform selection and whether a cost profile exists, pick
 * the current week's captured rows and fold them into an [InicioUiState]. Extracted so the dashboard
 * aggregation/selection logic is unit-testable with fake data, without the Android-bound watchdog.
 */
object InicioStats {

    fun forCurrentWeek(
        allWeekly: List<WeeklyAggregateEntity>,
        currentWeekStart: String,
        weeklyNetGoalMxn: Double?,
        selectedPlatform: Platform?,
        costProfileSet: Boolean,
    ): InicioUiState {
        val capturedThisWeek = allWeekly.filter {
            it.weekStart == currentWeekStart && it.source == AggregateSource.CAPTURED.name
        }
        val chips = PlatformSelection.chips(capturedThisWeek)
        val resolved = PlatformSelection.resolve(capturedThisWeek, selectedPlatform)
        val period = PeriodStats.fromWeekly(capturedThisWeek, resolved)
        val streakWeeks = StreakCalculator().streak(allWeekly.map { it.weekStart }.distinct())

        return InicioUiState(
            loading = false,
            hasData = !period.isEmpty,
            period = period,
            chips = chips,
            selectedPlatform = resolved,
            goal = GoalProgress.of(weeklyNetGoalMxn, period.netEarningsMxn),
            streak = StreakDisplay(streakWeeks),
            completeness = CompletenessHints.hintFor(
                hasTrips = period.totalTrips > 0,
                hoursOnline = period.hoursOnline,
                tripsEstimated = true,
            ),
            costProfileSet = costProfileSet,
        )
    }
}
