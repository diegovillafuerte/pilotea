package mx.kompara.ocr

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mx.kompara.capture.KomparaAccessibilityService
import mx.kompara.capture.ScreenReaderLane
import mx.kompara.data.service.HostForegroundObservation
import mx.kompara.data.service.ScreenReaderState
import mx.kompara.data.service.ScreenRect
import java.util.concurrent.Executor

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
 * A frame is left unrecycled here when its OCR timed out or was cancelled at teardown: ML Kit may still be reading it.
 *
 * Fallback: below API 30 [start] returns false and the caller keeps the MediaProjection
 * [OcrCaptureService] as the only lane. After [MAX_HARD_FAILURES] consecutive capture attempts end
 * with neither a recognized frame nor a skip (a hard screenshot error, a lost screenshot callback, an
 * OCR failure or timeout), the lane disables itself and clears [ScreenReaderState.silentLaneActive],
 * which re-enables the manual MediaProjection path (reader-down, then Semáforo's CAPTURE action). The
 * lane never restarts itself: only an accessibility rebind or a new process brings it back (TD-064).
 */
class SilentScreenshotLane(
    private val readiness: mx.kompara.data.service.ReaderReadinessRepository,
    coordinator: CaptureCoordinator,
    private val recognize: suspend (Bitmap) -> List<OcrBlock>? = OcrEngine()::recognize,
) : ScreenReaderLane {

    private val ownership = coordinator.ownership
    // Both fields, all ownership changes, and all pipeline publications use the same monitor.
    private var loop: Job? = null
    private var activeSession: CaptureSession? = null

    override fun start(scope: CoroutineScope, service: AccessibilityService): Boolean = ownership.serialized {
        // Every bind resets readiness, including devices where this lane can never start.
        ownership.reaffirm()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.i(TAG, "silent lane unavailable: API below 30; MediaProjection required")
            return@serialized false
        }
        if (loop?.isActive == true && activeSession?.let { ownership.permit(it) } != null) return@serialized true
        stop()
        val capabilities = try {
            service.serviceInfo?.capabilities ?: 0
        } catch (error: Throwable) {
            ownership.silentUnavailable()
            Log.e(TAG, "silent lane setup failed (${error.javaClass.simpleName})")
            return@serialized false
        }
        if (!canStartSilentLane(Build.VERSION.SDK_INT, capabilities)) {
            ownership.silentUnavailable()
            Log.e(TAG, "silent lane unavailable: screenshot capability missing capabilities=$capabilities")
            return@serialized false
        }
        val pipeline = OfferFramePipeline(readiness)
        val session = ownership.start(CaptureLane.SILENT) {
            try {
                pipeline.onCaptureEnd(System.currentTimeMillis())
            } catch (error: Throwable) {
                Log.e(TAG, "capture cleanup failed (${error.javaClass.simpleName})")
            }
        }
        activeSession = session
        // Lazy launch lets us register cleanup before even an already-cancelled scope can finish.
        val job = scope.launch(start = CoroutineStart.LAZY) { captureLoop(service, session, pipeline) }
        loop = job
        job.invokeOnCompletion {
            ownership.serialized {
                ownership.stop(session)
                if (activeSession == session) {
                    activeSession = null
                    loop = null
                }
            }
        }
        Log.i(TAG, "silent lane active: API 30+ screenshot capability granted")
        job.start()
        true
    }

    override fun stop() = ownership.serialized {
        val oldJob = loop
        val oldSession = activeSession
        activeSession = null
        loop = null
        // Invalidate and close before cancellation; the old loop's finally cannot touch a successor.
        oldSession?.let { ownership.stop(it) }
        oldJob?.cancel()
        Unit
    }

    private fun standDown(session: CaptureSession, reason: String) = ownership.serialized {
        if (ownership.stop(session, failed = true)) {
            Log.e(TAG, "silent screenshot lane disabled: $reason; MediaProjection fallback")
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    internal suspend fun captureLoop(service: AccessibilityService, session: CaptureSession, pipeline: OfferFramePipeline,
        shoot: suspend () -> ShotResult = { takeScreenshot(service) }) {
        val diagnostics = OcrDiagnostics()
        val rateLimits = ScreenshotRateLimits()
        var rateLimitRetries = 0
        var capturing = false
        var hostPackage: String? = null
        // TEMP DIAG: the debug recorder's files are capped when the lane starts.
        if (BuildConfig.DEBUG) runCatching { service.getExternalFilesDir(null)?.let { OfferFramePipeline.pruneFixtures(it) } }
        try {
            val power = service.getSystemService(PowerManager::class.java)
            var hardFailures = 0
            while (currentCoroutineContext().isActive) {
                val permit = ownership.permit(session) ?: return
                val observation = foregroundHostObservation(service)
                val host = (observation as? HostForegroundObservation.Target)?.packageName
                val screenInteractive = power?.isInteractive != false
                val readingAllowed = readiness.canRead() && (host == null || readiness.canRead(host))
                when (SilentLaneGate.decide(host != null, screenInteractive, hardFailures, MAX_HARD_FAILURES, readingAllowed)) {
                    LaneAction.DISABLE -> {
                        standDown(session, "$hardFailures consecutive capture failures")
                        return
                    }
                    LaneAction.IDLE -> {
                        val reason = when {
                            !readingAllowed -> "READINESS"
                            host == null -> "HOST_NOT_FOREGROUND"
                            else -> "SCREEN_NOT_INTERACTIVE"
                        }
                        diagnostics.blocked(reason)
                        val ended = CaptureHostPolicy.mustEnd(observation, screenInteractive, readingAllowed)
                        ownership.withOwner(permit) {
                            if (capturing) {
                                if (ended) pipeline.onCaptureEnd(System.currentTimeMillis())
                                else pipeline.onCaptureUncertain(System.currentTimeMillis())
                            }
                        }
                        if (ended) {
                            capturing = false
                            hostPackage = null
                        }
                        rateLimits.reset()
                        delay(IDLE_POLL_MS)
                    }
                    LaneAction.CAPTURE -> {
                        if (!ownership.withOwner(permit) {
                            if (capturing && hostPackage != host) {
                                pipeline.onCaptureEnd(System.currentTimeMillis())
                                rateLimits.reset()
                            }
                            pipeline.onCaptureStart()
                        }) return
                        capturing = true
                        hostPackage = host
                        val token = readiness.captureToken()
                        val capturedAtMs = System.currentTimeMillis()
                        val windowOwnership = mx.kompara.capture.HostWindowOwnershipSource.current(requireNotNull(host), capturedAtMs)
                        val shotStarted = SystemClock.elapsedRealtime()
                        when (val shot = shoot()) {
                            is ShotResult.Ok -> {
                                val retries = rateLimitRetries
                                rateLimitRetries = 0
                                rateLimits.reset()
                                val chipRect = ScreenReaderState.overlayChipRect
                                val outcome = processShot(shot.bitmap, chipRect, pipeline, permit, token,
                                    service.resources.displayMetrics.density, diagnostics, service, requireNotNull(host),
                                    capturedAtMs, windowOwnership, SystemClock.elapsedRealtime() - shotStarted, retries)
                                // A screenshot alone proves nothing: only a frame OCR answered clears the run.
                                when (outcome) {
                                    FrameOutcome.RECOGNIZED -> hardFailures = 0
                                    FrameOutcome.FAILED -> hardFailures++
                                    FrameOutcome.SKIPPED -> Unit
                                }
                                delay(THROTTLE_MS)
                            }
                            ShotResult.TimedOut -> {
                                rateLimits.reset()
                                hardFailures++
                                ownership.withOwner(permit) { pipeline.captureBlocked("SCREENSHOT_TIMEOUT", hardFailures) }
                            }
                            ShotResult.NoCapability -> {
                                ownership.withOwner(permit) { pipeline.captureBlocked("SCREENSHOT_NO_CAPABILITY") }
                                standDown(session, "takeScreenshot denied screenshot capability (SecurityException)")
                                return
                            }
                            ShotResult.RateLimited -> {
                                rateLimitRetries++
                                val count = rateLimits.onRateLimited()
                                ownership.withOwner(permit) { pipeline.captureBlocked("SCREENSHOT_RATE_LIMIT", count) }
                                delay(RATE_LIMIT_BACKOFF_MS)
                            }
                            ShotResult.HardError -> {
                                rateLimits.reset()
                                hardFailures++
                                ownership.withOwner(permit) { pipeline.captureBlocked("SCREENSHOT_HARD_ERROR") }
                                delay(RATE_LIMIT_BACKOFF_MS)
                            }
                        }
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            // A platform adapter/OCR can cancel its continuation without cancelling this job.
            // Release a still-current owner in either case; only unexpected cancellation is failure.
            if (currentCoroutineContext().isActive) standDown(session, "unexpected capture cancellation")
            throw cancellation
        } catch (error: Throwable) {
            standDown(session, "capture loop failed (${error.javaClass.simpleName})")
        } finally {
            // Also handles parent cancellation, even with no captured frames. A stale token is inert.
            ownership.stop(session)
        }
    }

    /**
     * Is a target host app the LIVE foreground app right now? Reads `rootInActiveWindow`'s package (the
     * authoritative "what's in front", unlike the a11y event stream which never fires when the driver
     * leaves a target app — packageNames filter), and recycles the node where that still matters (<33).
     */
    private fun foregroundHostObservation(service: AccessibilityService): HostForegroundObservation {
        val root = service.rootInActiveWindow
        val current = try { root?.packageName?.toString() } finally {
            if (Build.VERSION.SDK_INT < 33) @Suppress("DEPRECATION") root?.recycle()
        }
        val interactive = service.getSystemService(PowerManager::class.java)?.isInteractive != false
        return HostForegroundObservation.fromWindow(current, interactive,
            KomparaAccessibilityService.OCR_OWNED_PACKAGES)
    }

    private suspend fun processShot(bitmap: Bitmap, chipRect: ScreenRect?, pipeline: OfferFramePipeline,
        permit: CapturePermit, token: Long, density: Float, diagnostics: OcrDiagnostics,
        service: AccessibilityService, capturedHost: String, capturedAtMs: Long,
        windowOwnership: mx.kompara.capture.HostWindowOwnership?, screenshotMs: Long, rateLimitRetries: Int): FrameOutcome {
        if (ownership.permit(permit.session) != permit || !readiness.canRead(token = token)) {
            diagnostics.blocked("SCREENSHOT_STALE_OR_READINESS")
            bitmap.recycle()
            return FrameOutcome.SKIPPED
        }
        val display = mx.kompara.parsers.model.HostRect(0, 0, bitmap.width, bitmap.height)
        val accessibility = mx.kompara.capture.HostGeometryEvidence.state.value
        val ocrStarted = SystemClock.elapsedRealtime()
        val blocks = try {
            recognize(bitmap)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            // Scope cancelled (teardown) mid-recognition: ML Kit's native task may still be reading the
            // bitmap, so do NOT recycle here — GC reclaims it on shutdown rather than risk a
            // use-after-recycle. Honor the cancellation. (Parity with OcrCaptureService.runOcr.)
            throw cancellation
        } catch (t: Throwable) {
            // ML Kit finished with an error, so the frame is safe to free. It is a failed attempt:
            // a lane whose OCR keeps failing reads nothing, however many screenshots it gets.
            Log.e(TAG, "OCR failed on screenshot frame (${t.javaClass.simpleName})")
            ownership.withOwner(permit) { pipeline.captureBlocked("OCR_FAILURE") }
            bitmap.recycle()
            return FrameOutcome.FAILED
        }
        if (blocks == null) {
            // ML Kit's task cannot be cancelled and may still read this frame: never recycle it here.
            ownership.withOwner(permit) { pipeline.captureBlocked("OCR_TIMEOUT") }
            return FrameOutcome.FAILED
        }
        val ocrMs = SystemClock.elapsedRealtime() - ocrStarted
        // Retain only coarse luminance evidence from this frame; no bitmap crosses the pipeline.
        val appearance = try {
            HostAppearanceSamples.capture(bitmap, capturedAtMs, chipRect)
        } finally {
            bitmap.recycle()
        }
        ownership.withOwner(permit) {
            // Recognition can finish after an app switch. Never publish an old host frame into the
            // new foreground session; the next loop closes the old burst before reading its successor.
            val current = foregroundHostObservation(service)
            val currentWindows = mx.kompara.capture.HostWindowOwnershipSource.current(capturedHost, System.currentTimeMillis())
            if ((current as? HostForegroundObservation.Target)?.packageName == capturedHost &&
                windowOwnership?.samePixels(currentWindows) == true) {
                // TEMP DIAG: debug builds record offer frames (main's OcrCaptureService wiring).
                pipeline.process(blocks, chipRect, if (BuildConfig.DEBUG) service.getExternalFilesDir(null) else null,
                    capturedAtMs = capturedAtMs, token = token,
                    displayBounds = display, density = density, accessibility = accessibility, appearance = appearance,
                    foregroundPackage = capturedHost, windowOwnership = windowOwnership,
                    timing = OfferFramePipeline.FrameTiming(screenshotMs, ocrMs, rateLimitRetries))
            } else {
                pipeline.captureBlocked("SCREENSHOT_HOST_OR_WINDOWS_CHANGED")
                if (current == HostForegroundObservation.Unknown ||
                    (current as? HostForegroundObservation.Target)?.packageName == capturedHost) {
                    pipeline.onCaptureUncertain(System.currentTimeMillis())
                } else {
                    pipeline.onCaptureEnd(System.currentTimeMillis())
                }
            }
        }
        return FrameOutcome.RECOGNIZED
    }

    /** What one screenshot says about the run of failed capture attempts. */
    private enum class FrameOutcome {
        /** OCR answered: the run clears, whether or not the frame was then published. */
        RECOGNIZED,
        /** OCR threw or timed out: one more failed attempt. */
        FAILED,
        /** Dropped as stale or by readiness before OCR: the run neither grows nor clears. */
        SKIPPED,
    }

    internal sealed interface ShotResult {
        data class Ok(val bitmap: Bitmap) : ShotResult
        data object RateLimited : ShotResult
        data object HardError : ShotResult
        data object TimedOut : ShotResult
        data object NoCapability : ShotResult
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun takeScreenshot(service: AccessibilityService): ShotResult =
        awaitScreenshotResultWithin(
            timeoutMs = SCREENSHOT_TIMEOUT_MS,
            timedOut = ShotResult.TimedOut,
            discard = { result: ShotResult -> (result as? ShotResult.Ok)?.bitmap?.recycle() },
            failureResult = { error ->
                when (classifyScreenshotFailure(error)) {
                    ScreenshotFailure.NO_CAPABILITY -> ShotResult.NoCapability
                    ScreenshotFailure.HARD_ERROR -> ShotResult.HardError
                }
            },
        ) { completion ->
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                Executor { it.run() },
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        try {
                            val bitmap = toSoftwareBitmap(screenshot)
                            completion.complete(bitmap?.let { ShotResult.Ok(it) } ?: ShotResult.HardError)
                        } catch (cancellation: CancellationException) {
                            completion.cancel(cancellation)
                        } catch (error: Throwable) {
                            Log.e(TAG, "screenshot conversion failed (${error.javaClass.simpleName})")
                            completion.complete(ShotResult.HardError)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        completion.complete(
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
        // How many consecutive capture attempts may fail (hard error, lost callback, OCR failure or
        // timeout) before the lane stands down. Rate limits and skipped frames do not count.
        private const val MAX_HARD_FAILURES = 5
        // 0.2.19 run, 976 frames on S25: shotMs p50 36, max 71. Throttle + this bound + the frame costs
        // land just under OverlayController.FRAME_SILENCE_MS: a lost callback means usually no blink on
        // an S25, at worst one frame. A shorter bound would trade that for more false timeouts.
        internal const val SCREENSHOT_TIMEOUT_MS = 2_000L
    }
}
