package mx.kompara.ui.stats

import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.db.entity.OfferOutcome
import mx.kompara.metrics.VerdictLevel
import mx.kompara.metrics.percentile.PercentileResult
import mx.kompara.metrics.recommendation.BestHourBlock
import mx.kompara.metrics.recommendation.CrossPlatformRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RecommendationsBuilder] — the pure glue that maps the Inicio inputs (period, percentiles, offers,
 * best-hour, cross-platform) into the engine and back (B-048 viewmodel-combination requirement).
 * Exercised with fake entities; no Room/Compose.
 */
class RecommendationsBuilderTest {

    private fun period(
        net: Double = 2000.0,
        eph: Double? = 120.0,
        ept: Double? = 90.0,
        hours: Double = 20.0,
        trips: Int = 30,
        acceptance: Double? = null,
    ) = PeriodStats(
        netEarningsMxn = net,
        grossEarningsMxn = net + 200,
        totalTrips = trips,
        totalKm = trips * 8.0,
        hoursOnline = hours,
        earningsPerTrip = ept,
        earningsPerKm = 9.0,
        earningsPerHour = eph,
        tripsPerHour = if (hours > 0) trips / hours else null,
        acceptanceRate = acceptance,
    )

    private fun offer(
        fare: Double,
        verdict: VerdictLevel?,
        outcome: OfferOutcome,
    ) = OfferEntity(
        seenAt = 0L,
        platform = "UBER",
        fareMxn = fare,
        distanceKm = 5.0,
        durationMin = 12.0,
        verdict = verdict?.name,
        outcome = outcome.name,
    )

    @Test
    fun `maps declined green offers into the missed-good-offers warning`() {
        val offers = listOf(
            offer(120.0, VerdictLevel.GREEN, OfferOutcome.DECLINED),
            offer(80.0, VerdictLevel.GREEN, OfferOutcome.EXPIRED), // EXPIRED counts as not-taken
            offer(200.0, VerdictLevel.GREEN, OfferOutcome.ACCEPTED), // accepted — excluded
            offer(50.0, VerdictLevel.RED, OfferOutcome.DECLINED), // not green — excluded
        )
        val recs = RecommendationsBuilder.build(
            period = period(),
            percentiles = emptyList(),
            cityLabel = "CDMX",
            streakWeeks = 0,
            weeklyNetGoalMxn = null,
            offers = offers,
            bestHour = null,
            crossPlatform = emptyList(),
        )
        val missed = recs.firstOrNull { it.id == "missed_good_offers" }
        assertNotNull(missed)
        assertTrue(missed!!.body.contains("2 ofertas"))
        assertTrue(missed.body.contains("$200")) // 120 + 80
    }

    @Test
    fun `goal reached flag is derived from net vs goal`() {
        // Net below goal + low acceptance ⇒ loosen tip fires.
        val offers = List(12) { offer(60.0, VerdictLevel.GREEN, OfferOutcome.DECLINED) }
        val recs = RecommendationsBuilder.build(
            period = period(net = 1000.0, acceptance = 0.15),
            percentiles = emptyList(),
            cityLabel = "CDMX",
            streakWeeks = 0,
            weeklyNetGoalMxn = 5000.0,
            offers = offers,
            bestHour = null,
            crossPlatform = emptyList(),
        )
        assertNotNull(recs.firstOrNull { it.id == "acceptance_loosen" })

        // Same week but goal already met ⇒ no loosen tip.
        val met = RecommendationsBuilder.build(
            period = period(net = 6000.0, acceptance = 0.15),
            percentiles = emptyList(),
            cityLabel = "CDMX",
            streakWeeks = 0,
            weeklyNetGoalMxn = 5000.0,
            offers = offers,
            bestHour = null,
            crossPlatform = emptyList(),
        )
        assertNull(met.firstOrNull { it.id == "acceptance_loosen" })
    }

    @Test
    fun `percentile and cross-platform tips are flagged premium, capture tips are free`() {
        val recs = RecommendationsBuilder.build(
            period = period(),
            percentiles = listOf(
                PercentileResult("earnings_per_hour", 150.0, 90, 90, 50, false, false),
            ),
            cityLabel = "CDMX",
            streakWeeks = 5,
            weeklyNetGoalMxn = null,
            offers = emptyList(),
            bestHour = BestHourBlock(dayOfWeek = 5, hour = 19, netMxn = 300.0, tripCount = 4),
            crossPlatform = listOf(
                CrossPlatformRate("UBER", 10.0),
                CrossPlatformRate("DIDI", 8.0),
            ),
        )
        // The percentile praise + cross-platform tip are premium; streak + best-hours are free.
        val byId = recs.associateBy { it.id }
        byId["high_eph_percentile"]?.let { assertTrue(it.premium) }
        byId["cross_platform"]?.let { assertTrue(it.premium) }
        byId["streak_praise"]?.let { assertFalse(it.premium) }
        byId["best_hours"]?.let { assertFalse(it.premium) }
    }

    @Test
    fun `caps the combined output at three`() {
        val recs = RecommendationsBuilder.build(
            period = period(net = 1000.0, acceptance = 0.1),
            percentiles = listOf(PercentileResult("earnings_per_hour", 150.0, 95, 95, 50, false, false)),
            cityLabel = "CDMX",
            streakWeeks = 6,
            weeklyNetGoalMxn = 5000.0,
            offers = List(12) { offer(100.0, VerdictLevel.GREEN, OfferOutcome.DECLINED) },
            bestHour = BestHourBlock(5, 20, 400.0, 5),
            crossPlatform = listOf(CrossPlatformRate("UBER", 12.0), CrossPlatformRate("DIDI", 8.0)),
        )
        assertEquals(3, recs.size)
        // Warnings sort first.
        assertEquals(mx.kompara.metrics.recommendation.RecommendationType.WARNING, recs.first().type)
    }

    @Test
    fun `a thin week yields no recommendations`() {
        val recs = RecommendationsBuilder.build(
            period = period(net = 100.0, eph = 40.0, ept = 25.0, hours = 1.0, trips = 2),
            percentiles = emptyList(),
            cityLabel = "CDMX",
            streakWeeks = 1,
            weeklyNetGoalMxn = null,
            offers = emptyList(),
            bestHour = null,
            crossPlatform = emptyList(),
        )
        assertTrue(recs.isEmpty())
    }
}
