package mx.kompara.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
        )
}
