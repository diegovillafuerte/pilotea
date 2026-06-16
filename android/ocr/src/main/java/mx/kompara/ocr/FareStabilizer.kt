package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard
import kotlin.math.roundToInt

/**
 * Smooths the OCR fare across frames of one offer so a single garbled frame can't flip the chip's
 * number and traffic-light colour.
 *
 * On-device (Galaxy S25, 2026-06-15) one offer's fare flipped `MXN137.28` ↔ `MXN37.28` every ~0.4 s —
 * ML Kit drops the leading `1` off the tall bold fare glyph — so the verdict strobed 3×/sec. The trip
 * leg, by contrast, read `51 min (13.0 km)` on *every* frame. So we:
 *  - **key the offer session on the trip leg, not the fare** (`platform | km(1dp) | min`), so the
 *    jittery fare never starts a new session and a genuinely new offer (different leg) resets instantly;
 *  - within a session, display the **mode** of a short rolling window of parsed fares, breaking ties
 *    toward the **larger** value (a dropped leading digit only ever *lowers* the read, so the higher of
 *    a tie is the truer fare). The first frame of a session paints immediately (no warm-up lag).
 *
 * Occlusion is handled elsewhere: a fare hidden behind the chip makes `parse()` return null (no card),
 * which never reaches this class — [CardPresenceTracker] holds the last emitted verdict across the gap.
 *
 * Pure, no Android, single-threaded (the OCR service drives it under its frame mutex) → JVM-testable.
 */
class FareStabilizer(private val windowMs: Long = WINDOW_MS) {

    private data class Sample(val fare: Double, val atMs: Long)

    private var sessionKey: String? = null
    private val window = ArrayDeque<Sample>()
    private var displayed: Double? = null

    /** Return [card] with its `fare` replaced by the stabilized value for the current offer session. */
    fun onParsed(card: OfferCard, nowMs: Long): OfferCard {
        val fare = card.fare
        if (fare == null || fare <= 0.0) return card // nothing to stabilize; pass through untouched
        val key = sessionKey(card)
        if (key != sessionKey) {
            // A genuinely new offer (different trip leg / platform): reset and paint immediately.
            sessionKey = key
            window.clear()
            window.addLast(Sample(fare, nowMs))
            displayed = fare
            return card
        }
        window.addLast(Sample(fare, nowMs))
        while (window.isNotEmpty() && nowMs - window.first().atMs > windowMs) window.removeFirst()
        val stable = mode(window.map { it.fare }) ?: fare
        displayed = stable
        return card.copy(fare = stable)
    }

    /** Forget the session (card left the screen / capture torn down) so the next card paints fresh. */
    fun reset() {
        sessionKey = null
        window.clear()
        displayed = null
    }

    /** The trip leg is OCR-stable where the fare is not — key on it so jitter can't split the session. */
    private fun sessionKey(card: OfferCard): String {
        val km = card.tripDistanceKm?.let { "%.1f".format(it) } ?: "?"
        val min = card.tripDurationMin?.roundToInt()?.toString() ?: "?"
        return "${card.platform}|$km|$min"
    }

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
