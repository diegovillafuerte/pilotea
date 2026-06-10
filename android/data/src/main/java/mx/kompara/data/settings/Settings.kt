package mx.kompara.data.settings

import mx.kompara.data.model.City
import mx.kompara.data.model.Platform

/**
 * User-tunable settings backing the reader: which platforms are active and the per-platform
 * acceptance thresholds.
 */
data class Settings(
    /** Platforms the driver has enabled for capture/verdicts. */
    val enabledPlatforms: Set<Platform>,
    /** Per-platform acceptance floors; missing entries fall back to [PlatformThreshold.DEFAULT]. */
    val thresholds: Map<Platform, PlatformThreshold>,
    /**
     * Whether anonymous parse-health telemetry (B-034) may be uploaded. Default
     * ON: the counters carry NO personal data — only host package/version, spec
     * revision, day buckets, and integer attempt/success/failure counts — so
     * they're safe to collect by default and let us spot a parser breakage
     * before drivers complain. The driver can turn it off; the uploader
     * short-circuits when this is false. (Per-report fixture submissions are a
     * separate, always-explicit opt-in and are NOT gated by this flag.)
     */
    val telemetryEnabled: Boolean = DEFAULT_TELEMETRY_ENABLED,
    /**
     * Whether the driver has finished the onboarding funnel (value pitch → prominent disclosure →
     * accessibility grant → OEM survival kit → ready). Default OFF: the root composable routes to
     * onboarding until this flips true, after which it shows the main shell and arms the service
     * watchdog (B-036).
     */
    val onboardingCompleted: Boolean = DEFAULT_ONBOARDING_COMPLETED,

    /**
     * The driver's weekly **net** earnings target in MXN (B-039 goals & streaks), or null when no
     * goal is set. Compared against a week's captured (or imported) net to drive the goal-progress UI
     * and feed the consecutive-weeks streak. Net, not gross — the whole point of the reader is the
     * honest number.
     */
    val weeklyNetGoalMxn: Double? = null,

    /**
     * Whether the driver has consented to upload their **anonymous, derived** weekly aggregates so
     * they build the real population benchmarks (B-043). Default **OFF** — unlike the parse-health
     * [telemetryEnabled] toggle, this shares the driver's own earnings aggregates, so it is strictly
     * opt-in and the onboarding/Ajustes flow must ask for it explicitly
     * ("Compartir mis promedios semanales de forma anónima para comparativas").
     *
     * What's shared when ON: ONLY the derived weekly aggregate fields (earnings totals + the 5
     * efficiency metrics per platform/week). Raw capture data — individual offers, trips, screen
     * text — NEVER leaves the device. The [mx.kompara.sync] AggregateSyncWorker short-circuits when
     * this is false (and when signed-out), so nothing is uploaded without both a session and consent.
     */
    val shareAggregates: Boolean = DEFAULT_SHARE_AGGREGATES,

    /**
     * Whether the one-shot aggregate-sharing consent prompt has been dismissed (B-043). The Inicio
     * prompt is shown only while NOT [shareAggregates] AND NOT this flag, so "Ahora no" records the
     * dismissal here and the driver isn't nagged again. Default false (prompt eligible on a fresh
     * install once onboarding completes). Independent of [shareAggregates] so a driver who declines
     * can still opt in later from Ajustes.
     */
    val aggregatePromptDismissed: Boolean = DEFAULT_AGGREGATE_PROMPT_DISMISSED,

    /**
     * The driver's city (B-043), selecting which population benchmarks the app downloads
     * (`GET /v1/benchmarks?city=…`). Defaults to [City.DEFAULT] (CDMX, the largest market) until the
     * driver picks one; changing it invalidates the cached benchmarks so the new city's percentiles
     * are fetched. One of the 10 seeded benchmark cities — see [City].
     */
    val city: City = City.DEFAULT,

    /**
     * Debug-only override that unlocks premium-gated surfaces (currently the B-046 percentile UI) so
     * they can be demoed before a paywall exists. **Additive**: the real entitlement still grants
     * premium when present; this only ever *adds* access, never removes it. Default OFF. Surfaced in
     * Ajustes (debug builds) and folded into the capability check as
     * `canSeeBenchmarks || debugPremium`. Remove or hide once B-050 ships a real paywall.
     */
    val debugPremium: Boolean = DEFAULT_DEBUG_PREMIUM,

    /**
     * Whether the month-end IMSS summary notification is enabled (B-051). On day 1 (or the next app
     * open) the app posts one notification per platform — covered / not covered the previous month —
     * on a dedicated "fiscal" channel. Default **ON**: it's a low-frequency, high-value heads-up about
     * social-security coverage; the driver can turn it off here and the month-end worker short-circuits.
     */
    val fiscalMonthlySummaryEnabled: Boolean = DEFAULT_FISCAL_MONTHLY_SUMMARY_ENABLED,

    /**
     * The "yyyy-MM" of the last month a month-end IMSS summary was posted (B-051), or null if never.
     * Idempotency watermark so the month-end worker doesn't double-post on a day-1 trigger plus a
     * next-app-open trigger. Not user-facing.
     */
    val fiscalLastNotifiedMonth: String? = null,
) {
    /** Threshold for [platform], or the default when none has been set. */
    fun thresholdFor(platform: Platform): PlatformThreshold =
        thresholds[platform] ?: PlatformThreshold.DEFAULT

    /** Whether capture/verdicts are active for [platform]. */
    fun isEnabled(platform: Platform): Boolean = platform in enabledPlatforms

    companion object {
        /** Anonymous telemetry is on by default (no personal data — see [telemetryEnabled]). */
        const val DEFAULT_TELEMETRY_ENABLED = true

        /** Onboarding has not run yet for a fresh install — route to the funnel first (B-036). */
        const val DEFAULT_ONBOARDING_COMPLETED = false

        /** Aggregate sharing is OFF by default — strictly opt-in (B-043; see [shareAggregates]). */
        const val DEFAULT_SHARE_AGGREGATES = false

        /** The consent prompt has not been dismissed on a fresh install (B-043). */
        const val DEFAULT_AGGREGATE_PROMPT_DISMISSED = false

        /** The debug premium override is OFF by default — real entitlement decides (B-046). */
        const val DEFAULT_DEBUG_PREMIUM = false

        /** Month-end IMSS summary notification is ON by default (B-051; see [fiscalMonthlySummaryEnabled]). */
        const val DEFAULT_FISCAL_MONTHLY_SUMMARY_ENABLED = true

        /** Launch defaults: Uber + DiDi enabled, default thresholds, telemetry on, onboarding pending. */
        val DEFAULT = Settings(
            enabledPlatforms = setOf(Platform.UBER, Platform.DIDI),
            thresholds = emptyMap(),
            telemetryEnabled = DEFAULT_TELEMETRY_ENABLED,
            onboardingCompleted = DEFAULT_ONBOARDING_COMPLETED,
            shareAggregates = DEFAULT_SHARE_AGGREGATES,
            aggregatePromptDismissed = DEFAULT_AGGREGATE_PROMPT_DISMISSED,
            city = City.DEFAULT,
            debugPremium = DEFAULT_DEBUG_PREMIUM,
            fiscalMonthlySummaryEnabled = DEFAULT_FISCAL_MONTHLY_SUMMARY_ENABLED,
        )
    }
}
