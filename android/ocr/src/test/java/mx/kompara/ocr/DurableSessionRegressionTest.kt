package mx.kompara.ocr

import mx.kompara.parsers.model.HostRect
import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.model.OfferInstanceEvidence
import org.junit.Assert.*
import org.junit.Test

/** Direct lifetime regressions complement parser -> recorder -> DAO replays. */
class DurableSessionRegressionTest {
    private val original = OfferCard(UberOcrParser.UBER_PACKAGE, variant = "exclusive", fare = 79.45,
        pickupDistanceKm = .2, pickupEtaMin = 1.0, tripDistanceKm = 2.4, tripDurationMin = 24.0,
        instanceEvidence = OfferInstanceEvidence("UBER PRIORITY", "ACEPTAR", actionBounds = HostRect(440,1990,640,2045)))

    @Test fun `animation origins track continuously without replacing an offer`() {
        val tracker = OcrOfferSessionTracker()
        val first = tracker.parsed(original, 100)
        repeat(10) { n ->
            val moving = original.copy(instanceEvidence = original.instanceEvidence!!.copy(
                actionBounds = HostRect(440,1990 + 30*n,640,2045 + 30*n)))
            assertEquals(first.sessionId, tracker.parsed(moving, 1000L + n*900).sessionId)
        }
    }

    @Test fun `one persistent route change is insufficient replacement evidence`() {
        val tracker = OcrOfferSessionTracker()
        val first = tracker.parsed(original, 100)
        repeat(4) { n ->
            assertEquals(first.sessionId, tracker.parsed(original.copy(tripDistanceKm = 2.5), 1000L+n*900).sessionId)
        }
    }

    @Test fun `both action and route must agree twice before replacement`() {
        val tracker = OcrOfferSessionTracker()
        val first = tracker.parsed(original, 100)
        val changed = original.copy(tripDistanceKm = 3.5, instanceEvidence = original.instanceEvidence!!.copy(action = "COINCIDIR"))
        assertEquals(first.sessionId, tracker.parsed(changed, 1000).sessionId)
        assertNotEquals(first.sessionId, tracker.parsed(changed, 1900).sessionId)
    }

    @Test fun `missing higher priority surge label cannot replace independent exclusive offer`() {
        val tracker = OcrOfferSessionTracker()
        val first = tracker.parsed(original.copy(variant = "surge", surge = true), 100)
        assertEquals(first.sessionId, tracker.parsed(original, 1000).sessionId)
        assertEquals(first.sessionId, tracker.parsed(original.copy(variant = "surge", surge = true), 1900).sessionId)
    }
}
