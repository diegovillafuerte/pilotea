package mx.kompara.data.accounting

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class StatementSelectionTest {
    private val start = LocalDate.parse("2026-09-28")
    private val window = PeriodWindow(start, start.plusDays(6), Instant.parse("2026-10-29T12:00:00Z"), ZoneId.of("America/Mexico_City"))
    private val costs = CostSchedule(emptyList())
    private val captured = IncomeRecord("captured", "UBER", Coverage(start.plusDays(3), start.plusDays(6)), MoneySource.CAPTURED_ESTIMATE, 1480.toBigDecimal(), "150.6".toBigDecimal(), 14)
    private val statement = IncomeRecord("statement", "UBER", Coverage(start, start.plusDays(6), false), MoneySource.PARSED_STATEMENT, 2210.toBigDecimal(), null, 20)
    private val choice = StatementSelection(statement.id, setOf(MoneyField.PAYMENT, MoneyField.TRIPS))
    @Test fun `unselected statement never changes captured records or another app`() {
        val didi = captured.copy(id = "didi", app = "DIDI")
        val selected = selectStatements(listOf(captured, statement, didi), emptyList(), window)
        assertEquals(setOf(captured, didi), selected.records.toSet()); assertEquals(listOf(statement), selected.excluded)
    }
    @Test fun `partial day coverage selects full payment and trips without partial distance or invented net`() {
        val money = PeriodAccounting.calculate(window, listOf(captured, statement), costs, statementSelections = listOf(choice))
        assertNull(money.periodResult); assertEquals(2210.toBigDecimal(), money.paymentOnly.single().record.payment)
        assertNull(money.paymentOnly.single().record.km); assertEquals(20, money.paymentOnly.single().record.trips)
        assertEquals(captured, money.excluded.single()); assertEquals(MoneyLifecycle.TAKEN_ESTIMATE, captured.lifecycle)
    }
    @Test fun `cross month and day windows retain original dated capture and no divided import`() {
        val october = window.copy(start = LocalDate.parse("2026-10-01"), endInclusive = LocalDate.parse("2026-10-31"))
        val selected = selectStatements(listOf(captured, statement), listOf(choice), october)
        assertEquals(listOf(captured), selected.records); assertTrue(selected.excluded.contains(statement))
        val day = window.copy(start = start.plusDays(3), endInclusive = start.plusDays(3))
        assertEquals(listOf(captured), selectStatements(listOf(captured, statement), listOf(choice), day).records)
    }
    @Test fun `field choices cannot borrow distance even for equal counts or hours from captures`() {
        val full = statement.copy(trips = 14, km = 240.toBigDecimal(), minutes = 1200.toBigDecimal())
        val selected = selectStatements(listOf(captured, full), listOf(choice), window).records.single()
        assertNull(selected.km); assertNull(selected.minutes)
        assertEquals(mapOf(MoneyField.PAYMENT to "statement", MoneyField.TRIPS to "statement"), selected.fieldSources)
    }
    @Test fun `different apps and repeated selection are idempotent with no captured mutations`() {
        val didi = captured.copy(id = "didi", app = "DIDI")
        val once = selectStatements(listOf(captured, didi, statement), listOf(choice), window)
        val again = selectStatements(once.records, listOf(choice), window)
        assertEquals(once.records.toSet(), again.records.toSet()); assertTrue(once.records.contains(didi))
    }
    @Test fun `active and boundary overlapping records cannot be silently replaced`() {
        for (row in listOf(captured.copy(inProgress = 1), captured.copy(coverage = Coverage(start.minusDays(1), start.plusDays(3))))) {
            assertEquals(listOf(row), selectStatements(listOf(row, statement), listOf(choice), window).records)
        }
    }
    @Test fun `known zero distance remains distinct from missing distance`() {
        val withDistance = choice.copy(fields = choice.fields + MoneyField.DISTANCE)
        val record = selectStatements(listOf(captured, statement.copy(km = java.math.BigDecimal.ZERO)), listOf(withDistance), window).records.single()
        assertEquals(java.math.BigDecimal.ZERO, record.km)
    }
}
