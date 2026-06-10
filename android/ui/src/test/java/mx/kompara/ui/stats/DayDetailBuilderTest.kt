package mx.kompara.ui.stats

import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.db.entity.OfferOutcome
import mx.kompara.data.db.entity.ShiftEntity
import mx.kompara.data.db.entity.TripEntity
import mx.kompara.metrics.VerdictLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/** Offer-funnel classification and shift timeline assembly for [DayDetailBuilder] (B-040 req 2). */
class DayDetailBuilderTest {

    private fun offer(id: Long, fare: Double, outcome: OfferOutcome, verdict: VerdictLevel?) = OfferEntity(
        id = id,
        seenAt = 1000L * id,
        platform = "UBER",
        fareMxn = fare,
        distanceKm = 8.0,
        durationMin = 15.0,
        outcome = outcome.name,
        verdict = verdict?.name,
    )

    @Test
    fun `funnel counts seen, taken and declined (declined folds in expired)`() {
        val offers = listOf(
            offer(1, 100.0, OfferOutcome.ACCEPTED, VerdictLevel.GREEN),
            offer(2, 50.0, OfferOutcome.DECLINED, VerdictLevel.RED),
            offer(3, 60.0, OfferOutcome.EXPIRED, VerdictLevel.YELLOW),
            offer(4, 80.0, OfferOutcome.PENDING, null),
        )
        val detail = DayDetailBuilder.build(
            dayIso = "2026-06-10",
            period = PeriodStats.EMPTY,
            shifts = emptyList(),
            trips = emptyList(),
            offers = offers,
            bestHours = emptyList(),
        )
        assertEquals(4, detail.offers.seen)
        assertEquals(1, detail.offers.taken)
        assertEquals(2, detail.offers.declined) // declined + expired
        assertEquals(1, detail.offers.pending)
        // Verdicts carried through and rows sorted by seenAt.
        assertEquals(VerdictLevel.GREEN, detail.offers.rows.first().verdict)
    }

    @Test
    fun `shift timeline sorted by start with counts and net`() {
        val shifts = listOf(
            ShiftEntity(id = 2, startedAt = 5000L, endedAt = 9000L, tripCount = 3, netMxn = 240.0),
            ShiftEntity(id = 1, startedAt = 1000L, endedAt = 4000L, tripCount = 2, netMxn = 120.0),
        )
        val detail = DayDetailBuilder.build(
            dayIso = "2026-06-10",
            period = PeriodStats.EMPTY,
            shifts = shifts,
            trips = emptyList(),
            offers = emptyList(),
            bestHours = emptyList(),
        )
        assertEquals(2, detail.shifts.size)
        assertEquals(1L, detail.shifts.first().shiftId) // earliest start first
        assertEquals(2, detail.shifts.first().tripCount)
        assertEquals(240.0, detail.shifts.last().netMxn, 0.001)
    }

    @Test
    fun `open shift falls back to counting the day's trips when its counter is zero`() {
        val shift = ShiftEntity(id = 7, startedAt = 1000L, endedAt = null, tripCount = 0, netMxn = 0.0)
        val trips = listOf(
            TripEntity(id = 1, shiftId = 7, startedAt = 1500L, endedAt = 2000L, platform = "UBER", grossMxn = 100.0, distanceKm = 8.0, durationMin = 15.0),
            TripEntity(id = 2, shiftId = 7, startedAt = 2500L, endedAt = 3000L, platform = "UBER", grossMxn = 90.0, distanceKm = 7.0, durationMin = 14.0),
        )
        val detail = DayDetailBuilder.build(
            dayIso = "2026-06-10",
            period = PeriodStats.EMPTY,
            shifts = listOf(shift),
            trips = trips,
            offers = emptyList(),
            bestHours = emptyList(),
        )
        assertEquals(2, detail.shifts.first().tripCount)
    }
}
