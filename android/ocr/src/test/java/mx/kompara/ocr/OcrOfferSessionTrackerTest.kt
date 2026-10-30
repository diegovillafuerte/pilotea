package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.model.OfferInstanceEvidence
import mx.kompara.parsers.model.HostRect
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

    @Test fun `sustained changed route fields start a session but one numeric misread does not`() {
        for (other in listOf(card.copy(pickupDistanceKm = 0.8), card.copy(pickupEtaMin = 5.0),
            card.copy(tripDistanceKm = 6.4), card.copy(tripDurationMin = 50.0))) {
            val sessions = OcrOfferSessionTracker()
            val first = sessions.parsed(card, 100)
            val uncertain = sessions.parsed(other.copy(fare = null), 200)
            assertEquals(first.sessionId, uncertain.sessionId)
            val next = sessions.parsed(other.copy(fare = null), 300)
            assertNotEquals(first.sessionId, next.sessionId)
            assertEquals(300L, next.sessionStartedAt)
        }
    }

    @Test fun `unknown route keeps durable identity while explicit disappearance ends the instance`() {
        val sessions = OcrOfferSessionTracker()
        val first = sessions.parsed(card, 100)
        val unknown = sessions.parsed(card.copy(pickupEtaMin = null, fare = null), 200)
        assertEquals(first.sessionId, unknown.sessionId)
        val restored = sessions.parsed(card, 300)
        assertEquals(first.sessionId, restored.sessionId)
        assertEquals(unknown.sessionId, restored.sessionId)
        sessions.reset()
        assertNotEquals(restored.sessionId, sessions.parsed(card.copy(fare = null), 400).sessionId)
    }

    @Test fun `brief numeric misreads and partial fields never create durable instances`() {
        val sessions = OcrOfferSessionTracker()
        val first = sessions.parsed(card, 100)
        for ((i, frame) in listOf(card.copy(pickupEtaMin = 40.0), card,
            card.copy(tripDistanceKm = null), card.copy(pickupEtaMin = null), card).withIndex()) {
            val next = sessions.parsed(frame, 200L + i * 300)
            assertEquals(first.sessionId, next.sessionId)
            assertEquals(first.sessionStartedAt, next.sessionStartedAt)
        }
    }

    @Test fun `host variant product action and displaced anchors replace identical legs`() {
        val evidence = OfferInstanceEvidence("UBER PRIORITY", "ACEPTAR", HostRect(90, 650, 500, 740),
            HostRect(440, 1990, 640, 2045), 2)
        val original = card.copy(variant = "exclusive", instanceEvidence = evidence)
        for (replacement in listOf(original.copy(platform = DidiOcrParser.DIDI_PACKAGE),
            original.copy(variant = "trip_radar"),
            original.copy(instanceEvidence = evidence.copy(product = "UBERX")),
            original.copy(instanceEvidence = evidence.copy(action = "COINCIDIR")),
            original.copy(instanceEvidence = evidence.copy(headingBounds = HostRect(90, 900, 500, 990))),
            original.copy(instanceEvidence = evidence.copy(actionBounds = HostRect(440, 1700, 640, 1755))))) {
            val sessions = OcrOfferSessionTracker()
            val first = sessions.parsed(original, 100)
            val next = sessions.parsed(replacement.copy(fare = null), 200)
            assertNotEquals(first.sessionId, next.sessionId)
            assertEquals(200L, next.sessionStartedAt)
        }
    }

    @Test fun `missing tokens do not erase replacement evidence or create a record`() {
        val sessions = OcrOfferSessionTracker()
        val first = sessions.parsed(card.copy(variant = "exclusive",
            instanceEvidence = OfferInstanceEvidence(product = "UBER PRIORITY", action = "ACEPTAR")), 100)
        assertEquals(first.sessionId, sessions.parsed(card.copy(fare = null), 200).sessionId)
        assertNotEquals(first.sessionId, sessions.parsed(card.copy(fare = null,
            instanceEvidence = OfferInstanceEvidence(action = "COINCIDIR")), 300).sessionId)
    }

    @Test fun `a secondary card disappearance loses confidence without duplicating the active record`() {
        val sessions = OcrOfferSessionTracker()
        val stacked = card.copy(instanceEvidence = OfferInstanceEvidence(visibleCards = 2))
        val first = sessions.parsed(stacked, 100)
        val one = stacked.copy(instanceEvidence = OfferInstanceEvidence(visibleCards = 1))
        assertTrue(sessions.stackChanged(one))
        assertEquals(first.sessionId, sessions.parsed(one, 200).sessionId)
        assertFalse(sessions.stackChanged(one))
    }

    @Test fun `small anchor steps cannot drift beyond the instance geometry tolerance`() {
        val sessions = OcrOfferSessionTracker()
        val initial = card.copy(instanceEvidence = OfferInstanceEvidence(headingBounds = HostRect(90, 650, 500, 740)))
        val first = sessions.parsed(initial, 100)
        val small = initial.copy(instanceEvidence = OfferInstanceEvidence(headingBounds = HostRect(90, 670, 500, 760)))
        assertEquals(first.sessionId, sessions.parsed(small, 200).sessionId)
        val moved = initial.copy(instanceEvidence = OfferInstanceEvidence(headingBounds = HostRect(90, 690, 500, 780)))
        assertNotEquals(first.sessionId, sessions.parsed(moved, 300).sessionId)
    }

    @Test fun `independently measured card bounds replace an instance beyond tolerance`() {
        val display = HostRect(0, 0, 1080, 2340)
        val measured = mx.kompara.parsers.model.HostGeometry(UberOcrParser.UBER_PACKAGE, "measured", 100,
            display, mx.kompara.parsers.model.HostOrientation.PORTRAIT, HostRect(0, 0, 1080, 200),
            HostRect(0, 650, 1080, 2340), emptyList(), emptyList())
        val sessions = OcrOfferSessionTracker()
        val first = sessions.parsed(card.copy(hostGeometry = measured), 100)
        assertNotEquals(first.sessionId, sessions.parsed(card.copy(fare = null,
            hostGeometry = measured.copy(offerCard = HostRect(0, 400, 1080, 2340))), 200).sessionId)
    }
}
