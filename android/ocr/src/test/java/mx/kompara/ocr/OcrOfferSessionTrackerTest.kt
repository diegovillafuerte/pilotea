package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.model.OfferInstanceEvidence
import mx.kompara.parsers.model.HostRect
import org.junit.Assert.*
import org.junit.Test

class OcrOfferSessionTrackerTest {
    private val card = OfferCard(UberOcrParser.UBER_PACKAGE, fare = 147.46,
        pickupDistanceKm = 0.7, pickupEtaMin = 4.0, tripDistanceKm = 6.3, tripDurationMin = 49.0,
        instanceEvidence = OfferInstanceEvidence(action = "ACEPTAR"))

    @Test fun `same route retains identity across missing fare and confirmed fare changes`() {
        val sessions = OcrOfferSessionTracker()
        val first = sessions.parsed(card, 100)
        for ((index, fare) in listOf(null, null, 147.46, 79.45).withIndex()) {
            val next = sessions.parsed(card.copy(fare = fare), 200L + index)
            assertEquals(first.sessionId, next.sessionId)
            assertEquals(100L, next.sessionStartedAt)
        }
    }

    // 0.2.14: the replacement key is main's trip leg; pickup (live ETA) only corroborates.
    @Test fun `a sustained trip leg change starts a session but one frame or a pickup change does not`() {
        for (other in listOf(card.copy(tripDistanceKm = 6.4), card.copy(tripDurationMin = 50.0))) {
            val sessions = OcrOfferSessionTracker()
            val first = sessions.parsed(card, 100)
            val corroborated = other.copy(fare = null, instanceEvidence = OfferInstanceEvidence(action = "ME INTERESA"))
            val uncertain = sessions.parsed(corroborated, 200)
            assertEquals(first.sessionId, uncertain.sessionId)
            val next = sessions.parsed(corroborated, 300)
            assertNotEquals(first.sessionId, next.sessionId)
            assertEquals(300L, next.sessionStartedAt)
        }
        for (other in listOf(card.copy(pickupDistanceKm = 0.8), card.copy(pickupEtaMin = 5.0))) {
            val sessions = OcrOfferSessionTracker()
            val first = sessions.parsed(card, 100)
            repeat(3) { assertEquals(first.sessionId, sessions.parsed(other.copy(fare = null), 200L + it * 100).sessionId) }
        }
    }

    @Test fun `a masked pending frame publishes its own legs without payment and votes from visible legs`() {
        // Round 2 (B7): our chip mask really covers the heading-less (DiDi) baseline's card evidence.
        val headless = card.copy(platform = DidiOcrParser.DIDI_PACKAGE)
        val mask = HostRect(28, 860, 764, 1196)
        val geometry = { c: OfferCard, overlay: HostRect? -> c.copy(hostGeometry = mx.kompara.parsers.model.HostGeometry(
            c.platform, "t", 0, HostRect(0, 0, 1080, 2340), mx.kompara.parsers.model.HostOrientation.PORTRAIT,
            HostRect(0, 0, 1080, 91), HostRect(0, 900, 1080, 2340), emptyList(), emptyList(),
            ocrEvidence = mx.kompara.parsers.model.HostOcrGeometryEvidence(overlay, emptyList(), emptyList(), false))) }
        val didi = OcrOfferSessionTracker()
        val didiFirst = didi.parsed(geometry(headless, null), 100)
        assertTrue("The mask covers the baseline's card evidence", didi.obscuredBy(mask))
        val next = headless.copy(fare = 61.20, tripDistanceKm = 5.1, tripDurationMin = 14.0)
        val pending = didi.update(geometry(next, mask), 200)
        assertEquals(didiFirst.sessionId, pending.card!!.sessionId)
        assertNull("Another card never shows the held payment", pending.card!!.fare)
        assertEquals(5.1, pending.card!!.tripDistanceKm!!, 0.0)
        assertTrue(pending.identityUnresolved)
        val replaced = didi.update(geometry(next, mask), 300)
        assertTrue("A heading-less card under our mask still replaces", replaced.replaced)
        assertNotEquals(didiFirst.sessionId, replaced.card!!.sessionId)
        assertEquals("The pending frame's clean fare seeds the new instance", 61.20, replaced.seed!!.fare!!, 0.0)
    }

    @Test fun `a guard-rejected pending fare seeds a replacement only when the confirming frame agrees`() {
        // Round 2 (A1 (b)): identity confirmation is not payment admission.
        val next = card.copy(fare = 61.20, tripDistanceKm = 5.1, tripDurationMin = 14.0)
        fun confirm(pendingAccepted: Boolean, confirming: OfferCard): OfferCard? {
            val sessions = OcrOfferSessionTracker()
            sessions.parsed(card, 100)
            sessions.update(next, 200, guardAccepted = pendingAccepted)
            return sessions.update(confirming, 300, guardAccepted = false).also { assertTrue(it.replaced) }.seed
        }
        assertEquals("An accepted pending fare seeds even a garbled confirming frame", 61.20,
            confirm(true, next.copy(fare = 7.84))!!.fare!!, 0.0)
        assertEquals("Two agreeing readings of the new trip leg are their own consensus", 61.20,
            confirm(false, next)!!.fare!!, 0.0)
        assertNull("A rejected pending fare never seeds a disagreeing replacement", confirm(false, next.copy(fare = 7.84)))
        // Round 3 (A1): a consensus is identical economics, not only an identical fare.
        assertNull("Two rejected readings that disagree on the pickup are no consensus",
            confirm(false, next.copy(pickupDistanceKm = 6.0)))
    }

    @Test fun `economics come from one guard-accepted frame on replacement and in the held baseline`() {
        // Round 3 (A1, Codex R2 #1): B's confirming frame loses the pickup's "0." and is rejected.
        val next = card.copy(fare = 61.20, pickupDistanceKm = 0.6, tripDistanceKm = 5.1, tripDurationMin = 14.0)
        val sessions = OcrOfferSessionTracker()
        sessions.parsed(card, 100)
        sessions.update(next, 200, guardAccepted = true)
        val replaced = sessions.update(next.copy(pickupDistanceKm = 6.0), 300, guardAccepted = false)
        assertTrue(replaced.replaced)
        assertEquals("The rejected frame takes the accepted pending frame's legs", 0.6, replaced.card!!.pickupDistanceKm!!, 0.0)
        assertEquals(0.6, replaced.admitted!!.pickupDistanceKm!!, 0.0)
        assertEquals(61.20, replaced.seed!!.fare!!, 0.0)
        // A rejected CONFIDENT frame of the same offer refreshes evidence, never the held legs that a
        // route-incomplete frame republishes with the confirmed fare.
        sessions.update(next.copy(pickupDistanceKm = 6.0), 400, guardAccepted = false)
        val held = sessions.update(next.copy(tripDistanceKm = null), 500)
        assertEquals(OcrOfferSessionTracker.Confidence.UNCERTAIN, held.confidence)
        assertEquals("The held verdict keeps the accepted pickup", 0.6, held.card!!.pickupDistanceKm!!, 0.0)
        // An accepted frame updates them as before.
        sessions.update(next.copy(pickupDistanceKm = 0.5), 600, guardAccepted = true)
        assertEquals(0.5, sessions.update(next.copy(tripDistanceKm = null), 700).card!!.pickupDistanceKm!!, 0.0)
    }

    @Test fun `our own mask over the held trip leg's evidence never pins the offer uncertain`() {
        // Round 2 (A3 (ii)): the frame reads the held trip leg; what our overlay hides is not uncertainty.
        val evidence = OfferInstanceEvidence("UBERX", "ME INTERESA", HostRect(193, 590, 333, 639),
            HostRect(330, 1870, 750, 1960), 1, labels = setOf("exclusive"))
        val held = card.copy(instanceEvidence = evidence, hostGeometry = mx.kompara.parsers.model.HostGeometry(
            card.platform, "t", 0, HostRect(0, 0, 1080, 2340), mx.kompara.parsers.model.HostOrientation.PORTRAIT,
            HostRect(0, 0, 1080, 91), HostRect(0, 542, 1080, 2340), emptyList(), emptyList()))
        val mask = HostRect(28, 131, 764, 650)
        val masked = held.copy(instanceEvidence = evidence.copy(product = null, headingBounds = null, labels = emptySet()),
            hostGeometry = held.hostGeometry!!.copy(ocrEvidence = mx.kompara.parsers.model.HostOcrGeometryEvidence(
                mask, emptyList(), emptyList(), false)))
        val sessions = OcrOfferSessionTracker()
        sessions.update(held, 100)
        assertEquals(OcrOfferSessionTracker.Confidence.CONFIDENT, sessions.update(masked, 200).confidence)
        val other = masked.copy(tripDistanceKm = 9.6)
        assertEquals("Another trip leg under our mask still votes", true, sessions.update(other, 300).identityUnresolved)
        val unmaskedLoss = held.copy(instanceEvidence = evidence.copy(action = null))
        val fresh = OcrOfferSessionTracker().apply { update(held, 100) }
        assertEquals("Token loss our mask does not explain stays uncertain", OcrOfferSessionTracker.Confidence.UNCERTAIN,
            fresh.update(unmaskedLoss, 200).confidence)
    }

    @Test fun `a vote is void only when the block it rests on lies under our mask`() {
        // Round 4 (A1, Codex R3 #1): our dock's mask reaching the held card's PADDED envelope (or an
        // unrelated band) is no ground to void the next card's visible action/product/class votes.
        val geometry = { c: OfferCard, overlay: HostRect? -> c.copy(hostGeometry = mx.kompara.parsers.model.HostGeometry(
            c.platform, "t", 0, HostRect(0, 0, 1080, 2340), mx.kompara.parsers.model.HostOrientation.PORTRAIT,
            HostRect(0, 0, 1080, 91), HostRect(0, 605, 1080, 2196), emptyList(), emptyList(),
            ocrEvidence = mx.kompara.parsers.model.HostOcrGeometryEvidence(overlay, emptyList(), emptyList(), false))) }
        val priority = card.copy(fare = 79.45, pickupDistanceKm = 0.2, pickupEtaMin = 1.0, tripDistanceKm = 2.4,
            tripDurationMin = 24.0, instanceEvidence = OfferInstanceEvidence("UBERPRIORITY", "ACEPTAR",
                HostRect(96, 653, 502, 746), HostRect(442, 1989, 640, 2046), 1, labels = setOf("exclusive"),
                cardBands = listOf(HostRect(96, 653, 640, 2046), HostRect(96, 300, 640, 600))))
        val radar = priority.copy(fare = 88.30, instanceEvidence = OfferInstanceEvidence("UBERX", "ME INTERESA",
            HostRect(193, 817, 333, 866), HostRect(394, 1948, 687, 2001), 1))
        val mask = HostRect(28, 275, 764, 627)
        val sessions = OcrOfferSessionTracker()
        val first = sessions.update(geometry(priority, null), 100)
        assertTrue("Our mask reaches the held card's padded envelope and a band", sessions.obscuredBy(mask))
        val pending = sessions.update(geometry(radar, mask), 200)
        assertTrue("Visible votes are pending, never the held payment", pending.identityUnresolved)
        assertNull(pending.card!!.fare)
        val replaced = sessions.update(geometry(radar, mask), 300)
        assertTrue("Visible votes replace on the second frame", replaced.replaced)
        assertNotEquals(first.card!!.sessionId, replaced.card!!.sessionId)
        // Symmetric: an action block genuinely under our plate votes neither its label nor its class.
        val hidden = radar.copy(instanceEvidence = priority.instanceEvidence!!.copy(action = "ME INTERESA",
            actionBounds = HostRect(394, 560, 687, 613), labels = emptySet()))
        val masked = OcrOfferSessionTracker().apply { update(geometry(priority, null), 100) }
        repeat(3) {
            assertFalse("An action read under our own plate never votes",
                masked.update(geometry(hidden, mask), 200L + it * 100).replaced)
        }
    }

    @Test fun `an identical-leg replacement takes its economics from one guard-accepted voting frame`() {
        // Round 4 (B6): Exclusivo -> Radar of the same product and legs replaces on two keys; like a
        // trip-leg replacement, the accepted pending vote seeds the first reveal and lends its legs to
        // a guard-rejected confirming frame.
        val exclusive = card.copy(fare = 79.45, pickupDistanceKm = 0.2, instanceEvidence = OfferInstanceEvidence(
            "UBERPRIORITY", "ACEPTAR", labels = setOf("exclusive")))
        val radar = exclusive.copy(fare = 88.30, instanceEvidence = OfferInstanceEvidence("UBERPRIORITY", "ME INTERESA"))
        fun confirm(voteAccepted: Boolean, confirming: OfferCard, confirmingAccepted: Boolean): OcrOfferSessionTracker.Decision {
            val sessions = OcrOfferSessionTracker()
            sessions.update(exclusive, 100)
            assertFalse(sessions.update(radar, 200, guardAccepted = voteAccepted).replaced)
            return sessions.update(confirming, 300, guardAccepted = confirmingAccepted).also { assertTrue(it.replaced) }
        }
        assertEquals("A fare-unread confirming frame paints the accepted pending vote's fare", 88.30,
            confirm(true, radar.copy(fare = null), true).seed!!.fare!!, 0.0)
        val rejected = confirm(true, radar.copy(pickupDistanceKm = 2.0), false)
        assertEquals("A rejected confirming frame takes the accepted vote's legs", 0.2, rejected.card!!.pickupDistanceKm!!, 0.0)
        assertEquals(0.2, rejected.admitted!!.pickupDistanceKm!!, 0.0)
        assertEquals(88.30, rejected.seed!!.fare!!, 0.0)
        assertEquals("Two identical rejected readings are their own consensus", 88.30,
            confirm(false, radar, false).seed!!.fare!!, 0.0)
        assertNull("A rejected vote never seeds a disagreeing confirming frame",
            confirm(false, radar.copy(fare = 98.30), true).seed)
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

    @Test fun `single semantic signals and displaced anchors cannot replace identical legs`() {
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
            if (replacement.platform != original.platform) {
                assertNotEquals(first.sessionId, next.sessionId)
                assertEquals(200L, next.sessionStartedAt)
            } else {
                assertEquals(first.sessionId, next.sessionId)
                assertEquals(100L, next.sessionStartedAt)
                assertEquals(first.sessionId, sessions.parsed(replacement, 300).sessionId)
            }
        }
    }

    @Test fun `missing tokens do not erase replacement evidence or create a record`() {
        val sessions = OcrOfferSessionTracker()
        val first = sessions.parsed(card.copy(variant = "exclusive",
            instanceEvidence = OfferInstanceEvidence(product = "UBER PRIORITY", action = "ACEPTAR")), 100)
        assertEquals(first.sessionId, sessions.parsed(card.copy(fare = null), 200).sessionId)
        val replacement = card.copy(fare = null, variant = "trip_radar",
            instanceEvidence = OfferInstanceEvidence(action = "COINCIDIR"))
        assertEquals(first.sessionId, sessions.parsed(replacement, 300).sessionId)
        assertNotEquals(first.sessionId, sessions.parsed(replacement, 400).sessionId)
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

    @Test fun `small anchor steps track animation without replacing the instance`() {
        val sessions = OcrOfferSessionTracker()
        val initial = card.copy(instanceEvidence = OfferInstanceEvidence(headingBounds = HostRect(90, 650, 500, 740)))
        val first = sessions.parsed(initial, 100)
        val small = initial.copy(instanceEvidence = OfferInstanceEvidence(headingBounds = HostRect(90, 670, 500, 760)))
        assertEquals(first.sessionId, sessions.parsed(small, 200).sessionId)
        val moved = initial.copy(instanceEvidence = OfferInstanceEvidence(headingBounds = HostRect(90, 690, 500, 780)))
        assertEquals(first.sessionId, sessions.parsed(moved, 300).sessionId)
    }

    @Test fun `independently measured card growth alone cannot replace an instance`() {
        val display = HostRect(0, 0, 1080, 2340)
        val measured = mx.kompara.parsers.model.HostGeometry(UberOcrParser.UBER_PACKAGE, "measured", 100,
            display, mx.kompara.parsers.model.HostOrientation.PORTRAIT, HostRect(0, 0, 1080, 200),
            HostRect(0, 650, 1080, 2340), emptyList(), emptyList())
        val sessions = OcrOfferSessionTracker()
        val first = sessions.parsed(card.copy(hostGeometry = measured), 100)
        assertEquals(first.sessionId, sessions.parsed(card.copy(fare = null,
            hostGeometry = measured.copy(offerCard = HostRect(0, 400, 1080, 2340))), 200).sessionId)
    }
}
