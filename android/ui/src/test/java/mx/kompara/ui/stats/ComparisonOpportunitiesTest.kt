package mx.kompara.ui.stats

import mx.kompara.data.model.Platform
import mx.kompara.data.model.PopulationStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ComparisonOpportunities] (B-088): platform-mix, single-app nudge, and top-of-city gap rules. */
class ComparisonOpportunitiesTest {

    private fun stats(eph: Double? = null) = PeriodStats(
        netEarningsMxn = 1000.0,
        grossEarningsMxn = 1300.0,
        totalTrips = 20,
        totalKm = 100.0,
        hoursOnline = 10.0,
        earningsPerTrip = 50.0,
        earningsPerKm = 10.0,
        earningsPerHour = eph,
        tripsPerHour = 2.0,
        acceptanceRate = 0.8,
    )

    private fun stat(platform: String, metric: String, mean: Double, p90: Double = mean) =
        PopulationStat("cdmx", platform, metric, "current", 1500, mean * 0.6, mean * 0.8, mean, mean * 1.2, p90, mean, true)

    @Test
    fun `platform mix fires when a platform pays 15pct more and the driver under-indexes`() {
        val recs = ComparisonOpportunities.build(
            blended = stats(eph = 150.0),
            platformStats = listOf(
                stat("uber", "earnings_per_hour", 130.0),
                stat("didi", "earnings_per_hour", 160.0), // DiDi ~23% higher
            ),
            platformMix = mapOf(Platform.UBER to 20.0, Platform.DIDI to 5.0), // drives more Uber
        )
        assertTrue(recs.any { it.id == "compare_platform_mix" && it.title.contains("DiDi") })
    }

    @Test
    fun `single app nudges trying the other app`() {
        val recs = ComparisonOpportunities.build(
            blended = stats(eph = 150.0),
            platformStats = listOf(stat("didi", "earnings_per_hour", 145.0)),
            platformMix = mapOf(Platform.UBER to 30.0), // only Uber
        )
        assertTrue(recs.any { it.id == "compare_try_other_app" && it.title.contains("DiDi") })
    }

    @Test
    fun `top of city gap fires when the all-population p90 beats the driver`() {
        val recs = ComparisonOpportunities.build(
            blended = stats(eph = 150.0),
            platformStats = listOf(stat("all", "earnings_per_hour", 160.0, p90 = 200.0)),
            platformMix = mapOf(Platform.UBER to 20.0, Platform.DIDI to 20.0),
        )
        assertTrue(recs.any { it.id == "compare_top_gap" })
    }

    @Test
    fun `no platform-mix opportunity when the gap is small`() {
        val recs = ComparisonOpportunities.build(
            blended = stats(eph = 150.0),
            platformStats = listOf(
                stat("uber", "earnings_per_hour", 150.0),
                stat("didi", "earnings_per_hour", 155.0), // ~3%
            ),
            platformMix = mapOf(Platform.UBER to 20.0, Platform.DIDI to 20.0),
        )
        assertEquals(0, recs.count { it.id == "compare_platform_mix" })
    }
}
