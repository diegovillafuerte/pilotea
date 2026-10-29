package mx.kompara.ui.stats

import androidx.test.core.app.ApplicationProvider
import mx.kompara.data.db.dao.SuppressedOffer
import mx.kompara.data.service.*
import mx.kompara.data.settings.Settings
import mx.kompara.ui.fixtures.LimitsFixture
import mx.kompara.ui.components.StatusGlyph
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SemaforoStateTest {
    private val f get() = LimitsFixture(ApplicationProvider.getApplicationContext())
    @Test fun `prepared history pause off and partial support use real per app proof`() {
        assertEquals("Lectura comprobada · 14:32", f.reader("G4").apps[0].detail)
        assertEquals(StatusGlyph.DONE, f.reader("G4").apps[0].glyph)
        assertEquals("Esperando una oferta", f.reader("G4e").apps[0].detail)
        assertEquals("Última lectura ayer 20:10", f.reader("G4f").apps[0].detail)
        assertEquals("Sin ofertas leídas hoy", f.reader("G4d").apps[1].detail)
        assertEquals("Lectura comprobada · 12:10", f.reader("G4d").apps[0].detail)
        assertEquals("Por ahora no la leemos", f.reader("G4g").apps[1].detail)
        assertEquals("Aún no disponible", f.reader("G4g").apps[2].detail)
        assertEquals("Sin permiso, no leemos ofertas", f.reader("G4h").apps[0].detail)
        assertEquals("desde ayer 20:31", f.reader("G4h").since)
    }
    @Test fun `only real dated suppression has a door and paused off hide it`() {
        val fixture = f
        val record = SuppressedOffer(9, fixture.at(fixture.fixture.date, "12:10"), "NO_ROOM_NAVIGATION")
        assertEquals("12:10 · no cupo en la pantalla (navegación)", fixture.reader("G4c").suppressionDetail)
        for (frame in listOf("G4", "G4d", "G4h", "G4e")) assertTrue(fixture.reader(frame).suppressed.isEmpty())
        for (frame in listOf("G4d", "G4h")) assertTrue(semaforoState(fixture.readiness(frame), Settings.DEFAULT, listOf(record), fixture.fixture.zone).suppressed.isEmpty())
        assertTrue(semaforoState(fixture.readiness("G4").copy(date = fixture.fixture.date.plusDays(1)), Settings.DEFAULT, listOf(record), fixture.fixture.zone).suppressed.isEmpty())
    }
    @Test fun `actions reflect permission battery pause or actual fallback never a redundant OCR button`() {
        assertNull(f.reader("G4").primary); assertNull(f.reader("G4e").primary)
        assertNull(f.reader("G4g").primary)
        assertEquals(SemaforoAction.RESUME, f.reader("G4d").primary); assertFalse(f.reader("G4d").canPause)
        assertEquals(SemaforoAction.PERMISSION, f.reader("G4h").primary); assertFalse(f.reader("G4h").canPause)
        assertEquals(SemaforoAction.BATTERY, f.reader("G4b").primary)
        val fixture = f
        assertEquals(SemaforoAction.CAPTURE, semaforoState(fixture.readiness("G4e"), Settings.DEFAULT, emptyList(), fixture.fixture.zone, true).primary)
        assertEquals("$8.00/km · $6.50/km", fixture.reader("G4i").limits)
    }
}
