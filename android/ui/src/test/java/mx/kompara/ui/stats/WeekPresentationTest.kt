package mx.kompara.ui.stats

import androidx.test.core.app.ApplicationProvider
import mx.kompara.data.accounting.*
import mx.kompara.ui.fixtures.WeekFixture
import mx.kompara.ui.format.Formatters
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeekPresentationTest {
    private val fixture get() = WeekFixture(ApplicationProvider.getApplicationContext())
    @Test fun `closed week ledger app rates and days have one basis and truthful rounding`() {
        val week = fixture.week()
        assertEquals("$4,980", week.amount)
        assertEquals(62, week.money.trips)
        assertEquals("$1,166", week.bars[4].display)
        assertEquals("Redondeado una vez · los días suman $4,979", week.roundingNote)
        assertEquals("DiDi te dejó $1.21 más por km", week.finding)
        assertEquals(listOf("$10.34/km", "$9.13/km"), week.apps.map { Formatters.formatPerKm(it.value.ratePerKm!!) })
        val ledger = weekCalculation(week)
        assertEquals(listOf("$8,400", "−$1,920", "−$1,500", "$4,980"), ledger.rows.map { it.value })
        assertEquals("Uber $5,028 · DiDi $3,372", ledger.rows.first().detail)
        assertEquals(listOf("El pago final lo confirma tu resumen de la app. Antes de impuestos."), ledger.notes)
    }
    @Test fun `no trip Sunday is a negative calendar day without a false rounding note`() {
        val week = fixture.week("E1c")
        assertEquals("$4,481", week.amount)
        assertEquals("−$214", week.bars.last().display)
        assertEquals("Sin viajes; lo del coche corre igual", week.dayNote(6))
        assertNull(week.roundingNote)
    }
    @Test fun `open Monday agrees with Hoy including live trip and no future charges`() {
        val week = fixture.week("E8")
        assertEquals("$548", week.amount)
        assertTrue(week.quietLine.contains("6 viajes tomados (1 en curso)"))
        assertEquals("Llevas esta semana · aprox.", week.label)
        assertEquals(1, week.money.carCharges.size)
        assertEquals(6, week.days.count { it == null })
    }
    @Test fun `four calendar weeks keep totals visible and never sell missing breakdowns`() {
        val week = fixture.week("E2")
        assertTrue(week.locked); assertEquals("$4,747", week.amount)
        assertEquals("5 – 11 oct · hace 4 semanas", weekSelectorLabel(week.start, week.today))
        assertFalse(week.copy(premium = true).locked)
        assertTrue(HistoryWeeks.isFree(LocalDate.parse("2026-10-12"), week.today))
        val empty = week.money.copy(contributions = emptyList(), byApp = emptyMap())
        assertFalse(WeekPresentation(week.snapshot.copy(money = empty)).locked)
    }
    @Test fun `statement difference is same app same week payment before costs only`() {
        val week = fixture.week()
        assertEquals("+$34", week.differences.single().display)
        assertEquals("UBER", week.differences.single().app)
        val source = week.snapshot.statements.single()
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
    @Test fun `coverage gaps and missing costs never become rounding notes or optimistic zero`() {
        val week = fixture.week()
        val changed = week.copy(snapshot = week.snapshot.copy(money = week.money.copy(periodResult = week.money.periodResult!! + 34.toBigDecimal())))
        assertNull(changed.roundingNote)
        val unknown = week.copy(snapshot = week.snapshot.copy(money = week.money.copy(periodResult = null)))
        assertNull(unknown.amount)
        assertTrue(unknown.quietLine.contains("faltan datos"))
        assertNull(unknown.roundingNote)
    }

}
