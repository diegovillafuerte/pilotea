package mx.kompara.metrics.compare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The [CompareCalculator] decision matrix (B-047): winners per metric, the commission inversion,
 * no-comparable handling for one-sided nulls, the loser-relative percent maths, and the verdict-line
 * metric selection.
 */
class CompareCalculatorTest {

    private fun row(result: CompareResult, metric: CompareMetric): CompareRow =
        result.rows.first { it.metric == metric }

    // ─── Winners (higher wins) ──────────────────────────────────────────────────────────────────

    @Test
    fun `higher value wins for a normal metric`() {
        val result = CompareCalculator.compare(
            PlatformMetrics.of("UBER", earningsPerKm = 8.0),
            PlatformMetrics.of("DIDI", earningsPerKm = 10.0),
        )
        val r = row(result, CompareMetric.EARNINGS_PER_KM)
        assertTrue(r.comparable)
        assertEquals(CompareWinner.B, r.winner)
    }

    @Test
    fun `platform A wins when it is higher`() {
        val result = CompareCalculator.compare(
            PlatformMetrics.of("UBER", earningsPerHour = 200.0),
            PlatformMetrics.of("DIDI", earningsPerHour = 150.0),
        )
        assertEquals(CompareWinner.A, row(result, CompareMetric.EARNINGS_PER_HOUR).winner)
    }

    // ─── Commission inversion (lower wins) ──────────────────────────────────────────────────────

    @Test
    fun `commission inverts — the lower cut wins`() {
        val result = CompareCalculator.compare(
            PlatformMetrics.of("UBER", commissionPct = 0.25),
            PlatformMetrics.of("DIDI", commissionPct = 0.20),
        )
        val r = row(result, CompareMetric.COMMISSION_PCT)
        assertTrue(r.comparable)
        // DiDi's 20 % cut is better than Uber's 25 %.
        assertEquals(CompareWinner.B, r.winner)
    }

    @Test
    fun `commission inversion also favors platform A when it is lower`() {
        val result = CompareCalculator.compare(
            PlatformMetrics.of("UBER", commissionPct = 0.18),
            PlatformMetrics.of("DIDI", commissionPct = 0.30),
        )
        assertEquals(CompareWinner.A, row(result, CompareMetric.COMMISSION_PCT).winner)
    }

    @Test
    fun `commission inversion is exercised via the metric flag, not a name`() {
        assertTrue(CompareMetric.COMMISSION_PCT.lowerIsBetter)
        assertFalse(CompareMetric.EARNINGS_PER_KM.lowerIsBetter)
    }

    // ─── Ties ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `equal values are a tie with zero pct difference`() {
        val result = CompareCalculator.compare(
            PlatformMetrics.of("UBER", earningsPerTrip = 55.0),
            PlatformMetrics.of("DIDI", earningsPerTrip = 55.0),
        )
        val r = row(result, CompareMetric.EARNINGS_PER_TRIP)
        assertEquals(CompareWinner.TIE, r.winner)
        assertEquals(0.0, r.pctDifference!!, 1e-9)
    }

    @Test
    fun `values within epsilon are a tie`() {
        val result = CompareCalculator.compare(
            PlatformMetrics.of("UBER", earningsPerKm = 8.0),
            PlatformMetrics.of("DIDI", earningsPerKm = 8.0 + 1e-12),
        )
        assertEquals(CompareWinner.TIE, row(result, CompareMetric.EARNINGS_PER_KM).winner)
    }

    // ─── No comparable (one-sided nulls) ─────────────────────────────────────────────────────────

    @Test
    fun `a one-sided null is not comparable and names the missing platform`() {
        // inDrive doesn't report hours.
        val result = CompareCalculator.compare(
            PlatformMetrics.of("UBER", earningsPerHour = 180.0),
            PlatformMetrics.of("INDRIVE", earningsPerHour = null),
        )
        val r = row(result, CompareMetric.EARNINGS_PER_HOUR)
        assertFalse(r.comparable)
        assertNull(r.winner)
        assertNull(r.pctDifference)
        assertEquals("INDRIVE", r.missingPlatform)
    }

    @Test
    fun `a missing value on platform A names A as missing`() {
        val result = CompareCalculator.compare(
            PlatformMetrics.of("INDRIVE", earningsPerHour = null),
            PlatformMetrics.of("UBER", earningsPerHour = 180.0),
        )
        val r = row(result, CompareMetric.EARNINGS_PER_HOUR)
        assertFalse(r.comparable)
        assertEquals("INDRIVE", r.missingPlatform)
    }

    @Test
    fun `both null is not comparable with no missing platform singled out`() {
        val result = CompareCalculator.compare(
            PlatformMetrics.of("UBER", earningsPerHour = null),
            PlatformMetrics.of("DIDI", earningsPerHour = null),
        )
        val r = row(result, CompareMetric.EARNINGS_PER_HOUR)
        assertFalse(r.comparable)
        assertNull(r.missingPlatform)
    }

    // ─── Percent difference (relative to the loser) ───────────────────────────────────────────────

    @Test
    fun `pct difference is relative to the loser`() {
        // 10 vs 8: winner leads by (10-8)/8 = 25 %.
        val result = CompareCalculator.compare(
            PlatformMetrics.of("UBER", earningsPerKm = 8.0),
            PlatformMetrics.of("DIDI", earningsPerKm = 10.0),
        )
        assertEquals(0.25, row(result, CompareMetric.EARNINGS_PER_KM).pctDifference!!, 1e-9)
    }

    @Test
    fun `pct difference of the example — 12 percent more per km`() {
        // DiDi 11.2 vs Uber 10.0 → (11.2-10)/10 = 12 %.
        val result = CompareCalculator.compare(
            PlatformMetrics.of("UBER", earningsPerKm = 10.0),
            PlatformMetrics.of("DIDI", earningsPerKm = 11.2),
        )
        val r = row(result, CompareMetric.EARNINGS_PER_KM)
        assertEquals(CompareWinner.B, r.winner)
        assertEquals(0.12, r.pctDifference!!, 1e-9)
    }

    @Test
    fun `pct difference is undefined when the loser is zero`() {
        val result = CompareCalculator.compare(
            PlatformMetrics.of("UBER", netEarnings = 0.0),
            PlatformMetrics.of("DIDI", netEarnings = 500.0),
        )
        val r = row(result, CompareMetric.NET_EARNINGS)
        assertTrue(r.comparable)
        assertEquals(CompareWinner.B, r.winner) // any positive beats zero
        assertNull(r.pctDifference) // (500-0)/0 undefined
    }

    // ─── Verdict-line metric selection ─────────────────────────────────────────────────────────────

    @Test
    fun `verdict prefers per-km when available`() {
        val result = CompareCalculator.compare(
            PlatformMetrics.of("UBER", earningsPerKm = 10.0, earningsPerHour = 200.0, earningsPerTrip = 50.0),
            PlatformMetrics.of("DIDI", earningsPerKm = 11.2, earningsPerHour = 150.0, earningsPerTrip = 60.0),
        )
        val v = result.verdict!!
        assertEquals(CompareMetric.EARNINGS_PER_KM, v.metric)
        assertEquals("DIDI", v.winnerPlatform)
        assertEquals("UBER", v.loserPlatform)
        assertEquals(0.12, v.pctDifference!!, 1e-9)
    }

    @Test
    fun `verdict falls back to per-hour when per-km is not comparable`() {
        val result = CompareCalculator.compare(
            PlatformMetrics.of("UBER", earningsPerKm = null, earningsPerHour = 200.0, earningsPerTrip = 50.0),
            PlatformMetrics.of("DIDI", earningsPerKm = 12.0, earningsPerHour = 150.0, earningsPerTrip = 60.0),
        )
        val verdict = result.verdict!!
        assertEquals(CompareMetric.EARNINGS_PER_HOUR, verdict.metric)
        assertEquals("UBER", verdict.winnerPlatform)
    }

    @Test
    fun `verdict falls back to per-trip when neither per-km nor per-hour compare`() {
        val result = CompareCalculator.compare(
            PlatformMetrics.of("UBER", earningsPerKm = null, earningsPerHour = null, earningsPerTrip = 50.0),
            PlatformMetrics.of("DIDI", earningsPerKm = 12.0, earningsPerHour = null, earningsPerTrip = 60.0),
        )
        assertEquals(CompareMetric.EARNINGS_PER_TRIP, result.verdict!!.metric)
    }

    @Test
    fun `no verdict when none of the three rate metrics compare`() {
        // Only volume metrics compare; the rate metrics are all one-sided.
        val result = CompareCalculator.compare(
            PlatformMetrics.of("UBER", netEarnings = 1000.0, totalTrips = 40),
            PlatformMetrics.of("DIDI", netEarnings = 1200.0, totalTrips = 45),
        )
        assertNull(result.verdict)
        assertTrue(result.hasComparable) // net + trips still compared
    }

    @Test
    fun `verdict reports a tie with no winner platform`() {
        val result = CompareCalculator.compare(
            PlatformMetrics.of("UBER", earningsPerKm = 9.0),
            PlatformMetrics.of("DIDI", earningsPerKm = 9.0),
        )
        val v = result.verdict!!
        assertEquals(CompareWinner.TIE, v.winner)
        assertNull(v.winnerPlatform)
        assertNull(v.loserPlatform)
    }

    // ─── Result shape ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `result carries a row for every metric in declaration order`() {
        val result = CompareCalculator.compare(
            PlatformMetrics.of("UBER", earningsPerKm = 8.0),
            PlatformMetrics.of("DIDI", earningsPerKm = 10.0),
        )
        assertEquals(CompareMetric.entries.size, result.rows.size)
        assertEquals(CompareMetric.entries.toList(), result.rows.map { it.metric })
    }

    @Test
    fun `comparableRows excludes the one-sided rows`() {
        val result = CompareCalculator.compare(
            PlatformMetrics.of("UBER", earningsPerKm = 8.0, earningsPerHour = 180.0),
            PlatformMetrics.of("INDRIVE", earningsPerKm = 9.0, earningsPerHour = null),
        )
        assertEquals(1, result.comparableRows.size)
        assertEquals(CompareMetric.EARNINGS_PER_KM, result.comparableRows.first().metric)
        assertNotNull(result.verdict)
    }

    @Test
    fun `hasComparable is false when nothing lines up`() {
        val result = CompareCalculator.compare(
            PlatformMetrics.of("UBER", earningsPerKm = 8.0),
            PlatformMetrics.of("DIDI", earningsPerHour = 180.0),
        )
        assertFalse(result.hasComparable)
        assertNull(result.verdict)
    }
}
