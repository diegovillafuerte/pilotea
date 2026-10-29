package mx.kompara.ui.stats

import androidx.test.core.app.ApplicationProvider
import mx.kompara.data.service.ReaderPhase
import mx.kompara.data.settings.Settings
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.ui.fixtures.HoyFixture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InicioStatsTest {
    private val fixture get() = HoyFixture(ApplicationProvider.getApplicationContext())

    @Test fun `unknown permission is loading rather than permission ready`() {
        val state = fixture.state("B3")
        assertTrue(InicioStats.today(state.snapshot!!, state.reader!!.copy(permission = null), Settings.DEFAULT).loading)
    }
    @Test fun `teaching offer changes verdict with the saved limits and chosen unit`() {
        val state = fixture.state("B3")
        val yellow = InicioStats.today(state.snapshot!!, state.reader!!,
            Settings.DEFAULT.copy(threshold = PlatformThreshold.DEFAULT.copy(minPerKmMxn = 9.50)))
        assertEquals(mx.kompara.ui.components.ReglaPosition.MEETS, (yellow.example!!.band as mx.kompara.ui.components.ReglaBand.Rated).position)
        assertEquals("$9.49", yellow.example!!.rateText)
        val hourly = InicioStats.today(state.snapshot!!, state.reader!!,
            Settings.DEFAULT.copy(preferredMetric = mx.kompara.data.settings.PreferredMetric.IPH))
        assertEquals(mx.kompara.ui.components.ReglaUnit.HOUR, hourly.example!!.unit)
        assertEquals("$285", hourly.example!!.rateText)
    }
    @Test fun `today contains six trips and one in progress not this week's money`() {
        val state = fixture.state("B1")
        assertEquals("$548", state.amount)
        assertEquals("6 viajes tomados (1 en curso),\nya restamos gasolina, desgaste y coche", state.quietLine)
        assertEquals(listOf("En curso", "Se fue sin tomar", "Sin lectura"), state.offers.map { it.outcome })
        assertEquals(listOf("$9.49/km", "$2.20/km", null), state.offers.map { it.rate })
        assertTrue(state.offers[0].detail.startsWith("14:32"))
        assertNull(state.yesterday)
    }
    @Test fun `pause partial support and one trip use same accounting`() {
        assertEquals("$434", fixture.state("B5").amount)
        assertTrue(fixture.state("B5").quietLine.replace('\u00a0', ' ').contains("4 h 30 min"))
        val partial = fixture.state("B6")
        assertEquals("$166", partial.amount)
        assertEquals("Llevas hoy sin contar DiDi · aprox.", partial.label)
        assertEquals(listOf("10:05", "9:12", "8:36"), partial.offers.map { it.detail.substringBefore(" · ") })
        val negative = fixture.state("B8")
        assertEquals("−$100", negative.amount)
        assertTrue(negative.quietLine.contains("1 viaje tomado (en curso)"))
        assertTrue(negative.quietLine.contains("($214 por día)"))
    }
    @Test fun `no trips never displays accrued car or zero as a hero`() {
        for (id in listOf("B2", "B2r", "B3", "B3n", "B4")) assertNull(id, fixture.state(id).amount)
        assertEquals(HoyMode.FIRST_OFF, fixture.state("B3n").mode)
        assertEquals(HoyMode.FIRST_PREPARED, fixture.state("B3").mode)
        assertFalse(fixture.state("B3").snapshot!!.hasHistory)
    }
    @Test fun `yesterday date and amount stay paired in both morning worlds`() {
        assertEquals("Ayer, lunes 19 oct, te quedaron $1,026 aprox.", fixture.state("B2").yesterday)
        assertEquals("Ayer, domingo 18 oct, te quedaron $284 aprox.", fixture.state("B4").yesterday)
        assertEquals(fixture.state("B2").yesterday, fixture.state("B2r").yesterday)
    }
    @Test fun `permission recovery keeps history without claiming functioning`() {
        val off = fixture.state("B2")
        assertEquals("· desde ayer 20:31", off.statusDetail)
        val restored = fixture.state("B2r")
        assertEquals(HoyMode.PREPARED, restored.mode)
        assertEquals(ReaderPhase.PREPARED, restored.reader!!.phase)
        assertEquals("permiso listo", restored.statusDetail)
        assertTrue(restored.offers.isEmpty())
    }
    @Test fun `no room is positive presentation evidence never unread fare`() {
        val normal = fixture.state("B1")
        assertFalse(normal.statusDetail.contains("no cupo"))
        val suppressed = fixture.state("B9")
        assertEquals("no cupo el semáforo en 1 oferta hoy", suppressed.statusDetail)
        assertEquals("12:10 · sin semáforo en pantalla", suppressed.offers[1].detail)
        assertEquals("11:48 · No pudimos leer la tarifa", suppressed.offers[2].detail)
        assertEquals(normal.amount, suppressed.amount)
    }
    @Test fun `battery attention and night never change money`() {
        assertEquals(fixture.state("B1").amount, fixture.state("B7").amount)
        assertEquals(ReaderPhase.ATTENTION, fixture.state("B7").reader!!.phase)
        assertEquals(fixture.state("B1"), fixture.state("N1"))
        assertEquals(fixture.state("B2"), fixture.state("N2"))
    }
    @Test fun `changing limits updates status with cents without recoloring history`() {
        val before = fixture.state("B1")
        val after = InicioStats.today(before.snapshot!!, before.reader!!,
            Settings.DEFAULT.copy(threshold = PlatformThreshold.DEFAULT.copy(redPerKmMxn = 6.50)))
        assertEquals("tu meta $8.00 · tu mínimo $6.50", after.statusDetail)
        assertEquals(before.offers, after.offers)
        assertEquals(before.amount, after.amount)
    }
    @Test fun `typical cost confirmation is independent of first day empty state`() {
        assertNotNull(fixture.state("B3").typicalCost)
        assertEquals(HoyMode.COSTS, fixture.state("B4").mode)
        assertEquals("última lectura ayer 20:10", fixture.state("B4").statusDetail)
        assertNull(fixture.state("B1").typicalCost)
    }
    @Test fun `unknown amount remains absent even with a taken trip`() {
        val original = fixture.state("B1")
        val snapshot = original.snapshot!!
        val state = InicioStats.today(snapshot.copy(today = snapshot.today.copy(money = snapshot.today.money.copy(periodResult = null))), original.reader!!, Settings.DEFAULT)
        assertEquals(HoyMode.MONEY, state.mode)
        assertNull(state.amount)
    }
}
