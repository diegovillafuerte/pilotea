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
    fun `a garbled first frame corrects on the next real frame`() {
        val s = FareStabilizer()
        assertEquals(37.28, s.onParsed(uber(37.28), 0).fare!!, 1e-9) // first paint is unavoidable
        assertEquals(137.28, s.onParsed(uber(137.28), 300).fare!!, 1e-9) // tie → larger → corrected
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
}
