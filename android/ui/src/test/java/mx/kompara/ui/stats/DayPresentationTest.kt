package mx.kompara.ui.stats

import androidx.test.core.app.ApplicationProvider
import mx.kompara.ui.fixtures.DayFixture
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.screens.offerReadingReport
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.db.entity.OfferPresentationEntity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DayPresentationTest {
    private val fixture get() = DayFixture(ApplicationProvider.getApplicationContext())
    private fun DayPresentation.at(time: String) = rows.first { clock(it) == time }.offerId
    private fun visible(text: String) = text.replace('\u00a0', ' ').replace("\u2060", "")

    @Test fun `ten and thirteen complete offers share money and clock with their day ledger`() {
        val early = fixture.day()
        val late = fixture.day(late = true)
        assertEquals(10, early.rows.size); assertEquals(13, late.rows.size)
        assertEquals("$548", early.amount); assertEquals("$1,026", late.amount)
        assertEquals("6 viajes tomados en 4 h 50 min (1 en curso),\nya restamos gasolina, desgaste y coche", visible(early.quietLine))
        assertEquals("9 viajes tomados en 8 h 10 min,\nya restamos gasolina, desgaste y coche", visible(late.quietLine))
        assertEquals(listOf("14:32", "12:10", "11:48", "11:20", "10:41", "10:02", "9:51", "9:24", "8:40", "8:15"), early.rows.map(early::clock))
        assertEquals(listOf("9:51", "8:40"), early.missedAbove.map(early::clock))
        assertEquals("Oferta de las 9:51 · Se fue sin tomar", early.offer(early.at("9:51"))!!.title)
        assertEquals(listOf("$979", "−$217", "−$214", "$548"), dayCalculation(early.money).rows.map { it.value })
        assertEquals(listOf("$1,590", "−$350", "−$214", "$1,026"), dayCalculation(late.money).rows.map { it.value })
        assertEquals(1, dayCalculation(early.money).notes.size)
        assertEquals("1 en curso · antes de gasolina, desgaste y coche", dayCalculation(early.money).rows[0].detail)
    }

    @Test fun `D2e is the last finished trip and D2 changes tense without changing its saved judgement`() {
        val early = fixture.day()
        val finished = early.offer(early.at("11:20"))!!
        assertEquals("$133", finished.amount)
        assertEquals("$9.81/km · arriba de tu meta de $8.00/km", finished.why)
        assertEquals("$308/h · 26 min", finished.timeRate)
        assertEquals("Te dejó · aprox.", finished.label)
        assertEquals("$172", finished.fare.value); assertEquals("−$39", finished.running.value)
        assertEquals("13.6 km (2.4 para recoger) × $2.84", visible(finished.running.detail!!))
        assertEquals("Llevas hoy, con el coche: $548", finished.dayLine)
        assertEquals("Se resta por día ($214), no por viaje", finished.carLine)
        val progress = early.offer(early.at("14:32"))!!
        val late = fixture.day(true).offer(early.at("14:32"))!!
        assertEquals("Oferta de las 14:32 · en curso", progress.title)
        assertEquals("Te deja · aprox.", progress.label)
        assertEquals("$285/h · 24 min", progress.timeRate)
        assertEquals("Te dejó · aprox.", late.label)
        assertEquals(progress.why, late.why)
        assertEquals("Llevas hoy, con el coche: $1,026", late.dayLine)
    }

    @Test fun `D2b D2c D2d each retain own arithmetic outcome and suppression evidence`() {
        val day = fixture.day(true)
        val red = day.offer(day.at("12:10"))!!
        val yellow = day.offer(day.at("14:50"))!!
        assertEquals("$27", red.amount); assertNull(red.cancellableTripId)
        assertEquals("Te habría dejado · aprox.", red.label)
        assertEquals("lo que ofrecía la app", red.fare.detail)
        assertEquals("$54/h · 30 min", red.timeRate)
        assertEquals("$86", yellow.amount); assertNotNull(yellow.cancellableTripId)
        assertEquals("$7.16/km · cumple tu mínimo de $6.00/km", yellow.why)
        assertEquals("$191/h · 27 min", yellow.timeRate)
        val suppressed = fixture.day(suppressed = true)
        val row = suppressed.rows.first { suppressed.clock(it) == "12:10" }
        assertEquals("sin semáforo en pantalla", suppressed.meaning(row))
        assertEquals("$2.20/km", row.rate)
        assertEquals("Sin semáforo: no cupo en la pantalla (navegación)", suppressed.offer(row.offerId)!!.suppression)
        assertEquals("$548", suppressed.amount)
        val unsupported = DayPresentation(suppressed.snapshot.copy(presentations = listOf(OfferPresentationEntity("fixture-12:10", "UNSUPPORTED", row.seenAt, "UNVALIDATED_GEOMETRY"))), suppressed.settings)
        assertNull(unsupported.offer(row.offerId)!!.suppression)
    }

    @Test fun `editing the other limit or selected unit never rewrites an old offer`() {
        val day = fixture.day()
        val changed = DayPresentation(day.snapshot, day.settings.copy(threshold = day.settings.effectiveThreshold.copy(redPerKmMxn = 6.5), preferredMetric = PreferredMetric.IPH))
        assertEquals(day.offer(day.at("11:20"))!!.why, changed.offer(day.at("11:20"))!!.why)
        assertEquals("$2.20/km · abajo de tu mínimo de $6.00/km a esa hora", changed.offer(day.at("12:10"))!!.why)
        assertEquals(day.offer(day.at("12:10"))!!.amount, changed.offer(day.at("12:10"))!!.amount)
        val costChanged = DayPresentation(day.snapshot.copy(activeCost = null, runningCosts = day.snapshot.runningCosts + day.snapshot.runningCosts.single().copy(id = 2,
            inputs = mx.kompara.data.accounting.RunningCostInputs(mx.kompara.data.accounting.RunningCostMode.DIRECT, directPerKm = "99".toBigDecimal()))), day.settings)
        assertEquals(day.offer(day.at("11:20"))!!.running, costChanged.offer(day.at("11:20"))!!.running)
    }

    @Test fun `unknown fields legacy and closed dates do not borrow current money or time`() {
        val day = fixture.day()
        val grey = day.offer(day.at("11:48"))!!
        assertEquals("No pudimos leer la tarifa", grey.why)
        assertNull(grey.amount); assertNull(grey.timeRate); assertEquals("—", grey.fare.value)
        val raw = day.snapshot.offers.first { it.id == day.at("11:20") }
        val legacy = DayPresentation(day.snapshot.copy(offers = listOf(raw.copy(evaluation = null, inputProvenance = "LEGACY_UNSPECIFIED"))), day.settings).offer(raw.id)!!
        assertEquals("No conservamos tus límites de ese día", legacy.why)
        assertNull(legacy.amount); assertNull(legacy.timeRate)
        val past = DayPresentation(day.snapshot.copy(money = day.money.copy(window = day.money.window.copy(asOf = day.money.window.asOf.plusSeconds(86_400)))), day.settings)
        assertEquals("Te quedaron el lunes 19 oct · aprox.", past.label)
        assertEquals("Ese día, lunes 19 oct, te quedaron, con el coche: $548", past.offer(raw.id)!!.dayLine)
        assertFalse(dayCalculation(past.money).title.contains("hoy"))
    }

    @Test fun `only today's no trip day hides accrued car money`() {
        val day = fixture.day()
        val empty = mx.kompara.data.accounting.PeriodAccounting.calculate(day.money.window, emptyList(), fixture.fixture.schedule)
        val state = DayPresentation(day.snapshot.copy(money = empty, offers = emptyList(), trips = emptyList()), day.settings)
        assertNull(state.amount); assertEquals("Aún no registramos ofertas hoy", state.emptyMessage)
        assertEquals("−$214", Formatters.formatMxnWhole(empty.periodResult!!))
        val pastMoney = empty.copy(window = empty.window.copy(asOf = empty.window.asOf.plusSeconds(86_400)))
        val past = state.copy(snapshot = state.snapshot.copy(money = pastMoney))
        assertEquals("−$214", past.amount)
        assertEquals("Te quedaron el lunes 19 oct · aprox.", past.label)
        assertEquals("Sin viajes; lo del coche corre igual", past.quietLine)
        assertEquals(past.amount, dayCalculation(pastMoney).rows.last().value)
        val withOffers = past.copy(snapshot = past.snapshot.copy(offers = day.snapshot.offers.filter { !it.accepted }))
        assertEquals(past.amount, withOffers.amount)
        assertEquals(past.quietLine, withOffers.quietLine)
        val unknown = past.copy(snapshot = past.snapshot.copy(money = pastMoney.copy(periodResult = null)))
        assertNull(unknown.amount)
        val noCost = day.money.copy(carCharges = emptyList(), carCost = java.math.BigDecimal.ZERO)
        assertEquals(3, dayCalculation(noCost).rows.size)
        assertFalse(DayPresentation(day.snapshot.copy(money = noCost), day.settings).quietLine.contains("coche"))
    }

    @Test fun `past no trip zero cost remains visible without borrowing today's amount`() {
        val day = fixture.day()
        val pastWindow = day.money.window.copy(asOf = day.money.window.asOf.plusSeconds(86_400))
        val schedule = fixture.fixture.schedule
        val freeCar = mx.kompara.data.accounting.CostSchedule(schedule.running,
            schedule.car.map { it.copy(weeklyMxn = java.math.BigDecimal.ZERO) })
        val money = mx.kompara.data.accounting.PeriodAccounting.calculate(pastWindow, emptyList(), freeCar)
        val past = day.copy(snapshot = day.snapshot.copy(money = money, offers = emptyList(), trips = emptyList()))
        assertEquals("$0", past.amount)
        assertEquals("Sin viajes; lo del coche corre igual", past.quietLine)
    }

    @Test fun `report contains only explicit derived reading fields not daily earnings or raw capture`() {
        val day = fixture.day()
        val report = offerReadingReport(day.offer(day.at("11:20"))!!, day.date.toString())
        assertTrue(report.contains("2026-10-19")); assertTrue(report.contains("11:20")); assertTrue(report.contains("$133 sin el coche"))
        assertFalse(report.contains("$548")); assertFalse(report.contains("fixture-")); assertFalse(report.contains("$172"))
    }
}
