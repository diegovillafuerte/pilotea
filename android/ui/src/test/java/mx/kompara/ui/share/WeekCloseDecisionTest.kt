package mx.kompara.ui.share

import org.junit.Assert.assertEquals
import org.junit.Test

/** The pure week-close reminder scheduling decision (B-055). */
class WeekCloseDecisionTest {

    private val week = "2026-06-01"

    @Test
    fun `posts when enabled, has data, and not yet reminded`() {
        assertEquals(
            WeekCloseAction.POST,
            WeekCloseDecision.decide(enabled = true, closedWeekStart = week, lastReminderWeek = null, hasData = true),
        )
    }

    @Test
    fun `skips when already reminded for this week`() {
        assertEquals(
            WeekCloseAction.SKIP,
            WeekCloseDecision.decide(enabled = true, closedWeekStart = week, lastReminderWeek = week, hasData = true),
        )
    }

    @Test
    fun `stamps only when toggle is off`() {
        assertEquals(
            WeekCloseAction.STAMP_ONLY,
            WeekCloseDecision.decide(enabled = false, closedWeekStart = week, lastReminderWeek = null, hasData = true),
        )
    }

    @Test
    fun `stamps only when the week had no data`() {
        assertEquals(
            WeekCloseAction.STAMP_ONLY,
            WeekCloseDecision.decide(enabled = true, closedWeekStart = week, lastReminderWeek = null, hasData = false),
        )
    }

    @Test
    fun `a different prior week does not block a new week`() {
        assertEquals(
            WeekCloseAction.POST,
            WeekCloseDecision.decide(
                enabled = true,
                closedWeekStart = week,
                lastReminderWeek = "2026-05-25",
                hasData = true,
            ),
        )
    }
}
