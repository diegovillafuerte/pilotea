package mx.kompara.metrics.fiscal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * Matrix for the pure [FiscalCalculator] (B-052): rate application against the regime table, the
 * missing-commission approximation + flag, YTD accumulation, month boundaries, and mixed
 * captured+imported months. Pure JVM — no Android.
 */
class FiscalCalculatorTest {

    private val calc = FiscalCalculator()
    private val rates = PlatformWithholdingRatesSnapshot.DEFAULT // 2.1% ISR, 8% IVA

    private fun row(platform: String, day: String, gross: Double, net: Double) =
        FiscalMonthInput(platform, day, gross, net)

    // ─── rates table ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `withholdings apply the regime rates to gross`() {
        val rows = listOf(row("UBER", "2026-06-10", gross = 10_000.0, net = 7_500.0))
        val summary = calc.summarize(YearMonth.of(2026, 6), rows, rates = rates)

        val uber = summary.platforms.single()
        assertEquals(10_000.0, uber.grossMxn, 0.001)
        assertEquals(7_500.0, uber.fiscalNetMxn, 0.001)
        assertEquals(2_500.0, uber.commissionMxn, 0.001)
        // 2.1% ISR + 8% IVA on $10,000 gross.
        assertEquals(210.0, uber.estimatedIsrMxn, 0.001)
        assertEquals(800.0, uber.estimatedIvaMxn, 0.001)
        assertEquals(1_010.0, uber.totalWithheldMxn, 0.001)
        assertFalse(uber.commissionApproximated)
    }

    @Test
    fun `rate constants are internally consistent`() {
        // 8% IVA withholding == 50% of the 16% standard IVA — the regime relationship.
        assertEquals(
            PlatformWithholdingRates.IVA_RATE_TRANSPORT,
            PlatformWithholdingRates.STANDARD_IVA_RATE * PlatformWithholdingRates.IVA_WITHHOLDING_FRACTION,
            0.0001,
        )
        assertEquals(0.021, PlatformWithholdingRates.ISR_RATE_TRANSPORT, 0.0)
    }

    // ─── missing commission (DiDi-style) ─────────────────────────────────────────────────────────

    @Test
    fun `missing net split flags commission as approximated and uses gross as net`() {
        // net == 0 (only gross known) ⇒ no usable split ⇒ fiscal net = gross, commission = 0, flagged.
        val rows = listOf(row("DIDI", "2026-06-10", gross = 8_000.0, net = 0.0))
        val summary = calc.summarize(YearMonth.of(2026, 6), rows, rates = rates)

        val didi = summary.platforms.single()
        assertTrue(didi.commissionApproximated)
        assertEquals(8_000.0, didi.fiscalNetMxn, 0.001)
        assertEquals(0.0, didi.commissionMxn, 0.001)
        // Withholdings still computed on gross regardless of the net approximation.
        assertEquals(168.0, didi.estimatedIsrMxn, 0.001) // 2.1% of 8000
        assertEquals(640.0, didi.estimatedIvaMxn, 0.001) // 8% of 8000
        assertTrue(summary.anyCommissionApproximated)
    }

    @Test
    fun `net above gross is treated as no usable split (flagged)`() {
        val rows = listOf(row("DIDI", "2026-06-10", gross = 5_000.0, net = 9_999.0))
        val summary = calc.summarize(YearMonth.of(2026, 6), rows, rates = rates)
        val didi = summary.platforms.single()
        assertTrue(didi.commissionApproximated)
        assertEquals(5_000.0, didi.fiscalNetMxn, 0.001)
    }

    @Test
    fun `one bad row in a platform flags the whole platform's net`() {
        // A platform with one good day and one gross-only day: the platform's net is an approximation.
        val rows = listOf(
            row("DIDI", "2026-06-05", gross = 4_000.0, net = 3_000.0),
            row("DIDI", "2026-06-12", gross = 4_000.0, net = 0.0),
        )
        val summary = calc.summarize(YearMonth.of(2026, 6), rows, rates = rates)
        val didi = summary.platforms.single()
        assertTrue(didi.commissionApproximated)
        assertEquals(8_000.0, didi.grossMxn, 0.001)
        assertEquals(8_000.0, didi.fiscalNetMxn, 0.001) // falls back to gross
    }

    // ─── per-platform isolation + ordering ───────────────────────────────────────────────────────

    @Test
    fun `platforms are summarized independently and sorted by name`() {
        val rows = listOf(
            row("UBER", "2026-06-10", 10_000.0, 7_500.0),
            row("DIDI", "2026-06-11", 6_000.0, 4_500.0),
        )
        val summary = calc.summarize(YearMonth.of(2026, 6), rows, rates = rates)
        assertEquals(listOf("DIDI", "UBER"), summary.platforms.map { it.platform })

        // Month totals fold both platforms.
        assertEquals(16_000.0, summary.monthTotals.grossMxn, 0.001)
        assertEquals(12_000.0, summary.monthTotals.fiscalNetMxn, 0.001)
        assertEquals(16_000.0 * 0.021, summary.monthTotals.estimatedIsrMxn, 0.001)
        assertEquals(16_000.0 * 0.08, summary.monthTotals.estimatedIvaMxn, 0.001)
    }

    // ─── month boundaries ──────────────────────────────────────────────────────────────────────

    @Test
    fun `rows outside the target month are excluded`() {
        val rows = listOf(
            row("UBER", "2026-05-31", 9_999.0, 7_000.0), // previous month
            row("UBER", "2026-06-01", 1_000.0, 800.0), // in month (first day)
            row("UBER", "2026-06-30", 2_000.0, 1_500.0), // in month (last day)
            row("UBER", "2026-07-01", 9_999.0, 7_000.0), // next month
        )
        val summary = calc.summarize(YearMonth.of(2026, 6), rows, rates = rates)
        assertEquals(3_000.0, summary.platforms.single().grossMxn, 0.001)
    }

    @Test
    fun `empty month yields an empty summary`() {
        val summary = calc.summarize(YearMonth.of(2026, 6), emptyList(), rates = rates)
        assertTrue(summary.isEmpty)
        assertEquals(0.0, summary.monthTotals.grossMxn, 0.001)
        assertEquals("2026-06", summary.month)
    }

    @Test
    fun `malformed day strings are ignored, not crashed`() {
        val rows = listOf(
            row("UBER", "not-a-date", 5_000.0, 4_000.0),
            row("UBER", "2026-06-10", 3_000.0, 2_400.0),
        )
        val summary = calc.summarize(YearMonth.of(2026, 6), rows, rates = rates)
        assertEquals(3_000.0, summary.platforms.single().grossMxn, 0.001)
    }

    // ─── YTD accumulation ─────────────────────────────────────────────────────────────────────

    @Test
    fun `ytd sums January through the target month inclusive`() {
        val ytdRows = listOf(
            row("UBER", "2026-01-15", 10_000.0, 8_000.0),
            row("UBER", "2026-03-15", 10_000.0, 8_000.0),
            row("UBER", "2026-06-15", 10_000.0, 8_000.0), // target month
        )
        val summary = calc.summarize(
            month = YearMonth.of(2026, 6),
            rows = ytdRows.filter { it.day.startsWith("2026-06") },
            ytdRows = ytdRows,
            rates = rates,
        )
        // YTD gross = 30,000 (Jan + Mar + Jun).
        assertEquals(30_000.0, summary.ytdTotals.grossMxn, 0.001)
        assertEquals(30_000.0 * 0.021, summary.ytdTotals.estimatedIsrMxn, 0.001)
        assertEquals(30_000.0 * 0.08, summary.ytdTotals.estimatedIvaMxn, 0.001)
        // Month totals only the target month.
        assertEquals(10_000.0, summary.monthTotals.grossMxn, 0.001)
    }

    @Test
    fun `ytd excludes months after the target and prior years`() {
        val ytdRows = listOf(
            row("UBER", "2025-12-15", 10_000.0, 8_000.0), // prior year — excluded
            row("UBER", "2026-04-15", 10_000.0, 8_000.0), // in window
            row("UBER", "2026-09-15", 10_000.0, 8_000.0), // after target (June) — excluded
        )
        val summary = calc.summarize(
            month = YearMonth.of(2026, 6),
            rows = emptyList(),
            ytdRows = ytdRows,
            rates = rates,
        )
        assertEquals(10_000.0, summary.ytdTotals.grossMxn, 0.001)
    }

    // ─── mixed captured + imported months ──────────────────────────────────────────────────────

    @Test
    fun `mixed captured and imported rows merge into one platform line`() {
        // Imported row (full net split) + captured row (net split too) for the same platform/month.
        val rows = listOf(
            row("UBER", "2026-06-03", 5_000.0, 3_800.0),
            row("UBER", "2026-06-20", 4_000.0, 3_000.0),
        )
        val summary = calc.summarize(YearMonth.of(2026, 6), rows, rates = rates)
        val uber = summary.platforms.single()
        assertEquals(9_000.0, uber.grossMxn, 0.001)
        assertEquals(6_800.0, uber.fiscalNetMxn, 0.001)
        assertFalse(uber.commissionApproximated)
    }

    @Test
    fun `custom rate snapshot is honored`() {
        val custom = PlatformWithholdingRatesSnapshot(isrRate = 0.01, ivaRate = 0.16, year = 2030)
        val rows = listOf(row("UBER", "2026-06-10", 10_000.0, 8_000.0))
        val summary = calc.summarize(YearMonth.of(2026, 6), rows, rates = custom)
        assertEquals(100.0, summary.platforms.single().estimatedIsrMxn, 0.001)
        assertEquals(1_600.0, summary.platforms.single().estimatedIvaMxn, 0.001)
        assertEquals(2030, summary.ratesYear)
    }
}
