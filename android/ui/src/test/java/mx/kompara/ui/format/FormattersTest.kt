package mx.kompara.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test fun `clock follows kit hour style and the evidence zone at midnight and noon`() {
        val zone = java.time.ZoneId.of("America/Mexico_City")
        for (time in listOf("00:05" to "0:05", "09:51" to "9:51", "12:00" to "12:00", "23:59" to "23:59")) {
            val at = java.time.LocalDate.parse("2026-10-19").atTime(java.time.LocalTime.parse(time.first)).atZone(zone).toInstant().toEpochMilli()
            assertEquals(time.second, Formatters.formatClock(at, zone))
        }
        assertEquals("9:51", Formatters.formatClock(java.time.Instant.parse("2026-10-19T15:51:00Z").toEpochMilli(), zone))
    }

    @Test fun `hourly limits preserve saved cents while hourly earnings still round to pesos`() {
        assertEquals("$315.25/h", Formatters.formatLimit("315.25".toBigDecimal(), perHour = true))
        assertEquals("$200/h", Formatters.formatLimit("200.00".toBigDecimal(), perHour = true))
        assertEquals("$315/h", Formatters.formatPerHourWhole("315.25".toBigDecimal()))
    }

    @Test fun `no break formatting is idempotent and normalizes to the exact visible contract`() {
        for (value in listOf("−$1.05/km", "$1,200/h", "$6.50/km", "(1 en curso)", "4 h 50 min", "78 %")) {
            val protected = Formatters.unbroken(value)
            assertEquals(protected, Formatters.unbroken(protected))
            assertEquals(value, mx.kompara.ui.contract.FrameContract.normalize(protected))
        }
    }

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
        assertEquals("−$12.30", Formatters.formatMxn(-12.3))
    }

    @Test
    fun `formatMxn groups large numbers`() {
        assertEquals("$1,000,000.00", Formatters.formatMxn(1_000_000.0))
    }

    @Test
    fun `formatKm renders one decimal with km suffix`() {
        assertEquals("12.3\u00a0km", Formatters.formatKm(12.34))
        assertEquals("0.0\u00a0km", Formatters.formatKm(0.0))
        assertEquals("8.0\u00a0km", Formatters.formatKm(8.0))
    }

    @Test
    fun `formatPerHour appends per-hour suffix to money`() {
        assertEquals("$186/h", Formatters.formatPerHour(185.5))
        assertEquals("$0/h", Formatters.formatPerHour(0.0))
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

    @Test fun `v3 decimal formatting rounds once with a real minus before peso`() {
        assertEquals("−$1.05/km", Formatters.formatPerKm(java.math.BigDecimal("-1.053")))
        assertEquals("−$1,234", Formatters.formatMxnWhole(-1234.4))
        assertEquals("$0", Formatters.formatMxnWhole(-0.1))
        assertEquals("$6.50/km", Formatters.formatLimit(java.math.BigDecimal("6.5")))
        assertEquals("$200/h", Formatters.formatLimit(java.math.BigDecimal("200"), perHour = true))
        assertEquals("$1,235", Formatters.formatMxnWhole(java.math.BigDecimal("1234.5")))
    }

    @Test fun `percentages round and units cannot split from figures`() {
        assertEquals("78\u00a0%", Formatters.formatPercent(.775))
        assertEquals("100\u00a0%", Formatters.formatPercent(1.2))
        assertEquals("0\u00a0%", Formatters.formatPercent(-.1))
        assertEquals("8\u00a0h\u00a010\u00a0min", Formatters.formatDuration(490))
        assertEquals("12\u00a0–\u00a018\u00a0oct", Formatters.unbroken("12 – 18 oct"))
        assertEquals("−$1.05\u2060/\u2060km", Formatters.unbroken("−$1.05/km"))
    }

    @Test fun `relative money labels always identify the date`() {
        val today = java.time.LocalDate.of(2026, 10, 20)
        assertEquals("Ayer, lunes 19 oct", Formatters.formatDatedRelativeLabel(today.minusDays(1), today))
        assertEquals("Domingo 18 oct", Formatters.formatDatedRelativeLabel(today.minusDays(2), today))
        assertEquals("Ayer, jueves 31 dic 2026", Formatters.formatDatedRelativeLabel(
            java.time.LocalDate.of(2026, 12, 31), java.time.LocalDate.of(2027, 1, 1)))
    }
}
