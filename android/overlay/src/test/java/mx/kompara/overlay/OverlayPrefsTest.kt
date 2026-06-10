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

/**
 * Position persistence round-trips through a fake in-memory preferences [DataStore]. No DataStore
 * file IO, no Android — just the encode/decode and the default-when-unset behaviour.
 */
class OverlayPrefsTest {

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
        assertNull(prefs.getPosition())
    }

    @Test
    fun `saved position round-trips`() = runTest {
        val prefs = OverlayPrefs(FakePreferencesDataStore())
        prefs.savePosition(OverlayPosition(x = 120, y = 340))
        assertEquals(OverlayPosition(120, 340), prefs.getPosition())
    }

    @Test
    fun `later save overwrites the earlier one`() = runTest {
        val prefs = OverlayPrefs(FakePreferencesDataStore())
        prefs.savePosition(OverlayPosition(10, 20))
        prefs.savePosition(OverlayPosition(30, 40))
        assertEquals(OverlayPosition(30, 40), prefs.getPosition())
    }
}
