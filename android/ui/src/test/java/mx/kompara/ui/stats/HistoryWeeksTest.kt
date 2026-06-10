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

    // ── B-050 free-tier truncation ───────────────────────────────────────────

    private fun weeksList(vararg weekStarts: String): List<HistoryWeek> =
        weekStarts.map { ws ->
            HistoryWeek(weekStart = ws, source = WeekSourceBadge.CAPTURADO, period = PeriodStats.EMPTY)
        }

    @Test
    fun `unlocked partition keeps every week visible and locks nothing`() {
        val weeks = weeksList("2026-06-15", "2026-06-08", "2026-06-01", "2026-05-25")
        val p = HistoryWeeks.partition(weeks, locked = false)
        assertEquals(4, p.visible.size)
        assertEquals(0, p.locked.size)
        assertEquals(false, p.hasLocked)
    }

    @Test
    fun `locked partition shows the two newest weeks free and gates the rest`() {
        val weeks = weeksList("2026-06-15", "2026-06-08", "2026-06-01", "2026-05-25")
        val p = HistoryWeeks.partition(weeks, locked = true)
        assertEquals(listOf("2026-06-15", "2026-06-08"), p.visible.map { it.weekStart })
        assertEquals(listOf("2026-06-01", "2026-05-25"), p.locked.map { it.weekStart })
        assertEquals(true, p.hasLocked)
    }

    @Test
    fun `locked partition with two or fewer weeks gates nothing`() {
        val p = HistoryWeeks.partition(weeksList("2026-06-15", "2026-06-08"), locked = true)
        assertEquals(2, p.visible.size)
        assertEquals(0, p.locked.size)
    }

    @Test
    fun `locked partition newest-first regardless of input order`() {
        val weeks = weeksList("2026-05-25", "2026-06-15", "2026-06-01", "2026-06-08")
        val p = HistoryWeeks.partition(weeks, locked = true)
        assertEquals(listOf("2026-06-15", "2026-06-08"), p.visible.map { it.weekStart })
        assertEquals(listOf("2026-06-01", "2026-05-25"), p.locked.map { it.weekStart })
    }
}
