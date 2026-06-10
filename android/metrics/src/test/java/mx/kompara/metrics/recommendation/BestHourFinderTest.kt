package mx.kompara.metrics.recommendation

import mx.kompara.data.db.entity.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * [BestHourFinder] buckets a week's trips by (day-of-week, hour) and names the most lucrative block
 * (B-048). Pure → tested against hand-built epoch timestamps in a fixed UTC zone.
 */
class BestHourFinderTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val finder = BestHourFinder(zone, marginalCostPerKm = 2.0)

    /** A trip starting at [dateTime] (UTC), [km] long, grossing [gross]. Completed unless [open]. */
    private fun trip(dateTime: LocalDateTime, gross: Double, km: Double, open: Boolean = false): TripEntity {
        val start = dateTime.toInstant(ZoneOffset.UTC).toEpochMilli()
        return TripEntity(
            startedAt = start,
            endedAt = if (open) null else start + 600_000,
            platform = "UBER",
            grossMxn = gross,
            distanceKm = km,
            durationMin = 10.0,
        )
    }

    @Test
    fun `returns null when there are no completed trips`() {
        assertNull(finder.best(emptyList()))
        assertNull(finder.best(listOf(trip(LocalDateTime.of(2026, 6, 5, 19, 0), 100.0, 5.0, open = true))))
    }

    @Test
    fun `picks the highest-net day-of-week and hour block, netting marginal cost`() {
        // 2026-06-05 is a Friday (ISO dow 5); 2026-06-06 a Saturday (dow 6).
        val trips = listOf(
            // Friday 19:00 block: two trips, gross 100 + 120 = 220, km 5 + 5 = 10 → net 220 - 20 = 200.
            trip(LocalDateTime.of(2026, 6, 5, 19, 10), 100.0, 5.0),
            trip(LocalDateTime.of(2026, 6, 5, 19, 40), 120.0, 5.0),
            // Saturday 14:00 block: one trip gross 90, km 3 → net 90 - 6 = 84.
            trip(LocalDateTime.of(2026, 6, 6, 14, 0), 90.0, 3.0),
        )
        val best = finder.best(trips)
        assertNotNull(best)
        assertEquals(5, best!!.dayOfWeek)
        assertEquals(19, best.hour)
        assertEquals(2, best.tripCount)
        assertEquals(200.0, best.netMxn, 0.001)
    }

    @Test
    fun `buckets the same hour on different days separately`() {
        val trips = listOf(
            trip(LocalDateTime.of(2026, 6, 1, 18, 0), 80.0, 2.0), // Monday 18:00
            trip(LocalDateTime.of(2026, 6, 2, 18, 0), 200.0, 2.0), // Tuesday 18:00 — higher
        )
        val best = finder.best(trips)
        assertNotNull(best)
        assertEquals(2, best!!.dayOfWeek) // Tuesday wins
        assertEquals(18, best.hour)
        assertEquals(1, best.tripCount)
    }
}
