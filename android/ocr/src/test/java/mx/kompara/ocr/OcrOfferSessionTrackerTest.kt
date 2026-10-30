package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard
import org.junit.Assert.*
import org.junit.Test

class OcrOfferSessionTrackerTest {
    private val card = OfferCard(UberOcrParser.UBER_PACKAGE, fare = 147.46,
        pickupDistanceKm = 0.7, pickupEtaMin = 4.0, tripDistanceKm = 6.3, tripDurationMin = 49.0)

    @Test fun `same route retains identity across missing fare and confirmed fare changes`() {
        val sessions = OcrOfferSessionTracker()
        val first = sessions.parsed(card, 100)
        for ((index, fare) in listOf(null, null, 147.46, 79.45).withIndex()) {
            val next = sessions.parsed(card.copy(fare = fare), 200L + index)
            assertEquals(first.sessionId, next.sessionId)
            assertEquals(100L, next.sessionStartedAt)
        }
    }

    @Test fun `each changed route field or host starts a session even with no fare`() {
        for (other in listOf(card.copy(platform = DidiOcrParser.DIDI_PACKAGE),
            card.copy(pickupDistanceKm = 0.8), card.copy(pickupEtaMin = 5.0),
            card.copy(tripDistanceKm = 6.4), card.copy(tripDurationMin = 50.0))) {
            val sessions = OcrOfferSessionTracker()
            val first = sessions.parsed(card, 100)
            val next = sessions.parsed(other.copy(fare = null), 200)
            assertNotEquals(first.sessionId, next.sessionId)
            assertEquals(200L, next.sessionStartedAt)
        }
    }

    @Test fun `unknown route and explicit disappearance cannot reuse a previous session`() {
        val sessions = OcrOfferSessionTracker()
        val first = sessions.parsed(card, 100)
        val unknown = sessions.parsed(card.copy(pickupEtaMin = null, fare = null), 200)
        assertNotEquals(first.sessionId, unknown.sessionId)
        val restored = sessions.parsed(card, 300)
        assertNotEquals(first.sessionId, restored.sessionId)
        assertNotEquals(unknown.sessionId, restored.sessionId)
        sessions.reset()
        assertNotEquals(restored.sessionId, sessions.parsed(card.copy(fare = null), 400).sessionId)
    }
}
