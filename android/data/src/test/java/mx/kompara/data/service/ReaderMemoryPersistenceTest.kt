package mx.kompara.data.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderMemoryPersistenceTest {
    @Test fun `preferences reopen retains incident pause proof and dated positive count`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("readiness-test", Context.MODE_PRIVATE)
        val date = LocalDate.of(2026, 10, 20)
        val memory = ReaderMemory(true, date, mapOf("uber" to AppReadProof(123, "old-binding", "offer", 3)),
            ReaderIncident(ReaderIncidentCause.PERMISSION_LOST, 456, true), date.minusDays(1), 1)
        PreferencesReaderMemoryStore(prefs).save(memory)
        assertEquals(memory, PreferencesReaderMemoryStore(prefs).load())
        val repo = ReaderReadinessRepository(PreferencesReaderMemoryStore(prefs),
            Clock.fixed(Instant.parse("2026-10-20T14:00:00Z"), ZoneId.of("America/Mexico_City")))
        assertFalse(repo.state.value.bound)
        assertFalse(repo.state.value.paused)
        assertEquals(0, repo.state.value.noRoomCount)
        assertEquals(456L, repo.state.value.incident?.firstSeenAt)
    }
    @Test fun `clearing optional fields really removes them from disk`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PreferencesReaderMemoryStore(context.getSharedPreferences("readiness-clear", Context.MODE_PRIVATE))
        store.save(ReaderMemory(pauseUntil = LocalDate.now(), incident = ReaderIncident(ReaderIncidentCause.CAPTURE_STOPPED, 1)))
        store.save(ReaderMemory())
        assertEquals(ReaderMemory(), store.load())
    }
}
