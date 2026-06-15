package mx.kompara.ocr

import mx.kompara.capture.lifecycle.LifecycleSignal
import mx.kompara.parsers.model.OfferCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The OCR → lifecycle-signal mapping (B-039 OCR ledger path). OCR runs ~3×/s, so the contract is:
 * emit only on a phase change or a new offer; hold the current phase through garbled frames.
 */
class OcrLifecycleClassifierTest {

    private val classifier = OcrLifecycleClassifier()

    private val uberCard = OfferCard(
        platform = "com.ubercab.driver",
        fare = 96.17,
        pickupDistanceKm = 0.8,
        pickupEtaMin = 5.0,
        tripDistanceKm = 11.3,
        tripDurationMin = 32.0,
    )

    // offerPresent mirrors CardPresenceTracker.isPresent() — debounced "card on screen". Defaults to
    // card-parsed; pass it explicitly to simulate a held garble frame (true) or a real exit (false).
    // host is the authoritative foreground target app (the service only calls when one is foreground).
    private fun frame(
        text: String = "",
        card: OfferCard? = null,
        offerPresent: Boolean = card != null,
        host: String = "com.ubercab.driver",
        t: Long = 0L,
    ) = classifier.onFrame(text, card, offerPresent, host, t)

    @Test
    fun `a new offer emits OfferSeen, re-asserting the same offer is silent`() {
        val first = frame(card = uberCard, t = 1)
        assertTrue(first is LifecycleSignal.OfferSeen)
        assertEquals("com.ubercab.driver", (first as LifecycleSignal.OfferSeen).card.platform)
        // Same offer parsed again on the next frame → no duplicate.
        assertNull(frame(card = uberCard, t = 2))
        // A garbled frame mid-offer (no parse, signature still present) → still no transition.
        assertNull(frame(text = "MXN96.17 garbled", card = null, offerPresent = true, t = 3))
    }

    @Test
    fun `offer to trip to idle emits one signal per transition, attributed to the offer platform`() {
        frame(card = uberCard, t = 10) // OfferSeen, sets platform context
        // Card gone, navigation screen → accept.
        val trip = frame(text = "Iniciar viaje | Llegar a Polanco", t = 11)
        assertTrue(trip is LifecycleSignal.TripStateEntered)
        assertEquals("com.ubercab.driver", (trip as LifecycleSignal.TripStateEntered).packageName)
        // Still navigating → no repeat.
        assertNull(frame(text = "En camino al destino", t = 12))
        // Back to the home/searching screen → trip closes.
        val idle = frame(text = "Buscando viajes | Estás conectado", t = 13)
        assertTrue(idle is LifecycleSignal.IdleStateEntered)
        assertEquals("com.ubercab.driver", (idle as LifecycleSignal.IdleStateEntered).packageName)
        // Still idle → no repeat.
        assertNull(frame(text = "Buscando viajes", t = 14))
    }

    @Test
    fun `trip and idle are attributed to the foreground host, even before an offer`() {
        // The reader is on, driver is on the Uber home before any offer — the host comes from the
        // accessibility service, so the shift can start here (not only at the first offer).
        val idle = frame(text = "Buscando viajes", host = "com.ubercab.driver", t = 1)
        assertTrue(idle is LifecycleSignal.IdleStateEntered)
        assertEquals("com.ubercab.driver", (idle as LifecycleSignal.IdleStateEntered).packageName)
    }

    @Test
    fun `non-offer frames attribute to the CURRENT host, not the last offer's platform`() {
        // Regression for cross-target ledger mutation: an Uber offer, then the driver foregrounds
        // DiDi and sees its idle screen — the idle must be attributed to DiDi, not Uber.
        frame(card = uberCard, host = "com.ubercab.driver", t = 1) // OfferSeen(uber)
        val didiIdle = frame(text = "Buscando viajes", host = "com.didiglobal.driver", t = 2)
        assertTrue(didiIdle is LifecycleSignal.IdleStateEntered)
        assertEquals("com.didiglobal.driver", (didiIdle as LifecycleSignal.IdleStateEntered).packageName)
    }

    @Test
    fun `a different offer after the prior one leaves emits a fresh OfferSeen`() {
        frame(card = uberCard, t = 1) // OfferSeen
        frame(text = "Buscando viajes", card = null, offerPresent = false, t = 2) // session ends (idle)
        val cheaper = uberCard.copy(fare = 40.0, tripDistanceKm = 2.4)
        assertTrue(frame(card = cheaper, t = 3) is LifecycleSignal.OfferSeen) // new session
    }

    @Test
    fun `OCR jitter on the same card does not re-emit a duplicate offer (TD-028)`() {
        // Same card, but OCR flickers the trip distance 11.3 -> 113 -> 1.4 across frames. The session
        // (continuous card presence) must yield exactly ONE OfferSeen, not three duplicate ledger
        // offers. Regression guard for the dedup-by-numeric-key bug the review caught.
        assertTrue(frame(card = uberCard, t = 1) is LifecycleSignal.OfferSeen)
        assertNull(frame(card = uberCard.copy(tripDistanceKm = 113.0), t = 2))
        assertNull(frame(card = uberCard.copy(tripDistanceKm = 1.4), t = 3))
        // A garbled frame (no parse, signature present) mid-session also doesn't re-emit.
        assertNull(frame(text = "MXN96.17 garble", card = null, offerPresent = true, t = 4))
    }

    @Test
    fun `back-to-back offers from different platforms each emit OfferSeen with no idle between`() {
        val uber = frame(card = uberCard, host = "com.ubercab.driver", t = 1)
        assertTrue(uber is LifecycleSignal.OfferSeen)
        // A DiDi offer replaces the Uber one with NO intervening non-offer frame (presence stays
        // "present"). The platform changed, so it must be a fresh ledger offer — not swallowed.
        val didiCard = OfferCard(
            platform = "com.didiglobal.driver",
            fare = 70.0,
            pickupDistanceKm = 1.0,
            pickupEtaMin = 4.0,
            tripDistanceKm = 5.0,
            tripDurationMin = 12.0,
        )
        val didi = frame(card = didiCard, host = "com.didiglobal.driver", t = 2)
        assertTrue(didi is LifecycleSignal.OfferSeen)
        assertEquals("com.didiglobal.driver", (didi as LifecycleSignal.OfferSeen).card.platform)
    }

    @Test
    fun `a different fare on the same platform back-to-back emits a fresh OfferSeen`() {
        assertTrue(frame(card = uberCard, t = 1) is LifecycleSignal.OfferSeen)
        // Distinct fare with no gap → genuinely a new offer (fare is stable, unlike jittery distance).
        assertTrue(frame(card = uberCard.copy(fare = 211.40), t = 2) is LifecycleSignal.OfferSeen)
    }

    @Test
    fun `an idle frame between two sightings of the same offer is a real transition`() {
        frame(card = uberCard, t = 1) // OfferSeen
        // Genuinely not an offer and no signature → idle transition (the offer truly left).
        val idle = frame(text = "Buscando viajes", card = null, offerPresent = false, t = 2)
        assertTrue(idle is LifecycleSignal.IdleStateEntered)
        // The same offer re-appearing later is a new OfferSeen (phase left OFFER).
        assertTrue(frame(card = uberCard, t = 3) is LifecycleSignal.OfferSeen)
    }

    @Test
    fun `onCaptureEnd closes an open session with a final idle, then resets clean`() {
        frame(card = uberCard, t = 1) // OfferSeen — platform context = uber
        frame(text = "Iniciar viaje | En camino", t = 2) // TripStateEntered — open trip
        // Projection lost mid-trip (screen lock): close out the ledger session.
        val closing = classifier.onCaptureEnd(99)
        assertTrue(closing is LifecycleSignal.IdleStateEntered)
        assertEquals("com.ubercab.driver", (closing as LifecycleSignal.IdleStateEntered).packageName)
        // Reset happened: a fresh offer after the restart emits OfferSeen again (no wedge).
        assertTrue(frame(card = uberCard, t = 3) is LifecycleSignal.OfferSeen)
    }

    @Test
    fun `onCaptureEnd before any offer emits nothing`() {
        assertNull(classifier.onCaptureEnd(1)) // no platform context → nothing to close
    }

    @Test
    fun `trip markers recognise the navigation screen and default to idle otherwise`() {
        val markers = OcrTripMarkers.DEFAULT
        assertTrue(markers.isTripLike("Finalizar viaje | 4.9 km"))
        assertTrue(markers.isTripLike("Iniciar viaje"))
        assertEquals(false, markers.isTripLike("Buscando viajes | Estás conectado"))
        assertEquals(false, markers.isTripLike("MXN96.17 | Aceptar | Viaje: 32 min (11.3 km)"))
    }
}
