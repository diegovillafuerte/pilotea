package mx.kompara.ui.stats

import androidx.test.core.app.ApplicationProvider
import mx.kompara.data.accounting.PeriodAccounting
import mx.kompara.data.accounting.RunningCostMode
import mx.kompara.data.db.entity.OfferPresentationEntity
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.RateBasis
import mx.kompara.ui.fixtures.DayFixture
import mx.kompara.ui.fixtures.GananciasFixture
import mx.kompara.ui.screens.offerReadingReport
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Plan 4.5 Hoy, a past day, the offer sheet (L7) and Cómo se calculó (L8), on the kit's figures. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DayPresentationTest {
    private val fixture get() = GananciasFixture(ApplicationProvider.getApplicationContext())
    private val v3 get() = DayFixture(ApplicationProvider.getApplicationContext())
    private fun DayPresentation.at(time: String) = rows.first { clock(it) == time }.offerId
    private fun DayPresentation.row(time: String) = rows.first { clock(it) == time }
    private fun visible(text: String?) = text?.replace(' ', ' ')?.replace("⁠", "")

    @Test fun `L1 Hoy names each offer's app and outcome with the rate in the driver's unit and basis`() {
        val day = fixture.dayPresentation("L1")
        assertEquals("Hoy llevas", day.label); assertEquals("$762", day.amount)
        assertEquals("6 viajes · 4 h 50 min\nYa restamos gasolina y desgaste", visible(day.quietLine))
        assertTrue(day.calculable)
        assertEquals("Ofertas de hoy", day.section); assertEquals(11, day.rows.size); assertEquals("por km, neto", day.column)
        assertEquals(listOf("14:32", "12:25", "12:10", "11:48", "11:20", "10:41", "10:02", "9:51", "9:24", "8:40", "8:15"), day.rows.map(day::clock))
        assertEquals(listOf("Uber", "DiDi", "Uber", "Uber"), day.rows.take(4).map { appName(it.platform) })
        assertEquals(listOf("14:32 · En curso", "12:25 · Se fue sin tomar", "12:10 · Se fue sin tomar", "11:48 · Sin lectura", "11:20 · La tomaste"),
            day.rows.take(5).map(day::detail))
        assertEquals(listOf("$9.49/km", "$6.39/km", "$2.20/km", null, "$9.81/km", "$9.41/km"), day.rows.take(6).map(day::rate))
        // Offers not taken are dimmed; the unread one too.
        assertEquals(listOf(false, true, true, true, false, false), day.rows.take(6).map(day::dim))
    }

    @Test fun `every rate follows the current unit and basis and says the word`() {
        val day = fixture.dayPresentation("L1")
        val gross = DayPresentation(day.snapshot, day.settings.copy(rateBasis = RateBasis.GROSS))
        assertEquals("por km, bruto", gross.column); assertEquals("$12.33/km", gross.rate(gross.row("14:32")))
        val hour = DayPresentation(day.snapshot, day.settings.copy(preferredMetric = PreferredMetric.IPH))
        assertEquals("por hora, neto", hour.column); assertEquals("$285/h", hour.rate(hour.row("14:32"))); assertNull(hour.rate(hour.row("11:48")))
        val grossHour = DayPresentation(day.snapshot, day.settings.copy(preferredMetric = PreferredMetric.IPH, rateBasis = RateBasis.GROSS))
        assertEquals("por hora, bruto", grossHour.column); assertEquals("$370/h", grossHour.rate(grossHour.row("14:32")))
        // Peso totals never change with the choice.
        assertEquals(day.amount, gross.amount); assertEquals(day.amount, grossHour.amount)
    }

    @Test fun `no figure subtracts the car`() {
        val day = fixture.dayPresentation("L1")
        assertTrue(day.money.carConfigured)
        assertEquals("$548", mx.kompara.ui.format.Formatters.formatMxnWhole(day.money.periodResult!!))
        assertEquals("$762", day.amount)
        assertEquals("$762", day.calculation.rows.last().value)
    }

    @Test fun `L6 a past day is Hoy's layout with the date and Ganaste`() {
        val day = fixture.dayPresentation("L6")
        assertFalse(day.isToday)
        assertEquals("Lunes 19 oct", day.title); assertEquals("Ganaste", day.label); assertEquals("$1,240", day.amount)
        assertEquals("9 viajes · 8 h 10 min\nYa restamos gasolina y desgaste", visible(day.quietLine))
        assertEquals("Ofertas del día", day.section); assertEquals(14, day.rows.size)
        assertEquals(listOf("$11.40/km", "$11.04/km", "$7.16/km", "$9.49/km", "$6.39/km", "$2.20/km", null), day.rows.take(7).map(day::rate))
        assertEquals("DiDi", appName(day.row("12:25").platform)); assertEquals("12:25 · Se fue sin tomar", day.detail(day.row("12:25")))
        val calculation = day.calculation
        assertEquals("Cómo se calculó · lun 19 oct", calculation.title)
        assertEquals(listOf("$1,590", "−$350", "$1,240"), calculation.rows.map { it.value })
        assertEquals("Ganaste", calculation.rows.last().label)
    }

    @Test fun `L8 two lines a total and the one sentence about estimates`() {
        val calculation = fixture.dayPresentation("L1").calculation
        assertEquals("Cómo se calculó · hoy", calculation.title)
        assertEquals(listOf("6 viajes", "Gasolina y desgaste", "Hoy llevas"), calculation.rows.map { it.label })
        assertEquals(listOf("$979", "−$217", "$762"), calculation.rows.map { it.value })
        assertEquals(listOf("Las tarifas de tus ofertas", "76.3 km × $2.84", null), calculation.rows.map { visible(it.detail) })
        assertEquals(listOf("Es un estimado. El pago final lo confirma tu app de viajes."), calculation.notes)
    }

    @Test fun `a period spanning two costs shows its km without the multiplier`() {
        val money = fixture.dayPresentation("L1").money
        val other = money.contributions.first().runningCostVersion!!.copy(id = 9)
        val mixed = money.copy(contributions = money.contributions.mapIndexed { i, it -> if (i == 0) it.copy(runningCostVersion = other) else it })
        assertEquals("76.3 km", visible(periodCalculation(mixed, "hoy", "Hoy llevas").rows[1].detail))
        val electric = money.copy(contributions = money.contributions.map { it.copy(runningCostVersion = it.runningCostVersion!!.copy(
            inputs = it.runningCostVersion!!.inputs.copy(mode = RunningCostMode.ELECTRIC))) })
        assertEquals("Energía y desgaste", periodCalculation(electric, "hoy", "Hoy llevas").rows[1].label)
        val missing = money.copy(contributions = money.contributions.mapIndexed { i, it -> if (i == 0) it.copy(record = it.record.copy(km = null)) else it })
        assertEquals("Faltan kilómetros", periodCalculation(missing, "hoy", "Hoy llevas").rows[1].detail)
    }

    @Test fun `L7 one offer in the basis and unit it was judged in`() {
        val day = fixture.dayPresentation("L1")
        val offer = day.offer(day.at("14:32"))!!
        assertEquals("14:32 · Uber · en curso", offer.title)
        assertEquals("Te deja", offer.label); assertEquals("$114", offer.amount)
        assertEquals("$9.49 por km neto", offer.rate); assertEquals("$9.49 por km neto · arriba de tu meta de $8.00", offer.why)
        assertEquals("Tarifa", offer.fare.label); assertEquals("$148", offer.fare.value); assertNull(offer.fare.detail)
        assertEquals("Gasolina y desgaste", offer.running.label); assertEquals("−$34", offer.running.value)
        assertEquals("12.0 km × $2.84", visible(offer.running.detail))
        assertEquals(listOf("Por hora, neto", "$285", "24 min"), offer.other!!.let { listOf(it.label, it.value, it.detail) })
        assertNotNull(offer.cancellableTripId)
        val finished = day.offer(day.at("11:20"))!!
        assertEquals("Te dejó", finished.label); assertEquals("$133", finished.amount)
        assertEquals("$9.81 por km neto · arriba de tu meta de $8.00", finished.why)
        assertEquals(listOf("Por hora, neto", "$308", "26 min"), finished.other!!.let { listOf(it.label, it.value, it.detail) })
        val didi = day.offer(day.at("12:25"))!!
        assertEquals("12:25 · DiDi · se fue sin tomar", didi.title); assertEquals("Te habría dejado", didi.label)
        assertEquals("$6.39 por km neto · entre tu mínimo de $6.00 y tu meta de $8.00", didi.why)
        assertNull(didi.other); assertNull(didi.cancellableTripId)
        val late = fixture.dayPresentation("L6")
        assertEquals("$2.20 por km neto · abajo de tu mínimo de $6.00", late.offer(late.at("12:10"))!!.why)
        assertEquals("$7.16 por km neto · entre tu mínimo de $6.00 y tu meta de $8.00", late.offer(late.at("14:50"))!!.why)
        // The sheet keeps the row's own judgement when the driver later shows bruto.
        val gross = DayPresentation(day.snapshot, day.settings.copy(rateBasis = RateBasis.GROSS))
        assertEquals(offer.why, gross.offer(day.at("14:32"))!!.why)
    }

    @Test fun `a row judged in bruto or per hour explains itself in that basis and unit`() {
        val day = fixture.dayPresentation("L1")
        val raw = day.snapshot.offers.first { it.id == day.at("14:32") }
        fun judged(evaluation: mx.kompara.data.db.entity.OfferEvaluation) =
            DayPresentation(day.snapshot.copy(offers = day.snapshot.offers.map { if (it.id == raw.id) it.copy(evaluation = evaluation) else it }), day.settings).offer(raw.id)!!
        val gross = judged(raw.evaluation!!.copy(rateBasis = RateBasis.GROSS.name, goal = "11", minimum = "9"))
        assertEquals("$12.33 por km bruto · arriba de tu meta de $11.00 a esa hora", gross.why)
        assertEquals(listOf("Por hora, bruto", "$370"), gross.other!!.let { listOf(it.label, it.value) })
        val hourly = judged(raw.evaluation!!.copy(unit = "IPH", goal = "200", minimum = "150"))
        assertEquals("$285 por hora neto · arriba de tu meta de $200 a esa hora", hourly.why)
        assertEquals(listOf("Por km, neto", "$9.49", "12.0 km"), hourly.other!!.let { listOf(it.label, it.value, visible(it.detail)) })
    }

    @Test fun `editing a limit later marks only the named one and never rewrites the offer`() {
        val day = fixture.dayPresentation("L6")
        val changed = DayPresentation(day.snapshot, day.settings.copy(threshold = day.settings.effectiveThreshold.copy(redPerKmMxn = 6.5)))
        assertEquals(day.offer(day.at("11:20"))!!.why, changed.offer(day.at("11:20"))!!.why)
        assertEquals("$2.20 por km neto · abajo de tu mínimo de $6.00 a esa hora", changed.offer(day.at("12:10"))!!.why)
        assertEquals(day.offer(day.at("12:10"))!!.amount, changed.offer(day.at("12:10"))!!.amount)
    }

    @Test fun `unread legacy and suppressed offers keep 0_2_20's wording without borrowing money`() {
        val day = fixture.dayPresentation("L1")
        val unread = day.offer(day.at("11:48"))!!
        assertEquals("11:48 · Uber · sin lectura", unread.title)
        assertEquals("No pudimos leer la tarifa", unread.why); assertNull(unread.rate)
        assertNull(unread.amount); assertNull(unread.other); assertEquals("—", unread.fare.value)
        val raw = day.snapshot.offers.first { it.id == day.at("11:20") }
        val legacy = DayPresentation(day.snapshot.copy(offers = listOf(raw.copy(evaluation = null, inputProvenance = "LEGACY_UNSPECIFIED"))), day.settings).offer(raw.id)!!
        assertEquals("No conservamos tus límites de ese día", legacy.why)
        assertNull(legacy.amount); assertNull(legacy.other); assertEquals("—", legacy.fare.value)
        val suppressed = v3.day(suppressed = true)
        val row = suppressed.rows.first { suppressed.clock(it) == "12:10" }
        assertEquals("Sin semáforo: no cupo en la pantalla (navegación)", suppressed.offer(row.offerId)!!.suppression)
        val unsupported = DayPresentation(suppressed.snapshot.copy(presentations = listOf(OfferPresentationEntity("fixture-12:10", "UNSUPPORTED", row.seenAt, "UNVALIDATED_GEOMETRY"))), suppressed.settings)
        assertNull(unsupported.offer(row.offerId)!!.suppression)
    }

    @Test fun `days without trips say so and open no calculation`() {
        val morning = fixture.dayPresentation("empty")
        assertEquals("Hoy llevas", morning.label); assertEquals("$0", morning.amount)
        assertEquals("Aún no hay viajes hoy", morning.quietLine); assertFalse(morning.calculable); assertTrue(morning.rows.isEmpty())
        val day = fixture.dayPresentation("L6")
        val empty = PeriodAccounting.calculate(day.money.window, emptyList(), fixture.fixture.schedule)
        val past = DayPresentation(day.snapshot.copy(money = empty, offers = emptyList(), trips = emptyList()), day.settings)
        assertEquals("Ganaste", past.label); assertEquals("$0", past.amount); assertEquals("No registramos ofertas este día", past.quietLine)
        val offersOnly = DayPresentation(day.snapshot.copy(money = empty, trips = emptyList(), offers = day.snapshot.offers.filter { !it.accepted }), day.settings)
        assertEquals("No tomaste ofertas este día", offersOnly.quietLine)
        val unknown = DayPresentation(day.snapshot.copy(money = day.money.copy(tripContribution = null)), day.settings)
        assertNull(unknown.amount); assertEquals("9 viajes\nFaltan datos para calcular lo que ganaste", unknown.quietLine)
    }

    @Test fun `a report carries only the offer's derived reading`() {
        val day = fixture.dayPresentation("L1")
        val report = offerReadingReport(day.offer(day.at("11:20"))!!, day.date.toString())
        assertTrue(report.contains("2026-10-19")); assertTrue(report.contains("11:20")); assertTrue(report.contains("Te dejó $133"))
        assertTrue(report.contains("Por hora, neto: $308"))
        assertFalse(report.contains("$762")); assertFalse(report.contains("fixture-")); assertFalse(report.contains("$172"))
    }
}
