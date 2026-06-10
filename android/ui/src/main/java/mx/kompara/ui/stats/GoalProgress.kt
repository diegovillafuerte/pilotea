package mx.kompara.ui.stats

/**
 * The weekly net-goal header state (B-039 goals & streaks), as the dashboard renders it.
 *
 * @property hasGoal whether the driver set a target at all — no goal ⇒ no progress bar.
 * @property goalMxn the target net in MXN (0 when [hasGoal] is false).
 * @property netMxn net earned so far this week.
 * @property fraction 0f..1f progress toward the goal, clamped (>100% reads as full).
 * @property reached whether the goal has been met or beaten.
 * @property remainingMxn pesos still to go (0 once reached).
 */
data class GoalProgress(
    val hasGoal: Boolean,
    val goalMxn: Double,
    val netMxn: Double,
    val fraction: Float,
    val reached: Boolean,
    val remainingMxn: Double,
) {
    companion object {
        /**
         * Compute progress toward a weekly net goal.
         *
         * @param goalMxn the target, or null/non-positive when no goal is set.
         * @param netMxn net earned this week (may be negative on a bad week — fraction floors at 0).
         */
        fun of(goalMxn: Double?, netMxn: Double): GoalProgress {
            if (goalMxn == null || goalMxn <= 0.0) {
                return GoalProgress(
                    hasGoal = false,
                    goalMxn = 0.0,
                    netMxn = netMxn,
                    fraction = 0f,
                    reached = false,
                    remainingMxn = 0.0,
                )
            }
            val fraction = (netMxn / goalMxn).toFloat().coerceIn(0f, 1f)
            val reached = netMxn >= goalMxn
            return GoalProgress(
                hasGoal = true,
                goalMxn = goalMxn,
                netMxn = netMxn,
                fraction = fraction,
                reached = reached,
                remainingMxn = (goalMxn - netMxn).coerceAtLeast(0.0),
            )
        }
    }
}

/**
 * The consecutive-weeks streak badge state. A streak of 0 is not shown (a driver with no run yet
 * shouldn't see "0 semanas"); 1+ renders "N semana(s)".
 */
data class StreakDisplay(
    val weeks: Int,
) {
    /** Whether to render the badge at all. */
    val visible: Boolean get() = weeks >= 1

    /** Whether the unit should read "semana" (singular) vs "semanas". */
    val singular: Boolean get() = weeks == 1
}
