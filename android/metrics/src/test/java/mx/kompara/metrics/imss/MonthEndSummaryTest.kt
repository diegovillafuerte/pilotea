package mx.kompara.metrics.imss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the pure [MonthEndSummary] decision logic (B-051). */
class MonthEndSummaryTest {

    private fun status(platform: String, net: Double, covered: Boolean) = PlatformImssStatus(
        platform = platform,
        netSoFarMxn = net,
        thresholdMxn = 8364.0,
        remainingMxn = (8364.0 - net).coerceAtLeast(0.0),
        progress = (net / 8364.0).coerceIn(0.0, 1.0),
        daysRemaining = 0,
        projectedMonthEndMxn = net,
        status = if (covered) CoverageStatus.COVERED else CoverageStatus.UNLIKELY,
        phase = MonthPhase.PAST,
    )

    @Test
    fun `posts one decision per active platform with the right covered flag`() {
        val decisions = MonthEndSummary.decide(
            monthKey = "2026-05",
            enabled = true,
            statuses = listOf(
                status("UBER", 9000.0, covered = true),
                status("DIDI", 4000.0, covered = false),
            ),
            alreadyNotifiedMonth = null,
        )
        assertEquals(2, decisions.size)
        assertEquals(true, decisions.first { it.platform == "UBER" }.covered)
        assertEquals(false, decisions.first { it.platform == "DIDI" }.covered)
        decisions.forEach { assertEquals("2026-05", it.monthKey) }
    }

    @Test
    fun `skips platforms with no activity last month`() {
        val decisions = MonthEndSummary.decide(
            monthKey = "2026-05",
            enabled = true,
            statuses = listOf(
                status("UBER", 9000.0, covered = true),
                status("DIDI", 0.0, covered = false), // idle → no verdict
            ),
            alreadyNotifiedMonth = null,
        )
        assertEquals(listOf("UBER"), decisions.map { it.platform })
    }

    @Test
    fun `returns empty when the toggle is off`() {
        val decisions = MonthEndSummary.decide(
            monthKey = "2026-05",
            enabled = false,
            statuses = listOf(status("UBER", 9000.0, covered = true)),
            alreadyNotifiedMonth = null,
        )
        assertTrue(decisions.isEmpty())
    }

    @Test
    fun `is idempotent — already-notified month yields nothing`() {
        val decisions = MonthEndSummary.decide(
            monthKey = "2026-05",
            enabled = true,
            statuses = listOf(status("UBER", 9000.0, covered = true)),
            alreadyNotifiedMonth = "2026-05",
        )
        assertTrue(decisions.isEmpty())
    }

    @Test
    fun `a different already-notified month still posts`() {
        val decisions = MonthEndSummary.decide(
            monthKey = "2026-05",
            enabled = true,
            statuses = listOf(status("UBER", 9000.0, covered = true)),
            alreadyNotifiedMonth = "2026-04",
        )
        assertEquals(1, decisions.size)
    }

    @Test
    fun `no statuses yields no decisions`() {
        val decisions = MonthEndSummary.decide(
            monthKey = "2026-05",
            enabled = true,
            statuses = emptyList(),
            alreadyNotifiedMonth = null,
        )
        assertTrue(decisions.isEmpty())
    }
}
