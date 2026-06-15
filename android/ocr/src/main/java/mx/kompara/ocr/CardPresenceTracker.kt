package mx.kompara.ocr

/**
 * Decides when a tracked offer card has actually LEFT the screen, given each frame's parse outcome.
 * Pure and clock-free (callers pass timestamps) so the policy is JVM-testable (B-077).
 *
 * The old policy counted EVERY unparseable frame toward "offer gone", so a few seconds of OCR
 * garble — routine while the map animates under the card — hid the chip mid-offer. Now:
 *
 *  - a successful parse (re)starts tracking and clears the miss streak;
 *  - a failed frame that still has the card signature (fare + leg, see
 *    [DidiOcrParser.hasCardSignature]) means "card still up, frame garbled": hold the verdict and
 *    clear the streak;
 *  - only frames with NO signature count toward [goneFrames] consecutive misses (a real dismissal
 *    lands on the bare map/idle screen, which has no leg line);
 *  - [zombieTimeoutMs] without a successful parse declares the card gone regardless, so a
 *    signature-matching lookalike (e.g. a navigation ETA bubble) can never pin a stale verdict.
 */
class CardPresenceTracker(
    private val goneFrames: Int = DEFAULT_GONE_FRAMES,
    private val zombieTimeoutMs: Long = DEFAULT_ZOMBIE_TIMEOUT_MS,
) {
    private var tracking = false
    private var missStreak = 0
    private var lastParseAtMs = 0L

    /**
     * Whether an offer card is currently considered on-screen — true from the first [onParsed] until
     * [onMiss] declares it gone (held through transient garbled frames). The lifecycle classifier
     * reuses this so an offer "session" survives the same OCR dropouts the overlay verdict does,
     * instead of splitting into duplicate offers on a single bad frame.
     */
    fun isPresent(): Boolean = tracking

    /**
     * Forget any tracked card. Called when capture restarts (re-consent) so a new projection begins
     * from a clean state instead of inheriting a stale "card present" that would starve [onMiss].
     */
    fun reset() {
        tracking = false
        missStreak = 0
        lastParseAtMs = 0L
    }

    /** Record a frame that parsed into a full offer. */
    fun onParsed(nowMs: Long) {
        tracking = true
        missStreak = 0
        lastParseAtMs = nowMs
    }

    /**
     * Record a frame that failed to parse. Returns true when the card should now be considered
     * gone (tracking then stops until the next [onParsed]). No-op false while nothing is tracked.
     */
    fun onMiss(cardLike: Boolean, nowMs: Long): Boolean {
        if (!tracking) return false
        if (nowMs - lastParseAtMs >= zombieTimeoutMs) return gone()
        if (cardLike) {
            missStreak = 0
            return false
        }
        missStreak++
        return if (missStreak >= goneFrames) gone() else false
    }

    private fun gone(): Boolean {
        tracking = false
        missStreak = 0
        return true
    }

    companion object {
        /**
         * Consecutive signature-free frames before the card counts as dismissed: ~1.8 s at the
         * service's 300 ms cadence, plus the overlay's 500 ms grace ≈ 2.3 s perceived hide.
         */
        const val DEFAULT_GONE_FRAMES: Int = 6

        /** Hard ceiling between successful parses; DiDi offers expire well within this. */
        const val DEFAULT_ZOMBIE_TIMEOUT_MS: Long = 15_000L
    }
}
