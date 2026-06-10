package mx.kompara.data.rollup

import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.db.entity.OfferOutcome
import mx.kompara.data.db.entity.ShiftEntity
import mx.kompara.data.db.entity.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Hand-computed rollup math for [RollupCalculator] (B-039). Pure JVM — no Room, no Android.
 *
 * Uses UTC so the epoch-millis ↔ local-date mapping in the assertions is unambiguous; the calculator
 * is zone-parametric so this exercises the exact same code path the device uses with its own zone.
 */
class RollupCalculatorTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val marginalCostPerKm = 2.0 // MXN/km
    private val uber = "UBER"

    /** Epoch millis for a UTC wall-clock time. */
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun calc() = RollupCalculator(zone = zone, marginalCostPerKm = marginalCostPerKm)

    private fun trip(
        startedAt: Long,
        endedAt: Long?,
        gross: Double,
        km: Double,
        durationMin: Double = 0.0,
        platform: String = uber,
    ) = TripEntity(
        offerId = null,
        startedAt = startedAt,
        endedAt = endedAt,
        platform = platform,
        grossMxn = gross,
        distanceKm = km,
        durationMin = durationMin,
        estimated = true,
    )

    @Test
    fun `weekly aggregate matches hand-computed week`() {
        // Wed 2026-06-10 and Thu 2026-06-11 (same ISO week starting Mon 2026-06-08).
        val trips = listOf(
            trip(at(2026, 6, 10, 9), at(2026, 6, 10, 10), gross = 100.0, km = 10.0),
            trip(at(2026, 6, 11, 9), at(2026, 6, 11, 10), gross = 200.0, km = 20.0),
        )
        // One shift covering 4 hours each day (8 online hours total in the week).
        val shifts = listOf(
            ShiftEntity(startedAt = at(2026, 6, 10, 8), endedAt = at(2026, 6, 10, 12)),
            ShiftEntity(startedAt = at(2026, 6, 11, 8), endedAt = at(2026, 6, 11, 12)),
        )
        // 3 offers seen: 2 accepted, 1 declined ⇒ acceptance 2/3.
        val offers = listOf(
            offer(at(2026, 6, 10, 9), OfferOutcome.ACCEPTED),
            offer(at(2026, 6, 11, 9), OfferOutcome.ACCEPTED),
            offer(at(2026, 6, 11, 12), OfferOutcome.DECLINED),
        )

        val result = calc().rollup(trips, shifts, offers, computedAt = 0L)
        val week = result.weekly.single()

        assertEquals("2026-06-08", week.weekStart) // Monday
        assertEquals(uber, week.platform)
        assertEquals("CAPTURED", week.source)
        assertEquals(300.0, week.grossEarningsMxn, 1e-9)
        // net = 300 - (10+20)*2 = 300 - 60 = 240
        assertEquals(240.0, week.netEarningsMxn, 1e-9)
        assertEquals(2, week.totalTrips)
        assertEquals(30.0, week.totalKm, 1e-9)
        assertEquals(8.0, week.hoursOnline, 1e-9)
        assertEquals(120.0, week.earningsPerTrip!!, 1e-9) // 240 / 2
        assertEquals(8.0, week.earningsPerKm!!, 1e-9) // 240 / 30
        assertEquals(30.0, week.earningsPerHour!!, 1e-9) // 240 / 8
        assertEquals(0.25, week.tripsPerHour!!, 1e-9) // 2 / 8
        assertEquals(2.0 / 3.0, week.acceptanceRate!!, 1e-9)
    }

    @Test
    fun `Monday week_start handles a Sunday correctly (Sunday belongs to the prior Monday's week)`() {
        // Sun 2026-06-14 is in the ISO week that started Mon 2026-06-08, NOT 2026-06-15.
        val sundayTrip = trip(at(2026, 6, 14, 23), at(2026, 6, 14, 23, 30), gross = 50.0, km = 5.0)
        val mondayTrip = trip(at(2026, 6, 15, 1), at(2026, 6, 15, 2), gross = 70.0, km = 7.0)

        val result = calc().rollup(listOf(sundayTrip, mondayTrip), emptyList(), emptyList(), 0L)
        val byWeek = result.weekly.associateBy { it.weekStart }

        assertTrue("Sunday's week should start 2026-06-08", byWeek.containsKey("2026-06-08"))
        assertTrue("Monday's week should start 2026-06-15", byWeek.containsKey("2026-06-15"))
        assertEquals(50.0, byWeek.getValue("2026-06-08").grossEarningsMxn, 1e-9)
        assertEquals(70.0, byWeek.getValue("2026-06-15").grossEarningsMxn, 1e-9)
    }

    @Test
    fun `daily aggregates bucket by trip start day`() {
        val trips = listOf(
            trip(at(2026, 6, 10, 9), at(2026, 6, 10, 10), gross = 100.0, km = 10.0),
            trip(at(2026, 6, 11, 9), at(2026, 6, 11, 10), gross = 200.0, km = 20.0),
        )
        val result = calc().rollup(trips, emptyList(), emptyList(), 0L)
        val byDay = result.daily.associateBy { it.day }
        assertEquals(2, byDay.size)
        assertEquals(100.0, byDay.getValue("2026-06-10").grossEarningsMxn, 1e-9)
        assertEquals(200.0, byDay.getValue("2026-06-11").grossEarningsMxn, 1e-9)
    }

    @Test
    fun `open trips and pending offers are excluded`() {
        val trips = listOf(
            trip(at(2026, 6, 10, 9), endedAt = null, gross = 100.0, km = 10.0), // open ⇒ excluded
            trip(at(2026, 6, 10, 11), at(2026, 6, 10, 12), gross = 80.0, km = 8.0),
        )
        val offers = listOf(
            offer(at(2026, 6, 10, 9), OfferOutcome.PENDING), // pending ⇒ not counted
            offer(at(2026, 6, 10, 11), OfferOutcome.ACCEPTED),
        )
        val result = calc().rollup(trips, emptyList(), offers, 0L)
        val day = result.daily.single()
        assertEquals(1, day.totalTrips)
        assertEquals(80.0, day.grossEarningsMxn, 1e-9)
        assertEquals(1.0, day.acceptanceRate!!, 1e-9) // only the 1 resolved offer, accepted
    }

    @Test
    fun `declined offers do not pollute earnings but lower acceptance rate`() {
        val trips = listOf(trip(at(2026, 6, 10, 9), at(2026, 6, 10, 10), gross = 100.0, km = 10.0))
        val offers = listOf(
            offer(at(2026, 6, 10, 9), OfferOutcome.ACCEPTED),
            offer(at(2026, 6, 10, 9, 5), OfferOutcome.DECLINED),
            offer(at(2026, 6, 10, 9, 6), OfferOutcome.EXPIRED),
        )
        val result = calc().rollup(trips, emptyList(), offers, 0L)
        val day = result.daily.single()
        assertEquals(100.0, day.grossEarningsMxn, 1e-9) // declined/expired add no earnings
        assertEquals(1, day.totalTrips)
        assertEquals(1.0 / 3.0, day.acceptanceRate!!, 1e-9)
    }

    @Test
    fun `rates are null when their denominator is zero`() {
        // A completed trip with zero km, no shift hours.
        val trips = listOf(trip(at(2026, 6, 10, 9), at(2026, 6, 10, 10), gross = 0.0, km = 0.0))
        val result = calc().rollup(trips, emptyList(), emptyList(), 0L)
        val day = result.daily.single()
        assertNull(day.earningsPerKm) // km == 0
        assertNull(day.earningsPerHour) // hours == 0
        assertNull(day.tripsPerHour) // hours == 0
        assertNull(day.acceptanceRate) // no resolved offers
        assertEquals(0.0, day.earningsPerTrip!!, 1e-9) // trips == 1, net 0
    }

    @Test
    fun `online hours clip a shift to the day bucket`() {
        // Shift 22:00 Wed -> 02:00 Thu spans midnight: 2h belong to the 10th, 2h to the 11th.
        val trips = listOf(
            trip(at(2026, 6, 10, 23), at(2026, 6, 10, 23, 30), gross = 10.0, km = 1.0),
            trip(at(2026, 6, 11, 1), at(2026, 6, 11, 1, 30), gross = 10.0, km = 1.0),
        )
        val shift = ShiftEntity(startedAt = at(2026, 6, 10, 22), endedAt = at(2026, 6, 11, 2))
        val result = calc().rollup(trips, listOf(shift), emptyList(), 0L)
        val byDay = result.daily.associateBy { it.day }
        assertEquals(2.0, byDay.getValue("2026-06-10").hoursOnline, 1e-9)
        assertEquals(2.0, byDay.getValue("2026-06-11").hoursOnline, 1e-9)
    }

    private fun offer(seenAt: Long, outcome: OfferOutcome, platform: String = uber) = OfferEntity(
        seenAt = seenAt,
        platform = platform,
        fareMxn = 0.0,
        distanceKm = 0.0,
        durationMin = 0.0,
        outcome = outcome.name,
    )
}
