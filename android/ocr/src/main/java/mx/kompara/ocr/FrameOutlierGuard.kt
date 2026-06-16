package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard
import kotlin.math.max
import kotlin.math.min

/**
 * Drops a single OCR frame whose parsed numbers are a *gross* (≈order-of-magnitude) outlier versus the
 * recent consensus for the offer on screen — the last line of defence behind the per-parser
 * decimal-loss recovery ([recoverDroppedDistanceDecimal]).
 *
 * Within one offer the true fare and leg distances are constant, so a lone frame that reads a value
 * ≥ [OUTLIER_FACTOR]× (or ≤ 1/[OUTLIER_FACTOR]×) the median of the last ~second is an OCR misread, not
 * a new reality — e.g. a decimal drop that slips past recovery (13.0 km → 130 km) or a gross fare
 * garble (DiDi `$200.85` read as `$7.84`, live 2026-06-15). [FareStabilizer] can't catch the distance
 * case because it *keys the offer session on the trip leg*: a ×10 trip leg starts a brand-new session
 * and the bad frame paints immediately. This guard runs BEFORE the stabilizer, the overlay emit AND the
 * B-039 ledger, so a rejected frame reaches none of them — it is treated like a garbled parse and
 * [CardPresenceTracker] holds the last good verdict across it.
 *
 * Why a *median* over a recorded window: the median ignores the lone outlier it is judging, and
 * recording every sample (outliers included) lets a *genuine* sustained change — a real next offer, a
 * few frames of it — move the median and be accepted, so nothing is locked out. The window is time-
 * bounded and [reset] when the card leaves the screen, so each offer is judged only against itself.
 *
 * Sub-[OUTLIER_FACTOR] jitter (the `MXN137.28` ↔ `37.28` flicker, ~3.7×) is deliberately left to
 * [FareStabilizer]'s mode smoothing; this guard only fires on the gross, order-of-magnitude class.
 *
 * Pure, single-threaded (driven under the OCR frame mutex) → JVM-testable.
 */
class FrameOutlierGuard(private val windowMs: Long = WINDOW_MS) {

    private data class Sample(
        val fare: Double?,
        val pickupKm: Double?,
        val tripKm: Double?,
        val atMs: Long,
    )

    private val window = ArrayDeque<Sample>()

    /**
     * True to keep [card], false to drop this frame as a gross OCR outlier. Records the sample either
     * way (so a genuine sustained shift moves the consensus and is then accepted).
     */
    fun accept(card: OfferCard, nowMs: Long): Boolean {
        while (window.isNotEmpty() && nowMs - window.first().atMs > windowMs) window.removeFirst()
        val outlier = isGrossOutlier(card.fare, window.mapNotNull { it.fare }) ||
            isGrossOutlier(card.tripDistanceKm, window.mapNotNull { it.tripKm }) ||
            isGrossOutlier(card.pickupDistanceKm, window.mapNotNull { it.pickupKm })
        window.addLast(Sample(card.fare, card.pickupDistanceKm, card.tripDistanceKm, nowMs))
        return !outlier
    }

    /** Forget the session (card left the screen / capture torn down) so the next offer is judged fresh. */
    fun reset() = window.clear()

    /** A positive value is a gross outlier when it differs from the window median by ≥ [OUTLIER_FACTOR]×. */
    private fun isGrossOutlier(value: Double?, priors: List<Double>): Boolean {
        if (value == null || value <= 0.0 || priors.size < MIN_CONSENSUS) return false
        val median = median(priors)
        if (median <= 0.0) return false
        return max(value, median) / min(value, median) >= OUTLIER_FACTOR
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    companion object {
        /** Rolling window (~5 frames at the ~300ms OCR cadence), matching [FareStabilizer.WINDOW_MS]. */
        const val WINDOW_MS = 1500L

        /** Prior readings needed before anything is judged an outlier (the first frames paint blind). */
        const val MIN_CONSENSUS = 2

        /** ≈ a dropped/added digit. Below this is left to [FareStabilizer]'s mode smoothing. */
        const val OUTLIER_FACTOR = 5.0
    }
}
