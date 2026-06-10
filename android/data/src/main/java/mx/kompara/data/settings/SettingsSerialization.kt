package mx.kompara.data.settings

import mx.kompara.data.model.Platform

/**
 * Pure (Android-free) conversion between [Settings] and the flat key/value form stored in
 * DataStore preferences. Kept separate from the repository so the encode/decode logic can be
 * unit-tested on the plain JVM without instrumentation.
 *
 * Encoding:
 *  - enabled platforms → a set of platform names under [KEY_ENABLED_PLATFORMS]
 *  - each platform's thresholds → two doubles keyed [perKmKey]/[perHourKey]
 */
object SettingsSerialization {

    const val KEY_ENABLED_PLATFORMS = "enabled_platforms"

    fun perKmKey(platform: Platform): String = "threshold_${platform.name}_per_km"
    fun perHourKey(platform: Platform): String = "threshold_${platform.name}_per_hour"

    /** Names of platforms that should be persisted as enabled. */
    fun encodeEnabledPlatforms(settings: Settings): Set<String> =
        settings.enabledPlatforms.map { it.name }.toSet()

    /** Decode the enabled-platform name set back into [Platform]s, ignoring unknown names. */
    fun decodeEnabledPlatforms(names: Set<String>?): Set<Platform> {
        if (names == null) return Settings.DEFAULT.enabledPlatforms
        return names.mapNotNull { name -> runCatching { Platform.valueOf(name) }.getOrNull() }.toSet()
    }

    /**
     * Reconstruct [Settings] from a flat lookup of stored preferences. [lookupDouble] returns
     * null when a key is absent (so defaults apply); [enabledNames] is the stored name set.
     */
    fun decode(
        enabledNames: Set<String>?,
        lookupDouble: (String) -> Double?,
    ): Settings {
        val thresholds = mutableMapOf<Platform, PlatformThreshold>()
        for (platform in Platform.entries) {
            val perKm = lookupDouble(perKmKey(platform))
            val perHour = lookupDouble(perHourKey(platform))
            if (perKm != null || perHour != null) {
                thresholds[platform] = PlatformThreshold(
                    minPerKmMxn = perKm ?: PlatformThreshold.DEFAULT.minPerKmMxn,
                    minPerHourMxn = perHour ?: PlatformThreshold.DEFAULT.minPerHourMxn,
                )
            }
        }
        return Settings(
            enabledPlatforms = decodeEnabledPlatforms(enabledNames),
            thresholds = thresholds,
        )
    }
}
