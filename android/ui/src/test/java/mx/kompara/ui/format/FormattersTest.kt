package mx.kompara.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `formatMxn renders peso sign, grouping and two decimals`() {
        assertEquals("$1,234.56", Formatters.formatMxn(1234.56))
    }

    @Test
    fun `formatMxn pads to two decimals`() {
        assertEquals("$8.00", Formatters.formatMxn(8.0))
        assertEquals("$8.50", Formatters.formatMxn(8.5))
    }

    @Test
    fun `formatMxn rounds to two decimals`() {
        assertEquals("$8.46", Formatters.formatMxn(8.455))
    }

    @Test
    fun `formatMxn handles zero and negatives`() {
        assertEquals("$0.00", Formatters.formatMxn(0.0))
        assertEquals("$-12.30", Formatters.formatMxn(-12.3))
    }

    @Test
    fun `formatMxn groups large numbers`() {
        assertEquals("$1,000,000.00", Formatters.formatMxn(1_000_000.0))
    }

    @Test
    fun `formatKm renders one decimal with km suffix`() {
        assertEquals("12.3 km", Formatters.formatKm(12.34))
        assertEquals("0.0 km", Formatters.formatKm(0.0))
        assertEquals("8.0 km", Formatters.formatKm(8.0))
    }

    @Test
    fun `formatPerHour appends per-hour suffix to money`() {
        assertEquals("$185.50/h", Formatters.formatPerHour(185.5))
        assertEquals("$0.00/h", Formatters.formatPerHour(0.0))
    }

    @Test
    fun `formatPerHourWhole rounds to whole pesos`() {
        assertEquals("$186/h", Formatters.formatPerHourWhole(185.5))
        assertEquals("$185/h", Formatters.formatPerHourWhole(185.4))
        assertEquals("$1,200/h", Formatters.formatPerHourWhole(1200.0))
        assertEquals("$0/h", Formatters.formatPerHourWhole(0.0))
    }

    @Test
    fun `formatWeekRangeLabel renders a same-month Mon to Sun range`() {
        // 2026-06-01 is a Monday; the week runs Mon 1 – Sun 7 June.
        assertEquals("Semana del 1–7 jun", Formatters.formatWeekRangeLabel("2026-06-01"))
    }

    @Test
    fun `formatWeekRangeLabel renders a month-straddling range`() {
        // 2026-06-29 Monday → Sun 5 July; spans June into July.
        assertEquals("Semana del 29 jun–5 jul", Formatters.formatWeekRangeLabel("2026-06-29"))
    }

    @Test
    fun `formatWeekRangeLabel falls back on unparseable input`() {
        assertEquals("garbage", Formatters.formatWeekRangeLabel("garbage"))
    }

    @Test
    fun `formatMonthLabel renders a capitalised month and year`() {
        assertEquals("Junio 2026", Formatters.formatMonthLabel("2026-06-01"))
        assertEquals("Enero 2026", Formatters.formatMonthLabel("2026-01-15"))
    }

    @Test
    fun `formatMonthLabel falls back on unparseable input`() {
        assertEquals("nope", Formatters.formatMonthLabel("nope"))
    }
}
