package mx.kompara.overlay

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first
import mx.kompara.parsers.model.HostOrientation
import javax.inject.Inject
import javax.inject.Singleton

data class OverlayPositionKey(val host: String, val orientation: HostOrientation) {
    val prefix get() = "overlay_v3_${host}_${orientation.name}"
}

/** A saved drag is a preference, never proof that the next host layout can fit the plate. */
@Singleton
class OverlayPrefs @Inject constructor(private val dataStore: DataStore<Preferences>) {
    suspend fun getPosition(key: OverlayPositionKey): OverlayPosition? {
        val prefs = dataStore.data.first()
        val x = prefs[intPreferencesKey("${key.prefix}_x")] ?: return null
        val y = prefs[intPreferencesKey("${key.prefix}_y")] ?: return null
        return OverlayPosition(x, y)
    }

    suspend fun savePosition(key: OverlayPositionKey, position: OverlayPosition) {
        dataStore.edit { prefs ->
            prefs[intPreferencesKey("${key.prefix}_x")] = position.x
            prefs[intPreferencesKey("${key.prefix}_y")] = position.y
        }
    }
    // The old global overlay_position_x/y keys are deliberately not migrated across hosts.
}
