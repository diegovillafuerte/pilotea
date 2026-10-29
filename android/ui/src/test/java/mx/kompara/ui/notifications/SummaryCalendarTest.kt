package mx.kompara.ui.notifications

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SummaryCalendarTest {
    private fun at(value: String) = ZonedDateTime.parse(value)
    @Test fun `day is local and catchup retains yesterday until evening`() {
        assertEquals("2026-10-18", SummaryCalendar.due(NoticeKind.DAY, at("2026-10-19T08:00:00-06:00[America/Mexico_City]")))
        assertEquals("2026-10-19", SummaryCalendar.due(NoticeKind.DAY, at("2026-10-19T21:00:00-06:00[America/Mexico_City]")))
    }
    @Test fun `closed calendar weeks and months handle year rollover`() {
        assertEquals("2026-12-28", SummaryCalendar.due(NoticeKind.WEEK, at("2027-01-04T09:00:00-06:00[America/Mexico_City]")))
        assertEquals("2026-12", SummaryCalendar.due(NoticeKind.MONTH, at("2027-01-01T08:00:00-06:00[America/Mexico_City]")))
    }
    @Test fun `daily initial delay follows local time across daylight savings not fixed 24 hours`() {
        assertEquals(Duration.ofHours(23), SummaryCalendar.dayDelay(at("2026-03-07T21:00:00-05:00[America/New_York]")))
        assertEquals(Duration.ofHours(25), SummaryCalendar.dayDelay(at("2026-10-31T21:00:00-04:00[America/New_York]")))
    }
}
