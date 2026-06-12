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
        // An untestable metric gets no explainer — the missing-data hint covers it.
        assertEquals(VerdictChipState.ExplainKind.NONE, state.explainKind)
    }

    @Test
    fun `explainer names which floor fell short`() {
        // Floors 8/140 (zero costs): full offer nets 10/km and 300/h -> both strong.
        val strong = metricsFor(
            TripOffer("uber", fareMxn = 100.0, pickupKm = 2.0, pickupMin = 5.0, tripKm = 8.0, tripMin = 15.0),
        )
        assertEquals(
            VerdictChipState.ExplainKind.BOTH_STRONG,
            VerdictChipState.from(strong).explainKind,
        )

        // Long slow trip: 100 over 20 km / 60 min -> 5/km (below 8) but 100/h... below 140 too.
        // Use a fare that keeps the hour green: 150 over 20 km / 60 min -> 7.5/km weak, 150/h green.
        val kmWeak = metricsFor(
            TripOffer("uber", fareMxn = 150.0, pickupKm = 5.0, pickupMin = 10.0, tripKm = 15.0, tripMin = 50.0),
        )
        assertEquals(
            VerdictChipState.ExplainKind.KM_WEAK,
            VerdictChipState.from(kmWeak).explainKind,
        )

        // Short well-paid km but slow: 80 over 5 km / 60 min -> 16/km green, 80/h below 140.
        val hourWeak = metricsFor(
            TripOffer("uber", fareMxn = 80.0, pickupKm = 1.0, pickupMin = 10.0, tripKm = 4.0, tripMin = 50.0),
        )
        assertEquals(
            VerdictChipState.ExplainKind.HOUR_WEAK,
            VerdictChipState.from(hourWeak).explainKind,
        )

        // Lowball: 40 over 10 km / 30 min -> 4/km and 80/h, both under their green floors.
        val bothWeak = metricsFor(
            TripOffer("uber", fareMxn = 40.0, pickupKm = 2.0, pickupMin = 6.0, tripKm = 8.0, tripMin = 24.0),
        )
        assertEquals(
            VerdictChipState.ExplainKind.BOTH_WEAK,
            VerdictChipState.from(bothWeak).explainKind,
        )
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
