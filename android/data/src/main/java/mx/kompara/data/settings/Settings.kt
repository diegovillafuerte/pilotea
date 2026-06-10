package mx.kompara.data.settings

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

        /** Launch defaults: Uber + DiDi enabled, default thresholds, telemetry on, onboarding pending. */
        val DEFAULT = Settings(
            enabledPlatforms = setOf(Platform.UBER, Platform.DIDI),
            thresholds = emptyMap(),
            telemetryEnabled = DEFAULT_TELEMETRY_ENABLED,
            onboardingCompleted = DEFAULT_ONBOARDING_COMPLETED,
        )
    }
}
