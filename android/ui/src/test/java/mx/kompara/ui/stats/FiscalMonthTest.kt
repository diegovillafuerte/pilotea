package mx.kompara.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Tests for [FiscalMonth] (B-051): month offsets, query ranges, straddling-week bounds, labels. */
class FiscalMonthTest {

    private val zone = ZoneId.of("America/Mexico_City")
    private val fm = FiscalMonth(zone)

    /** Epoch millis at local noon of [iso] (yyyy-MM-dd) in the test zone. */
    private fun noonOf(iso: String): Long =
        java.time.LocalDate.parse(iso).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `current month and offsets`() {
        val now = noonOf("2026-06-10")
        assertEquals(YearMonth.of(2026, 6), fm.currentMonth(now))
        assertEquals(YearMonth.of(2026, 6), fm.monthAt(now, 0))
        assertEquals(YearMonth.of(2026, 5), fm.monthAt(now, 1))
        assertEquals(YearMonth.of(2025, 12), fm.monthAt(now, 6))
    }

    @Test
    fun `month day range spans the whole month`() {
        val june = YearMonth.of(2026, 6)
        assertEquals("2026-06-01", fm.monthStartDay(june))
        assertEquals("2026-06-30", fm.monthEndDay(june))
        // February non-leap.
        assertEquals("2026-02-28", fm.monthEndDay(YearMonth.of(2026, 2)))
        // February leap.
        assertEquals("2024-02-29", fm.monthEndDay(YearMonth.of(2024, 2)))
    }

    @Test
    fun `week range start is the Monday on or before the first of the month`() {
        // June 1 2026 is a Monday → range start = 2026-06-01.
        assertEquals("2026-06-01", fm.weekRangeStart(YearMonth.of(2026, 6)))
        // July 1 2026 is a Wednesday → previous Monday = 2026-06-29 (captures the straddling week).
        assertEquals("2026-06-29", fm.weekRangeStart(YearMonth.of(2026, 7)))
    }

    @Test
    fun `week range end is the last day of the month`() {
        assertEquals("2026-06-30", fm.weekRangeEnd(YearMonth.of(2026, 6)))
    }

    @Test
    fun `label is a capitalized Spanish month and year`() {
        assertEquals("Junio 2026", fm.label(YearMonth.of(2026, 6)))
        assertEquals("Diciembre 2025", fm.label(YearMonth.of(2025, 12)))
    }

    @Test
    fun `today reflects the injected zone`() {
        val now = noonOf("2026-06-10")
        assertEquals(java.time.LocalDate.of(2026, 6, 10), fm.today(now))
    }
}
