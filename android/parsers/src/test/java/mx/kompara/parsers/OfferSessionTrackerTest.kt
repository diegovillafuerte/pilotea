package mx.kompara.parsers

import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.model.OfferSessionTracker
import org.junit.Assert.*
import org.junit.Test

class OfferSessionTrackerTest {
    private val card = OfferCard("uber", fare = 148.0, tripDistanceKm = 10.0)
    @Test fun `distance jitter and duplicate consumption keep one identity`() {
        val sessions = OfferSessionTracker()
        val first = sessions.parsed(card, 1_000)
        assertEquals(first, sessions.parsed(card, 1_000))
        assertEquals(first.sessionId, sessions.parsed(card.copy(tripDistanceKm = 100.0), 1_300).sessionId)
        sessions.missing(1_500)
        assertEquals(first.sessionId, sessions.parsed(card, 1_999).sessionId)
    }
    @Test fun `host switch and actual disappearance create new identities even for equal money`() {
        val sessions = OfferSessionTracker()
        val first = sessions.parsed(card, 1_000)
        assertNotEquals(first.sessionId, sessions.parsed(card.copy(platform = "didi"), 1_100).sessionId)
        val back = sessions.parsed(card, 1_200)
        assertNotEquals(first.sessionId, back.sessionId)
        sessions.missing(1_300)
        assertNotEquals(back.sessionId, sessions.parsed(card, 1_801).sessionId)
    }
    @Test fun `a partial first frame learns its fare before the next offer`() {
        val sessions = OfferSessionTracker()
        val partial = sessions.parsed(card.copy(fare = null), 1_000)
        val recovered = sessions.parsed(card, 1_100)
        assertEquals(partial.sessionId, recovered.sessionId)
        assertEquals(1_000L, recovered.sessionStartedAt)
        assertEquals(recovered.sessionId, sessions.parsed(card.copy(fare = null), 1_200).sessionId)
        assertNotEquals(recovered.sessionId, sessions.parsed(card.copy(fare = 200.0), 1_300).sessionId)
    }
    @Test fun `restart never reuses a different offers identity`() {
        val sessions = OfferSessionTracker()
        val first = sessions.parsed(card, 1_000)
        sessions.reset()
        val next = sessions.parsed(card, 2_000)
        assertNotEquals(first.sessionId, next.sessionId)
        assertEquals(2_000L, next.sessionStartedAt)
    }
}
