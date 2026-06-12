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

    /** Boolean key for the month-end IMSS summary notification toggle (B-051); default ON. */
    const val KEY_FISCAL_MONTHLY_SUMMARY = "fiscal_monthly_summary_enabled"

    /** String key ("yyyy-MM") for the last month a month-end IMSS summary was posted (B-051). */
    const val KEY_FISCAL_LAST_NOTIFIED_MONTH = "fiscal_last_notified_month"

    /** Boolean key for the share-card hide-amounts default (B-055); default OFF. */
    const val KEY_SHARE_HIDE_AMOUNTS = "share_hide_amounts"

    /** Boolean key for the Monday week-close share reminder toggle (B-055); default ON. */
    const val KEY_SHARE_WEEKLY_REMINDER = "share_weekly_reminder_enabled"

    /** String key (ISO Monday) for the last week a share reminder was posted (B-055). */
    const val KEY_SHARE_LAST_REMINDER_WEEK = "share_last_reminder_week"

    /** Int key for the anonymous local share-tap funnel counter (B-055). */
    const val KEY_SHARE_COUNT = "share_count"

    /** Int key for the anonymous local fiscal-PDF-export funnel counter (B-052). */
    const val KEY_FISCAL_EXPORT_COUNT = "fiscal_export_count"

    /** The single shared semáforo (B-076): green + red floors for $/km and $/hr, platform-less. */
    const val KEY_THRESHOLD_PER_KM = "threshold_per_km"
    const val KEY_THRESHOLD_PER_HOUR = "threshold_per_hour"
    const val KEY_THRESHOLD_PER_KM_RED = "threshold_per_km_red"
    const val KEY_THRESHOLD_PER_HOUR_RED = "threshold_per_hour_red"

    /** Legacy per-platform keys (pre-B-076). Read-only: decode migrates them, nothing writes them. */
    fun perKmKey(platform: Platform): String = "threshold_${platform.name}_per_km"
    fun perHourKey(platform: Platform): String = "threshold_${platform.name}_per_hour"

    /** Red floors (two-tier verdict). Absent on installs that predate them ⇒ derived from green. */
    fun perKmRedKey(platform: Platform): String = "threshold_${platform.name}_per_km_red"
    fun perHourRedKey(platform: Platform): String = "threshold_${platform.name}_per_hour_red"

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
        lookupInt: (String) -> Int? = { null },
    ): Settings {
        return Settings(
            enabledPlatforms = decodeEnabledPlatforms(enabledNames),
            threshold = decodeThreshold(lookupDouble),
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
            fiscalMonthlySummaryEnabled = lookupBoolean(KEY_FISCAL_MONTHLY_SUMMARY)
                ?: Settings.DEFAULT_FISCAL_MONTHLY_SUMMARY_ENABLED,
            fiscalLastNotifiedMonth = lookupString(KEY_FISCAL_LAST_NOTIFIED_MONTH),
            shareHideAmounts = lookupBoolean(KEY_SHARE_HIDE_AMOUNTS)
                ?: Settings.DEFAULT_SHARE_HIDE_AMOUNTS,
            shareWeeklyReminderEnabled = lookupBoolean(KEY_SHARE_WEEKLY_REMINDER)
                ?: Settings.DEFAULT_SHARE_WEEKLY_REMINDER_ENABLED,
            shareLastReminderWeek = lookupString(KEY_SHARE_LAST_REMINDER_WEEK),
            shareCount = lookupInt(KEY_SHARE_COUNT) ?: 0,
            fiscalExportCount = lookupInt(KEY_FISCAL_EXPORT_COUNT) ?: 0,
        )
    }

    /**
     * The shared semáforo (B-076), or null when never tuned. Prefers the platform-less keys; an
     * install upgrading from per-platform floors migrates the first match of DiDi → Uber → inDrive
     * (DiDi is the OCR-live platform the pilot drivers actually tuned). The legacy keys are left in
     * place — once the global keys are written they win, so the fallback only runs pre-first-write.
     */
    private fun decodeThreshold(lookupDouble: (String) -> Double?): PlatformThreshold? {
        thresholdAt(
            KEY_THRESHOLD_PER_KM,
            KEY_THRESHOLD_PER_HOUR,
            KEY_THRESHOLD_PER_KM_RED,
            KEY_THRESHOLD_PER_HOUR_RED,
            lookupDouble,
        )?.let { return it }
        for (platform in listOf(Platform.DIDI, Platform.UBER, Platform.INDRIVE)) {
            thresholdAt(
                perKmKey(platform),
                perHourKey(platform),
                perKmRedKey(platform),
                perHourRedKey(platform),
                lookupDouble,
            )?.let { return it }
        }
        return null
    }

    /** A [PlatformThreshold] from one key quartet, or null when neither green floor was written. */
    private fun thresholdAt(
        perKmKey: String,
        perHourKey: String,
        perKmRedKey: String,
        perHourRedKey: String,
        lookupDouble: (String) -> Double?,
    ): PlatformThreshold? {
        val perKm = lookupDouble(perKmKey)
        val perHour = lookupDouble(perHourKey)
        if (perKm == null && perHour == null) return null
        val greenPerKm = perKm ?: PlatformThreshold.DEFAULT.minPerKmMxn
        val greenPerHour = perHour ?: PlatformThreshold.DEFAULT.minPerHourMxn
        // Red floors landed after green floors shipped: absent keys derive from the green floor
        // (and a stored red is clamped to it) so pre-existing installs migrate free.
        return PlatformThreshold(
            minPerKmMxn = greenPerKm,
            minPerHourMxn = greenPerHour,
            redPerKmMxn = lookupDouble(perKmRedKey)?.coerceAtMost(greenPerKm)
                ?: (greenPerKm * PlatformThreshold.DEFAULT_RED_FRACTION),
            redPerHourMxn = lookupDouble(perHourRedKey)?.coerceAtMost(greenPerHour)
                ?: (greenPerHour * PlatformThreshold.DEFAULT_RED_FRACTION),
        )
    }
}
