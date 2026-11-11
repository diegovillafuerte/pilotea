package mx.kompara.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** History weeks folding + source badge precedence for [HistoryWeeks] (B-040 req 3). */
class HistoryWeeksTest {

    // ── B-050 free-tier truncation ───────────────────────────────────────────

    private fun weeksList(vararg weekStarts: String): List<HistoryWeek> =
        weekStarts.map { ws ->
            HistoryWeek(weekStart = ws, source = WeekSourceBadge.CAPTURADO, period = PeriodStats.EMPTY)
        }

    @Test
    fun `unlocked partition keeps every week visible and locks nothing`() {
        val weeks = weeksList("2026-06-15", "2026-06-08", "2026-06-01", "2026-05-25")
        val p = HistoryWeeks.partition(weeks, locked = false, today = LocalDate.parse("2026-06-29"))
        assertEquals(4, p.visible.size)
        assertEquals(0, p.locked.size)
        assertEquals(false, p.hasLocked)
    }

    @Test
    fun `calendar window gates breakdowns but keeps every own week visible`() {
        val weeks = weeksList("2026-06-15", "2026-06-08", "2026-06-01", "2026-05-25")
        val p = HistoryWeeks.partition(weeks, locked = true, today = LocalDate.parse("2026-06-29"))
        assertEquals(listOf("2026-06-15", "2026-06-08", "2026-06-01", "2026-05-25"), p.visible.map { it.weekStart })
        assertEquals(listOf("2026-06-01", "2026-05-25"), p.locked.map { it.weekStart })
        assertEquals(true, p.hasLocked)
    }

    @Test
    fun `recent sparse weeks still fall inside the calendar window`() {
        val p = HistoryWeeks.partition(weeksList("2026-06-15", "2026-06-08"), locked = true, today = LocalDate.parse("2026-06-29"))
        assertEquals(2, p.visible.size)
        assertEquals(0, p.locked.size)
    }

    @Test
    fun `locked partition newest-first regardless of input order`() {
        val weeks = weeksList("2026-05-25", "2026-06-15", "2026-06-01", "2026-06-08")
        val p = HistoryWeeks.partition(weeks, locked = true, today = LocalDate.parse("2026-06-29"))
        assertEquals(listOf("2026-06-15", "2026-06-08", "2026-06-01", "2026-05-25"), p.visible.map { it.weekStart })
        assertEquals(listOf("2026-06-01", "2026-05-25"), p.locked.map { it.weekStart })
    }
}
