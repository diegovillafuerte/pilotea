package mx.kompara.ui.stats

import mx.kompara.data.db.entity.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Best-hours bucketing for [BestHours] (B-040 req 2). UTC so hour mapping is unambiguous. */
class BestHoursTest {

    private val zone = ZoneOffset.UTC
    private val marginalCostPerKm = 2.0

    private fun at(h: Int, mi: Int = 0): Long =
        LocalDateTime.of(2026, 6, 10, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun trip(startHour: Int, gross: Double, km: Double, ended: Boolean = true) = TripEntity(
        startedAt = at(startHour),
        endedAt = if (ended) at(startHour, 30) else null,
        platform = "UBER",
        grossMxn = gross,
        distanceKm = km,
        durationMin = 30.0,
    )

    @Test
    fun `buckets trips by start hour and sums net`() {
        val trips = listOf(
            trip(18, gross = 100.0, km = 10.0), // net 100 - 20 = 80
            trip(18, gross = 60.0, km = 5.0), // net 60 - 10 = 50 → hour 18 total 130
            trip(9, gross = 80.0, km = 10.0), // net 80 - 20 = 60
        )
        val blocks = BestHours(zone, marginalCostPerKm).blocks(trips)
        assertEquals(2, blocks.size)
        // Sorted by net desc: hour 18 (130) first, then hour 9 (60).
        assertEquals(18, blocks[0].hour)
        assertEquals(2, blocks[0].trips)
        assertEquals(130.0, blocks[0].netMxn, 0.001)
        assertEquals(9, blocks[1].hour)
        assertEquals(60.0, blocks[1].netMxn, 0.001)
    }

    @Test
    fun `open trips are ignored`() {
        val trips = listOf(trip(18, gross = 100.0, km = 10.0, ended = false))
        assertEquals(0, BestHours(zone, marginalCostPerKm).blocks(trips).size)
    }
}
