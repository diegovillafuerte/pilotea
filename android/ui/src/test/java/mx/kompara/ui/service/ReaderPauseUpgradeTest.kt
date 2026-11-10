package mx.kompara.ui.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import mx.kompara.data.service.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderPauseUpgradeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-10-19T20:40:00Z"), ZoneId.of("America/Mexico_City"))
    private val store = object : ReaderMemoryStore {
        var value = ReaderMemory()
        override fun load() = value
        override fun save(memory: ReaderMemory) { value = memory }
    }

    @Test fun `a pause stored by 0_2_x is cleared once and a later pause is left alone`() {
        val prefs = context.getSharedPreferences("v4_upgrade_test", Context.MODE_PRIVATE)
        val reader = ReaderReadinessRepository(store, clock)
        reader.pauseUntilTomorrow()
        assertTrue(reader.state.value.paused)
        ReaderPauseUpgrade(prefs, reader).run()
        assertFalse(reader.state.value.paused)
        assertNull(store.value.pauseUntil)
        reader.forgetHistoryAndPause() // Tus datos' local erase still pauses until tomorrow.
        ReaderPauseUpgrade(prefs, ReaderReadinessRepository(store, clock)).run()
        assertTrue(ReaderReadinessRepository(store, clock).state.value.paused)
    }
}
