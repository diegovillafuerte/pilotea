package mx.kompara.ocr

/**
 * Corroborated absence at the real capture cadence (700 ms wait AFTER recognition).
 * Three signature-free observations must span at least 1,800 ms. A fast burst cannot dismiss a
 * card; a signature, including one with unreadable fields, breaks absence agreement. Elapsed time
 * alone is never proof of disappearance, except main's ceiling: [parseCeilingMs] without a
 * successful parse ends the card even through signature frames, so a lookalike can never pin a
 * stale verdict. The caller can separately supply an authoritative idle layout.
 */
class CardPresenceTracker(
    private val goneFrames: Int = DEFAULT_GONE_FRAMES,
    private val absenceSpanMs: Long = DEFAULT_ABSENCE_SPAN_MS,
    private val parseCeilingMs: Long = DEFAULT_PARSE_CEILING_MS,
) {
    private var tracking = false
    private var missStreak = 0
    private var firstMissAtMs = 0L
    private var lastParseAtMs = 0L

    fun isPresent(): Boolean = tracking
    fun reset() {
        tracking = false
        missStreak = 0
        firstMissAtMs = 0L
        lastParseAtMs = 0L
    }
    fun onParsed(nowMs: Long) {
        tracking = true
        missStreak = 0
        firstMissAtMs = nowMs
        lastParseAtMs = nowMs
    }
    fun onMiss(cardLike: Boolean, nowMs: Long): Boolean {
        if (!tracking) return false
        if (nowMs - lastParseAtMs >= parseCeilingMs) {
            reset()
            return true
        }
        if (cardLike) {
            missStreak = 0
            return false
        }
        if (missStreak == 0 || nowMs < firstMissAtMs) {
            firstMissAtMs = nowMs
            missStreak = 0
        }
        missStreak++
        if (missStreak < goneFrames || nowMs - firstMissAtMs < absenceSpanMs) return false
        reset()
        return true
    }
    companion object {
        const val DEFAULT_GONE_FRAMES = 3
        const val DEFAULT_ABSENCE_SPAN_MS = 1_800L
        /** Main's zombie ceiling between successful parses; offers expire well within it. */
        const val DEFAULT_PARSE_CEILING_MS = 15_000L
    }
}
