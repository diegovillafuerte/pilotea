package mx.kompara.metrics.recommendation

import mx.kompara.metrics.percentile.PercentileResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive per-rule tests for [RecommendationEngine] (B-048 test requirement): EVERY rule fires on a
 * crafted fixture week AND stays silent on insufficient data, plus the priority/max-3 selection and the
 * premium partition.
 */
class RecommendationEngineTest {

    private val engine = RecommendationEngine()

    // ─── Fixture helpers ─────────────────────────────────────────────────────────────────────────

    private fun pct(metric: String, display: Int, raw: Int = display) = PercentileResult(
        metric = metric,
        value = 0.0,
        percentile = raw,
        displayPercentile = display,
        sampleSize = 50,
        isNationalFallback = false,
        isSynthetic = false,
    )

    private fun offer(fare: Double, green: Boolean, accepted: Boolean, declined: Boolean) =
        OfferSummary(fareMxn = fare, verdictGreen = green, accepted = accepted, declined = declined)

    /** A context with enough hours/trips to clear the basic sufficiency guards but no rule signals. */
    private fun baseContext() = RecommendationContext(
        earningsPerHour = 120.0,
        earningsPerTrip = 90.0,
        hoursOnline = 20.0,
        totalTrips = 30,
    )

    private fun ids(context: RecommendationContext) = engine.recommend(context).map { it.id }

    private fun fire(context: RecommendationContext, id: String): Recommendation? =
        engine.recommend(context).firstOrNull { it.id == id }

    // ─── streak praise ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `streak fires at 4 weeks and is free`() {
        val r = fire(baseContext().copy(streakWeeks = 4), "streak_praise")
        assertNotNull(r)
        assertEquals(RecommendationType.POSITIVE, r!!.type)
        assertFalse(r.premium)
        assertTrue(r.body.contains("4 semanas"))
    }

    @Test
    fun `streak silent below 4 weeks`() {
        assertNull(fire(baseContext().copy(streakWeeks = 3), "streak_praise"))
        assertNull(fire(baseContext().copy(streakWeeks = 0), "streak_praise"))
    }

    // ─── high $/hr percentile praise ───────────────────────────────────────────────────────────────

    @Test
    fun `high eph praise fires top quartile, is premium, names city and top percent`() {
        val r = fire(
            baseContext().copy(
                city = "CDMX",
                percentiles = listOf(pct(RecommendationEngine.METRIC_EPH, display = 92)),
            ),
            "high_eph_percentile",
        )
        assertNotNull(r)
        assertTrue(r!!.premium)
        assertEquals(RecommendationType.POSITIVE, r.type)
        assertTrue(r.body.contains("top 8%")) // 100 - 92
        assertTrue(r.body.contains("CDMX"))
    }

    @Test
    fun `high eph praise silent below top quartile or without percentile or with too few hours`() {
        assertNull(
            fire(baseContext().copy(percentiles = listOf(pct(RecommendationEngine.METRIC_EPH, 74))), "high_eph_percentile"),
        )
        assertNull(fire(baseContext(), "high_eph_percentile")) // no percentile cached
        assertNull(
            fire(
                baseContext().copy(hoursOnline = 1.0, percentiles = listOf(pct(RecommendationEngine.METRIC_EPH, 95))),
                "high_eph_percentile",
            ),
        )
    }

    // ─── low $/trip warning ──────────────────────────────────────────────────────────────────────

    @Test
    fun `low ept fires below p25 and is premium info`() {
        val r = fire(
            baseContext().copy(percentiles = listOf(pct(RecommendationEngine.METRIC_EPT, display = 18))),
            "low_ept_percentile",
        )
        assertNotNull(r)
        assertTrue(r!!.premium)
        assertEquals(RecommendationType.INFO, r.type)
    }

    @Test
    fun `low ept silent at or above p25, without percentile, or with too few trips`() {
        assertNull(fire(baseContext().copy(percentiles = listOf(pct(RecommendationEngine.METRIC_EPT, 25))), "low_ept_percentile"))
        assertNull(fire(baseContext(), "low_ept_percentile"))
        assertNull(
            fire(
                baseContext().copy(totalTrips = 4, percentiles = listOf(pct(RecommendationEngine.METRIC_EPT, 5))),
                "low_ept_percentile",
            ),
        )
    }

    // ─── high commission warning ──────────────────────────────────────────────────────────────────

    @Test
    fun `high commission fires when display percentile low and commission known, premium warning`() {
        val r = fire(
            baseContext().copy(
                commissionPct = 31.5,
                percentiles = listOf(pct(RecommendationEngine.METRIC_COMMISSION, display = 12)),
            ),
            "high_commission",
        )
        assertNotNull(r)
        assertTrue(r!!.premium)
        assertEquals(RecommendationType.WARNING, r.type)
        assertTrue(r.body.contains("31.5%"))
    }

    @Test
    fun `high commission silent without commission value, without percentile, or when commission is fine`() {
        // No commission value (a pure captured week).
        assertNull(fire(baseContext().copy(percentiles = listOf(pct(RecommendationEngine.METRIC_COMMISSION, 5))), "high_commission"))
        // Commission known but no benchmark.
        assertNull(fire(baseContext().copy(commissionPct = 30.0), "high_commission"))
        // Commission known and benchmark says it's fine (high display percentile = low commission).
        assertNull(
            fire(
                baseContext().copy(commissionPct = 18.0, percentiles = listOf(pct(RecommendationEngine.METRIC_COMMISSION, 80))),
                "high_commission",
            ),
        )
    }

    // ─── cross-platform tip ──────────────────────────────────────────────────────────────────────

    @Test
    fun `cross platform fires on a wide km gap and is premium info`() {
        val r = fire(
            baseContext().copy(
                crossPlatform = listOf(
                    CrossPlatformRate("UBER", netPerKm = 10.0),
                    CrossPlatformRate("DIDI", netPerKm = 8.0), // 25% gap
                ),
            ),
            "cross_platform",
        )
        assertNotNull(r)
        assertTrue(r!!.premium)
        assertEquals(RecommendationType.INFO, r.type)
        assertTrue(r.title.contains("Uber"))
        assertTrue(r.body.contains("25%"))
    }

    @Test
    fun `cross platform silent on a narrow gap, single platform, or zero rates`() {
        assertNull(
            fire(
                baseContext().copy(
                    crossPlatform = listOf(CrossPlatformRate("UBER", 10.0), CrossPlatformRate("DIDI", 9.5)),
                ),
                "cross_platform",
            ),
        )
        assertNull(fire(baseContext().copy(crossPlatform = listOf(CrossPlatformRate("UBER", 10.0))), "cross_platform"))
        assertNull(
            fire(
                baseContext().copy(crossPlatform = listOf(CrossPlatformRate("UBER", 0.0), CrossPlatformRate("DIDI", 0.0))),
                "cross_platform",
            ),
        )
    }

    // ─── missed good offers (capture-powered) ───────────────────────────────────────────────────────

    @Test
    fun `missed good offers fires on 2+ declined greens, sums fares, is free warning`() {
        val r = fire(
            baseContext().copy(
                offers = listOf(
                    offer(120.0, green = true, accepted = false, declined = true),
                    offer(80.0, green = true, accepted = false, declined = true),
                    offer(50.0, green = false, accepted = false, declined = true), // not green, excluded
                    offer(200.0, green = true, accepted = true, declined = false), // accepted, excluded
                ),
            ),
            "missed_good_offers",
        )
        assertNotNull(r)
        assertFalse(r!!.premium)
        assertEquals(RecommendationType.WARNING, r.type)
        assertTrue(r.body.contains("2 ofertas"))
        assertTrue(r.body.contains("$200")) // 120 + 80
    }

    @Test
    fun `missed good offers silent on fewer than 2 declined greens`() {
        assertNull(
            fire(
                baseContext().copy(offers = listOf(offer(120.0, green = true, accepted = false, declined = true))),
                "missed_good_offers",
            ),
        )
        assertNull(fire(baseContext(), "missed_good_offers"))
    }

    // ─── best hours (capture-powered) ──────────────────────────────────────────────────────────────

    @Test
    fun `best hours fires on a strong block, is free info, formats day and hour range`() {
        val r = fire(
            baseContext().copy(bestHour = BestHourBlock(dayOfWeek = 5, hour = 19, netMxn = 340.0, tripCount = 4)),
            "best_hours",
        )
        assertNotNull(r)
        assertFalse(r!!.premium)
        assertEquals(RecommendationType.INFO, r.type)
        assertTrue(r.body.contains("viernes"))
        assertTrue(r.body.contains("19:00"))
        assertTrue(r.body.contains("20:00"))
        assertTrue(r.body.contains("$340"))
    }

    @Test
    fun `best hours silent on too few trips, low net, or no block`() {
        assertNull(fire(baseContext().copy(bestHour = BestHourBlock(5, 19, 340.0, tripCount = 2)), "best_hours"))
        assertNull(fire(baseContext().copy(bestHour = BestHourBlock(5, 19, 10.0, tripCount = 5)), "best_hours"))
        assertNull(fire(baseContext(), "best_hours"))
    }

    @Test
    fun `best hours wraps midnight hour correctly`() {
        val r = fire(baseContext().copy(bestHour = BestHourBlock(6, 23, 300.0, tripCount = 4)), "best_hours")
        assertNotNull(r)
        assertTrue(r!!.body.contains("23:00"))
        assertTrue(r.body.contains("00:00")) // (23 + 1) % 24
    }

    // ─── acceptance guidance (capture-powered) ──────────────────────────────────────────────────────

    @Test
    fun `acceptance raise-floor fires when accepting reds, free warning, takes priority over loosen`() {
        // Also low acceptance + missed goal, but accepting reds must win (it's the money leak).
        val offers = buildList {
            repeat(3) { add(offer(60.0, green = false, accepted = true, declined = false)) } // 3 accepted reds
            repeat(8) { add(offer(60.0, green = true, accepted = false, declined = true)) } // many declines
        }
        val r = fire(
            baseContext().copy(offers = offers, acceptanceRate = 0.2, goalReached = false),
            "acceptance_raise_floor",
        )
        assertNotNull(r)
        assertFalse(r!!.premium)
        assertEquals(RecommendationType.WARNING, r.type)
        assertTrue(r.body.contains("3 viajes"))
        // The loosen rule must NOT also fire (mutually exclusive).
        assertNull(fire(baseContext().copy(offers = offers, acceptanceRate = 0.2, goalReached = false), "acceptance_loosen"))
    }

    @Test
    fun `acceptance loosen fires on very low acceptance and missed goal, free info`() {
        val offers = buildList {
            repeat(2) { add(offer(60.0, green = true, accepted = true, declined = false)) }
            repeat(10) { add(offer(60.0, green = true, accepted = false, declined = true)) }
        }
        val r = fire(
            baseContext().copy(offers = offers, acceptanceRate = 0.16, goalReached = false),
            "acceptance_loosen",
        )
        assertNotNull(r)
        assertFalse(r!!.premium)
        assertEquals(RecommendationType.INFO, r.type)
    }

    @Test
    fun `acceptance guidance silent with too few resolved offers, healthy acceptance, or met goal`() {
        // Too few resolved offers.
        val few = List(5) { offer(60.0, green = true, accepted = false, declined = true) }
        assertNull(fire(baseContext().copy(offers = few, acceptanceRate = 0.1, goalReached = false), "acceptance_loosen"))
        // Low acceptance but the goal WAS met — no nudge.
        val many = List(12) { offer(60.0, green = true, accepted = false, declined = true) }
        assertNull(fire(baseContext().copy(offers = many, acceptanceRate = 0.1, goalReached = true), "acceptance_loosen"))
        // Healthy acceptance, no accepted reds — nothing fires.
        val healthy = List(6) { offer(60.0, green = true, accepted = true, declined = false) } +
            List(6) { offer(60.0, green = true, accepted = false, declined = true) }
        assertNull(fire(baseContext().copy(offers = healthy, acceptanceRate = 0.5, goalReached = false), "acceptance_loosen"))
        assertNull(fire(baseContext().copy(offers = healthy, acceptanceRate = 0.5, goalReached = false), "acceptance_raise_floor"))
    }

    // ─── selection: priority + max 3 ──────────────────────────────────────────────────────────────

    @Test
    fun `caps at 3 recommendations`() {
        val context = baseContext().copy(
            streakWeeks = 6,
            city = "CDMX",
            commissionPct = 35.0,
            goalNetMxn = 5000.0,
            goalReached = false,
            acceptanceRate = 0.15,
            percentiles = listOf(
                pct(RecommendationEngine.METRIC_EPH, 95),
                pct(RecommendationEngine.METRIC_COMMISSION, 5),
            ),
            offers = buildList {
                repeat(3) { add(offer(100.0, green = true, accepted = false, declined = true)) }
                repeat(8) { add(offer(60.0, green = true, accepted = false, declined = true)) }
            },
            bestHour = BestHourBlock(5, 20, 400.0, tripCount = 5),
            crossPlatform = listOf(CrossPlatformRate("UBER", 12.0), CrossPlatformRate("DIDI", 8.0)),
        )
        assertEquals(3, engine.recommend(context).size)
    }

    @Test
    fun `warnings sort before info before positive`() {
        val context = baseContext().copy(
            streakWeeks = 6, // positive
            commissionPct = 35.0, // warning
            percentiles = listOf(pct(RecommendationEngine.METRIC_COMMISSION, 5)),
            bestHour = BestHourBlock(5, 20, 400.0, tripCount = 5), // info
        )
        val types = engine.recommend(context).map { it.type }
        assertEquals(listOf(RecommendationType.WARNING, RecommendationType.INFO, RecommendationType.POSITIVE), types)
    }

    @Test
    fun `empty context yields no recommendations`() {
        assertTrue(engine.recommend(RecommendationContext()).isEmpty())
    }

    @Test
    fun `a fresh driver with thin data gets no recommendations`() {
        // 2 trips, 1 hour, no streak, no percentiles, no offers — every guard must hold.
        val thin = RecommendationContext(
            earningsPerHour = 50.0,
            earningsPerTrip = 25.0,
            hoursOnline = 1.0,
            totalTrips = 2,
            streakWeeks = 1,
        )
        assertTrue(engine.recommend(thin).isEmpty())
    }
}
