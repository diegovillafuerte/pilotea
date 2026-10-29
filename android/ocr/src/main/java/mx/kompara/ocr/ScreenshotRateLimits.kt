package mx.kompara.ocr

/** Counts only consecutive interval refusals; any other response or a new burst resets the run. */
internal class ScreenshotRateLimits {
    private var consecutive = 0
    fun onRateLimited(): Int {
        if (consecutive < Int.MAX_VALUE) consecutive++
        return consecutive
    }
    fun reset() { consecutive = 0 }
}
