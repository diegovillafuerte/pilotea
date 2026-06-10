package mx.kompara.ui.share

/**
 * Pure scheduling decision for the Monday week-close share reminder (B-055), split out so the
 * "should we post now?" logic is unit-tested without WorkManager or a notifier.
 *
 * The reminder is for the **week that just closed** — the previous Monday's week. We post at most once
 * per closed week (idempotency watermark [lastReminderWeek]) and only when the driver hasn't turned the
 * reminder off. The worker stamps the watermark even when it decides not to post a notification, so an
 * idle/disabled week is never re-evaluated forever.
 */
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
    ): WeekCloseAction {
        // Already handled this week (posted or stamped) → nothing to do, no re-stamp needed.
        if (lastReminderWeek == closedWeekStart) return WeekCloseAction.SKIP
        // Toggle off, or the driver had no data that week → stamp so we don't keep re-checking, but
        // post nothing.
        if (!enabled || !hasData) return WeekCloseAction.STAMP_ONLY
        return WeekCloseAction.POST
    }
}

/** The outcome of [WeekCloseDecision.decide]. */
enum class WeekCloseAction {
    /** Post the reminder notification, then stamp the watermark. */
    POST,

    /** Post nothing, but stamp the watermark so this closed week isn't re-evaluated. */
    STAMP_ONLY,

    /** Do nothing at all — already handled this closed week. */
    SKIP,
}
