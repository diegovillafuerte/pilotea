package mx.kompara.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
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

    val settings: Flow<Settings> = dataStore.data.map { prefs -> prefs.toSettings() }

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
        )
}
