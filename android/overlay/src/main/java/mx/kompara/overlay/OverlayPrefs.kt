package mx.kompara.overlay

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists overlay-only UI state — currently the chip's last drag position — in the shared
 * preferences [DataStore]. Per-platform acceptance thresholds are *not* stored here; those belong
 * to `:data`'s `SettingsRepository` (the threshold sheet writes through to it) so the engine and
 * the in-app Ajustes screen share one source of truth.
 *
 * The encode/decode is trivial (two ints) so it lives inline; the position is read back as a
 * nullable [OverlayPosition] (null = never dragged → caller uses the default top-right slot).
 */
@Singleton
class OverlayPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val xKey = intPreferencesKey(KEY_POSITION_X)
    private val yKey = intPreferencesKey(KEY_POSITION_Y)

    /** The last persisted chip position, or null if the driver has never moved it. */
    val position: Flow<OverlayPosition?> = dataStore.data.map { prefs ->
        val x = prefs[xKey]
        val y = prefs[yKey]
        if (x != null && y != null) OverlayPosition(x, y) else null
    }

    /** One-shot read of the persisted position (null when unset). */
    suspend fun getPosition(): OverlayPosition? = position.first()

    /** Persist the chip's [position] after a drag settles. */
    suspend fun savePosition(position: OverlayPosition) {
        dataStore.edit { prefs ->
            prefs[xKey] = position.x
            prefs[yKey] = position.y
        }
    }

    companion object {
        const val KEY_POSITION_X = "overlay_position_x"
        const val KEY_POSITION_Y = "overlay_position_y"
    }
}
