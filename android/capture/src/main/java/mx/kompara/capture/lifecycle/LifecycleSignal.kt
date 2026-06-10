package mx.kompara.capture.lifecycle

import mx.kompara.parsers.model.OfferCard

/**
 * The reduced, framework-free input to [TripLifecycleTracker]'s state machine (B-039).
 *
 * The raw capture stream is rich and lossy; the tracker only needs four facts, so [LifecycleSignal]
 * collapses every [mx.kompara.capture.OfferEvent] (plus window-state classification) into exactly
 * these. Keeping the tracker driven by this tiny vocabulary is what makes it testable against
 * synthetic, virtual-time sequences with no device — the messy "is this screen a trip?" judgement
 * lives entirely in [OfferEventLifecycleMapper] and its data-driven [TripStateHeuristics].
 *
 * Every signal carries the host [packageName] and the event [timestampMs] (host event clock, ms) so
 * the tracker can stamp entities and run its time-window heuristics without an ambient clock.
 */
sealed interface LifecycleSignal {

    val packageName: String
    val timestampMs: Long

    /** An offer card is on screen, parsed into [card]. The driver hasn't decided yet. */
    data class OfferSeen(
        override val packageName: String,
        override val timestampMs: Long,
        val card: OfferCard,
    ) : LifecycleSignal

    /**
     * The host is showing a **trip-like** screen (navigation/active-trip), per the heuristic markers.
     * Used to confirm an acceptance and to keep an open trip alive.
     */
    data class TripStateEntered(
        override val packageName: String,
        override val timestampMs: Long,
    ) : LifecycleSignal

    /**
     * The host is showing an **idle / offer-capable** screen (home, waiting-for-request) — no offer
     * card and not a trip. Closes an in-progress trip and is the resting state between offers.
     */
    data class IdleStateEntered(
        override val packageName: String,
        override val timestampMs: Long,
    ) : LifecycleSignal
}
