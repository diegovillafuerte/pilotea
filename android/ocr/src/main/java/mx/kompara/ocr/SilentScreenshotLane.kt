package mx.kompara.ocr

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import mx.kompara.capture.KomparaAccessibilityService
import mx.kompara.capture.ScreenReaderLane
import mx.kompara.data.service.ScreenReaderState
import mx.kompara.data.service.ScreenRect
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * The **primary, Play-clean capture lane** on API 30+ (B-091): it reads the host app's trip-offer
 * card via [AccessibilityService.takeScreenshot] — NO MediaProjection consent prompt, NO persistent
 * screen-cast indicator, and it survives screen lock — then OCRs the frame in memory and publishes the
 * verdict through the shared [OfferFramePipeline]. Nothing about the offer or the screen is stored or
 * transmitted (release builds write no fixtures), which is what keeps the Data-Safety declaration true.
 *
 * It captures **only while a target host app (Uber/DiDi/inDrive) is the live foreground app** — gated
 * on `rootInActiveWindow`'s package, read fresh each tick — so it never screenshots an unrelated app
 * (e.g. the driver's banking or WhatsApp screen). Frames are captured, OCR'd, and recycled one at a
 * time in a single sequential loop, so the pipeline's "single serialized context" requirement holds.
 *
 * Fallback: below API 30 [start] returns false and the caller keeps the MediaProjection
 * [OcrCaptureService] as the only lane. If `takeScreenshot` HARD-fails repeatedly on a device (an OEM
 * that broke it), the lane disables itself and clears [ScreenReaderState.silentLaneActive], which
 * re-enables the manual MediaProjection path (the reader-down banner / Lector "Iniciar" CTA).
 */
class SilentScreenshotLane : ScreenReaderLane {

    private val engine = OcrEngine()
    private var loop: Job? = null
    // True only while THIS lane owns ScreenReaderState.running (started on 30+ and not yet stood down).
    // Guards stop()/teardown from clobbering the MediaProjection fallback's running state on <30 or
    // after a self-disable.
    @Volatile private var owningRunning = false

    override fun start(scope: CoroutineScope, service: AccessibilityService): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        if (loop?.isActive == true) return true
        ScreenReaderState.setSilentLaneActive(true)
        // On 30+ the reader is live for the whole time the a11y service is enabled — no per-session
        // projection to lose — so it reports "running" for the lane's lifetime, not per capture burst.
        owningRunning = true
        ScreenReaderState.setRunning(true)
        // Each capture loop owns a FRESH pipeline: if a service reconnect launches a replacement loop
        // while a cancelled one is still unwinding, the old loop's teardown resets its OWN (orphaned)
        // pipeline, never the new session's — no shared-state race across loop generations.
        loop = scope.launch { captureLoop(service, OfferFramePipeline()) }
        return true
    }

    override fun stop() {
        loop?.cancel()
        loop = null
        ScreenReaderState.setSilentLaneActive(false)
        // Only clear running if this lane actually owns it — never stomp a MediaProjection fallback.
        if (owningRunning) {
            owningRunning = false
            ScreenReaderState.setRunning(false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureLoop(service: AccessibilityService, pipeline: OfferFramePipeline) {
        val power = service.getSystemService(PowerManager::class.java)
        var hardFailures = 0
        var capturing = false
        try {
            while (currentCoroutineContext().isActive) {
                val hostForeground = targetAppForeground(service)
                val screenInteractive = power?.isInteractive != false
                when (SilentLaneGate.decide(hostForeground, screenInteractive, hardFailures, MAX_HARD_FAILURES)) {
                    LaneAction.DISABLE -> {
                        // OEM broke takeScreenshot: stand down so the MediaProjection fallback can take over.
                        Log.w(TAG, "silent screenshot lane disabled after $hardFailures failures; MediaProjection fallback")
                        if (capturing) {
                            pipeline.onCaptureEnd(System.currentTimeMillis())
                            capturing = false
                        }
                        ScreenReaderState.setSilentLaneActive(false)
                        if (owningRunning) {
                            owningRunning = false
                            ScreenReaderState.setRunning(false)
                        }
                        return
                    }
                    LaneAction.IDLE -> {
                        // Left the host app / screen off: close the session so the chip hides and the
                        // ledger doesn't dangle, then poll cheaply for the next host-foreground window.
                        if (capturing) {
                            pipeline.onCaptureEnd(System.currentTimeMillis())
                            capturing = false
                        }
                        delay(IDLE_POLL_MS)
                    }
                    LaneAction.CAPTURE -> {
                        capturing = true
                        when (val shot = takeScreenshot(service)) {
                            is ShotResult.Ok -> {
                                hardFailures = 0
                                // Snapshot the chip rect at CAPTURE time so the mask matches THESE
                                // pixels — the chip may move/hide while ML Kit runs (parity with the
                                // MediaProjection lane's CapturedFrame.chipRect).
                                val chipRect = ScreenReaderState.overlayChipRect
                                processShot(shot.bitmap, chipRect, pipeline)
                                delay(THROTTLE_MS)
                            }
                            ShotResult.RateLimited -> delay(RATE_LIMIT_BACKOFF_MS)
                            ShotResult.HardError -> {
                                hardFailures++
                                delay(RATE_LIMIT_BACKOFF_MS)
                            }
                        }
                    }
                }
            }
        } finally {
            // Service destroyed / scope cancelled mid-offer: never strand a verdict or leave the OCR
            // ledger session open. onCaptureEnd only publishes to non-suspending buses + resets the
            // (singleton) pipeline state, so a later service reconnect starts clean. tryEmit is safe
            // to run from a cancelled context's finally.
            if (capturing) pipeline.onCaptureEnd(System.currentTimeMillis())
        }
    }

    /**
     * Is a target host app the LIVE foreground app right now? Reads `rootInActiveWindow`'s package (the
     * authoritative "what's in front", unlike the a11y event stream which never fires when the driver
     * leaves a target app — packageNames filter), and recycles the node where that still matters (<33).
     */
    private fun targetAppForeground(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        return try {
            root.packageName?.toString() in KomparaAccessibilityService.OCR_OWNED_PACKAGES
        } finally {
            if (Build.VERSION.SDK_INT < 33) @Suppress("DEPRECATION") root.recycle()
        }
    }

    private suspend fun processShot(bitmap: Bitmap, chipRect: ScreenRect?, pipeline: OfferFramePipeline) {
        val blocks = try {
            engine.recognize(bitmap)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            // Scope cancelled (teardown) mid-recognition: ML Kit's native task may still be reading the
            // bitmap, so do NOT recycle here — GC reclaims it on shutdown rather than risk a
            // use-after-recycle. Honor the cancellation. (Parity with OcrCaptureService.runOcr.)
            throw cancellation
        } catch (t: Throwable) {
            // An OCR failure is not a capture-capability failure — log and free the frame.
            Log.e(TAG, "OCR failed on screenshot frame", t)
            bitmap.recycle()
            return
        }
        // Recognition completed; ML Kit no longer touches the bitmap and the rest works off `blocks`.
        bitmap.recycle()
        pipeline.process(blocks, chipRect, null)
    }

    private sealed interface ShotResult {
        data class Ok(val bitmap: Bitmap) : ShotResult
        data object RateLimited : ShotResult
        data object HardError : ShotResult
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun takeScreenshot(service: AccessibilityService): ShotResult =
        suspendCancellableCoroutine { cont ->
            val executor = Executor { it.run() }
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                executor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val bitmap = toSoftwareBitmap(screenshot)
                        val result = bitmap?.let { ShotResult.Ok(it) } ?: ShotResult.HardError
                        // resume(value, onCancellation): if the continuation is already cancelled, or is
                        // cancelled before the value is delivered, the lambda runs with the discarded
                        // value and we recycle its frame — closing the leak the plain isActive-check
                        // TOCTOU left open.
                        cont.resume(result) { _, discarded, _ ->
                            (discarded as? ShotResult.Ok)?.bitmap?.recycle()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        cont.resumeIfActive(
                            if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                                ShotResult.RateLimited
                            } else {
                                ShotResult.HardError
                            },
                        )
                    }
                },
            )
        }

    private fun CancellableContinuation<ShotResult>.resumeIfActive(value: ShotResult) {
        if (isActive) resume(value)
    }

    /**
     * Copy the hardware-buffer screenshot into a software ARGB_8888 bitmap ML Kit can read, then
     * release the hardware buffer immediately. The frame lives only in memory; it is never written to
     * disk or transmitted.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun toSoftwareBitmap(screenshot: AccessibilityService.ScreenshotResult): Bitmap? {
        val buffer = screenshot.hardwareBuffer
        return try {
            val hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace) ?: return null
            // Recycle the wrapped hardware bitmap even if copy() throws — else it leaks until GC.
            try {
                hardware.copy(Bitmap.Config.ARGB_8888, false)
            } finally {
                hardware.recycle()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "hardware-buffer conversion failed", t)
            null
        } finally {
            buffer.close()
        }
    }

    companion object {
        private const val TAG = "KomparaSilentLane"
        // takeScreenshot is system-rate-limited (~1/s on most devices); this throttle only bounds the
        // busy-loop, the system enforces the real ceiling. NEEDS ON-DEVICE CALIBRATION.
        private const val THROTTLE_MS = 700L
        private const val IDLE_POLL_MS = 750L
        private const val RATE_LIMIT_BACKOFF_MS = 1_000L
        // How many consecutive HARD (non-rate-limit) takeScreenshot failures before we conclude the
        // device's screenshot path is broken and fall back to MediaProjection.
        private const val MAX_HARD_FAILURES = 5
    }
}
