package mx.kompara.ui.stats

import mx.kompara.data.db.entity.AggregateSource
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform
import mx.kompara.metrics.compare.CompareMetric
import mx.kompara.metrics.compare.CompareWinner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure [CompareState] mode selection (B-047): 0/1/2/3-platform branching, the automatic vs.
 * chip-driven pair, imported-over-captured preference, and the available-weeks list for the picker.
 */
class CompareStateTest {

    private fun weekly(
        platform: Platform,
        weekStart: String,
        source: AggregateSource = AggregateSource.CAPTURED,
        net: Double = 1000.0,
        perKm: Double? = 9.0,
        perHour: Double? = 180.0,
        perTrip: Double? = 55.0,
        trips: Int = 40,
        acceptance: Double? = 0.8,
    ) = WeeklyAggregateEntity(
        platform = platform.name,
        weekStart = weekStart,
        source = source.name,
        netEarningsMxn = net,
        grossEarningsMxn = net * 1.3,
        totalTrips = trips,
        totalKm = 200.0,
        hoursOnline = 30.0,
        earningsPerTrip = perTrip,
        earningsPerKm = perKm,
        earningsPerHour = perHour,
        tripsPerHour = 1.3,
        acceptanceRate = acceptance,
        computedAt = 0L,
    )

    private val week = "2026-06-08"

    @Test
    fun `zero platforms is the empty state`() {
        val data = CompareState.forWeek(emptyList(), week)
        assertEquals(CompareMode.Empty, data.mode)
    }

    @Test
    fun `one platform is the single-platform state with that platform`() {
        val data = CompareState.forWeek(listOf(weekly(Platform.UBER, week)), week)
        val mode = data.mode as CompareMode.SinglePlatform
        assertEquals(Platform.UBER, mode.platform)
    }

    @Test
    fun `two platforms compare automatically with no chips`() {
        val data = CompareState.forWeek(
            listOf(
                weekly(Platform.UBER, week, perKm = 9.0),
                weekly(Platform.DIDI, week, perKm = 10.0),
            ),
            week,
        )
        val mode = data.mode as CompareMode.Comparison
        assertEquals(Platform.UBER, mode.platformA)
        assertEquals(Platform.DIDI, mode.platformB)
        assertFalse(mode.showsChips)
        // DiDi wins per-km.
        val r = mode.result.rows.first { it.metric == CompareMetric.EARNINGS_PER_KM }
        assertEquals(CompareWinner.B, r.winner)
    }

    @Test
    fun `three platforms show chips and default to the first two`() {
        val data = CompareState.forWeek(
            listOf(
                weekly(Platform.UBER, week),
                weekly(Platform.DIDI, week),
                weekly(Platform.INDRIVE, week),
            ),
            week,
        )
        val mode = data.mode as CompareMode.Comparison
        assertTrue(mode.showsChips)
        assertEquals(3, mode.platforms.size)
        // Default pair = first two in declaration order.
        assertEquals(Platform.UBER, mode.platformA)
        assertEquals(Platform.DIDI, mode.platformB)
    }

    @Test
    fun `three platforms honor the selected pair`() {
        val rows = listOf(
            weekly(Platform.UBER, week),
            weekly(Platform.DIDI, week),
            weekly(Platform.INDRIVE, week),
        )
        val data = CompareState.forWeek(rows, week, selectedPair = Platform.UBER to Platform.INDRIVE)
        val mode = data.mode as CompareMode.Comparison
        assertEquals(Platform.UBER, mode.platformA)
        assertEquals(Platform.INDRIVE, mode.platformB)
    }

    @Test
    fun `an invalid selected pair falls back to the first two`() {
        val rows = listOf(
            weekly(Platform.UBER, week),
            weekly(Platform.DIDI, week),
        )
        // INDRIVE has no data this week → invalid selection → fall back.
        val data = CompareState.forWeek(rows, week, selectedPair = Platform.UBER to Platform.INDRIVE)
        val mode = data.mode as CompareMode.Comparison
        assertEquals(Platform.UBER, mode.platformA)
        assertEquals(Platform.DIDI, mode.platformB)
    }

    @Test
    fun `imported figures win over captured for the same platform-week`() {
        val rows = listOf(
            weekly(Platform.UBER, week, source = AggregateSource.CAPTURED, perKm = 9.0),
            weekly(Platform.UBER, week, source = AggregateSource.IMPORTED, perKm = 12.0),
            weekly(Platform.DIDI, week, source = AggregateSource.CAPTURED, perKm = 10.0),
        )
        val mode = CompareState.forWeek(rows, week).mode as CompareMode.Comparison
        // Uber's imported 12.0 beats DiDi's 10.0 (would have lost on the captured 9.0).
        val r = mode.result.rows.first { it.metric == CompareMetric.EARNINGS_PER_KM }
        assertEquals(CompareWinner.A, r.winner)
    }

    @Test
    fun `a one-sided null metric is not comparable`() {
        val rows = listOf(
            weekly(Platform.UBER, week, perHour = 180.0),
            weekly(Platform.INDRIVE, week, perHour = null),
        )
        val mode = CompareState.forWeek(rows, week).mode as CompareMode.Comparison
        val r = mode.result.rows.first { it.metric == CompareMetric.EARNINGS_PER_HOUR }
        assertFalse(r.comparable)
        assertEquals("INDRIVE", r.missingPlatform)
    }

    @Test
    fun `available weeks lists every week with data newest first`() {
        val rows = listOf(
            weekly(Platform.UBER, "2026-06-01"),
            weekly(Platform.DIDI, "2026-06-08"),
            weekly(Platform.UBER, "2026-06-08"),
        )
        assertEquals(listOf("2026-06-08", "2026-06-01"), CompareState.availableWeeks(rows))
    }

    @Test
    fun `switching weeks compares the rows of the chosen week`() {
        val rows = listOf(
            // Last week: only Uber → single.
            weekly(Platform.UBER, "2026-06-01"),
            // This week: Uber + DiDi → comparison.
            weekly(Platform.UBER, "2026-06-08"),
            weekly(Platform.DIDI, "2026-06-08"),
        )
        assertTrue(CompareState.forWeek(rows, "2026-06-08").mode is CompareMode.Comparison)
        assertTrue(CompareState.forWeek(rows, "2026-06-01").mode is CompareMode.SinglePlatform)
    }
}
