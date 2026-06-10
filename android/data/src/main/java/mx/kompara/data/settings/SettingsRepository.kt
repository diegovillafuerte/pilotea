package mx.kompara.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import mx.kompara.data.model.City
import mx.kompara.data.model.Platform
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flow-based access to the driver's [Settings], backed by DataStore preferences.
 *
 * Reads expose a cold [Flow] that re-emits on every change; writes are suspending. The
 * encode/decode logic lives in [SettingsSerialization] so it stays unit-testable.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val enabledPlatformsKey =
        stringSetPreferencesKey(SettingsSerialization.KEY_ENABLED_PLATFORMS)
    private val telemetryEnabledKey =
        booleanPreferencesKey(SettingsSerialization.KEY_TELEMETRY_ENABLED)
    private val onboardingCompletedKey =
        booleanPreferencesKey(SettingsSerialization.KEY_ONBOARDING_COMPLETED)
    private val weeklyNetGoalKey =
        doublePreferencesKey(SettingsSerialization.KEY_WEEKLY_NET_GOAL)
    private val shareAggregatesKey =
        booleanPreferencesKey(SettingsSerialization.KEY_SHARE_AGGREGATES)
    private val aggregatePromptDismissedKey =
        booleanPreferencesKey(SettingsSerialization.KEY_AGGREGATE_PROMPT_DISMISSED)
    private val cityKey =
        stringPreferencesKey(SettingsSerialization.KEY_CITY)
    private val debugPremiumKey =
        booleanPreferencesKey(SettingsSerialization.KEY_DEBUG_PREMIUM)
    private val fiscalMonthlySummaryKey =
        booleanPreferencesKey(SettingsSerialization.KEY_FISCAL_MONTHLY_SUMMARY)
    private val fiscalLastNotifiedMonthKey =
        stringPreferencesKey(SettingsSerialization.KEY_FISCAL_LAST_NOTIFIED_MONTH)

    val settings: Flow<Settings> = dataStore.data.map { prefs -> prefs.toSettings() }

    /** Snapshot read of whether anonymous telemetry upload is currently allowed. */
    suspend fun isTelemetryEnabled(): Boolean = settings.first().telemetryEnabled

    /** Turn anonymous parse-health telemetry upload on or off (B-034). */
    suspend fun setTelemetryEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[telemetryEnabledKey] = enabled }
    }

    /** Snapshot read of whether the onboarding funnel has been completed (B-036). */
    suspend fun isOnboardingCompleted(): Boolean = settings.first().onboardingCompleted

    /** Mark the onboarding funnel as completed; flips the root composable to the main shell (B-036). */
    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { prefs -> prefs[onboardingCompletedKey] = completed }
    }

    /**
     * Snapshot read of whether consented aggregate sharing is on (B-043). The sync worker also
     * reads this; default OFF so nothing uploads until the driver explicitly opts in.
     */
    suspend fun isShareAggregatesEnabled(): Boolean = settings.first().shareAggregates

    /**
     * Turn consented aggregate sharing on or off (B-043). When ON (and signed in) the
     * [mx.kompara.sync] AggregateSyncWorker uploads ONLY derived weekly aggregates; raw capture data
     * never leaves the device. Turning it off does not retroactively delete already-uploaded
     * aggregates (account deletion is the path for that) — it just stops future uploads.
     */
    suspend fun setShareAggregates(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[shareAggregatesKey] = enabled }
    }

    /**
     * Record whether the one-shot aggregate-sharing consent prompt has been dismissed (B-043), so the
     * Inicio prompt isn't shown again after the driver declines. Independent of [setShareAggregates].
     */
    suspend fun setAggregatePromptDismissed(dismissed: Boolean) {
        dataStore.edit { prefs -> prefs[aggregatePromptDismissedKey] = dismissed }
    }

    /** Snapshot read of the driver's benchmark city (B-043). */
    suspend fun currentCity(): City = settings.first().city

    /** Snapshot read of whether the month-end IMSS summary notification is enabled (B-051). */
    suspend fun isFiscalMonthlySummaryEnabled(): Boolean = settings.first().fiscalMonthlySummaryEnabled

    /**
     * Turn the month-end IMSS summary notification on or off (B-051). When OFF, the month-end worker
     * short-circuits and posts nothing. Default ON.
     */
    suspend fun setFiscalMonthlySummaryEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[fiscalMonthlySummaryKey] = enabled }
    }

    /**
     * The "yyyy-MM" of the last month a month-end IMSS summary was posted (B-051), or null if never.
     * The month-end worker compares this against the just-ended month so a day-1 trigger plus a
     * next-app-open trigger don't double-post (idempotency watermark).
     */
    suspend fun fiscalLastNotifiedMonth(): String? = settings.first().fiscalLastNotifiedMonth

    /** Record that the month-end summary for [monthKey] ("yyyy-MM") has been posted (B-051). */
    suspend fun setFiscalLastNotifiedMonth(monthKey: String) {
        dataStore.edit { prefs -> prefs[fiscalLastNotifiedMonthKey] = monthKey }
    }

    /**
     * Set the driver's benchmark [city] (B-043). Changing it should invalidate the cached benchmarks
     * (the caller — BenchmarksRepository — keys its cache on city and re-fetches on change).
     */
    suspend fun setCity(city: City) {
        dataStore.edit { prefs -> prefs[cityKey] = city.name }
    }

    /**
     * Toggle the debug premium override (B-046). Additive over the real entitlement so a tester can
     * preview the percentile UI before a paywall exists. Default OFF.
     */
    suspend fun setDebugPremium(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[debugPremiumKey] = enabled }
    }

    /** Enable or disable capture/verdicts for a single platform. */
    suspend fun setPlatformEnabled(platform: Platform, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = SettingsSerialization
                .decodeEnabledPlatforms(prefs[enabledPlatformsKey])
                .toMutableSet()
            if (enabled) current.add(platform) else current.remove(platform)
            prefs[enabledPlatformsKey] = current.map { it.name }.toSet()
        }
    }

    /**
     * Set or clear the weekly net earnings goal in MXN (B-039). A null or non-positive [goalMxn]
     * clears the goal (no target). Drives goal-progress UI and the streak.
     */
    suspend fun setWeeklyNetGoal(goalMxn: Double?) {
        dataStore.edit { prefs ->
            if (goalMxn == null || goalMxn <= 0.0) {
                prefs.remove(weeklyNetGoalKey)
            } else {
                prefs[weeklyNetGoalKey] = goalMxn
            }
        }
    }

    /** Set the acceptance thresholds for a single platform. */
    suspend fun setThreshold(platform: Platform, threshold: PlatformThreshold) {
        dataStore.edit { prefs ->
            prefs[doublePreferencesKey(SettingsSerialization.perKmKey(platform))] =
                threshold.minPerKmMxn
            prefs[doublePreferencesKey(SettingsSerialization.perHourKey(platform))] =
                threshold.minPerHourMxn
        }
    }

    private fun Preferences.toSettings(): Settings =
        SettingsSerialization.decode(
            enabledNames = this[enabledPlatformsKey],
            lookupDouble = { key -> this[doublePreferencesKey(key)] },
            lookupBoolean = { key -> this[booleanPreferencesKey(key)] },
            lookupString = { key -> this[stringPreferencesKey(key)] },
        )
}
