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
}
