package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [FareStabilizer] holds one offer's fare steady through OCR jitter, driven by the EXACT captured
 * timeline (Galaxy S25, 2026-06-15): `MXN137.28` ↔ `MXN37.28` every ~300 ms while the trip leg stays
 * `51 min (13.0 km)`.
 */
class FareStabilizerTest {

    private fun uber(fare: Double, km: Double = 13.0, min: Double = 51.0) = OfferCard(
        platform = "com.ubercab.driver",
        fare = fare,
        pickupDistanceKm = 1.0,
        pickupEtaMin = 5.0,
        tripDistanceKm = km,
        tripDurationMin = min,
    )

    @Test
    fun `holds 137_28 through the real 137 to 37 jitter`() {
        val s = FareStabilizer()
        var t = 0L
        assertEquals(137.28, s.onParsed(uber(137.28), t).fare!!, 1e-9) // first frame paints immediately
        repeat(12) {
            t += 300
            val garbled = if (it % 2 == 0) 37.28 else 137.28
            assertEquals(137.28, s.onParsed(uber(garbled), t).fare!!, 1e-9) // never shows 37.28
        }
    }

    @Test
    fun `a garbled first frame corrects after two agreeing real frames`() {
        val s = FareStabilizer()
        assertEquals(37.28, s.onParsed(uber(37.28), 0).fare!!, 1e-9) // first paint is unavoidable
        assertEquals(37.28, s.onParsed(uber(137.28), 300).fare!!, 1e-9) // one reading cannot revise it
        assertEquals(137.28, s.onParsed(uber(137.28), 600).fare!!, 1e-9)
    }

    @Test
    fun `a new trip leg resets the session and paints the new fare immediately`() {
        val s = FareStabilizer()
        s.onParsed(uber(137.28), 0)
        // Different offer (different leg + lower fare) → instant new value, no lag, no stale max-hold.
        assertEquals(80.0, s.onParsed(uber(80.0, km = 4.0, min = 12.0), 300).fare!!, 1e-9)
    }

    @Test
    fun `reset forgets the session so the next offer paints fresh`() {
        val s = FareStabilizer()
        s.onParsed(uber(137.28), 0)
        s.onParsed(uber(37.28), 300)
        s.reset()
        assertEquals(37.28, s.onParsed(uber(37.28), 600).fare!!, 1e-9) // fresh session first-paints
    }

    @Test
    fun `a non-positive or null fare passes through untouched`() {
        val s = FareStabilizer()
        assertEquals(0.0, s.onParsed(uber(0.0), 0).fare!!, 1e-9)
        assertNull(s.onParsed(uber(137.28).copy(fare = null), 300).fare)
    }

    @Test fun `same complete route retains payment and provenance through unreadable frames`() {
        val s = FareStabilizer()
        val known = uber(147.46).copy(paymentBasis = "DRIVER_PAYMENT", raw = mapOf("fare" to "147.46"))
        val unread = known.copy(fare = null, paymentBasis = "UNCONFIRMED", raw = emptyMap(),
            contentBounds = mx.kompara.parsers.model.OfferContentBounds(10, 500, 200, 700))
        assertEquals(147.46, s.onParsed(known, 0).fare!!, 0.001)
        for (at in listOf(300L, 2_000L)) {
            val held = s.onParsed(unread, at)
            assertEquals(147.46, held.fare!!, 0.001)
            assertEquals("DRIVER_PAYMENT", held.paymentBasis)
            assertEquals("147.46", held.raw["fare"])
            assertEquals(unread.contentBounds, held.contentBounds)
            assertNull(held.hostGeometry)
        }
        assertEquals(147.46, s.onParsed(known, 2_300).fare!!, 0.001)
    }

    @Test fun `changed pickup or trip fields reset before a missing fare can inherit`() {
        val known = uber(147.46)
        for (different in listOf(known.copy(pickupDistanceKm = 0.7), known.copy(pickupEtaMin = 4.0),
            known.copy(tripDistanceKm = 6.3), known.copy(tripDurationMin = 49.0),
            known.copy(platform = DidiOcrParser.DIDI_PACKAGE))) {
            val s = FareStabilizer()
            s.onParsed(known, 0)
            assertNull(s.onParsed(different.copy(fare = null), 300).fare)
            assertNull(s.onParsed(known.copy(fare = null), 600).fare)
            assertEquals(79.45, s.onParsed(different.copy(fare = 79.45), 900).fare!!, 0.001)
        }
    }

    @Test fun `unknown incomplete or invalid route identity cannot retain a fare`() {
        val known = uber(147.46)
        for (unknown in listOf(known.copy(pickupDistanceKm = null), known.copy(pickupEtaMin = null),
            known.copy(tripDistanceKm = null), known.copy(tripDurationMin = null),
            known.copy(tripDurationMin = Double.NaN), known.copy(pickupDistanceKm = -1.0))) {
            val s = FareStabilizer()
            s.onParsed(known, 0)
            assertNull(s.onParsed(unknown.copy(fare = null), 300).fare)
            assertNull(s.onParsed(known.copy(fare = null), 600).fare)
        }
    }

    @Test fun `reset prevents an identical route replacement from inheriting unread payment`() {
        val s = FareStabilizer()
        s.onParsed(uber(147.46), 0)
        s.reset()
        assertNull(s.onParsed(uber(147.46).copy(fare = null), 300).fare)
    }

    @Test fun `a sustained lower fare replaces retained payment on the same route`() {
        val s = FareStabilizer()
        s.onParsed(uber(147.46), 0)
        s.onParsed(uber(79.45), 300)
        assertEquals(79.45, s.onParsed(uber(79.45), 600).fare!!, 0.001)
        assertEquals(79.45, s.onParsed(uber(79.45).copy(fare = null), 900).fare!!, 0.001)
    }

    @Test fun `an expired window never promotes one disagreeing reading to confirmed payment`() {
        val s = FareStabilizer()
        val known = uber(79.45).copy(paymentBasis = "DRIVER_PAYMENT", raw = mapOf("fare" to "$79.45"))
        val unread = known.copy(fare = null, paymentBasis = "UNCONFIRMED", raw = emptyMap())
        assertEquals(79.45, s.onParsed(known, 0).fare!!, 0.001)
        assertEquals(79.45, s.onParsed(known, 300).fare!!, 0.001)
        for (at in listOf(600L, 900L, 1200L)) {
            assertEquals(79.45, s.onParsed(unread, at).fare!!, 0.001)
        }
        val candidate = known.copy(fare = 99.45, raw = mapOf("fare" to "$99.45"))
        val held = s.onParsed(candidate, 3000)
        assertEquals(79.45, held.fare!!, 0.001)
        assertEquals("$79.45", held.raw["fare"])
        assertEquals(79.45, s.onParsed(unread, 60000).fare!!, 0.001)
    }

    @Test fun `two consecutive agreeing candidates confirm a revision after unread frames`() {
        val s = FareStabilizer()
        val known = uber(79.45).copy(paymentBasis = "DRIVER_PAYMENT")
        s.onParsed(known, 0)
        s.onParsed(known, 300)
        for (at in listOf(600L, 900L, 1200L)) s.onParsed(known.copy(fare = null), at)
        assertEquals(79.45, s.onParsed(known.copy(fare = 99.45), 3000).fare!!, 0.001)
        assertEquals(99.45, s.onParsed(known.copy(fare = 99.45), 3300).fare!!, 0.001)
        assertEquals(99.45, s.onParsed(known.copy(fare = null), 60000).fare!!, 0.001)
    }

    @Test fun `alternating candidates never cause a fare correction storm`() {
        val s = FareStabilizer()
        s.onParsed(uber(79.45), 0)
        repeat(20) { frame ->
            val reading = if (frame % 2 == 0) 99.45 else 79.45
            assertEquals("Frame $frame", 79.45,
                s.onParsed(uber(reading), (frame + 1) * 350L).fare!!, 0.001)
        }
    }

    @Test fun `unread frames and expired candidates break consecutive agreement`() {
        for (gap in listOf(600L, 3000L)) {
            val s = FareStabilizer()
            s.onParsed(uber(79.45), 0)
            assertEquals(79.45, s.onParsed(uber(99.45), 300).fare!!, 0.001)
            if (gap == 600L) s.onParsed(uber(79.45).copy(fare = null), 450)
            assertEquals(79.45, s.onParsed(uber(99.45), gap).fare!!, 0.001)
            assertEquals(99.45, s.onParsed(uber(99.45), gap + 300).fare!!, 0.001)
        }
    }

    @Test fun `new instance starts fresh even when numeric legs match`() {
        val s = FareStabilizer()
        val first = uber(79.45).copy(sessionId = "first")
        val next = first.copy(sessionId = "second", fare = null)
        s.onParsed(first, 0)
        assertNull(s.onParsed(next, 300).fare)
        assertEquals(99.45, s.onParsed(next.copy(fare = 99.45), 600).fare!!, 0.001)
    }

    @Test fun `lost route confidence does not grant this instance another immediate revision`() {
        val s = FareStabilizer()
        val known = uber(79.45).copy(sessionId = "same-instance")
        s.onParsed(known, 0)
        assertNull(s.onParsed(known.copy(pickupEtaMin = null, fare = null), 300).fare)
        assertNull(s.onParsed(known.copy(fare = 99.45), 600).fare)
        assertEquals(99.45, s.onParsed(known.copy(fare = 99.45), 900).fare!!, 0.001)
    }

    @Test fun `payment invalidation before a rejected frame requires agreement to reacquire`() {
        val s = FareStabilizer()
        val known = uber(79.45).copy(sessionId = "same-instance")
        s.onParsed(known, 0)
        s.invalidatePayment()
        assertNull(s.onParsed(known.copy(fare = null), 300).fare)
        assertNull(s.onParsed(known.copy(fare = 99.45), 600).fare)
        assertEquals(99.45, s.onParsed(known.copy(fare = 99.45), 900).fare!!, 0.001)
    }

    @Test fun `unparsed or rejected frames break agreement without losing confirmed payment`() {
        val s = FareStabilizer()
        val known = uber(79.45).copy(sessionId = "same-instance")
        s.onParsed(known, 0)
        assertEquals(79.45, s.onParsed(known.copy(fare = 99.45), 300).fare!!, 0.001)
        s.breakCandidateAgreement()
        assertEquals(79.45, s.onParsed(known.copy(fare = 99.45), 600).fare!!, 0.001)
        assertEquals(99.45, s.onParsed(known.copy(fare = 99.45), 900).fare!!, 0.001)
    }
}
