package mx.kompara.ocr

import mx.kompara.capture.lifecycle.LifecycleSignal
import mx.kompara.parsers.model.OfferCard

/**
 * Turns the OCR service's per-frame observations into the [LifecycleSignal] stream the B-039
 * [mx.kompara.capture.lifecycle.TripLifecycleTracker] consumes (via [OcrLifecycleBus]) — the OCR
 * analogue of `:capture`'s `OfferEventLifecycleMapper`.
 *
 * OCR runs ~3×/s, so this emits **only on a phase change** (or a new offer session): the tracker's
 * idle/trip handlers and shift heartbeat would otherwise be hammered with a DB write every frame.
 * Transitions are enough — the 30-min shift inactivity gap means a single idle signal keeps a shift
 * alive across normal between-offer waits.
 *
 * Phases:
 *  - **OFFER** — an offer card is on screen, per the caller's **debounced** [offerPresent] flag
 *    (the overlay's `CardPresenceTracker.isPresent()` — held through transient garbled frames, only
 *    false once the card is really gone). A single [LifecycleSignal.OfferSeen] is emitted per **offer
 *    session** (one continuous on-screen run), on the first frame that actually parses. The session
 *    is NOT keyed on the OCR-derived numbers: the same card's distance can flicker frame-to-frame
 *    (TD-028: "1.4 km" ↔ "14 km"), so keying on them would spawn duplicate ledger offers; and it does
 *    not end on a single no-parse frame, so a one-frame OCR dropout can't split one offer into two.
 *  - **TRIP** — no card/signature, [OcrTripMarkers] match → [LifecycleSignal.TripStateEntered].
 *  - **IDLE** — anything else → [LifecycleSignal.IdleStateEntered].
 *
 * ## Platform attribution
 * OCR captures the whole screen and can't see the foreground package itself, so the caller passes
 * the authoritative `hostPackage` (the parsed card's platform, else the accessibility service's
 * fresh foreground host) and only invokes this while a target app is confirmed foreground. Trip/idle
 * are attributed to that host — so a second target app can't mutate the first's ledger, and the
 * shift can start at the first host screen seen (not only at the first offer). Caller must skip
 * Kompara's own UI frames (it does — `KomparaUiGuard`).
 *
 * **Not thread-safe** — the holder mutates plain fields. The OCR service drives it from a single
 * sequential consumer (one frame fully processed before the next), which is also what keeps the
 * emitted signals in frame order.
 *
 * Pure and clock-free aside from the caller-supplied timestamp, so it is unit-testable on the JVM.
 */
class OcrLifecycleClassifier(private val markers: OcrTripMarkers = OcrTripMarkers.DEFAULT) {

    private enum class Phase { NONE, OFFER, TRIP, IDLE }

    private var phase = Phase.NONE
    private var offerEmitted = false
    private var sessionOfferKey: String? = null
    private var lastHost: String? = null

    /**
     * Reset to the initial phase. Called when capture restarts (re-consent) so a stale OFFER phase
     * (with offerEmitted already true) can't swallow the next real offer's [LifecycleSignal.OfferSeen].
     */
    fun reset() {
        phase = Phase.NONE
        offerEmitted = false
        sessionOfferKey = null
        lastHost = null
    }

    /**
     * Capture is ending (projection lost / screen lock / re-consent): we can no longer observe the
     * host, so close out the current ledger session. Returns a final [LifecycleSignal.IdleStateEntered]
     * for the active platform — which makes [mx.kompara.capture.lifecycle.TripLifecycleTracker] resolve
     * a still-pending offer and close an open trip at this point (idempotent if nothing is open) —
     * then resets. Without this, [reset] alone would strand an open OCR trip until the next offer or a
     * rollup sweep, since the restarted classifier has no platform context to emit the closing idle.
     */
    fun onCaptureEnd(timestampMs: Long): LifecycleSignal? {
        val pkg = lastHost
        reset()
        return pkg?.let { LifecycleSignal.IdleStateEntered(it, timestampMs) }
    }

    /**
     * Fold one OCR frame into the lifecycle.
     *
     * @param joinedText the frame's OCR text, joined.
     * @param card the parsed offer for this frame, or null.
     * @param offerPresent the caller's **debounced** "an offer card is on screen" flag
     *   (`CardPresenceTracker.isPresent()`); true through transient garbled frames so the session
     *   doesn't split, false only once the card has really left.
     * @param hostPackage the foreground target app this frame belongs to — `card.platform` when a
     *   card parsed, otherwise the accessibility service's fresh foreground host. The caller only
     *   invokes this when a target app is confirmed foreground, so trip/idle are attributed to IT
     *   (not the last offer's platform) — a second target app can't mutate the first's ledger.
     * @param timestampMs wall-clock epoch ms, used to stamp the signal (matches `OfferEntity.seenAt`).
     * @return a signal to publish, or null when nothing changed (steady phase / same offer session).
     */
    fun onFrame(
        joinedText: String,
        card: OfferCard?,
        offerPresent: Boolean,
        hostPackage: String,
        timestampMs: Long,
    ): LifecycleSignal? {
        lastHost = hostPackage
        // OFFER phase: an offer card is on screen (debounced), parsed this frame or not.
        if (offerPresent) {
            val entering = phase != Phase.OFFER
            phase = Phase.OFFER
            if (entering) offerEmitted = false // a fresh offer session begins
            if (card == null) return null // garbled frame: hold the session, nothing to emit yet
            // A genuinely different offer can replace the previous one with NO intervening non-offer
            // frame (Uber → DiDi, or a new fare on the same platform). Key the session on
            // (platform, fare) — both STABLE across frames — to start a fresh OfferSeen for it. The
            // jittery distance/duration (TD-028: "1.4 km" ↔ "14 km") is deliberately NOT in the key,
            // so OCR noise on the same card never re-emits a duplicate ledger offer.
            val key = "${card.platform}|${card.fare}"
            if (key != sessionOfferKey) {
                offerEmitted = false
                sessionOfferKey = key
            }
            return if (!offerEmitted) {
                offerEmitted = true
                LifecycleSignal.OfferSeen(card.platform, timestampMs, card)
            } else {
                null // same offer session, distance/duration may have jittered — do NOT re-emit
            }
        }

        // Non-offer frame: trip or idle, attributed to the CURRENT foreground host.
        return if (markers.isTripLike(joinedText)) {
            val changed = phase != Phase.TRIP
            phase = Phase.TRIP
            if (changed) LifecycleSignal.TripStateEntered(hostPackage, timestampMs) else null
        } else {
            val changed = phase != Phase.IDLE
            phase = Phase.IDLE
            if (changed) LifecycleSignal.IdleStateEntered(hostPackage, timestampMs) else null
        }
    }
}
