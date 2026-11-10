package mx.kompara.ui.stats

import androidx.test.core.app.ApplicationProvider
import mx.kompara.data.settings.PreferredMetric.IPH
import mx.kompara.data.settings.PreferredMetric.IPK
import mx.kompara.data.settings.RateBasis.GROSS
import mx.kompara.data.settings.RateBasis.NET
import mx.kompara.metrics.DriverSlices
import mx.kompara.metrics.HourBlock
import mx.kompara.metrics.SliceTotals
import mx.kompara.ui.fixtures.TuFixture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

/** Comparar · Tú (plan 4.6): every state and sentence, the kit's figures first. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TuPresentationTest {
    private val kit get() = TuFixture(ApplicationProvider.getApplicationContext())
    private fun ready(slices: DriverSlices, unit: mx.kompara.data.settings.PreferredMetric = IPK, basis: mx.kompara.data.settings.RateBasis = NET) =
        TuPresentation.of(slices, unit, basis) as TuPresentation.Ready

    /** [net] per trip group is fare − [cost] × km; [minutes] feed the per-hour rate. */
    private fun group(trips: Int, fare: Double, km: Double, minutes: Double = 0.0, cost: Double? = 2.84) = BigDecimal(fare).let { f ->
        val net = cost?.let { f.subtract(BigDecimal(it).multiply(BigDecimal(km))) }
        SliceTotals(trips, f, net, BigDecimal(km), BigDecimal(minutes), if (minutes > 0) f else BigDecimal.ZERO, if (minutes > 0) net else BigDecimal.ZERO)
    }
    private fun slices(apps: Map<String, SliceTotals>, days: Map<DayOfWeek, SliceTotals> = emptyMap(), hours: Map<HourBlock, SliceTotals> = emptyMap(),
        total: Int = apps.values.sumOf { it.trips }): DriverSlices {
        val all = apps.values.first().copy(trips = total)
        return DriverSlices(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 10, 18), all, apps, days, hours)
    }
    private val twoApps = mapOf("UBER" to group(20, 2000.0, 200.0, 300.0), "DIDI" to group(20, 2400.0, 200.0, 300.0))

    @Test fun `K1 the kit's figures in neto per km`() {
        val k1 = ready(kit.k1())
        assertEquals("Tus viajes · últimas 4 semanas · por km, neto", k1.header)
        assertEquals("DiDi te deja \$1.67 más por km.", k1.apps.finding)
        assertEquals(listOf("DiDi" to "\$10.62", "Uber" to "\$8.95"), k1.apps.bars.map { it.label to it.value })
        assertEquals(listOf(1f, .843f), k1.apps.bars.map { it.fraction!! }.map { Math.round(it * 1000) / 1000f })
        assertTrue(k1.apps.bars.first().best)
        assertEquals("El sábado te deja \$5.05 más que el viernes.", k1.days.finding)
        assertEquals(listOf("L", "M", "M", "J", "V", "S", "D"), k1.days.bars.map { it.label })
        assertEquals(listOf(null, null, null, null, "\$7.61", "\$12.66", null), k1.days.bars.map { it.value })
        assertTrue(k1.days.bars[5].best); assertTrue(k1.days.bars[4].low)
        assertEquals(listOf(75.0f, 75.9f, 75.0f, 74.3f, 60.1f, 100f, 90.9f), k1.days.bars.map { Math.round(it.fraction!! * 1000) / 10f })
        assertEquals("De 18 a 22 h es cuando más te deja.", k1.hours.finding)
        assertEquals(listOf("6–10", "10–14", "14–18", "18–22", "22–6"), k1.hours.bars.map { it.label })
        assertEquals(listOf(null, null, null, "\$11.62", null), k1.hours.bars.map { it.value })
        assertTrue(k1.days.description.startsWith("lunes \$")); assertTrue("sábado \$12.66" in k1.days.description)
        assertEquals("DiDi \$10.62, Uber \$8.95", k1.apps.description)
    }

    @Test fun `K2 under thirty taken trips the screen gathers and counts`() {
        val k2 = TuPresentation.of(kit.k2(), IPK, NET) as TuPresentation.Gathering
        assertEquals("Con 30 viajes empezamos a comparar tus apps, tus días y tus horas. Llevas 6.", k2.line.text)
        assertEquals("6", k2.line.strong); assertEquals(.2f, k2.progress)
        assertTrue(TuPresentation.of(slices(twoApps, total = 29), IPK, NET) is TuPresentation.Gathering)
        assertTrue(TuPresentation.of(slices(twoApps, total = 30), IPK, NET) is TuPresentation.Ready)
    }

    @Test fun `bruto says te paga and rates are the fare over the km`() {
        val k1 = ready(kit.k1(), basis = GROSS)
        assertEquals("Tus viajes · últimas 4 semanas · por km, bruto", k1.header)
        // DiDi 7,942 / 590.1 km = 13.46; Uber 11,508 / 975.6 km = 11.80
        assertEquals("DiDi te paga \$1.66 más por km.", k1.apps.finding)
        assertEquals(listOf("\$13.46", "\$11.80"), k1.apps.bars.map { it.value })
        assertEquals("El sábado te paga \$5.05 más que el viernes.", k1.days.finding)
        assertEquals("De 18 a 22 h es cuando más te paga.", k1.hours.finding)
        val tie = ready(slices(mapOf("UBER" to group(20, 2000.0, 200.0), "DIDI" to group(20, 2000.0, 200.0))), basis = GROSS)
        assertEquals("Uber y DiDi te pagan lo mismo por km.", tie.apps.finding)
    }

    @Test fun `per hour uses whole pesos and says por hora`() {
        val hour = ready(slices(twoApps), unit = IPH)
        assertEquals("Tus viajes · últimas 4 semanas · por hora, neto", hour.header)
        // Uber (2,000 − 568) × 60 / 300 = 286.40; DiDi (2,400 − 568) × 60 / 300 = 366.40
        assertEquals(listOf("\$366", "\$286"), hour.apps.bars.map { it.value })
        assertEquals("DiDi te deja \$80 más por hora.", hour.apps.finding)
        assertEquals("Tus viajes · últimas 4 semanas · por hora, bruto", ready(slices(twoApps), IPH, GROSS).header)
    }

    @Test fun `one app only says so and draws that app`() {
        val one = ready(slices(mapOf("UBER" to group(40, 4000.0, 400.0))))
        assertEquals("Solo tienes viajes de Uber en estas semanas.", one.apps.finding)
        assertEquals(listOf("Uber" to "\$7.16"), one.apps.bars.map { it.label to it.value })
    }

    @Test fun `a group under three trips is a hairline and is never the best or the worst`() {
        val days = mapOf(DayOfWeek.MONDAY to group(10, 1000.0, 100.0), DayOfWeek.TUESDAY to group(10, 900.0, 100.0),
            DayOfWeek.SUNDAY to group(2, 900.0, 30.0)) // the best rate of all, on two trips
        val apps = mapOf("UBER" to group(36, 3000.0, 300.0), "DIDI" to group(2, 500.0, 20.0))
        val p = ready(slices(apps, days))
        assertEquals(listOf(true, false, false, false, false, false, false), p.days.bars.map { it.best })
        assertNull(p.days.bars[6].fraction); assertNull(p.days.bars[6].value)
        assertNull(p.days.bars[2].fraction) // no trips at all on Wednesday
        assertEquals("El lunes te deja \$1.00 más que el martes.", p.days.finding)
        assertEquals("Aún no podemos comparar tus apps.", p.apps.finding)
        assertEquals(listOf("Uber" to "\$7.16", "DiDi" to null), p.apps.bars.map { it.label to it.value })
        assertNull(p.apps.bars[1].fraction)
        assertFalse("domingo" in p.days.description)
    }

    @Test fun `differences are taken from the printed figures`() {
        // 10.625 prints $10.63 and 8.954 prints $8.95: the sentence says $1.68, not the raw 1.671
        val apps = mapOf("UBER" to group(20, 895.4, 100.0, cost = 0.0), "DIDI" to group(20, 1062.5, 100.0, cost = 0.0))
        assertEquals("DiDi te deja \$1.68 más por km.", ready(slices(apps)).apps.finding)
        val tie = mapOf("UBER" to group(20, 800.1, 100.0, cost = 0.0), "DIDI" to group(20, 800.4, 100.0, cost = 0.0))
        assertEquals("Uber y DiDi te dejan lo mismo por km.", ready(slices(tie)).apps.finding)
    }

    @Test fun `too few comparable days or hours say so`() {
        val days = mapOf(DayOfWeek.SATURDAY to group(38, 3800.0, 380.0), DayOfWeek.SUNDAY to group(2, 300.0, 20.0))
        val hours = mapOf(HourBlock.H22_6 to group(40, 4000.0, 400.0))
        val p = ready(slices(twoApps, days, hours))
        assertEquals("Aún no podemos comparar tus días.", p.days.finding)
        assertEquals("Aún no podemos comparar tus horas.", p.hours.finding)
        assertEquals("\$7.16", p.hours.bars.last().value)
        val even = mapOf(DayOfWeek.MONDAY to group(20, 2000.0, 200.0), DayOfWeek.FRIDAY to group(20, 2000.0, 200.0))
        // two days drawn: name them; "todos tus días" only from three
        assertEquals("El lunes y el viernes te dejan lo mismo.", ready(slices(twoApps, even)).days.finding)
        val three = even + (DayOfWeek.SATURDAY to group(20, 2000.0, 200.0))
        assertEquals("Todos tus días te dejan lo mismo.", ready(slices(twoApps, three)).days.finding)
    }

    @Test fun `the two best hour blocks at the same figure are both named`() {
        val hours = mapOf(HourBlock.H6_10 to group(20, 1800.0, 200.0), HourBlock.H14_18 to group(20, 2400.0, 200.0),
            HourBlock.H18_22 to group(20, 2400.0, 200.0))
        assertEquals("De 14 a 18 h y de 18 a 22 h es cuando más te deja.", ready(slices(twoApps, hours = hours)).hours.finding)
    }

    @Test fun `per hour the days sentence says por hora as the apps sentence does`() {
        val days = mapOf(DayOfWeek.FRIDAY to group(20, 2000.0, 200.0, 300.0), DayOfWeek.SATURDAY to group(20, 2400.0, 200.0, 300.0))
        val hour = ready(slices(twoApps, days), unit = IPH)
        assertEquals("DiDi te deja \$80 más por hora.", hour.apps.finding)
        assertEquals("El sábado te deja \$80 más por hora que el viernes.", hour.days.finding)
        val even = mapOf(DayOfWeek.MONDAY to group(20, 2000.0, 200.0, 300.0), DayOfWeek.FRIDAY to group(20, 2000.0, 200.0, 300.0))
        assertEquals("El lunes y el viernes te dejan lo mismo por hora.", ready(slices(twoApps, even), unit = IPH).days.finding)
    }

    @Test fun `the night block reads de 22 a 6 h`() {
        val hours = mapOf(HourBlock.H6_10 to group(20, 1800.0, 200.0), HourBlock.H22_6 to group(20, 2400.0, 200.0))
        val p = ready(slices(twoApps, hours = hours))
        assertEquals("De 22 a 6 h es cuando más te deja.", p.hours.finding)
        assertEquals("22–6", p.hours.bars.last().label)
    }

    @Test fun `without a cost per km neto has no rate but bruto does`() {
        val apps = mapOf("UBER" to group(20, 2000.0, 200.0, cost = null), "DIDI" to group(20, 2400.0, 200.0, cost = null))
        val net = ready(slices(apps))
        assertEquals("Aún no podemos comparar tus apps.", net.apps.finding)
        assertTrue(net.apps.bars.all { it.fraction == null })
        assertEquals("DiDi te paga \$2.00 más por km.", ready(slices(apps), basis = GROSS).apps.finding)
    }
}
