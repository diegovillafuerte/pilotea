package mx.kompara.data.settings

import mx.kompara.data.model.City
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

    /** Boolean key for the anonymous-telemetry consent toggle (B-034). */
    const val KEY_TELEMETRY_ENABLED = "telemetry_enabled"

    /** Boolean key for whether the onboarding funnel has been completed (B-036). */
    const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

    /** Double key for the weekly net earnings goal in MXN (B-039); absent ⇒ no goal set. */
    const val KEY_WEEKLY_NET_GOAL = "weekly_net_goal_mxn"

    /** Boolean key for the consented aggregate-sharing toggle (B-043); default OFF. */
    const val KEY_SHARE_AGGREGATES = "share_aggregates"

    /** Boolean key for whether the one-shot aggregate-sharing prompt was dismissed (B-043). */
    const val KEY_AGGREGATE_PROMPT_DISMISSED = "aggregate_prompt_dismissed"

    /** String key for the driver's benchmark city (B-043), stored as [City.name]; absent ⇒ default. */
    const val KEY_CITY = "benchmark_city"

    /** Boolean key for the debug premium override (B-046); default OFF, additive over entitlement. */
    const val KEY_DEBUG_PREMIUM = "debug_premium"

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

    /** Decode the stored [City] enum name back into a [City]; unknown/absent ⇒ [City.DEFAULT]. */
    fun decodeCity(name: String?): City {
        if (name == null) return City.DEFAULT
        return runCatching { City.valueOf(name) }.getOrNull() ?: City.DEFAULT
    }

    /**
     * Reconstruct [Settings] from a flat lookup of stored preferences. [lookupDouble] returns
     * null when a key is absent (so defaults apply); [enabledNames] is the stored name set.
     * [lookupBoolean] returns null when the telemetry toggle has never been written, in which
     * case the default ([Settings.DEFAULT_TELEMETRY_ENABLED]) applies.
     */
    fun decode(
        enabledNames: Set<String>?,
        lookupDouble: (String) -> Double?,
        lookupBoolean: (String) -> Boolean? = { null },
        lookupString: (String) -> String? = { null },
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
            telemetryEnabled = lookupBoolean(KEY_TELEMETRY_ENABLED)
                ?: Settings.DEFAULT_TELEMETRY_ENABLED,
            onboardingCompleted = lookupBoolean(KEY_ONBOARDING_COMPLETED)
                ?: Settings.DEFAULT_ONBOARDING_COMPLETED,
            weeklyNetGoalMxn = lookupDouble(KEY_WEEKLY_NET_GOAL),
            shareAggregates = lookupBoolean(KEY_SHARE_AGGREGATES)
                ?: Settings.DEFAULT_SHARE_AGGREGATES,
            aggregatePromptDismissed = lookupBoolean(KEY_AGGREGATE_PROMPT_DISMISSED)
                ?: Settings.DEFAULT_AGGREGATE_PROMPT_DISMISSED,
            city = decodeCity(lookupString(KEY_CITY)),
            debugPremium = lookupBoolean(KEY_DEBUG_PREMIUM)
                ?: Settings.DEFAULT_DEBUG_PREMIUM,
        )
    }
}
