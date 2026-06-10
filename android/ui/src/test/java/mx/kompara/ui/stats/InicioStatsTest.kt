package mx.kompara.ui.stats

import mx.kompara.data.db.entity.AggregateSource
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dashboard's current-week pick + platform-filter + totals logic ([InicioStats]), the pure core
 * of [InicioDashboardViewModel]. Exercised with fake aggregate rows (B-040 test requirement).
 */
class InicioStatsTest {

    private val thisWeek = "2026-06-08"
    private val lastWeek = "2026-06-01"

    private fun weekly(
        weekStart: String,
        platform: Platform,
        net: Double,
        trips: Int,
        hours: Double,
        source: AggregateSource = AggregateSource.CAPTURED,
    ) = WeeklyAggregateEntity(
        platform = platform.name,
        weekStart = weekStart,
        source = source.name,
        netEarningsMxn = net,
        grossEarningsMxn = net + 100,
        totalTrips = trips,
        totalKm = trips * 10.0,
        hoursOnline = hours,
        earningsPerTrip = if (trips == 0) null else net / trips,
        earningsPerKm = if (trips == 0) null else net / (trips * 10.0),
        earningsPerHour = if (hours <= 0) null else net / hours,
        tripsPerHour = if (hours <= 0) null else trips / hours,
        acceptanceRate = 0.5,
        computedAt = 0L,
    )

    @Test
    fun `picks only the current week's captured rows`() {
        val rows = listOf(
            weekly(thisWeek, Platform.UBER, net = 1000.0, trips = 10, hours = 5.0),
            weekly(lastWeek, Platform.UBER, net = 9999.0, trips = 99, hours = 50.0), // must be ignored
        )
        val state = InicioStats.forCurrentWeek(
            allWeekly = rows,
            currentWeekStart = thisWeek,
            weeklyNetGoalMxn = null,
            selectedPlatform = null,
            costProfileSet = true,
        )
        assertEquals(1000.0, state.period.netEarningsMxn, 0.001)
        assertEquals(10, state.period.totalTrips)
        assertTrue(state.hasData)
    }

    @Test
    fun `ignores imported rows for the current-week dashboard (captured only)`() {
        val rows = listOf(
            weekly(thisWeek, Platform.UBER, net = 1000.0, trips = 10, hours = 5.0, source = AggregateSource.CAPTURED),
            weekly(thisWeek, Platform.UBER, net = 8000.0, trips = 80, hours = 5.0, source = AggregateSource.IMPORTED),
        )
        val state = InicioStats.forCurrentWeek(rows, thisWeek, null, null, true)
        assertEquals(1000.0, state.period.netEarningsMxn, 0.001)
    }

    @Test
    fun `Todas sums platforms, single-platform selection filters`() {
        val rows = listOf(
            weekly(thisWeek, Platform.UBER, net = 1000.0, trips = 10, hours = 5.0),
            weekly(thisWeek, Platform.DIDI, net = 500.0, trips = 5, hours = 5.0),
        )
        val todas = InicioStats.forCurrentWeek(rows, thisWeek, null, null, true)
        assertEquals(1500.0, todas.period.netEarningsMxn, 0.001)
        assertEquals(15, todas.period.totalTrips)
        assertEquals(listOf(null, Platform.UBER, Platform.DIDI), todas.chips)

        val onlyDidi = InicioStats.forCurrentWeek(rows, thisWeek, null, Platform.DIDI, true)
        assertEquals(500.0, onlyDidi.period.netEarningsMxn, 0.001)
        assertEquals(Platform.DIDI, onlyDidi.selectedPlatform)
    }

    @Test
    fun `empty week shows the empty state and no cost nudge flag is independent`() {
        val state = InicioStats.forCurrentWeek(emptyList(), thisWeek, null, null, costProfileSet = false)
        assertFalse(state.hasData)
        assertFalse(state.costProfileSet) // drives the first-run nudge
    }

    @Test
    fun `streak counts consecutive weeks across all data, goal reflects current net`() {
        val rows = listOf(
            weekly(thisWeek, Platform.UBER, net = 1000.0, trips = 10, hours = 5.0),
            weekly(lastWeek, Platform.UBER, net = 800.0, trips = 8, hours = 4.0),
        )
        val state = InicioStats.forCurrentWeek(
            allWeekly = rows,
            currentWeekStart = thisWeek,
            weeklyNetGoalMxn = 2000.0,
            selectedPlatform = null,
            costProfileSet = true,
        )
        assertEquals(2, state.streak.weeks) // two consecutive weeks of data
        assertTrue(state.goal.hasGoal)
        assertEquals(0.5f, state.goal.fraction, 0.001f) // 1000/2000
        assertFalse(state.goal.reached)
    }
}
