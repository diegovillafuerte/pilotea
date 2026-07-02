package mx.kompara.ocr

/** What the silent-screenshot capture loop should do on a given tick. */
internal enum class LaneAction {
    /** A target host app is foreground and the screen is on — take a screenshot and OCR it. */
    CAPTURE,

    /** Nothing to read right now (no host foreground, or screen off) — poll again shortly. */
    IDLE,

    /** takeScreenshot has hard-failed too many times on this device — hand off to the fallback lane. */
    DISABLE,
}

/**
 * The pure decision at the heart of the silent-screenshot lane (B-091), split out so the gating is
 * unit-tested without Android. Given whether a target host app is currently foreground, whether the
 * screen is interactive, and how many consecutive HARD takeScreenshot failures we've seen, it decides
 * whether to capture, idle, or disable the lane (falling back to MediaProjection).
 *
 * Rate-limit failures ([android.accessibilityservice.AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT])
 * are NOT hard failures — the caller just backs off and does not increment the counter — so a busy
 * device throttling us never trips the disable path.
 */
internal object SilentLaneGate {

    fun decide(
        hostForeground: Boolean,
        screenInteractive: Boolean,
        consecutiveHardFailures: Int,
        maxHardFailures: Int,
    ): LaneAction = when {
        consecutiveHardFailures >= maxHardFailures -> LaneAction.DISABLE
        hostForeground && screenInteractive -> LaneAction.CAPTURE
        else -> LaneAction.IDLE
    }
}
