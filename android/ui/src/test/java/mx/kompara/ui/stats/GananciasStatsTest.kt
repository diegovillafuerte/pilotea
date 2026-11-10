package mx.kompara.ui.stats

import androidx.test.core.app.ApplicationProvider
import mx.kompara.data.service.*
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.RateBasis
import mx.kompara.data.settings.Settings
import mx.kompara.ui.components.ReglaBand
import mx.kompara.ui.components.ReglaPosition
import mx.kompara.ui.components.ReglaUnit
import mx.kompara.ui.components.StatusGlyph
import mx.kompara.ui.fixtures.GananciasFixture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Plan 4.5: the status line, which body Hoy shows, the first day (L2) and every reader-off cause (L3). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GananciasStatsTest {
    private val fixture get() = GananciasFixture(ApplicationProvider.getApplicationContext())

    @Test fun `the status line has one sentence per state and opens the Semáforo screen while it is on`() {
        val working = fixture.ganancias("L1")
        assertEquals(GananciasStatus("Semáforo funcionando", null, StatusGlyph.HEALTHY, true), working.status)
        assertEquals(HoyMode.DAY, working.mode)
        assertEquals(GananciasStatus("Semáforo preparado", "permiso listo", StatusGlyph.WAITING, true), fixture.ganancias("L2").status)
        assertEquals(GananciasStatus("Semáforo apagado", "desde ayer 20:31", StatusGlyph.OFF, false), fixture.ganancias("L3").status)
        val f = fixture
        fun with(phase: ReaderPhase, permission: Boolean? = true, incident: ReaderIncident? = null) =
            GananciasStats.of(f.today("L1"), f.readiness("L1").copy(phase = phase, permission = permission, incident = incident), Settings.DEFAULT)
        assertEquals(GananciasStatus("Batería con restricciones · aún funciona", null, StatusGlyph.ATTENTION, true), with(ReaderPhase.ATTENTION).status)
        assertEquals(HoyMode.DAY, with(ReaderPhase.ATTENTION).mode); assertNull(with(ReaderPhase.ATTENTION).off)
        // A stored pause is a stopped reader: off, and the tab already shows how to turn it back on.
        assertEquals(GananciasStatus("Semáforo apagado", null, StatusGlyph.OFF, false), with(ReaderPhase.PAUSED).status)
        assertEquals("falta el permiso", with(ReaderPhase.OFF, permission = false).status!!.detail)
        assertNull(with(ReaderPhase.OFF).status!!.detail)
        val older = ReaderIncident(ReaderIncidentCause.SERVICE_DISCONNECTED, f.at(f.date.minusDays(3), "9:15").toEpochMilli())
        assertEquals("desde el vie 16 oct, 9:15", with(ReaderPhase.OFF, incident = older).status!!.detail)
        assertEquals("desde 13:02", with(ReaderPhase.OFF, incident = older.copy(firstSeenAt = f.at(f.date, "13:02").toEpochMilli())).status!!.detail)
        // Until the reader reports its permission, Hoy waits.
        assertEquals(GananciasUiState(), with(ReaderPhase.PREPARED, permission = null))
    }

    @Test fun `L3 maps every reader-off cause to one layout with 0_2_20's wording`() {
        val off = fixture.ganancias("L3")
        assertEquals(HoyMode.OFF, off.mode)
        assertEquals(ReaderOff("Falta el permiso de Accesibilidad",
            "Android a veces lo apaga, por ejemplo al ahorrar batería. Sin él, Kompara no ve tus ofertas.", "Volver a dar el permiso", OffAction.RECOVERY), off.off)
        assertEquals("Ayer, lunes 19 oct, ganaste $1,240.", off.yesterday!!.text); assertEquals(fixture.date, off.yesterday!!.day)
        assertNull(off.today); assertTrue(off.todayUnread)
        assertEquals("Que no se vuelva a apagar", GananciasStats.SECONDARY)
        val f = fixture
        val first = GananciasStats.of(f.today("L2"), f.readiness("L3").copy(apps = f.readiness("L2").apps), Settings.DEFAULT)
        assertEquals(ReaderOff("Activa el semáforo", "Sin el permiso de Accesibilidad, Kompara no ve tus ofertas.", "Activar el semáforo", OffAction.RECOVERY,
            secondary = false), first.off)
        assertFalse("It was never on, so nothing can keep it from turning off again", first.off!!.secondary)
        val stopped = GananciasStats.of(f.today("L1"), f.readiness("L1").copy(phase = ReaderPhase.OFF,
            incident = ReaderIncident(ReaderIncidentCause.CAPTURE_STOPPED, f.at(f.date, "13:02").toEpochMilli())), Settings.DEFAULT)
        assertEquals(ReaderOff("Dejamos de leer tus ofertas", "Revisa el estado del semáforo para volver a leer tus ofertas.", "Revisar el semáforo", OffAction.SEMAFORO), stopped.off)
        // Read offers today: the cause still leads, with what today has kept.
        assertEquals("Hoy llevas $762.", stopped.today!!.text); assertFalse(stopped.todayUnread)
        assertTrue(off.off!!.secondary); assertTrue(stopped.off!!.secondary)
    }

    @Test fun `a stored pause is a stopped reader with one button that turns it back on and no mañana`() {
        val f = fixture
        val stopped = f.ganancias("stopped")
        assertEquals(HoyMode.OFF, stopped.mode)
        assertEquals(ReaderOff("Detuviste el semáforo", "Enciéndelo cuando quieras; mientras tanto no leemos tus ofertas.",
            "Encender el semáforo", OffAction.RESUME), stopped.off)
        assertFalse("The driver stopped it; Android did not", stopped.off!!.secondary)
        assertEquals(GananciasStatus("Semáforo apagado", null, StatusGlyph.OFF, false), stopped.status)
        assertEquals("Ayer, lunes 19 oct, ganaste $1,240.", stopped.yesterday!!.text)
        // A pause that also lost the permission still offers to turn the reader on first.
        val both = GananciasStats.of(f.today("stopped"), f.readiness("stopped").copy(permission = false), Settings.DEFAULT)
        assertEquals(OffAction.RESUME, both.off!!.action)
        assertFalse(listOf(stopped.off!!.headline, stopped.off!!.body, stopped.off!!.primary, stopped.status!!.title)
            .any { "mañana" in it || "pausa" in it })
    }

    @Test fun `L2 the first day shows the two defaults and the example until an offer has ever been read`() {
        val first = fixture.ganancias("L2")
        assertEquals(HoyMode.FIRST_DAY, first.mode)
        val day = first.firstDay!!
        assertEquals("Tu meta $8.00 · tu mínimo $6.00", day.limits); assertEquals("Por km, neto. Valores iniciales", day.limitsDetail)
        assertEquals("Manejar te cuesta $2.84 por km", day.cost); assertEquals("Costo típico de un sedán", day.costDetail)
        assertEquals(ReglaBand.Rated(ReglaPosition.ABOVE, day.example.let { (it.band as ReglaBand.Rated).rate }), day.example.band)
        assertEquals("$9.49", day.example.rateText); assertEquals("neto", day.example.basisWord); assertEquals(ReglaUnit.KM, day.example.unit)
        val f = fixture
        val chosen = GananciasStats.of(f.today("L2"), f.readiness("L2"), Settings.DEFAULT.copy(threshold = PlatformThreshold(11.0, 300.0, 9.0, 225.0),
            preferredMetric = PreferredMetric.IPH, rateBasis = RateBasis.GROSS)).firstDay!!
        assertEquals("Tu meta $300 · tu mínimo $225", chosen.limits); assertEquals("Por hora, bruto", chosen.limitsDetail)
        assertEquals("$370", chosen.example.rateText); assertEquals("bruto", chosen.example.basisWord)
        assertEquals(ReglaPosition.ABOVE, (chosen.example.band as ReglaBand.Rated).position)
        // One offer ever read ends the first day, even if none was read today.
        val read = GananciasStats.of(f.today("L2"), f.readiness("L2").copy(apps = f.readiness("L1").apps), Settings.DEFAULT)
        assertEquals(HoyMode.DAY, read.mode)
    }

    @Test fun `an empty Hoy with history offers yesterday's line`() {
        val empty = fixture.ganancias("empty")
        assertEquals(HoyMode.DAY, empty.mode)
        assertEquals(GananciasStatus("Semáforo preparado", "permiso listo", StatusGlyph.WAITING, true), empty.status)
        assertEquals("Ayer, lunes 19 oct, ganaste $1,240.", empty.yesterday!!.text)
        assertNull(fixture.ganancias("L1").yesterday)
    }
}
