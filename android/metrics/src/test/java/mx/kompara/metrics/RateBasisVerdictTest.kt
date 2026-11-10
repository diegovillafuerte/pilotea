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

/** The v4 kit's figures (fixture.json): cost 24.5 ÷ 12 + 0.8 = 2.8417 per km; limits 8 / 6 neto ↔ 11 / 9 bruto. */
class RateBasisVerdictTest {
    private val engine = NetProfitEngine()
    private val cost = RunningCostInputs(RunningCostMode.GAS, BigDecimal("0.8"), BigDecimal("24.5"), BigDecimal("12")).costPerKm!!
    private val neto = PlatformThreshold(minPerKmMxn = 8.0, minPerHourMxn = 200.0, redPerKmMxn = 6.0, redPerHourMxn = 150.0)
    private val bruto = PlatformThreshold(minPerKmMxn = 11.0, minPerHourMxn = 280.0, redPerKmMxn = 9.0, redPerHourMxn = 230.0)

    /** The kit's S1 offer: $148, 12.0 km, 24 min (2 km / 4 min pickup + 10 km / 20 min trip). */
    private val verde = TripOffer("UBER", 148.0, 2.0, 4.0, 10.0, 20.0)

    private fun BigDecimal?.cents() = this!!.setScale(2, RoundingMode.HALF_UP)
    private fun Double?.cents() = this!!.toBigDecimal().cents()

    @Test fun `neto judges 9·49 per km green and keeps the gross rates`() {
        val m = engine.evaluate(verde, cost, neto)
        // net = 148 − 12 × 2.8417 = 113.90; 113.90 / 12 = 9.49 ≥ 8
        assertEquals(VerdictLevel.GREEN, m.verdict.level)
        assertEquals(RateBasis.NET, m.verdict.rateBasis)
        assertEquals(BigDecimal("9.49"), m.netPerKm.cents())
        // net per hour = 113.90 × 60 / 24 = 284.75, shown $285
        assertEquals(BigDecimal("285"), m.netPerHour!!.toBigDecimal().setScale(0, RoundingMode.HALF_UP))
        assertEquals(BigDecimal("12.33"), m.grossPerKm.cents()) // 148 / 12
        assertEquals(BigDecimal("370.00"), m.grossPerHour.cents()) // 148 × 60 / 24
        assertEquals(BigDecimal("9.49"), m.rate(PreferredMetric.IPK, RateBasis.NET).cents())
        assertEquals(BigDecimal("12.33"), m.rate(PreferredMetric.IPK, RateBasis.GROSS).cents())
        assertEquals(BigDecimal("284.75"), m.rate(PreferredMetric.IPH, RateBasis.NET).cents())
        assertEquals(BigDecimal("370.00"), m.rate(PreferredMetric.IPH, RateBasis.GROSS).cents())
    }

    @Test fun `bruto judges 12·33 per km green against 11 and 9`() {
        val m = engine.evaluate(verde, cost, bruto, rateBasis = RateBasis.GROSS)
        assertEquals(VerdictLevel.GREEN, m.verdict.level)
        assertEquals(RateBasis.GROSS, m.verdict.rateBasis)
        assertEquals(VerdictLevel.GREEN, m.verdict.netPerKmLevel) // the km level, in the verdict's basis
        assertEquals(BigDecimal("9.49"), m.netPerKm.cents()) // net stays computable
    }

    @Test fun `the kit's yellow and red offers in both bases`() {
        // S2 $120, 12 km: neto (120 − 34.10) / 12 = 7.16 → yellow; bruto 120 / 12 = 10.00 → yellow (9 ≤ 10 < 11)
        val amarillo = TripOffer("UBER", 120.0, 2.0, 5.0, 10.0, 22.0)
        assertEquals(VerdictLevel.YELLOW, engine.evaluate(amarillo, cost, neto).verdict.level)
        assertEquals(VerdictLevel.YELLOW, engine.evaluate(amarillo, cost, bruto, rateBasis = RateBasis.GROSS).verdict.level)
        // S3 $62, 12.3 km: neto (62 − 34.95) / 12.3 = 2.20 → red; bruto 62 / 12.3 = 5.04 → red
        val rojo = TripOffer("UBER", 62.0, 2.3, 6.0, 10.0, 24.0)
        assertEquals(VerdictLevel.RED, engine.evaluate(rojo, cost, neto).verdict.level)
        assertEquals(VerdictLevel.RED, engine.evaluate(rojo, cost, bruto, rateBasis = RateBasis.GROSS).verdict.level)
    }

    @Test fun `per hour in bruto judges the fare per hour`() {
        // 148 × 60 / 24 = 370 ≥ 280 → green; with 400 / 380 limits it is red
        assertEquals(VerdictLevel.GREEN, engine.evaluate(verde, cost, bruto, PreferredMetric.IPH, RateBasis.GROSS).verdict.level)
        assertEquals(VerdictLevel.RED, engine.evaluate(verde, cost, bruto.copy(minPerHourMxn = 400.0, redPerHourMxn = 380.0),
            PreferredMetric.IPH, RateBasis.GROSS).verdict.level)
    }

    @Test fun `in bruto a missing cost does not block the verdict, in neto it does`() {
        val gross = engine.evaluate(verde, null, bruto, rateBasis = RateBasis.GROSS)
        assertEquals(VerdictLevel.GREEN, gross.verdict.level); assertNull(gross.verdict.unavailableReason)
        assertNull(gross.netMxn)
        val net = engine.evaluate(verde, null, neto)
        assertEquals(VerdictLevel.UNKNOWN, net.verdict.level); assertEquals("COST", net.verdict.unavailableReason)
    }

    @Test fun `only a driver payment is judged, in either basis`() {
        val unconfirmed = verde.copy(paymentBasis = "UNCONFIRMED")
        for (basis in RateBasis.entries) {
            val m = engine.evaluate(unconfirmed, cost, if (basis == RateBasis.NET) neto else bruto, rateBasis = basis)
            assertEquals(VerdictLevel.UNKNOWN, m.verdict.level); assertEquals("PAYMENT_BASIS", m.verdict.unavailableReason)
        }
    }

    @Test fun `bruto per hour needs no distance, bruto per km does`() {
        val noKm = verde.copy(pickupKm = null)
        assertEquals(VerdictLevel.GREEN, engine.evaluate(noKm, cost, bruto, PreferredMetric.IPH, RateBasis.GROSS).verdict.level)
        assertEquals("DISTANCE", engine.evaluate(noKm, cost, bruto, PreferredMetric.IPK, RateBasis.GROSS).verdict.unavailableReason)
        assertEquals("DISTANCE", engine.evaluate(noKm, cost, neto, PreferredMetric.IPH).verdict.unavailableReason)
    }

    @Test fun `a frozen row keeps its basis and old rows read as NET`() {
        val gross = OfferEvaluation(1, "IPK", "11", "9", 1, "OCR", "DRIVER_PAYMENT", "113.9", "9.4916", "284.75", null, "GROSS")
        val row = OfferEntity(seenAt = 1, platform = "UBER", fareMxn = 148.0, distanceKm = 12.0, durationMin = 24.0,
            verdict = "GREEN", evaluation = gross)
        val m = row.recordedMetrics()
        assertEquals(RateBasis.GROSS, m.verdict.rateBasis)
        assertEquals(BigDecimal("12.33"), m.rate(PreferredMetric.IPK, RateBasis.GROSS).cents())
        assertEquals(BigDecimal("370.00"), m.rate(PreferredMetric.IPH, RateBasis.GROSS).cents())
        assertEquals(BigDecimal("9.49"), m.rate(PreferredMetric.IPK, RateBasis.NET).cents())
        val old = OfferEvaluation(1, "IPK", "8", "6", 1, "OCR", "DRIVER_PAYMENT", "113.9", "9.4916", "284.75", null)
        assertEquals(RateBasis.NET, row.copy(evaluation = old).recordedMetrics().verdict.rateBasis)
    }
}
