package mx.kompara.ui.share

/** Shared period-delivery decision, retaining the existing week-close owner. Disabled/empty
 * periods can be handled silently; driving or Android denial must retain the dated pending work. */
object WeekCloseDecision {

    /**
     * @param enabled the [mx.kompara.data.settings.Settings.shareWeeklyReminderEnabled] toggle.
     * @param closedWeekStart ISO Monday of the week that just closed (the one being summarised).
     * @param lastReminderWeek ISO Monday already reminded for, or null if never.
     * @param hasData whether that closed week actually has captured data — no point nudging a driver
     *   who didn't drive at all.
     * @return what the worker should do.
     */
    fun decide(
        enabled: Boolean,
        closedWeekStart: String,
        lastReminderWeek: String?,
        hasData: Boolean,
        busy: Boolean = false,
        allowed: Boolean = true,
    ): WeekCloseAction {
        // Already handled this week (posted or stamped) → nothing to do, no re-stamp needed.
        if (lastReminderWeek == closedWeekStart) return WeekCloseAction.SKIP
        // Toggle off, or the driver had no data that week → stamp so we don't keep re-checking, but
        // post nothing.
        if (!enabled || !hasData) return WeekCloseAction.STAMP_ONLY
        if (busy || !allowed) return WeekCloseAction.DEFER
        return WeekCloseAction.POST
    }
}

/** The outcome of [WeekCloseDecision.decide]. */
enum class WeekCloseAction {
    /** Driving or Android denial; retain the period and retry later. */
    DEFER,
    /** Post the reminder notification, then stamp the watermark. */
    POST,

    /** Post nothing, but stamp the watermark so this closed week isn't re-evaluated. */
    STAMP_ONLY,

    /** Do nothing at all — already handled this closed week. */
    SKIP,
}
