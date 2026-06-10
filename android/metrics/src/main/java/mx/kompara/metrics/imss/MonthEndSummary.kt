package mx.kompara.metrics.imss

/**
 * Pure decision logic for the month-end IMSS summary notification (B-051).
 *
 * Separated from the Android notifier so the "should we post, and what does each one say" logic is
 * unit-testable on the JVM. The Android side (channel, POST_NOTIFICATIONS guard, the WorkManager
 * trigger) only consumes the [MonthEndDecision] list this produces.
 *
 * The rule: on day 1 of a new month (or the next app open after), for the **just-ended** month, post
 * one notification per platform that had any activity — "cubierto" if its net cleared the threshold,
 * "no cubierto" otherwise. A platform with no activity at all is skipped (nothing to report). A month
 * already notified ([alreadyNotifiedMonth]) yields no decisions, so the worker is idempotent across a
 * day-1 trigger plus a next-app-open trigger.
 */
object MonthEndSummary {

    /**
     * Decide what month-end notifications to post for [monthKey] (e.g. "2026-05") given the platform
     * statuses computed for that closed month.
     *
     * @param enabled the user's month-end-summary toggle; when false, returns empty (post nothing).
     * @param statuses the per-platform [PlatformImssStatus] for the closed month (phase = PAST).
     * @param alreadyNotifiedMonth the monthKey we last posted a summary for (idempotency guard); when
     *   it equals [monthKey], returns empty.
     */
    fun decide(
        monthKey: String,
        enabled: Boolean,
        statuses: List<PlatformImssStatus>,
        alreadyNotifiedMonth: String?,
    ): List<MonthEndDecision> {
        if (!enabled) return emptyList()
        if (alreadyNotifiedMonth == monthKey) return emptyList()
        return statuses
            // Only platforms that actually worked last month are worth a verdict.
            .filter { it.netSoFarMxn > 0.0 }
            .map { status ->
                MonthEndDecision(
                    platform = status.platform,
                    monthKey = monthKey,
                    covered = status.covered,
                    netMxn = status.netSoFarMxn,
                    thresholdMxn = status.thresholdMxn,
                )
            }
    }
}

/** One month-end notification to post (or, in tests, to assert). */
data class MonthEndDecision(
    val platform: String,
    /** The closed month this verdict is for, "yyyy-MM". */
    val monthKey: String,
    /** True ⇒ "cubierto el mes pasado"; false ⇒ "no cubierto". */
    val covered: Boolean,
    val netMxn: Double,
    val thresholdMxn: Double,
)
