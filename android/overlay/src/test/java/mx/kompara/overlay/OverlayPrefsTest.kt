package mx.kompara.overlay

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import mx.kompara.parsers.model.HostOrientation

/**
 * Position persistence round-trips through a fake in-memory preferences [DataStore]. No DataStore
 * file IO, no Android — just the encode/decode and the default-when-unset behaviour.
 */
class OverlayPrefsTest {
    private val key = OverlayPositionKey("com.ubercab.driver", HostOrientation.PORTRAIT)

    /** A minimal in-memory [DataStore] over a [MutableStateFlow], enough for read + edit. */
    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
        override val data: Flow<Preferences> get() = state
        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    @Test
    fun `position is null until saved`() = runTest {
        val prefs = OverlayPrefs(FakePreferencesDataStore())
        assertNull(prefs.getPosition(key))
    }

    @Test
    fun `saved position round-trips`() = runTest {
        val prefs = OverlayPrefs(FakePreferencesDataStore())
        prefs.savePosition(key, OverlayPosition(x = 120, y = 340))
        assertEquals(OverlayPosition(120, 340), prefs.getPosition(key))
    }

    @Test
    fun `later save overwrites the earlier one`() = runTest {
        val prefs = OverlayPrefs(FakePreferencesDataStore())
        prefs.savePosition(key, OverlayPosition(10, 20))
        prefs.savePosition(key, OverlayPosition(30, 40))
        assertEquals(OverlayPosition(30, 40), prefs.getPosition(key))
    }
    @Test fun `host and orientation positions remain independent across repository recreation`() = runTest {
        val store = FakePreferencesDataStore()
        val first = OverlayPrefs(store)
        val otherHost = key.copy(host = "com.didiglobal.driver")
        val landscape = key.copy(orientation = HostOrientation.LANDSCAPE)
        first.savePosition(key, OverlayPosition(12, 300))
        first.savePosition(otherHost, OverlayPosition(141, 400))
        first.savePosition(landscape, OverlayPosition(400, 80))
        val reopened = OverlayPrefs(store)
        assertEquals(OverlayPosition(12, 300), reopened.getPosition(key))
        assertEquals(OverlayPosition(141, 400), reopened.getPosition(otherHost))
        assertEquals(OverlayPosition(400, 80), reopened.getPosition(landscape))
    }

    @Test fun `legacy unscoped position is not reused for either app`() = runTest {
        val store = FakePreferencesDataStore()
        store.updateData { mutablePreferencesOf(
            androidx.datastore.preferences.core.intPreferencesKey("overlay_position_x") to 10,
            androidx.datastore.preferences.core.intPreferencesKey("overlay_position_y") to 900) }
        assertNull(OverlayPrefs(store).getPosition(key))
    }
}
