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
) {
    /** Threshold for [platform], or the default when none has been set. */
    fun thresholdFor(platform: Platform): PlatformThreshold =
        thresholds[platform] ?: PlatformThreshold.DEFAULT

    /** Whether capture/verdicts are active for [platform]. */
    fun isEnabled(platform: Platform): Boolean = platform in enabledPlatforms

    companion object {
        /** Anonymous telemetry is on by default (no personal data — see [telemetryEnabled]). */
        const val DEFAULT_TELEMETRY_ENABLED = true

        /** Launch defaults: Uber + DiDi enabled, default thresholds, telemetry on. */
        val DEFAULT = Settings(
            enabledPlatforms = setOf(Platform.UBER, Platform.DIDI),
            thresholds = emptyMap(),
            telemetryEnabled = DEFAULT_TELEMETRY_ENABLED,
        )
    }
}
