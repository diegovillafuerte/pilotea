package mx.kompara.metrics

import mx.kompara.data.accounting.RunningCostInputs
import mx.kompara.data.accounting.RunningCostMode
import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.db.entity.OfferEvaluation
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.RateBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pon Tu Precio (plan 4.4) on the kit's DiDi bids: S5 $90.05 for 22.9 km and S9 $96 for 10.4 km.
 * Cost per km 24.5 ÷ 12 + 0.8 = 2.841667, so the running expense of 22.9 km is 65.0742 and of 10.4 km 29.5533.
 */
class CounterOffersTest {
    private val engine = NetProfitEngine()
    private val cost = RunningCostInputs(RunningCostMode.GAS, BigDecimal("0.8"), BigDecimal("24.5"), BigDecimal("12")).costPerKm!!
    private val neto = PlatformThreshold(8.0, 200.0, 6.0, 150.0)
    private val bruto = PlatformThreshold(11.0, 280.0, 9.0, 230.0)

    private fun bid(fare: Double, km: Double, minutes: Double) = TripOffer("DIDI", fare, 0.0, 0.0, km, minutes)
    private fun counters(offer: TripOffer, limits: PlatformThreshold, unit: PreferredMetric, basis: RateBasis): Pair<VerdictLevel, CounterOffers?> {
        val m = engine.evaluate(offer, cost, limits, unit, basis)
        val km = unit == PreferredMetric.IPK
        val goal = (if (km) limits.minPerKmMxn else limits.minPerHourMxn).toBigDecimal()
        val minimum = (if (km) limits.redPerKmMxn else limits.redPerHourMxn).toBigDecimal()
        return m.verdict.level to CounterOffers.of(m, goal, minimum, unit, basis)
    }

    @Test fun `neto per km, below the minimum, shows both`() {
        val offer = bid(90.05, 22.9, 40.0)
        // net = 90.05 − 65.0742 = 24.9758; 24.9758 / 22.9 = 1.09 per km → red
        assertEquals(BigDecimal("1.09"), engine.evaluate(offer, cost, neto).netPerKm!!.toBigDecimal().setScale(2, RoundingMode.HALF_UP))
        // minimum: 6 × 22.9 + 65.0742 = 202.47 → 203; goal: 8 × 22.9 + 65.0742 = 248.27 → 249
        assertEquals(VerdictLevel.RED to CounterOffers(203, 249), counters(offer, neto, PreferredMetric.IPK, RateBasis.NET))
    }

    @Test fun `neto per km, between the limits, shows only the goal's`() {
        val offer = bid(96.0, 10.4, 22.0)
        // net = 96 − 29.5533 = 66.4467; / 10.4 = 6.39 per km → yellow
        assertEquals(BigDecimal("6.39"), engine.evaluate(offer, cost, neto).netPerKm!!.toBigDecimal().setScale(2, RoundingMode.HALF_UP))
        // minimum: 6 × 10.4 + 29.5533 = 91.95 ≤ 96 → none; goal: 8 × 10.4 + 29.5533 = 112.75 → 113
        assertEquals(VerdictLevel.YELLOW to CounterOffers(null, 113), counters(offer, neto, PreferredMetric.IPK, RateBasis.NET))
    }

    @Test fun `above the goal there are none`() {
        // $148 for 12 km: 9.49 neto per km ≥ 8
        assertEquals(VerdictLevel.GREEN to CounterOffers(null, null), counters(bid(148.0, 12.0, 24.0), neto, PreferredMetric.IPK, RateBasis.NET))
    }

    @Test fun `bruto per km has no running expense`() {
        // 90.05 / 22.9 = 3.93 → red; minimum 9 × 22.9 = 206.1 → 207; goal 11 × 22.9 = 251.9 → 252
        assertEquals(VerdictLevel.RED to CounterOffers(207, 252), counters(bid(90.05, 22.9, 40.0), bruto, PreferredMetric.IPK, RateBasis.GROSS))
        // 96 / 10.4 = 9.23 → yellow; minimum 9 × 10.4 = 93.6 ≤ 96 → none; goal 11 × 10.4 = 114.4 → 115
        assertEquals(VerdictLevel.YELLOW to CounterOffers(null, 115), counters(bid(96.0, 10.4, 22.0), bruto, PreferredMetric.IPK, RateBasis.GROSS))
        // 148 / 12 = 12.33 ≥ 11 → none
        assertEquals(VerdictLevel.GREEN to CounterOffers(null, null), counters(bid(148.0, 12.0, 24.0), bruto, PreferredMetric.IPK, RateBasis.GROSS))
    }

    @Test fun `neto per hour adds the running expense to the limit times the hours`() {
        // 40 min: net per hour = 24.9758 × 60 / 40 = 37.46 → red against 200 / 150
        // minimum 150 × 40 / 60 + 65.0742 = 165.07 → 166; goal 200 × 40 / 60 + 65.0742 = 198.41 → 199
        assertEquals(VerdictLevel.RED to CounterOffers(166, 199), counters(bid(90.05, 22.9, 40.0), neto, PreferredMetric.IPH, RateBasis.NET))
        // 22 min: 66.4467 × 60 / 22 = 181.2 → yellow; minimum 150 × 22 / 60 + 29.5533 = 84.55 ≤ 96 → none;
        // goal 200 × 22 / 60 + 29.5533 = 102.89 → 103
        assertEquals(VerdictLevel.YELLOW to CounterOffers(null, 103), counters(bid(96.0, 10.4, 22.0), neto, PreferredMetric.IPH, RateBasis.NET))
    }

    @Test fun `bruto per hour is the limit times the hours`() {
        // 90.05 × 60 / 40 = 135.08 → red against 280 / 230; minimum 230 × 40 / 60 = 153.33 → 154; goal 280 × 40 / 60 = 186.67 → 187
        assertEquals(VerdictLevel.RED to CounterOffers(154, 187), counters(bid(90.05, 22.9, 40.0), bruto, PreferredMetric.IPH, RateBasis.GROSS))
        // an exact peso is not rounded up: $99 in 25 min = 237.6 per hour → yellow against 240 / 228;
        // minimum 228 × 25 / 60 = 95 ≤ 99 → none; goal 240 × 25 / 60 = 100 exactly → $100
        assertEquals(VerdictLevel.YELLOW to CounterOffers(null, 100),
            counters(bid(99.0, 10.0, 25.0), PlatformThreshold(11.0, 240.0, 9.0, 228.0), PreferredMetric.IPH, RateBasis.GROSS))
    }

    @Test fun `unknown inputs give no counter-offers`() {
        val noCost = engine.evaluate(bid(90.05, 22.9, 40.0), null, neto)
        assertNull(CounterOffers.of(noCost, BigDecimal(8), BigDecimal(6), PreferredMetric.IPK, RateBasis.NET))
        val noTime = engine.evaluate(TripOffer("DIDI", 90.05, 0.0, null, 22.9, 40.0), cost, neto)
        assertNull(CounterOffers.of(noTime, BigDecimal(200), BigDecimal(150), PreferredMetric.IPH, RateBasis.NET))
    }

    @Test fun `a row uses its frozen goal, minimum, unit and basis`() {
        val m = engine.evaluate(bid(90.05, 22.9, 40.0), cost, neto)
        val row = OfferEntity(seenAt = 1, platform = "DIDI", fareMxn = 90.05, distanceKm = m.totalKm, durationMin = m.totalMin,
            verdict = m.verdict.level.name, evaluation = OfferEvaluation(1, "IPK", "8", "6", 1, "OCR", "DRIVER_PAYMENT",
                m.contributionDecimal, m.perKmDecimal, m.perHourDecimal, null))
        assertEquals(CounterOffers(203, 249), row.counterOffers())
        assertEquals(CounterOffers(207, 252), row.copy(evaluation = row.evaluation!!.copy(goal = "11", minimum = "9", rateBasis = "GROSS")).counterOffers())
        assertNull(row.copy(evaluation = null).counterOffers())
    }
}
