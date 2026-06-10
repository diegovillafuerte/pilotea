package mx.kompara.ui.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** The Monday-09:00 initial-delay math for the week-close reminder (B-055). */
class WeekCloseSchedulerTest {

    private val utc = ZoneId.of("UTC")
    private val hourMs = 60L * 60 * 1000
    private val dayMs = 24 * hourMs

    private fun ms(dateTime: LocalDateTime): Long =
        dateTime.toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `from a friday afternoon delays to the coming monday 9am`() {
        // 2026-06-05 is a Friday, 15:00. Next Monday 09:00 is 2026-06-08 09:00.
        val now = ms(LocalDateTime.of(2026, 6, 5, 15, 0))
        val delay = WeekCloseScheduler.initialDelayMillis(now, utc)
        val expected = ms(LocalDateTime.of(2026, 6, 8, 9, 0)) - now
        assertEquals(expected, delay)
    }

    @Test
    fun `monday before 9am delays to the same morning`() {
        // 2026-06-08 is a Monday, 07:30 → 09:00 the same day, 90 min away.
        val now = ms(LocalDateTime.of(2026, 6, 8, 7, 30))
        val delay = WeekCloseScheduler.initialDelayMillis(now, utc)
        assertEquals(90L * 60 * 1000, delay)
    }

    @Test
    fun `monday after 9am rolls to next monday`() {
        // 2026-06-08 Monday 10:00 → next Monday 2026-06-15 09:00.
        val now = ms(LocalDateTime.of(2026, 6, 8, 10, 0))
        val delay = WeekCloseScheduler.initialDelayMillis(now, utc)
        val expected = ms(LocalDateTime.of(2026, 6, 15, 9, 0)) - now
        assertEquals(expected, delay)
    }

    @Test
    fun `exactly monday 9am schedules a full week out, never zero`() {
        val now = ms(LocalDateTime.of(2026, 6, 8, 9, 0))
        val delay = WeekCloseScheduler.initialDelayMillis(now, utc)
        assertEquals(7 * dayMs, delay)
        assertTrue(delay > 0)
    }

    @Test
    fun `delay is always positive and within a week`() {
        // Sweep a few representative instants; the delay must stay in (0, 7 days].
        val instants = listOf(
            LocalDateTime.of(2026, 6, 3, 0, 0), // Wed midnight
            LocalDateTime.of(2026, 6, 7, 23, 59), // Sun late
            LocalDateTime.of(2026, 6, 8, 8, 59), // Mon just before
        )
        for (dt in instants) {
            val delay = WeekCloseScheduler.initialDelayMillis(ms(dt), utc)
            assertTrue("delay must be > 0 for $dt", delay > 0)
            assertTrue("delay must be <= 7 days for $dt", delay <= 7 * dayMs)
        }
    }
}
