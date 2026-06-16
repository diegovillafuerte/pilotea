package mx.kompara.ui.stats

import mx.kompara.data.db.entity.AggregateSource
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pure week/row helpers behind [CompararViewModel] (S-024): the available-weeks list for the
 * dropdown and the IMPORTED-over-CAPTURED preference when folding a week's rows.
 */
class CompareStateTest {

    private fun weekly(
        platform: Platform,
        weekStart: String,
        source: AggregateSource = AggregateSource.CAPTURED,
        net: Double = 1000.0,
        perKm: Double? = 9.0,
    ) = WeeklyAggregateEntity(
        platform = platform.name,
        weekStart = weekStart,
        source = source.name,
        netEarningsMxn = net,
        grossEarningsMxn = net * 1.3,
        totalTrips = 40,
        totalKm = 200.0,
        hoursOnline = 30.0,
        earningsPerTrip = 55.0,
        earningsPerKm = perKm,
        earningsPerHour = 180.0,
        tripsPerHour = 1.3,
        acceptanceRate = 0.8,
        computedAt = 0L,
    )

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
    fun `rowsForWeek returns one row per platform for the chosen week`() {
        val rows = listOf(
            weekly(Platform.UBER, "2026-06-08"),
            weekly(Platform.DIDI, "2026-06-08"),
            weekly(Platform.UBER, "2026-06-01"),
        )
        val week = CompareState.rowsForWeek(rows, "2026-06-08")
        assertEquals(setOf("UBER", "DIDI"), week.map { it.platform }.toSet())
        assertEquals(2, week.size)
    }

    @Test
    fun `rowsForWeek prefers imported over captured for the same platform-week`() {
        val rows = listOf(
            weekly(Platform.UBER, "2026-06-08", source = AggregateSource.CAPTURED, perKm = 9.0),
            weekly(Platform.UBER, "2026-06-08", source = AggregateSource.IMPORTED, perKm = 12.0),
        )
        val uber = CompareState.rowsForWeek(rows, "2026-06-08").single()
        assertEquals(AggregateSource.IMPORTED.name, uber.source)
        assertEquals(12.0, uber.earningsPerKm!!, 1e-9)
    }
}
