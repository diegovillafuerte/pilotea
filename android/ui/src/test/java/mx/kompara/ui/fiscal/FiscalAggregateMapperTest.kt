package mx.kompara.ui.fiscal

import mx.kompara.data.db.entity.DailyAggregateEntity
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * Pure mapping of Room rollup rows into the calculator's [mx.kompara.metrics.fiscal.FiscalMonthInput]s
 * (B-052): daily-wins, weekly pro-rate of the in-month uncovered days (mirrors B-051's month-net rule).
 */
class FiscalAggregateMapperTest {

    private fun daily(platform: String, day: String, gross: Double, net: Double) = DailyAggregateEntity(
        platform = platform, day = day, source = "CAPTURED",
        netEarningsMxn = net, grossEarningsMxn = gross, totalTrips = 0, totalKm = 0.0, hoursOnline = 0.0,
        earningsPerTrip = null, earningsPerKm = null, earningsPerHour = null, tripsPerHour = null,
        acceptanceRate = null, computedAt = 0L,
    )

    private fun weekly(platform: String, weekStart: String, gross: Double, net: Double) = WeeklyAggregateEntity(
        platform = platform, weekStart = weekStart, source = "IMPORTED",
        netEarningsMxn = net, grossEarningsMxn = gross, totalTrips = 0, totalKm = 0.0, hoursOnline = 0.0,
        earningsPerTrip = null, earningsPerKm = null, earningsPerHour = null, tripsPerHour = null,
        acceptanceRate = null, computedAt = 0L,
    )

    @Test
    fun `daily rows in month map one-to-one`() {
        val inputs = FiscalAggregateMapper.toInputs(
            YearMonth.of(2026, 6),
            daily = listOf(
                daily("UBER", "2026-06-10", 1000.0, 800.0),
                daily("UBER", "2026-05-31", 9999.0, 8000.0), // out of month
            ),
            weekly = emptyList(),
        )
        assertEquals(1, inputs.size)
        assertEquals(1000.0, inputs.single().grossMxn, 0.001)
        assertEquals(800.0, inputs.single().netMxn, 0.001)
    }

    @Test
    fun `weekly with no daily coverage pro-rates the in-month days`() {
        // Week of Mon 2026-06-29 spans Jun 29,30 + Jul 1..5. For June: 2 of 7 days in-month.
        val inputs = FiscalAggregateMapper.toInputs(
            YearMonth.of(2026, 6),
            daily = emptyList(),
            weekly = listOf(weekly("UBER", "2026-06-29", gross = 7000.0, net = 5600.0)),
        )
        assertEquals(1, inputs.size)
        // 2/7 of the week falls in June.
        assertEquals(7000.0 * 2.0 / 7.0, inputs.single().grossMxn, 0.001)
        assertEquals(5600.0 * 2.0 / 7.0, inputs.single().netMxn, 0.001)
    }

    @Test
    fun `daily coverage suppresses the weekly pro-rate for the same days`() {
        // Same straddling week, but Jun 29 has a daily row → only Jun 30 is uncovered → 1/7 pro-rate.
        val inputs = FiscalAggregateMapper.toInputs(
            YearMonth.of(2026, 6),
            daily = listOf(daily("UBER", "2026-06-29", 1000.0, 800.0)),
            weekly = listOf(weekly("UBER", "2026-06-29", gross = 7000.0, net = 5600.0)),
        )
        // One daily (1000) + one pro-rated weekly (1/7 of 7000 = 1000) = 2000 gross total.
        val gross = inputs.sumOf { it.grossMxn }
        assertEquals(2000.0, gross, 0.001)
    }

    @Test
    fun `a fully-covered week contributes nothing from the weekly row`() {
        val inputs = FiscalAggregateMapper.toInputs(
            YearMonth.of(2026, 6),
            daily = listOf(
                daily("UBER", "2026-06-01", 100.0, 80.0),
                daily("UBER", "2026-06-02", 100.0, 80.0),
                daily("UBER", "2026-06-03", 100.0, 80.0),
                daily("UBER", "2026-06-04", 100.0, 80.0),
                daily("UBER", "2026-06-05", 100.0, 80.0),
                daily("UBER", "2026-06-06", 100.0, 80.0),
                daily("UBER", "2026-06-07", 100.0, 80.0),
            ),
            weekly = listOf(weekly("UBER", "2026-06-01", gross = 9999.0, net = 8000.0)),
        )
        // Only the 7 daily rows; the weekly is fully covered.
        assertEquals(7, inputs.size)
        assertEquals(700.0, inputs.sumOf { it.grossMxn }, 0.001)
    }

    @Test
    fun `malformed dates are skipped`() {
        val inputs = FiscalAggregateMapper.toInputs(
            YearMonth.of(2026, 6),
            daily = listOf(daily("UBER", "garbage", 5000.0, 4000.0)),
            weekly = listOf(weekly("UBER", "nope", 5000.0, 4000.0)),
        )
        assertTrue(inputs.isEmpty())
    }

    @Test
    fun `week range covers straddling weeks`() {
        // July 1 2026 is Wednesday → previous Monday is 2026-06-29.
        val (start, end) = FiscalAggregateMapper.weekRange(YearMonth.of(2026, 7))
        assertEquals("2026-06-29", start)
        assertEquals("2026-07-31", end)
    }
}
