package mx.kompara.capture

import android.accessibilityservice.AccessibilityService
import kotlinx.coroutines.CoroutineScope

/**
 * The seam the accessibility service uses to drive the **silent-screenshot capture lane** (B-091)
 * without `:capture` depending on `:ocr` (the arrow points `:ocr -> :capture`, so it must stay one
 * way). The real implementation lives in `:ocr` (`SilentScreenshotLane`) and is bound into the graph
 * from `:app`; the service just injects this interface.
 *
 * The lane is the primary, Play-clean capture path on API 30+: it reads the host app's offer card via
 * [AccessibilityService.takeScreenshot] — no MediaProjection consent prompt, no persistent screen-cast
 * indicator, and it survives screen lock. Frames are OCR'd in memory and never stored. The existing
 * MediaProjection service ([mx.kompara.ocr.OcrCaptureService], reached via `:app`) stays as the API
 * <30 path and the automatic fallback if `takeScreenshot` fails repeatedly on a device.
 */
interface ScreenReaderLane {

    /**
     * Try to start the silent lane. [service] is the AccessibilityService whose `takeScreenshot`
     * token is used; capturing is collected on [scope] (the service scope) so it stops when the
     * service is destroyed.
     *
     * @return true if the lane started (API 30+), false if unsupported (<30) — the caller then keeps
     *   the MediaProjection path as the only lane.
     */
    fun start(scope: CoroutineScope, service: AccessibilityService): Boolean

    /** Stop the lane and release its capture state. Called from the service's onDestroy. */
    fun stop()
}
