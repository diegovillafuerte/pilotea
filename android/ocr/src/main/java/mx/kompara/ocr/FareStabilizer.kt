package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard

/**
 * Smooths the OCR fare across frames of one offer so a single garbled frame can't flip the chip's
 * number and traffic-light colour.
 *
 * On-device (Galaxy S25, 2026-06-15) one offer's fare flipped `MXN137.28` ↔ `MXN37.28` every ~0.4 s —
 * ML Kit drops the leading `1` off the tall bold fare glyph — so the verdict strobed 3×/sec. The trip
 * leg, by contrast, read `51 min (13.0 km)` on *every* frame. So we:
 *  - **key the offer session on the host and both complete, exact legs**, so the jittery fare never
 *    starts a new session and a different/unknown route resets before any payment can be retained;
 *  - within a session, display the **mode** of a short rolling window of parsed fares, breaking ties
 *    toward the **larger** value (a dropped leading digit only ever *lowers* the read, so the higher of
 *    a tie is the truer fare). The first frame of a session paints immediately (no warm-up lag).
 *
 * A recognized offer can have a null fare. Retain the proven payment only when that frame identifies
 * the same complete route; keep all current-frame bounds. Whole-card misses are handled by
 * [CardPresenceTracker]; a signature-free frame also resets this state before a replacement arrives.
 *
 * Pure, no Android, single-threaded (the OCR service drives it under its frame mutex) → JVM-testable.
 */
class FareStabilizer(private val windowMs: Long = WINDOW_MS) {

    private data class Sample(val fare: Double, val atMs: Long, val paymentBasis: String, val rawFare: String?)

    private var sessionKey: OcrOfferIdentity? = null
    private val window = ArrayDeque<Sample>()
    private var displayed: Sample? = null

    /** Return [card] with its `fare` replaced by the stabilized value for the current offer session. */
    fun onParsed(card: OfferCard, nowMs: Long): OfferCard {
        val key = OcrOfferIdentity.from(card)
        if (key == null || key != sessionKey) {
            // Check identity even on a missing-fare frame, before looking at the previous payment.
            reset()
            sessionKey = key
        }
        if (key == null) return card
        val fare = card.fare
        if (fare == null) return displayed?.let { card.withPayment(it) } ?: card
        if (!fare.isFinite() || fare <= 0.0) return card
        window.addLast(Sample(fare, nowMs, card.paymentBasis, card.raw["fare"]))
        while (window.isNotEmpty() && nowMs - window.first().atMs > windowMs) window.removeFirst()
        val stable = mode(window.map { it.fare }) ?: fare
        val sample = window.last { it.fare == stable }
        displayed = sample
        return card.withPayment(sample)
    }

    /** Forget the session (card left the screen / capture torn down) so the next card paints fresh. */
    fun reset() {
        sessionKey = null
        window.clear()
        displayed = null
    }

    private fun OfferCard.withPayment(sample: Sample) = copy(fare = sample.fare,
        paymentBasis = sample.paymentBasis,
        raw = (raw - "fare") + (sample.rawFare?.let { mapOf("fare" to it) } ?: emptyMap()))

    /** Most frequent fare in the window; ties resolve to the larger value (dropped digits read low). */
    private fun mode(values: List<Double>): Double? =
        values.groupingBy { it }.eachCount().entries
            .maxWithOrNull(compareBy({ it.value }, { it.key }))
            ?.key

    companion object {
        /** Rolling window (~5 frames at the ~300ms OCR cadence). NEEDS-CALIBRATION on more offers. */
        const val WINDOW_MS = 1500L
    }
}
