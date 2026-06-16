package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The frame outlier guard is the safety net behind decimal-loss recovery: a single OCR frame whose
 * numbers jump ~order-of-magnitude versus the recent consensus is dropped before it reaches the chip
 * or the B-039 ledger. Cases are pinned to the real ×10 distance frame and the gross DiDi fare garble
 * captured live (2026-06-15).
 */
class FrameOutlierGuardTest {

    private val guard = FrameOutlierGuard()

    @Test
    fun `rejects a single ×10 trip-leg frame and keeps the good frame after it`() {
        // The MXN137.28 / 13.0 km Uber offer: a lone frame reads the trip leg as 130 km (decimal
        // recovery's backstop). Two good frames establish consensus; the ×10 frame is dropped; the
        // good frame that follows is kept (the guard self-corrects, not latches).
        assertTrue(guard.accept(card(fare = 137.28, pickupKm = 1.0, tripKm = 13.0), nowMs = 0))
        assertTrue(guard.accept(card(fare = 137.28, pickupKm = 1.0, tripKm = 13.0), nowMs = 300))
        assertFalse(guard.accept(card(fare = 137.28, pickupKm = 10.0, tripKm = 130.0), nowMs = 600))
        assertTrue(guard.accept(card(fare = 137.28, pickupKm = 1.0, tripKm = 13.0), nowMs = 900))
    }

    @Test
    fun `rejects a gross fare garble against the consensus fare`() {
        // DiDi $200.85 read as $7.84 on one frame (~25×). The ledger gets the raw card, so this guard
        // — not just the chip's FareStabilizer — has to keep $7.84 out.
        assertTrue(guard.accept(card(fare = 200.85, pickupKm = 1.3, tripKm = 11.3), nowMs = 0))
        assertTrue(guard.accept(card(fare = 200.85, pickupKm = 1.3, tripKm = 11.3), nowMs = 300))
        assertFalse(guard.accept(card(fare = 7.84, pickupKm = 1.3, tripKm = 11.3), nowMs = 600))
    }

    @Test
    fun `leaves sub-order-of-magnitude fare jitter to the stabilizer`() {
        // MXN137.28 ↔ 37.28 (the dropped-leading-1 flicker, ~3.7×) is NOT gross — the guard passes it
        // through so FareStabilizer's mode smoothing owns it, instead of both fighting over the fare.
        assertTrue(guard.accept(card(fare = 137.28, pickupKm = 1.0, tripKm = 13.0), nowMs = 0))
        assertTrue(guard.accept(card(fare = 137.28, pickupKm = 1.0, tripKm = 13.0), nowMs = 300))
        assertTrue(guard.accept(card(fare = 37.28, pickupKm = 1.0, tripKm = 13.0), nowMs = 600))
    }

    @Test
    fun `the first frames of an offer paint blind`() {
        // With no consensus yet there is nothing to judge against, so even a wild first frame is kept —
        // recovery is what prevents a bad first frame; the guard only catches outliers vs an established
        // value.
        assertTrue(guard.accept(card(fare = 9.99, pickupKm = 99.0, tripKm = 999.0), nowMs = 0))
    }

    @Test
    fun `reset lets a genuinely different next offer through`() {
        // Build a consensus around a small fare, then the card leaves the screen (reset). A new offer
        // whose fare is 6× larger must not be mistaken for an outlier of the previous offer.
        guard.accept(card(fare = 100.0, pickupKm = 1.0, tripKm = 5.0), nowMs = 0)
        guard.accept(card(fare = 100.0, pickupKm = 1.0, tripKm = 5.0), nowMs = 300)
        // Without a reset this would be rejected (600 / 100 = 6×):
        assertFalse(guard.accept(card(fare = 600.0, pickupKm = 1.0, tripKm = 5.0), nowMs = 600))
        guard.reset()
        assertTrue(guard.accept(card(fare = 600.0, pickupKm = 1.0, tripKm = 5.0), nowMs = 900))
    }

    @Test
    fun `accepts a sustained shift once it becomes the consensus`() {
        // A real change held across several frames (not a lone garble) moves the median and is accepted,
        // so the guard can't lock out a genuinely new reality even without a reset between offers.
        guard.accept(card(fare = 100.0, pickupKm = 1.0, tripKm = 5.0), nowMs = 0)
        guard.accept(card(fare = 100.0, pickupKm = 1.0, tripKm = 5.0), nowMs = 300)
        assertFalse(guard.accept(card(fare = 700.0, pickupKm = 1.0, tripKm = 5.0), nowMs = 600)) // lone → out
        guard.accept(card(fare = 700.0, pickupKm = 1.0, tripKm = 5.0), nowMs = 900) // still building
        assertTrue(guard.accept(card(fare = 700.0, pickupKm = 1.0, tripKm = 5.0), nowMs = 1200)) // now consensus
    }

    private fun card(fare: Double, pickupKm: Double, tripKm: Double) = OfferCard(
        platform = "com.ubercab.driver",
        fare = fare,
        pickupDistanceKm = pickupKm,
        tripDistanceKm = tripKm,
    )
}
