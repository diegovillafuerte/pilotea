package mx.kompara.ui.stats

import androidx.test.core.app.ApplicationProvider
import mx.kompara.data.accounting.*
import mx.kompara.metrics.CarWeek
import mx.kompara.ui.fixtures.GananciasFixture
import mx.kompara.ui.fixtures.WeekFixture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate

/** Plan 4.5 Semana (L4, L5): the week's figure, seven bars, the car as one weekly bill. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeekPresentationTest {
    private val fixture get() = GananciasFixture(ApplicationProvider.getApplicationContext())
    private fun visible(text: String?) = text?.replace(' ', ' ')

    @Test fun `L4 a closed week its best day and the car covered on Tuesday`() {
        val week = fixture.week("L4")
        assertFalse(week.open)
        assertEquals("12 – 18 oct", week.title); assertEquals("Ganaste", week.label); assertEquals("$6,480", week.amount)
        assertEquals("62 viajes · ya restamos gasolina y desgaste", week.quietLine)
        assertEquals(listOf(58.6f, 68.9f, 65.9f, 75.5f, 100f, 64.6f, 36.1f), week.bars.map { Math.round(it.fraction * 1000) / 10f })
        assertEquals(listOf(4), week.bars.indices.filter { week.bars[it].best })
        assertEquals(listOf(null, null, null, null, "$1,380", null, null), week.bars.map { it.label })
        assertEquals(listOf("L", "M", "M", "J", "V", "S", "D"), week.bars.map { it.letter })
        assertTrue(week.bars.all { it.elapsed })
        val car = week.car!!
        assertTrue(car.covered); assertEquals(23.1f, Math.round(car.hatched * 1000) / 10f)
        assertEquals("El coche, cubierto el martes", car.label); assertEquals("$1,500", car.amount)
        assertEquals("Tuyo", car.restLabel); assertEquals("$4,980", car.rest)
        val calculation = week.calculation
        assertEquals("Cómo se calculó · semana", calculation.title)
        assertEquals(listOf("$8,400", "−$1,920", "$6,480"), calculation.rows.map { it.value })
        assertEquals(listOf("62 viajes", "Gasolina y desgaste", "Ganaste"), calculation.rows.map { it.label })
        assertEquals("675.8 km × $2.84", visible(calculation.rows[1].detail))
    }

    @Test fun `L5 the week under way counts toward the car until it is covered`() {
        val week = fixture.week("L5")
        assertTrue(week.open)
        assertEquals("19 – 25 oct · en curso", week.title); assertEquals("Esta semana llevas", week.label); assertEquals("$762", week.amount)
        assertEquals("6 viajes · ya restamos gasolina y desgaste", week.quietLine)
        assertEquals(listOf(1f, 0f, 0f, 0f, 0f, 0f, 0f), week.bars.map { it.fraction })
        assertEquals("$762", week.bars.first().label); assertEquals(listOf(true, false, false, false, false, false, false), week.bars.map { it.elapsed })
        val car = week.car!!
        assertFalse(car.covered); assertEquals(50.8f, Math.round(car.hatched * 1000) / 10f)
        assertEquals("El coche", car.label); assertEquals("$762 de $1,500", car.amount)
        assertEquals("Te faltan", car.restLabel); assertEquals("$738", car.rest)
        assertEquals("Esta semana llevas", week.calculation.rows.last().label)
    }

    @Test fun `a closed week short of the car says what was missing`() {
        val car = fixture.week("short").car!!
        assertFalse(car.covered)
        assertEquals("$808 de $1,500", car.amount); assertEquals("Te faltaron", car.restLabel); assertEquals("$692", car.rest)
    }

    @Test fun `no car bill draws no car and a slow day is never a negative bar`() {
        val week = fixture.week("L4")
        assertNull(week.copy(snapshot = week.snapshot.copy(car = CarWeek(null, week.snapshot.car.days))).car)
        val days = week.snapshot.car.days.toMutableList().also { it[6] = BigDecimal("-40") }
        val slow = week.copy(snapshot = week.snapshot.copy(car = week.snapshot.car.copy(days = days)))
        assertEquals(0f, slow.bars[6].fraction); assertFalse(slow.bars[6].best)
        assertEquals("−$40", slow.bars[6].value)
    }

    @Test fun `a week without trips says so and opens no calculation`() {
        val week = fixture.week("L5")
        val empty = PeriodAccounting.calculate(week.money.window, emptyList(), fixture.fixture.schedule)
        val state = WeekPresentation(week.snapshot.copy(money = empty, days = List(7) { null }, car = CarWeek(BigDecimal(1500), List(7) { null })))
        assertEquals("$0", state.amount); assertEquals("Todavía no hay viajes esta semana", state.quietLine); assertFalse(state.calculable)
        assertTrue(state.bars.none { it.best || it.label != null })
        assertEquals("$0 de $1,500", state.car!!.amount); assertEquals("$1,500", state.car!!.rest)
        val unknown = week.copy(snapshot = week.snapshot.copy(money = week.money.copy(tripContribution = null)))
        assertNull(unknown.amount); assertEquals("6 viajes\nFaltan datos para calcular lo que llevas", unknown.quietLine)
        // B7: no week figure, no car bar (it would count the day it cannot calculate as $0)
        assertNull(unknown.car)
    }

    @Test fun `a week without its costs says de esos días`() {
        fun PeriodMoney.lost() = copy(contributions = contributions.mapIndexed { i, it -> if (i == 0) it.copy(runningCostVersion = null) else it })
        val week = fixture.week("L4").money.lost()
        assertTrue(periodCalculation(week, "semana", "Ganaste").notes.contains("No conservamos tus costos de esos días"))
        val day = fixture.dayPresentation("L6").money.lost()
        assertTrue(periodCalculation(day, "lunes 19 oct", "Ganaste").notes.contains("No conservamos tus costos de ese día"))
    }

    @Test fun `a closed week without trips says it had none`() {
        val week = fixture.week("L4")
        val empty = PeriodAccounting.calculate(week.money.window, emptyList(), fixture.fixture.schedule)
        val state = WeekPresentation(week.snapshot.copy(money = empty, days = List(7) { null }, car = CarWeek(null, List(7) { null })))
        assertFalse(state.open); assertEquals("Ganaste", state.label)
        assertEquals("No tuviste viajes esa semana.", state.quietLine)
    }

    @Test fun `statement difference is same app same week payment before costs only`() {
        val week = WeekFixture(ApplicationProvider.getApplicationContext()).week()
        val source = week.snapshot.statements.single()
        assertEquals("+$34", statementDifferences(week.money, listOf(source)).single().display)
        listOf(source.copy(paymentBasis = PaymentBasis.UNCONFIRMED), source.copy(app = "DIDI"),
            source.copy(trips = 35), source.copy(coverage = source.coverage.copy(start = source.coverage.start.minusDays(7))),
            source.copy(payment = null), source.copy(source = MoneySource.CAPTURED_ESTIMATE)).forEach {
            assertTrue(statementDifferences(week.money, listOf(it)).isEmpty())
        }
        assertEquals("−$28", statementDifferences(week.money, listOf(source.copy(payment = "5000".toBigDecimal()))).single().display)
        assertEquals("$0", statementDifferences(week.money, listOf(source.copy(payment = "5028".toBigDecimal()))).single().display)
    }

    @Test fun `selected period and Spanish labels normalize Mondays clamp future and retain years`() {
        val today = LocalDate.parse("2026-10-19")
        assertEquals(today, SelectedPeriod.resolve(null, today))
        assertEquals(today, SelectedPeriod.resolve("2026-11-05", today))
        assertEquals(LocalDate.parse("2026-10-12"), SelectedPeriod.resolve("2026-10-15", today))
        assertEquals(today, SelectedPeriod.resolve("bad", today))
        assertEquals("28 sep – 4 oct", weekRange(LocalDate.parse("2026-09-28"), today))
        assertEquals("29 dic 2025 – 4 ene 2026", weekRange(LocalDate.parse("2025-12-29"), today))
    }
}
