package mx.kompara.ui.stats

import androidx.test.core.app.ApplicationProvider
import mx.kompara.data.service.*
import mx.kompara.ui.fixtures.SettingsFixture
import mx.kompara.ui.components.StatusGlyph
import org.junit.Assert.*
import mx.kompara.ui.format.Formatters
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
    @Test fun `prepared and history keep 0_2_20 proof wording`() {
        val prepared = state(f.readiness("prepared"))
        assertEquals("Esperando una oferta", prepared.apps[0].detail)
        assertEquals("Tu primera oferta lo confirma", prepared.detail)
        val yesterday = f.readiness("prepared").let { r -> r.copy(apps = r.apps + (ReaderReadinessReducer.UBER to AppReadiness(true, f.at(f.date.minusDays(1), "20:10"), false))) }
        assertEquals("Última lectura ayer 20:10", state(yesterday).apps[0].detail)
        assertEquals("Tu primera oferta de hoy lo confirma", state(yesterday).detail)
    }
    @Test fun `a stored pause is a stopped reader with one button that turns it back on and no mañana`() {
        // A pause can only come from the screen-capture fallback's own stop (below Android 11).
        val stopped = state(f.readiness("stopped"))
        assertEquals("Semáforo apagado", stopped.title)
        assertEquals(StatusGlyph.OFF, stopped.glyph)
        assertEquals("Detuviste el semáforo", stopped.detail)
        assertEquals("Enciéndelo cuando quieras; mientras tanto no leemos tus ofertas.", stopped.consequence)
        assertEquals(SemaforoAction.RESUME, stopped.primary)
        assertEquals("Encender el semáforo", stopped.primaryLabel)
        assertNull(stopped.since)
        assertEquals(listOf("Sin lectura de ofertas", "Sin lectura de ofertas", "Aún no disponible"), stopped.apps.map { it.detail })
        val words = listOfNotNull(stopped.title, stopped.detail, stopped.consequence, stopped.primaryLabel) + stopped.apps.map { it.detail }
        assertFalse(words.toString(), words.any { "mañana" in it || "pausa" in it })
        assertEquals("Apagado · lo detuviste", settingsReaderText(f.readiness("stopped")))
    }
    @Test fun `a phone that can pause the reader says so and offers the battery step without calling it off`() {
        val attention = state(f.readiness("attention"))
        assertEquals("Batería con restricciones · aún funciona", attention.title)
        assertEquals(StatusGlyph.ATTENTION, attention.glyph)
        assertEquals("Tu teléfono puede apagar el semáforo", attention.detail)
        assertEquals(SemaforoAction.BATTERY, attention.primary); assertEquals("Quitar restricción de batería", attention.primaryLabel)
        assertEquals(listOf("Leyó tu última oferta · 14:32", "Leyó tu última oferta · 12:25", "Aún no disponible"), attention.apps.map { it.detail })
        assertNull(attention.consequence); assertNull(attention.since)
        // Before the first offer it is prepared, and the battery button still has its cause.
        val prepared = state(f.readiness("prepared").copy(batteryRestricted = true))
        assertEquals("Semáforo preparado", prepared.title)
        assertEquals("Tu teléfono puede apagar el semáforo", prepared.detail)
        assertEquals(SemaforoAction.BATTERY, prepared.primary)
        assertEquals("Esperando una oferta", prepared.apps[0].detail)
        val words = listOfNotNull(attention.title, attention.detail, prepared.title, prepared.detail) + (attention.apps + prepared.apps).map { it.detail }
        assertFalse(words.toString(), words.any { "apagado" in it || "Sin lectura" in it })
        assertEquals("Batería con restricciones · aún funciona", settingsReaderText(f.readiness("attention")))
    }
    @Test fun `off states carry the cause and one button, the same as Ganancias`() {
        val off = state(f.readiness("off"))
        assertEquals("Semáforo apagado", off.title)
        assertEquals("desde ayer 20:31", off.since)
        // One date, one format: Ganancias says the same for an older incident.
        val incident = f.readiness("off").incident!!
        val older = incident.copy(firstSeenAt = incident.firstSeenAt - 2 * 86_400_000L)
        assertEquals(Formatters.formatSince(older.firstSeenAt, f.readiness("off").date, f.zone), state(f.readiness("off").copy(incident = older)).since)
        assertTrue(state(f.readiness("off").copy(incident = older)).since!!.matches(Regex("desde el \\S+ \\d+ \\S+, \\d+:\\d\\d")))
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
