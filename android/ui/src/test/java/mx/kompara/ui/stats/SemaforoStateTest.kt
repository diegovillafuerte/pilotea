package mx.kompara.ui.stats

import androidx.test.core.app.ApplicationProvider
import mx.kompara.data.service.*
import mx.kompara.ui.fixtures.SettingsFixture
import mx.kompara.ui.components.StatusGlyph
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SemaforoStateTest {
    private val f get() = SettingsFixture(ApplicationProvider.getApplicationContext())
    private fun state(reader: ReaderReadiness, capture: Boolean = false) = semaforoState(reader, f.zone, capture)
    private fun phase(phase: ReaderPhase, reader: ReaderReadiness = f.readiness()) = reader.copy(phase = phase)

    @Test fun `working shows each app's last read offer and inDrive as not available`() {
        val working = state(f.readiness())
        assertEquals("Semáforo funcionando", working.title)
        assertEquals(listOf("Uber", "DiDi", "inDrive"), working.apps.map { it.name })
        assertEquals(listOf("Leyó tu última oferta · 14:32", "Leyó tu última oferta · 12:25", "Aún no disponible"), working.apps.map { it.detail })
        assertEquals(listOf(StatusGlyph.DONE, StatusGlyph.DONE, StatusGlyph.OFF), working.apps.map { it.glyph })
        assertNull(working.primary); assertNull(working.detail)
    }
    @Test fun `prepared history and paused keep 0_2_20 proof wording`() {
        val prepared = state(f.readiness("prepared"))
        assertEquals("Esperando una oferta", prepared.apps[0].detail)
        assertEquals("Tu primera oferta lo confirma", prepared.detail)
        val yesterday = f.readiness("prepared").let { r -> r.copy(apps = r.apps + (ReaderReadinessReducer.UBER to AppReadiness(true, f.at(f.date.minusDays(1), "20:10"), false))) }
        assertEquals("Última lectura ayer 20:10", state(yesterday).apps[0].detail)
        assertEquals("Tu primera oferta de hoy lo confirma", state(yesterday).detail)
        // A pause can only come from the capture fallback's stop now; it has no button here.
        val paused = state(phase(ReaderPhase.PAUSED))
        assertNull(paused.primary)
        assertEquals("Leyó tu última oferta · 14:32", paused.apps[0].detail)
    }
    @Test fun `off states carry the cause and one button, the same as Ganancias`() {
        val off = state(f.readiness("off"))
        assertEquals("Semáforo apagado", off.title)
        assertEquals("desde ayer 20:31", off.since)
        assertEquals("Falta el permiso de Accesibilidad", off.detail)
        assertEquals("Android a veces lo apaga, por ejemplo al ahorrar batería. Sin él, Kompara no ve tus ofertas.", off.consequence)
        assertEquals(SemaforoAction.PERMISSION, off.primary)
        assertEquals("Volver a dar el permiso", off.primaryLabel)
        assertEquals("Sin permiso, no leemos ofertas", off.apps[0].detail)
        val disconnected = f.readiness("off").copy(permission = true)
        assertEquals(SemaforoAction.SETTINGS, state(disconnected).primary)
        assertEquals("Dejamos de leer tus ofertas", state(disconnected).detail)
        assertEquals("Sin lectura de ofertas", state(disconnected).apps[0].detail)
        assertEquals(SemaforoAction.BATTERY, state(phase(ReaderPhase.ATTENTION, f.readiness().copy(batteryRestricted = true))).primary)
        assertEquals(SemaforoAction.CAPTURE, state(f.readiness("prepared"), capture = true).primary)
    }
}
