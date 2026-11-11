package mx.kompara.ocr

/**
 * WP-AD (founder decision 2026-11-10): an offer card on screen that no frame can publish, with no session, gets
 * the grey "Sin lectura" chip once it has been on screen for a couple of seconds, so the driver can tell an unread
 * card from Kompara being off. This holder decides only when, per host: frame kinds and monotonic time in, what the
 * overlay should do out. The pipeline classifies the frames and owns every event; the notice never reaches the
 * session tracker, the ledger or the lifecycle classifier.
 *
 * - Start: [frames] [Frame.UNREAD] frames with no signature-free frame between them, spanning [spanMs]. A
 *   [Frame.SIGNATURE] frame neither counts nor breaks the run. From then on every unread frame shows it.
 * - While shown: a signature frame is the overlay's heartbeat; a signature-free frame hides it softly at once.
 *   Absence corroborated the way a session's is ([frames] signature-free frames spanning [spanMs], or the frame's
 *   own clear absence) hides it for good and resets the run; a signature-free frame under our own chip never
 *   counts as absence. An unread frame after a soft hide shows it again at once.
 * - Cap: [capMs] after it first showed it is retired, even with the card still there, and it stays off until
 *   absence is corroborated: a screen that merely looks like a card must not pin a grey chip.
 * - A frame the tracker rejected as masked ([Frame.MASKED], round 3b) ends a shown notice at once: its own mask is
 *   in the reader's way. The run starts over; nothing is retired.
 * - [reset] at a boundary (a publication, the end of capture, a reader that may not read) clears everything.
 *
 * The numbers are [CardPresenceTracker]'s: 3 frames over 1,800 ms, and the 15 s parse ceiling.
 */
internal class UnreadCardNotice(
    private val frames: Int = CardPresenceTracker.DEFAULT_GONE_FRAMES,
    private val spanMs: Long = CardPresenceTracker.DEFAULT_ABSENCE_SPAN_MS,
    private val capMs: Long = CardPresenceTracker.DEFAULT_PARSE_CEILING_MS,
) {
    enum class Frame {
        /** A card on screen with host geometry that this frame could read for the host but not publish, no session. */
        UNREAD,
        /** A card signature that is not [UNREAD] (no card selected, no geometry, a session present). */
        SIGNATURE,
        /** No card signature. */
        ABSENT,
        /** No card signature where our own chip covers the card's legs: the absence may be ours. */
        ABSENT_UNDER_CHIP,
        /** The frame's own clear-absence evidence (an idle layout, a scoped trip transition). */
        ABSENT_CLEAR,
        /**
         * Round 3b: the tracker rejected the frame as `PARSED_CARD_MASKED` with no session. With no session the only mask
         * is the notice's own, so the notice is in the reader's way: it ends at once and the run starts over (the next,
         * unmasked frame starts the session or is unread for its own reasons). With no notice up it is a card frame that
         * is not unread: it neither counts toward a run nor breaks one, and breaks absence agreement.
         */
        MASKED,
    }

    enum class Action { NONE, SHOW, HEARTBEAT, SOFT_HIDE, HIDE }

    /** TEMP DIAG codes for the frame line: what this frame left behind. */
    enum class State(val code: Int) { ABSENT(0), PENDING(1), SHOWN(2), RETIRED(3) }

    private var run = 0
    private var runStartMs = 0L
    private var shownAtMs: Long? = null
    private var retired = false
    private var misses = 0
    private var firstMissMs = 0L

    val state: State get() = when {
        shownAtMs != null -> State.SHOWN
        retired -> State.RETIRED
        run > 0 -> State.PENDING
        else -> State.ABSENT
    }

    /** Diagnostics: the run's unread frames and span when the notice last started showing. */
    var shownAfterFrames = 0; private set
    var shownAfterMs = 0L; private set

    fun shownForMs(nowMs: Long): Long = shownAtMs?.let { nowMs - it } ?: 0L

    fun onFrame(frame: Frame, nowMs: Long): Action {
        val capped = shownAtMs?.let { nowMs - it >= capMs } == true
        if (capped) retire()
        val action = step(frame, nowMs)
        return if (capped) Action.HIDE else action
    }

    private fun step(frame: Frame, nowMs: Long): Action {
        val shown = shownAtMs != null
        return when (frame) {
            Frame.UNREAD -> {
                misses = 0
                when {
                    shown -> Action.SHOW
                    retired -> Action.NONE
                    else -> {
                        if (run == 0) runStartMs = nowMs
                        run++
                        if (run < frames || nowMs - runStartMs < spanMs) Action.NONE else {
                            shownAtMs = nowMs; shownAfterFrames = run; shownAfterMs = nowMs - runStartMs
                            Action.SHOW
                        }
                    }
                }
            }
            Frame.SIGNATURE -> { misses = 0; if (shown) Action.HEARTBEAT else Action.NONE }
            Frame.ABSENT_UNDER_CHIP -> { run = 0; misses = 0; if (shown) Action.SOFT_HIDE else Action.NONE }
            Frame.ABSENT -> {
                run = 0
                if (!shown && !retired) return Action.NONE
                if (misses == 0 || nowMs < firstMissMs) { firstMissMs = nowMs; misses = 0 }
                misses++
                if (misses >= frames && nowMs - firstMissMs >= spanMs) {
                    reset()
                    if (shown) Action.HIDE else Action.NONE
                } else if (shown) Action.SOFT_HIDE else Action.NONE
            }
            Frame.ABSENT_CLEAR -> if (reset()) Action.HIDE else Action.NONE
            Frame.MASKED -> if (shown) { reset(); Action.HIDE } else { misses = 0; Action.NONE }
        }
    }

    /**
     * Off screen until absence is corroborated: the cap, or a session that reached its own parse ceiling. The absence
     * already counted stands (round 2): a card that expires as the cap falls must not hold back the next one.
     */
    fun retire() {
        shownAtMs = null
        run = 0
        retired = true
    }

    /** Clears everything; true when a notice was on screen (shown or soft-hidden). */
    fun reset(): Boolean {
        val wasShown = shownAtMs != null
        shownAtMs = null
        run = 0
        misses = 0
        retired = false
        return wasShown
    }
}
