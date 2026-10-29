package mx.kompara.ocr

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CancellationException
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
import java.util.concurrent.atomic.AtomicBoolean

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
class SilentScreenshotLane(private val readiness: mx.kompara.data.service.ReaderReadinessRepository) : ScreenReaderLane {

    private val engine = OcrEngine()
    private var loop: Job? = null
    // True only while THIS lane owns ScreenReaderState.running (started on 30+ and not yet stood down).
    // Guards stop()/teardown from clobbering the MediaProjection fallback's running state on <30 or
    // after a self-disable.
    private val owningRunning = AtomicBoolean(false)
    private val diagnosticTimes = mutableMapOf<String, Long>()

    override fun start(scope: CoroutineScope, service: AccessibilityService): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.i(TAG, "silent lane unavailable: API below 30; MediaProjection required")
            return false
        }
        if (loop?.isActive == true) return true
        val capabilities = service.serviceInfo?.capabilities ?: 0
        if (!canStartSilentLane(Build.VERSION.SDK_INT, capabilities)) {
            standDown("screenshot capability missing (capabilities=$capabilities); toggle Accessibility to refresh a stale grant")
            return false
        }
        ScreenReaderState.setSilentLaneFailed(false)
        ScreenReaderState.setSilentLaneActive(true)
        // On 30+ the reader is live for the whole time the a11y service is enabled — no per-session
        // projection to lose — so it reports "running" for the lane's lifetime, not per capture burst.
        owningRunning.set(true)
        readiness.captureRunning(true)
        ScreenReaderState.setRunning(true)
        // Each capture loop owns a FRESH pipeline: if a service reconnect launches a replacement loop
        // while a cancelled one is still unwinding, the old loop's teardown resets its OWN (orphaned)
        // pipeline, never the new session's — no shared-state race across loop generations.
        diagnosticTimes.clear()
        Log.i(TAG, "silent lane active: API 30+ screenshot capability granted")
        loop = scope.launch { captureLoop(service) }
        return true
    }

    override fun stop() {
        loop?.cancel()
        loop = null
        ScreenReaderState.setSilentLaneActive(false)
        releaseRunning()
    }

    private fun releaseRunning() {
        // Only clear running if this lane actually owns it — never stomp a MediaProjection fallback.
        if (owningRunning.compareAndSet(true, false)) {
            ScreenReaderState.setRunning(false)
            readiness.captureRunning(false)
        }
    }

    private fun standDown(reason: String) {
        Log.e(TAG, "silent screenshot lane disabled: $reason; MediaProjection fallback")
        ScreenReaderState.setSilentLaneFailed(true)
        ScreenReaderState.setSilentLaneActive(false)
        val ownedRunning = owningRunning.get()
        releaseRunning()
        // A missing grant can prevent our first capture entirely. Surface that failure too, but
        // never replace a live MediaProjection fallback's readiness/running state.
        if (!ownedRunning && !ScreenReaderState.running.value) readiness.captureFailed()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureLoop(service: AccessibilityService) {
        var pipeline: OfferFramePipeline? = null
        var capturing = false
        try {
            val session = OfferFramePipeline(readiness)
            pipeline = session
            val power = service.getSystemService(PowerManager::class.java)
            var hardFailures = 0
            while (currentCoroutineContext().isActive) {
                val hostForeground = targetAppForeground(service)
                val screenInteractive = power?.isInteractive != false
                val readingAllowed = readiness.canRead()
                when (SilentLaneGate.decide(hostForeground, screenInteractive, hardFailures, MAX_HARD_FAILURES, readingAllowed)) {
                    LaneAction.DISABLE -> {
                        standDown("$hardFailures consecutive screenshot hard failures")
                        return
                    }
                    LaneAction.IDLE -> {
                        val reason = when {
                            !readingAllowed -> "readiness/canRead=false"
                            !hostForeground -> "host not in foreground or not enabled for reading"
                            else -> "screenInteractive=false"
                        }
                        logSuppressed("idle:$reason", "chip hidden: gate=IDLE reason=$reason")
                        // Left the host app / screen off: close the session so the chip hides and the
                        // ledger doesn't dangle, then poll cheaply for the next host-foreground window.
                        if (capturing) {
                            session.onCaptureEnd(System.currentTimeMillis())
                            capturing = false
                        }
                        delay(IDLE_POLL_MS)
                    }
                    LaneAction.CAPTURE -> {
                        capturing = true
                        val token = readiness.captureToken()
                        when (val shot = takeScreenshot(service)) {
                            is ShotResult.Ok -> {
                                hardFailures = 0
                                // Snapshot the chip rect at CAPTURE time so the mask matches THESE
                                // pixels — the chip may move/hide while ML Kit runs (parity with the
                                // MediaProjection lane's CapturedFrame.chipRect).
                                val chipRect = ScreenReaderState.overlayChipRect
                                processShot(shot.bitmap, chipRect, session, token, service.resources.displayMetrics.density)
                                delay(THROTTLE_MS)
                            }
                            ShotResult.NoCapability -> {
                                standDown("takeScreenshot denied screenshot capability (SecurityException)")
                                return
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
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            if (currentCoroutineContext().isActive) standDown("capture loop failed (${error.javaClass.simpleName})")
            else Log.e(TAG, "stopped capture loop failed (${error.javaClass.simpleName})")
        } finally {
            // Service destroyed / scope cancelled mid-offer: never strand a verdict or leave the OCR
            // ledger session open. onCaptureEnd only publishes to non-suspending buses + resets the
            // (singleton) pipeline state, so a later service reconnect starts clean. tryEmit is safe
            // to run from a cancelled context's finally.
            if (capturing) {
                try {
                    pipeline?.onCaptureEnd(System.currentTimeMillis())
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    // A cancelled session may be unwinding after a replacement/fallback started.
                    if (currentCoroutineContext().isActive) standDown("capture cleanup failed (${error.javaClass.simpleName})")
                    else Log.e(TAG, "stopped capture cleanup failed (${error.javaClass.simpleName})")
                }
            }
        }
    }

    private fun logSuppressed(key: String, message: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        val last = diagnosticTimes[key]
        if (last == null || now - last >= DIAGNOSTIC_INTERVAL_MS) {
            diagnosticTimes[key] = now
            Log.d("KomparaOCR", message)
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
            root.packageName?.toString()?.let { it in KomparaAccessibilityService.OCR_OWNED_PACKAGES && readiness.canRead(it) } == true
        } finally {
            if (Build.VERSION.SDK_INT < 33) @Suppress("DEPRECATION") root.recycle()
        }
    }

    private suspend fun processShot(bitmap: Bitmap, chipRect: ScreenRect?, pipeline: OfferFramePipeline, token: Long, density: Float) {
        if (!readiness.canRead(token = token)) {
            logSuppressed("readiness", "chip hidden: screenshot discarded; readiness/canRead=false or stale capture token")
            bitmap.recycle()
            return
        }
        val capturedAtMs = System.currentTimeMillis()
        val display = mx.kompara.parsers.model.HostRect(0, 0, bitmap.width, bitmap.height)
        val accessibility = mx.kompara.capture.HostGeometryEvidence.state.value
        val blocks = try {
            engine.recognize(bitmap)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            // Scope cancelled (teardown) mid-recognition: ML Kit's native task may still be reading the
            // bitmap, so do NOT recycle here — GC reclaims it on shutdown rather than risk a
            // use-after-recycle. Honor the cancellation. (Parity with OcrCaptureService.runOcr.)
            throw cancellation
        } catch (t: Throwable) {
            // An OCR failure is not a capture-capability failure — log and free the frame.
            Log.e(TAG, "OCR failed on screenshot frame (${t.javaClass.simpleName})")
            bitmap.recycle()
            return
        }
        // Retain only coarse luminance evidence from this frame; no bitmap crosses the pipeline.
        val appearance = try {
            HostAppearanceSamples.capture(bitmap, capturedAtMs, chipRect)
        } finally {
            bitmap.recycle()
        }
        pipeline.process(blocks, chipRect, null, capturedAtMs = capturedAtMs, token = token,
            displayBounds = display, density = density, accessibility = accessibility, appearance = appearance)
    }

    private sealed interface ShotResult {
        data class Ok(val bitmap: Bitmap) : ShotResult
        data object RateLimited : ShotResult
        data object HardError : ShotResult
        data object NoCapability : ShotResult
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun takeScreenshot(service: AccessibilityService): ShotResult =
        suspendCancellableCoroutine { cont ->
            // A vendor may invoke a callback inline and then throw. Only the first result wins;
            // any subsequent successful frame is recycled without a second continuation resume.
            val completed = AtomicBoolean(false)
            fun complete(result: ShotResult) {
                if (!completed.compareAndSet(false, true)) {
                    (result as? ShotResult.Ok)?.bitmap?.recycle()
                    return
                }
                cont.resume(result) { _, discarded, _ ->
                    (discarded as? ShotResult.Ok)?.bitmap?.recycle()
                }
            }
            val executor = Executor { it.run() }
            try {
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    executor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            try {
                                val bitmap = toSoftwareBitmap(screenshot)
                                complete(bitmap?.let { ShotResult.Ok(it) } ?: ShotResult.HardError)
                            } catch (cancellation: CancellationException) {
                                cont.cancel(cancellation)
                            } catch (error: Throwable) {
                                // Callback work runs on the supplied executor, outside captureLoop's
                                // catch. A buffer/conversion failure must not crash the service either.
                                Log.e(TAG, "screenshot conversion failed (${error.javaClass.simpleName})")
                                complete(ShotResult.HardError)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            complete(
                                if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                                    ShotResult.RateLimited
                                } else {
                                    ShotResult.HardError
                                },
                            )
                        }
                    },
                )
            } catch (error: RuntimeException) {
                complete(when (classifyScreenshotFailure(error)) {
                    ScreenshotFailure.NO_CAPABILITY -> ShotResult.NoCapability
                    ScreenshotFailure.HARD_ERROR -> ShotResult.HardError
                })
            }
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
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            Log.e(TAG, "hardware-buffer conversion failed (${t.javaClass.simpleName})")
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
        private const val DIAGNOSTIC_INTERVAL_MS = 10_000L
        // How many consecutive HARD (non-rate-limit) takeScreenshot failures before we conclude the
        // device's screenshot path is broken and fall back to MediaProjection.
        private const val MAX_HARD_FAILURES = 5
    }
}
