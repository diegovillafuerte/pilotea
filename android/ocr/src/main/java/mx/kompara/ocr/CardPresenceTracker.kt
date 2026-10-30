package mx.kompara.ocr

/**
 * Corroborated absence at the real capture cadence (700 ms wait AFTER recognition).
 * Three signature-free observations must span at least 1,800 ms. A fast burst cannot dismiss a
 * card; a signature, including one with unreadable fields, breaks absence agreement. Elapsed time
 * alone is never proof of disappearance. The caller can separately supply an authoritative idle
 * accessibility layout to the session state machine.
 */
class CardPresenceTracker(
    private val goneFrames: Int = DEFAULT_GONE_FRAMES,
    private val absenceSpanMs: Long = DEFAULT_ABSENCE_SPAN_MS,
) {
    private var tracking = false
    private var missStreak = 0
    private var firstMissAtMs = 0L

    fun isPresent(): Boolean = tracking
    fun reset() {
        tracking = false
        missStreak = 0
        firstMissAtMs = 0L
    }
    fun onParsed(nowMs: Long) {
        tracking = true
        missStreak = 0
        firstMissAtMs = nowMs
    }
    fun onMiss(cardLike: Boolean, nowMs: Long): Boolean {
        if (!tracking) return false
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
    }
}
