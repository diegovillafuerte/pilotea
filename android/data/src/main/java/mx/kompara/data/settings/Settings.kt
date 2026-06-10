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
) {
    /** Threshold for [platform], or the default when none has been set. */
    fun thresholdFor(platform: Platform): PlatformThreshold =
        thresholds[platform] ?: PlatformThreshold.DEFAULT

    /** Whether capture/verdicts are active for [platform]. */
    fun isEnabled(platform: Platform): Boolean = platform in enabledPlatforms

    companion object {
        /** Launch defaults: Uber + DiDi enabled, default thresholds. */
        val DEFAULT = Settings(
            enabledPlatforms = setOf(Platform.UBER, Platform.DIDI),
            thresholds = emptyMap(),
        )
    }
}
