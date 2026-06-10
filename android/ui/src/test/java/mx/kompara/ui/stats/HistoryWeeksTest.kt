package mx.kompara.ui.stats

import mx.kompara.data.db.entity.AggregateSource
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test

/** History weeks folding + source badge precedence for [HistoryWeeks] (B-040 req 3). */
class HistoryWeeksTest {

    private fun row(
        weekStart: String,
        platform: Platform,
        source: AggregateSource,
        net: Double,
        trips: Int = 1,
    ) = WeeklyAggregateEntity(
        platform = platform.name,
        weekStart = weekStart,
        source = source.name,
        netEarningsMxn = net,
        grossEarningsMxn = net + 50,
        totalTrips = trips,
        totalKm = 10.0,
        hoursOnline = 1.0,
        earningsPerTrip = net / trips,
        earningsPerKm = net / 10.0,
        earningsPerHour = net,
        tripsPerHour = trips.toDouble(),
        acceptanceRate = null,
        computedAt = 0L,
    )

    @Test
    fun `weeks sorted newest first, summed across platforms`() {
        val rows = listOf(
            row("2026-06-01", Platform.UBER, AggregateSource.CAPTURED, net = 800.0),
            row("2026-06-08", Platform.UBER, AggregateSource.CAPTURED, net = 1000.0),
            row("2026-06-08", Platform.DIDI, AggregateSource.CAPTURED, net = 500.0),
        )
        val weeks = HistoryWeeks.build(rows)
        assertEquals(2, weeks.size)
        assertEquals("2026-06-08", weeks[0].weekStart)
        assertEquals(1500.0, weeks[0].period.netEarningsMxn, 0.001) // summed
        assertEquals(WeekSourceBadge.CAPTURADO, weeks[0].source)
        assertEquals("2026-06-01", weeks[1].weekStart)
    }

    @Test
    fun `imported rows win the badge and the summary for a mixed week`() {
        val rows = listOf(
            row("2026-06-08", Platform.UBER, AggregateSource.CAPTURED, net = 1000.0),
            row("2026-06-08", Platform.UBER, AggregateSource.IMPORTED, net = 1234.0),
        )
        val weeks = HistoryWeeks.build(rows)
        assertEquals(1, weeks.size)
        assertEquals(WeekSourceBadge.IMPORTADO, weeks[0].source)
        assertEquals(1234.0, weeks[0].period.netEarningsMxn, 0.001) // imported preferred
    }
}
