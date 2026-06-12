package mx.kompara.overlay

import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.CostProfile
import mx.kompara.metrics.OfferMetrics
import mx.kompara.metrics.TripOffer
import mx.kompara.metrics.Verdict
import mx.kompara.metrics.VerdictLevel
import mx.kompara.data.settings.PlatformThreshold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the verdict -> chip projection: formatting, the em-dash for gaps, and the hint kind. */
class VerdictChipStateTest {

    private val engine = NetProfitEngine()

    private fun metricsFor(offer: TripOffer): OfferMetrics =
        engine.evaluate(
            offer,
            CostProfile.ZERO,
            PlatformThreshold(minPerKmMxn = 8.0, minPerHourMxn = 140.0),
        )

    @Test
    fun `full offer formats all figures and shows no hint`() {
        val metrics = metricsFor(
            TripOffer("uber", fareMxn = 100.0, pickupKm = 2.0, pickupMin = 5.0, tripKm = 8.0, tripMin = 15.0),
        )
        val state = VerdictChipState.from(metrics)
        // total km = 10, total min = 20; net = gross (zero cost) = 100
        assertEquals("$10.00/km", state.netPerKm)
        assertEquals("$300/h", state.netPerHour) // 100 / (20/60), whole pesos for glanceability
        assertEquals("$100.00", state.netProfit)
        assertEquals("$5.00/min", state.netPerMin)
        assertEquals("$10.00/km", state.grossPerKm)
        assertFalse(state.hasMissingData)
        assertEquals(VerdictChipState.MissingHintKind.NONE, state.missingHintKind)
    }

    @Test
    fun `missing distance yields em-dash rates and a distance hint`() {
        val metrics = metricsFor(
            // no pickup/trip km at all -> totalKm null -> per-km rates null
            TripOffer("uber", fareMxn = 80.0, pickupKm = null, pickupMin = 5.0, tripKm = null, tripMin = 15.0),
        )
        val state = VerdictChipState.from(metrics)
        assertEquals(VerdictChipState.MISSING, state.netPerKm)
        assertEquals(VerdictChipState.MISSING, state.grossPerKm)
        assertEquals("$80.00", state.netProfit)
        assertTrue(state.hasMissingData)
        assertEquals(VerdictChipState.MissingHintKind.DISTANCE, state.missingHintKind)
    }

    @Test
    fun `missing fare yields em-dash everywhere and a fare hint`() {
        val metrics = metricsFor(
            TripOffer("uber", fareMxn = null, pickupKm = 2.0, pickupMin = 5.0, tripKm = 8.0, tripMin = 15.0),
        )
        val state = VerdictChipState.from(metrics)
        assertEquals(VerdictChipState.MISSING, state.netProfit)
        assertEquals(VerdictChipState.MISSING, state.netPerKm)
        assertEquals(VerdictChipState.MISSING, state.netPerHour)
        assertEquals(VerdictLevel.RED, state.level)
        assertEquals(VerdictChipState.MissingHintKind.FARE, state.missingHintKind)
    }

    @Test
    fun `missing time yields em-dash per-hour but keeps per-km`() {
        val metrics = metricsFor(
            TripOffer("uber", fareMxn = 80.0, pickupKm = 2.0, pickupMin = null, tripKm = 8.0, tripMin = null),
        )
        val state = VerdictChipState.from(metrics)
        assertEquals("$8.00/km", state.netPerKm)
        assertEquals(VerdictChipState.MISSING, state.netPerHour)
        assertTrue(state.hasMissingData)
    }

    @Test
    fun `level passes through from the engine verdict`() {
        val good = OfferMetrics(
            grossMxn = 100.0, netMxn = 100.0, totalKm = 10.0, totalMin = 20.0,
            grossPerKm = 10.0, grossPerMin = 5.0, netPerKm = 10.0, netPerMin = 5.0, netPerHour = 300.0,
            verdict = Verdict(VerdictLevel.GREEN, 10.0, 300.0, 100.0, 10.0, emptyList()),
        )
        assertEquals(VerdictLevel.GREEN, VerdictChipState.from(good).level)
    }
}
