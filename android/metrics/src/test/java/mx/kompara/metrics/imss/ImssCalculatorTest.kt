package mx.kompara.metrics.imss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Matrix for the pure [ImssCalculator] (B-051): under/over threshold, straddling-week pro-rate,
 * projection math, days-remaining across month lengths + leap years, and per-platform isolation.
 */
class ImssCalculatorTest {

    private val calc = ImssCalculator()
    private val threshold = 8364.0

    private fun daily(platform: String, day: String, net: Double) =
        DailyMonthInput(platform, day, net)

    private fun weekly(platform: String, weekStart: String, net: Double) =
        WeeklyMonthInput(platform, weekStart, net)

    // ─── under / over threshold ──────────────────────────────────────────────────────────────

    @Test
    fun `over threshold from daily rows is COVERED`() {
        val rows = listOf(
            daily("UBER", "2026-06-05", 5000.0),
            daily("UBER", "2026-06-15", 4000.0),
        )
        val s = calc.statusForPlatform(
            platform = "UBER",
            month = YearMonth.of(2026, 6),
            thresholdMxn = threshold,
            daily = rows,
            weekly = emptyList(),
            today = LocalDate.of(2026, 6, 20),
        )
        assertEquals(9000.0, s.netSoFarMxn, 0.001)
        assertEquals(CoverageStatus.COVERED, s.status)
        assertTrue(s.covered)
        assertEquals(0.0, s.remainingMxn, 0.001)
        assertEquals(1.0, s.progress, 0.001)
        assertEquals(100, s.progressPercent)
    }

    @Test
    fun `under threshold mid-month with strong pace is ON_TRACK`() {
        // $5,000 by day 10 of a 30-day month → run-rate 500/day → projects 15,000 > 8,364.
        val rows = listOf(daily("DIDI", "2026-06-10", 5000.0))
        val s = calc.statusForPlatform(
            platform = "DIDI",
            month = YearMonth.of(2026, 6),
            thresholdMxn = threshold,
            daily = rows,
            weekly = emptyList(),
            today = LocalDate.of(2026, 6, 10),
        )
        assertEquals(5000.0, s.netSoFarMxn, 0.001)
        assertEquals(3364.0, s.remainingMxn, 0.001)
        assertEquals(CoverageStatus.ON_TRACK, s.status)
        assertEquals(15000.0, s.projectedMonthEndMxn, 0.001)
    }

    @Test
    fun `under threshold with weak pace is UNLIKELY`() {
        // $1,000 by day 20 of June (30 days) → 50/day → projects 1,500 < 8,364.
        val rows = listOf(daily("UBER", "2026-06-20", 1000.0))
        val s = calc.statusForPlatform(
            platform = "UBER",
            month = YearMonth.of(2026, 6),
            thresholdMxn = threshold,
            daily = rows,
            weekly = emptyList(),
            today = LocalDate.of(2026, 6, 20),
        )
        assertEquals(CoverageStatus.UNLIKELY, s.status)
        assertEquals(1500.0, s.projectedMonthEndMxn, 0.001)
    }

    @Test
    fun `progress fraction is clamped and computed from net over threshold`() {
        val rows = listOf(daily("UBER", "2026-06-01", 4182.0))
        val s = calc.statusForPlatform(
            "UBER", YearMonth.of(2026, 6), threshold, rows, emptyList(), LocalDate.of(2026, 6, 15),
        )
        assertEquals(0.5, s.progress, 0.001)
        assertEquals(50, s.progressPercent)
    }

    // ─── straddling weeks: pro-rate when no daily coverage ─────────────────────────────────────

    @Test
    fun `weekly straddling two months pro-rates only the in-month days`() {
        // Week of Mon 2026-06-29..Sun 2026-07-05: 2 days (29,30) in June, 5 days in July.
        // Net 7,000 → June share = 7000 * 2/7 = 2,000.
        val weekly = listOf(weekly("UBER", "2026-06-29", 7000.0))
        val june = calc.statusForPlatform(
            "UBER", YearMonth.of(2026, 6), threshold, emptyList(), weekly, LocalDate.of(2026, 6, 30),
        )
        assertEquals(2000.0, june.netSoFarMxn, 0.001)

        val july = calc.statusForPlatform(
            "UBER", YearMonth.of(2026, 7), threshold, emptyList(), weekly, LocalDate.of(2026, 7, 6),
        )
        assertEquals(5000.0, july.netSoFarMxn, 0.001)
    }

    @Test
    fun `daily rows suppress pro-rate for the same days (no double count)`() {
        // A week fully inside June, but two of its days also have daily rows.
        // Daily should win for those days; the weekly only pro-rates the 5 uncovered days.
        val daily = listOf(
            daily("UBER", "2026-06-08", 1000.0),
            daily("UBER", "2026-06-09", 1000.0),
        )
        val weekly = listOf(weekly("UBER", "2026-06-08", 7000.0)) // Mon 2026-06-08..Sun 06-14
        val s = calc.statusForPlatform(
            "UBER", YearMonth.of(2026, 6), threshold, daily, weekly, LocalDate.of(2026, 6, 30),
        )
        // daily 2,000 + weekly pro-rate of 5 uncovered days = 7000 * 5/7 = 5,000 → 7,000 total.
        assertEquals(7000.0, s.netSoFarMxn, 0.001)
    }

    @Test
    fun `fully in-month week with no daily rows counts its whole net`() {
        val weekly = listOf(weekly("DIDI", "2026-06-08", 3500.0)) // all 7 days in June
        val s = calc.statusForPlatform(
            "DIDI", YearMonth.of(2026, 6), threshold, emptyList(), weekly, LocalDate.of(2026, 6, 30),
        )
        assertEquals(3500.0, s.netSoFarMxn, 0.001)
    }

    // ─── days remaining: month lengths + leap years ───────────────────────────────────────────

    @Test
    fun `days remaining includes today`() {
        val s = calc.statusForPlatform(
            "UBER", YearMonth.of(2026, 6), threshold, emptyList(), emptyList(), LocalDate.of(2026, 6, 20),
        )
        // June has 30 days; on the 20th, 30-20+1 = 11 remaining (incl. today).
        assertEquals(11, s.daysRemaining)
    }

    @Test
    fun `days remaining on the last day of month is 1`() {
        val s = calc.statusForPlatform(
            "UBER", YearMonth.of(2026, 1), threshold, emptyList(), emptyList(), LocalDate.of(2026, 1, 31),
        )
        assertEquals(1, s.daysRemaining)
    }

    @Test
    fun `february leap year has 29 days`() {
        // 2024 is a leap year. On Feb 1, days remaining = 29.
        val s = calc.statusForPlatform(
            "UBER", YearMonth.of(2024, 2), threshold, emptyList(), emptyList(), LocalDate.of(2024, 2, 1),
        )
        assertEquals(29, s.daysRemaining)
    }

    @Test
    fun `february non-leap year has 28 days`() {
        // 2026 is not a leap year. On Feb 1, days remaining = 28.
        val s = calc.statusForPlatform(
            "UBER", YearMonth.of(2026, 2), threshold, emptyList(), emptyList(), LocalDate.of(2026, 2, 1),
        )
        assertEquals(28, s.daysRemaining)
    }

    // ─── projection edge cases ─────────────────────────────────────────────────────────────────

    @Test
    fun `projection uses elapsed calendar days including today as the run-rate base`() {
        // $2,000 by day 5 of July (31 days) → 400/day → projects 12,400.
        val rows = listOf(daily("UBER", "2026-07-03", 2000.0))
        val s = calc.statusForPlatform(
            "UBER", YearMonth.of(2026, 7), threshold, rows, emptyList(), LocalDate.of(2026, 7, 5),
        )
        assertEquals(12400.0, s.projectedMonthEndMxn, 0.001)
    }

    @Test
    fun `zero net mid-month projects zero and is UNLIKELY`() {
        val s = calc.statusForPlatform(
            "UBER", YearMonth.of(2026, 6), threshold, emptyList(), emptyList(), LocalDate.of(2026, 6, 15),
        )
        assertEquals(0.0, s.projectedMonthEndMxn, 0.001)
        assertEquals(CoverageStatus.UNLIKELY, s.status)
        assertEquals(MonthPhase.CURRENT, s.phase)
    }

    // ─── past / future months ──────────────────────────────────────────────────────────────────

    @Test
    fun `past month under threshold is UNLIKELY and projection equals realized net`() {
        val rows = listOf(daily("UBER", "2026-05-10", 3000.0))
        val s = calc.statusForPlatform(
            "UBER", YearMonth.of(2026, 5), threshold, rows, emptyList(), LocalDate.of(2026, 6, 10),
        )
        assertEquals(MonthPhase.PAST, s.phase)
        assertEquals(0, s.daysRemaining)
        assertEquals(3000.0, s.projectedMonthEndMxn, 0.001)
        assertEquals(CoverageStatus.UNLIKELY, s.status)
    }

    @Test
    fun `past month over threshold is COVERED`() {
        val rows = listOf(daily("UBER", "2026-05-10", 9000.0))
        val s = calc.statusForPlatform(
            "UBER", YearMonth.of(2026, 5), threshold, rows, emptyList(), LocalDate.of(2026, 6, 10),
        )
        assertEquals(CoverageStatus.COVERED, s.status)
    }

    @Test
    fun `future month projects zero with full month of days remaining`() {
        val s = calc.statusForPlatform(
            "UBER", YearMonth.of(2026, 8), threshold, emptyList(), emptyList(), LocalDate.of(2026, 6, 10),
        )
        assertEquals(MonthPhase.FUTURE, s.phase)
        assertEquals(31, s.daysRemaining)
        assertEquals(0.0, s.projectedMonthEndMxn, 0.001)
    }

    // ─── per-platform isolation (the reform is per platform, not a blended total) ────────────────

    @Test
    fun `statusesFor isolates platforms and never blends totals`() {
        // Combined $14,000 clears the bar, but neither platform does alone.
        val rows = listOf(
            daily("UBER", "2026-06-10", 7000.0),
            daily("DIDI", "2026-06-10", 7000.0),
        )
        val statuses = calc.statusesFor(
            YearMonth.of(2026, 6), threshold, rows, emptyList(), LocalDate.of(2026, 6, 30),
        )
        assertEquals(listOf("DIDI", "UBER"), statuses.map { it.platform }) // sorted
        statuses.forEach { s ->
            assertEquals(7000.0, s.netSoFarMxn, 0.001)
            // On day 30 of a 30-day month, run-rate 7000/30*30 = 7000 < threshold → UNLIKELY.
            assertEquals(CoverageStatus.UNLIKELY, s.status)
        }
    }

    @Test
    fun `statusesFor surfaces a platform that only appears in weekly inputs`() {
        val weekly = listOf(weekly("INDRIVE", "2026-06-08", 3500.0))
        val statuses = calc.statusesFor(
            YearMonth.of(2026, 6), threshold, emptyList(), weekly, LocalDate.of(2026, 6, 30),
        )
        assertEquals(listOf("INDRIVE"), statuses.map { it.platform })
    }

    @Test
    fun `empty inputs yield no statuses`() {
        val statuses = calc.statusesFor(
            YearMonth.of(2026, 6), threshold, emptyList(), emptyList(), LocalDate.of(2026, 6, 15),
        )
        assertTrue(statuses.isEmpty())
    }
}
