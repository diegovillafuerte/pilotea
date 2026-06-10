package mx.kompara.ui.stats

import mx.kompara.data.db.entity.AggregateSource
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aggregation/selection math for [PeriodStats] (B-040): platform filter, the "Todas" sum, and the
 * rule that rates are recomputed from summed totals (not averaged across pre-divided per-platform
 * rates). Pure JVM — no Room, no Android.
 */
class PeriodStatsTest {

    private fun weekly(
        platform: Platform,
        net: Double,
        gross: Double,
        trips: Int,
        km: Double,
        hours: Double,
        acceptance: Double?,
        weekStart: String = "2026-06-08",
        source: AggregateSource = AggregateSource.CAPTURED,
    ) = WeeklyAggregateEntity(
        platform = platform.name,
        weekStart = weekStart,
        source = source.name,
        netEarningsMxn = net,
        grossEarningsMxn = gross,
        totalTrips = trips,
        totalKm = km,
        hoursOnline = hours,
        earningsPerTrip = if (trips == 0) null else net / trips,
        earningsPerKm = if (km <= 0) null else net / km,
        earningsPerHour = if (hours <= 0) null else net / hours,
        tripsPerHour = if (hours <= 0) null else trips / hours,
        acceptanceRate = acceptance,
        computedAt = 0L,
    )

    @Test
    fun `single platform fold matches the row`() {
        val rows = listOf(weekly(Platform.UBER, net = 1000.0, gross = 1200.0, trips = 10, km = 100.0, hours = 5.0, acceptance = 0.5))
        val s = PeriodStats.fromWeekly(rows, Platform.UBER)
        assertEquals(1000.0, s.netEarningsMxn, 0.001)
        assertEquals(10, s.totalTrips)
        assertEquals(100.0, s.totalKm, 0.001)
        assertEquals(5.0, s.hoursOnline, 0.001)
        assertEquals(100.0, s.earningsPerTrip!!, 0.001) // 1000/10
        assertEquals(10.0, s.earningsPerKm!!, 0.001) // 1000/100
        assertEquals(200.0, s.earningsPerHour!!, 0.001) // 1000/5
        assertEquals(2.0, s.tripsPerHour!!, 0.001) // 10/5
        assertEquals(0.5, s.acceptanceRate!!, 0.001)
    }

    @Test
    fun `platform filter selects only the chosen platform`() {
        val rows = listOf(
            weekly(Platform.UBER, net = 1000.0, gross = 1200.0, trips = 10, km = 100.0, hours = 5.0, acceptance = 0.5),
            weekly(Platform.DIDI, net = 500.0, gross = 600.0, trips = 8, km = 80.0, hours = 5.0, acceptance = 0.9),
        )
        val didi = PeriodStats.fromWeekly(rows, Platform.DIDI)
        assertEquals(500.0, didi.netEarningsMxn, 0.001)
        assertEquals(8, didi.totalTrips)
        assertEquals(0.9, didi.acceptanceRate!!, 0.001)
    }

    @Test
    fun `Todas sums earnings, trips and km, recomputing rates from totals`() {
        val rows = listOf(
            weekly(Platform.UBER, net = 1000.0, gross = 1200.0, trips = 10, km = 100.0, hours = 5.0, acceptance = 0.5),
            weekly(Platform.DIDI, net = 500.0, gross = 600.0, trips = 8, km = 80.0, hours = 5.0, acceptance = 0.9),
        )
        val all = PeriodStats.fromWeekly(rows, platform = null)
        assertEquals(1500.0, all.netEarningsMxn, 0.001)
        assertEquals(1800.0, all.grossEarningsMxn, 0.001)
        assertEquals(18, all.totalTrips)
        assertEquals(180.0, all.totalKm, 0.001)
        // Rates recomputed from the summed totals.
        assertEquals(1500.0 / 18, all.earningsPerTrip!!, 0.001)
        assertEquals(1500.0 / 180.0, all.earningsPerKm!!, 0.001)
        // Acceptance is the unweighted blend of the two rows' rates.
        assertEquals((0.5 + 0.9) / 2, all.acceptanceRate!!, 0.001)
    }

    @Test
    fun `Todas takes max hours, not the sum, because hours are platform-agnostic`() {
        // Both platform-rows carry the same 5h online window (replicated by the rollup).
        val rows = listOf(
            weekly(Platform.UBER, net = 1000.0, gross = 1200.0, trips = 10, km = 100.0, hours = 5.0, acceptance = 0.5),
            weekly(Platform.DIDI, net = 500.0, gross = 600.0, trips = 8, km = 80.0, hours = 5.0, acceptance = 0.9),
        )
        val all = PeriodStats.fromWeekly(rows, platform = null)
        assertEquals(5.0, all.hoursOnline, 0.001) // max(5,5) == 5, not 10
        assertEquals(1500.0 / 5.0, all.earningsPerHour!!, 0.001)
        assertEquals(18 / 5.0, all.tripsPerHour!!, 0.001)
    }

    @Test
    fun `empty rows fold to EMPTY with null rates`() {
        val s = PeriodStats.fromWeekly(emptyList(), platform = null)
        assertTrue(s.isEmpty)
        assertEquals(0.0, s.netEarningsMxn, 0.0)
        assertNull(s.earningsPerTrip)
        assertNull(s.earningsPerHour)
        assertNull(s.acceptanceRate)
    }

    @Test
    fun `zero-denominator rates are null even with earnings present`() {
        // A week with net but no hours (no shift detected) → per-hour rates null, per-trip ok.
        val rows = listOf(weekly(Platform.UBER, net = 300.0, gross = 350.0, trips = 4, km = 0.0, hours = 0.0, acceptance = null))
        val s = PeriodStats.fromWeekly(rows, Platform.UBER)
        assertEquals(75.0, s.earningsPerTrip!!, 0.001)
        assertNull(s.earningsPerKm)
        assertNull(s.earningsPerHour)
        assertNull(s.tripsPerHour)
        assertNull(s.acceptanceRate)
    }
}
