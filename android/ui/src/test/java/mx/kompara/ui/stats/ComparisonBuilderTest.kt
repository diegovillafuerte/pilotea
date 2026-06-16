package mx.kompara.ui.stats

import mx.kompara.data.db.entity.AggregateSource
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform
import mx.kompara.data.model.PopulationStat
import mx.kompara.metrics.percentile.PercentileResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ComparisonBuilder] (S-024): blended Tú, per-platform city averages, percentile mapping, the
 * intrinsic per-platform N/A (Uber no km, DiDi no comisión), and the hero standing pick.
 */
class ComparisonBuilderTest {

    private fun weekly(
        platform: Platform,
        net: Double,
        trips: Int,
        km: Double,
        hours: Double,
        perKm: Double?,
    ) = WeeklyAggregateEntity(
        platform = platform.name,
        weekStart = "2026-06-08",
        source = AggregateSource.CAPTURED.name,
        netEarningsMxn = net,
        grossEarningsMxn = net * 1.3,
        totalTrips = trips,
        totalKm = km,
        hoursOnline = hours,
        earningsPerTrip = if (trips == 0) null else net / trips,
        earningsPerKm = perKm,
        earningsPerHour = if (hours == 0.0) null else net / hours,
        tripsPerHour = if (hours == 0.0) null else trips / hours,
        acceptanceRate = 0.8,
        computedAt = 0L,
    )

    private fun stat(platform: String, metric: String, mean: Double) =
        PopulationStat(
            city = "cdmx",
            platform = platform,
            metric = metric,
            period = "current",
            sampleSize = 1500,
            p10 = mean * 0.6,
            p25 = mean * 0.8,
            p50 = mean,
            p75 = mean * 1.2,
            p90 = mean * 1.5,
            mean = mean,
            isSynthetic = true,
        )

    private fun row(c: WeeklyComparison, metric: String): ComparisonRow = c.rows.first { it.metric == metric }

    @Test
    fun `blends net across platforms and recomputes rates`() {
        val rows = listOf(
            weekly(Platform.UBER, net = 1000.0, trips = 20, km = 100.0, hours = 10.0, perKm = 10.0),
            weekly(Platform.DIDI, net = 800.0, trips = 20, km = 100.0, hours = 10.0, perKm = 8.0),
        )
        val c = ComparisonBuilder.build(
            weekStart = "2026-06-08",
            weekRows = rows,
            platforms = listOf(Platform.UBER, Platform.DIDI),
            platformStats = emptyList(),
            percentilesByMetric = emptyMap(),
        )
        // Net is summed (1800); $/km uses summed totals: 1800 / 200 = 9.0.
        assertEquals(1800.0, row(c, "net_earnings").tu!!, 1e-9)
        assertEquals(9.0, row(c, "earnings_per_km").tu!!, 1e-9)
    }

    @Test
    fun `reads each platform city average from population mean`() {
        val c = ComparisonBuilder.build(
            weekStart = "2026-06-08",
            weekRows = listOf(weekly(Platform.UBER, 1000.0, 20, 100.0, 10.0, 10.0)),
            platforms = listOf(Platform.UBER),
            platformStats = listOf(
                stat("uber", "earnings_per_hour", 150.0),
                stat("didi", "earnings_per_hour", 145.0),
            ),
            percentilesByMetric = emptyMap(),
        )
        val iph = row(c, "earnings_per_hour")
        assertEquals(150.0, iph.uberAvg!!, 1e-9)
        assertEquals(145.0, iph.didiAvg!!, 1e-9)
    }

    @Test
    fun `Uber km and DiDi commission are intrinsically not available`() {
        val c = ComparisonBuilder.build(
            weekStart = "2026-06-08",
            weekRows = listOf(weekly(Platform.DIDI, 800.0, 20, 100.0, 10.0, 8.0)),
            platforms = listOf(Platform.DIDI),
            // Even if a stray uber/km or didi/commission stat existed, the spec hides them.
            platformStats = listOf(
                stat("uber", "earnings_per_km", 9.0),
                stat("didi", "platform_commission_pct", 22.0),
            ),
            percentilesByMetric = emptyMap(),
        )
        assertNull(row(c, "earnings_per_km").uberAvg)
        assertEquals(NaReason.NO_KM, row(c, "earnings_per_km").uberNa)
        assertNull(row(c, "platform_commission_pct").didiAvg)
        assertEquals(NaReason.NO_COMMISSION, row(c, "platform_commission_pct").didiNa)
    }

    @Test
    fun `percentile maps by metric and the standing picks earnings_per_hour first`() {
        val pct = mapOf(
            "earnings_per_hour" to PercentileResult("earnings_per_hour", 0.0, 78, 78, 1500, false, false),
            "earnings_per_km" to PercentileResult("earnings_per_km", 0.0, 60, 60, 1500, false, false),
        )
        val c = ComparisonBuilder.build(
            weekStart = "2026-06-08",
            weekRows = listOf(weekly(Platform.UBER, 1000.0, 20, 100.0, 10.0, 10.0)),
            platforms = listOf(Platform.UBER),
            platformStats = emptyList(),
            percentilesByMetric = pct,
        )
        assertEquals(78, row(c, "earnings_per_hour").percentile!!.displayPercentile)
        assertEquals("earnings_per_hour", c.standingMetric)
        assertEquals(78, c.standing!!.displayPercentile)
    }

    @Test
    fun `single platform is flagged`() {
        val c = ComparisonBuilder.build(
            weekStart = "2026-06-08",
            weekRows = listOf(weekly(Platform.DIDI, 800.0, 20, 100.0, 10.0, 8.0)),
            platforms = listOf(Platform.DIDI),
            platformStats = emptyList(),
            percentilesByMetric = emptyMap(),
        )
        assertEquals(Platform.DIDI, c.singlePlatform)
        assertTrue(c.rows.size == COMPARE_METRICS.size)
    }
}
