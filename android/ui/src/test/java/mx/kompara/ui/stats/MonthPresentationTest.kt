package mx.kompara.ui.stats

import androidx.test.core.app.ApplicationProvider
import mx.kompara.data.accounting.*
import mx.kompara.metrics.monthMoneySnapshot
import mx.kompara.ui.fixtures.MonthFixture
import mx.kompara.ui.fiscal.FiscalReportComposer
import mx.kompara.ui.format.Formatters
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MonthPresentationTest {
    private val fixture get() = MonthFixture(ApplicationProvider.getApplicationContext())
    @Test fun `October one clock money weeks costs and payments agree with Hoy`() {
        val month = fixture.month()
        assertEquals("$11,692", month.amount)
        assertEquals("Llevas en octubre · aprox.", month.label)
        assertEquals(155, month.money.trips)
        assertTrue(month.quietLine.contains("(1 en curso)"))
        assertEquals(listOf("$1,417", "$4,747", "$4,980", "$548"), month.bars.map { it.display })
        assertEquals("4 de 7 días del coche", month.weekNote(month.weeks.first()))
        assertEquals(19, month.money.carCharges.size)
        assertNull(month.roundingNote)
        assertEquals(listOf("$20,429", "−$4,666", "−$4,071", "$11,692"), monthCalculation(month).rows.map { it.value })
        assertEquals("Del 1 de octubre hasta hoy a las 14:40", monthCalculation(month).rows.last().detail)
        assertEquals(listOf("$12,487", "$7,942"), ImssTracker.payments(month.money).map { Formatters.formatMxnWhole(it.amount!!) })
    }
    @Test fun `import only retains exact payment scope no net no dated bars`() {
        val month = fixture.month("E3b")
        assertTrue(month.importOnly)
        assertEquals("Te pagó Uber del 14 al 20 sep", month.label)
        assertEquals("$5,340", month.amount)
        assertEquals("29 viajes según tu resumen, sin km", month.quietLine)
        assertTrue(month.weeks.isEmpty())
        assertNull(month.money.periodResult)
        assertEquals(3, month.money.carCharges.size)
        assertTrue(monthCalculation(month).notes.any { it.contains("$643") })
    }
    @Test fun `mixed month excludes undated import from net keeps gap and payment only IMSS scope`() {
        val month = fixture.month("E3c")
        assertEquals("$7,170", month.amount)
        assertEquals(75, month.money.trips)
        assertEquals(3, month.bars.size)
        assertNull(month.bars.first().value)
        assertEquals(listOf("$5,723", "$1,446"), month.bars.drop(1).map { it.display })
        assertEquals("$15,440", Formatters.formatMxnWhole(ImssTracker.payments(month.money).single().amount!!))
        assertNotNull(month.roundingNote)
    }
    @Test fun `cross boundary undated summary stays in week and cannot erase dated income`() {
        val f = fixture.fixture
        val dated = f.monthRows.flatMap { it.contributions.map { c -> c.record } }
        val crossing = dated.first().copy(id = "boundary", source = MoneySource.PARSED_STATEMENT,
            coverage = Coverage(LocalDate.parse("2026-09-28"), LocalDate.parse("2026-10-04"), false), payment = "99999".toBigDecimal())
        val month = monthMoneySnapshot(f.monthMoney.window, dated + crossing, f.schedule)
        assertEquals(f.monthMoney.periodResult, month.money.periodResult)
        assertEquals(listOf(crossing), month.unallocated)
        assertTrue(month.money.paymentOnly.isEmpty())
    }
    @Test fun `crossing only summary names its full week and never claims a monthly net or IMSS payment`() {
        val f = fixture.fixture
        val record = f.todayRecords.first().copy(id = "cross", source = MoneySource.PARSED_STATEMENT,
            coverage = Coverage(LocalDate.parse("2026-09-28"), LocalDate.parse("2026-10-04"), false))
        val month = MonthPresentation(monthMoneySnapshot(f.monthMoney.window, listOf(record), f.schedule))
        assertTrue(month.importOnly)
        assertTrue(month.label.contains("28 sep al 4 oct"))
        assertTrue(month.weeks.isEmpty())
        assertTrue(ImssTracker.payments(month.money).isEmpty())
        assertTrue(monthCalculation(month).notes.any { it.contains("no se reparte entre meses") })
    }
    @Test fun `even known statement km cannot fabricate dated net and overlapping evidence never doubles payment`() {
        val f = fixture.fixture
        val dated = f.monthRows.flatMap { it.contributions.map { c -> c.record } }
        val duplicate = dated.first().copy(id = "duplicate", source = MoneySource.PARSED_STATEMENT, coverage = dated.first().coverage.copy(dated = false))
        val month = monthMoneySnapshot(f.monthMoney.window, dated + duplicate, f.schedule)
        assertEquals(f.monthMoney.periodResult, month.money.periodResult)
        assertEquals(f.monthMoney.payment, month.money.paymentScopeTotal)
        assertTrue(month.money.excluded.any { it.id == "duplicate" })
        val only = monthMoneySnapshot(f.monthMoney.window, listOf(duplicate), f.schedule)
        assertNull(only.money.periodResult)
        assertTrue(only.weeks.all { it.contributions.isEmpty() })
    }
    @Test fun `month comparison uses last closed week across Sunday Monday year and short months`() {
        fun last(month: String, today: String) = SelectedPeriod.lastClosedWeek(YearMonth.parse(month), LocalDate.parse(today)).toString()
        assertEquals("2026-10-12", last("2026-10", "2026-10-19"))
        assertEquals("2026-10-05", last("2026-10", "2026-10-18"))
        assertEquals("2026-09-21", last("2026-09", "2026-10-19"))
        assertEquals("2025-12-22", last("2026-01", "2026-01-01"))
        assertEquals("2028-02-21", last("2028-02", "2028-03-01"))
    }
    @Test fun `factual PDF has source labels and only recorded values no legal outputs`() {
        for (frame in listOf("E3", "E3b", "E3c")) {
            val data = FiscalReportComposer.recorded(fixture.month(frame), "Generado hoy")
            assertNotNull(data.recordedRows)
            assertTrue(data.columnHeaders.isEmpty())
            assertEquals("", data.monthWithheldTotal)
            assertEquals("", data.ratesNote)
            val text = (data.recordedRows.toString() + data.sourceNotes + data.imssCoverageLine).lowercase()
            listOf("cubierto", "retenciones", "isr", "iva ret", "0.94", "9,582").forEach { assertFalse(text.contains(it)) }
            if (frame == "E3") assertTrue(text.contains("12,487"))
            else assertTrue(text.contains("sin detalle por día"))
            if (frame == "E3c") assertTrue(text.contains("antes de redondear cada cifra"))
        }
    }
    @Test fun `negative unknown empty and older month retain truthful access`() {
        val f = fixture.fixture
        val noTrips = MonthPresentation(monthMoneySnapshot(f.monthMoney.window, emptyList(), f.schedule))
        assertEquals("−$4,071", noTrips.amount)
        assertFalse(noTrips.locked)
        val empty = MonthPresentation(monthMoneySnapshot(f.monthMoney.window, emptyList(), CostSchedule(emptyList(), emptyList())))
        assertFalse(empty.hasData)
        val older = fixture.month("E3c").copy(premium = false)
        assertTrue(older.locked)
        assertEquals("$7,170", older.amount)
        assertFalse(fixture.month("E3b").copy(premium = false).locked)
    }
}
