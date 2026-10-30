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
 *
 * Fallback: below API 30 [start] returns false and the caller keeps the MediaProjection
 * [OcrCaptureService] as the only lane. If `takeScreenshot` HARD-fails repeatedly on a device (an OEM
 * that broke it), the lane disables itself and clears [ScreenReaderState.silentLaneActive], which
 * re-enables the manual MediaProjection path (the reader-down banner / Lector "Iniciar" CTA).
 */
class SilentScreenshotLane(
    private val readiness: mx.kompara.data.service.ReaderReadinessRepository,
    coordinator: CaptureCoordinator,
) : ScreenReaderLane {

    private val engine = OcrEngine()
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
    private suspend fun captureLoop(service: AccessibilityService, session: CaptureSession, pipeline: OfferFramePipeline) {
        val diagnostics = OcrDiagnostics()
        val rateLimits = ScreenshotRateLimits()
        var capturing = false
        var hostPackage: String? = null
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
                        standDown(session, "$hardFailures consecutive screenshot hard failures")
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
                        when (val shot = takeScreenshot(service)) {
                            is ShotResult.Ok -> {
                                hardFailures = 0
                                rateLimits.reset()
                                val chipRect = ScreenReaderState.overlayChipRect
                                processShot(shot.bitmap, chipRect, pipeline, permit, token,
                                    service.resources.displayMetrics.density, diagnostics, service, requireNotNull(host),
                                    capturedAtMs, windowOwnership)
                                delay(THROTTLE_MS)
                            }
                            ShotResult.NoCapability -> {
                                ownership.withOwner(permit) { pipeline.captureBlocked("SCREENSHOT_NO_CAPABILITY") }
                                standDown(session, "takeScreenshot denied screenshot capability (SecurityException)")
                                return
                            }
                            ShotResult.RateLimited -> {
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
        windowOwnership: mx.kompara.capture.HostWindowOwnership?) {
        if (ownership.permit(permit.session) != permit || !readiness.canRead(token = token)) {
            diagnostics.blocked("SCREENSHOT_STALE_OR_READINESS")
            bitmap.recycle()
            return
        }
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
            ownership.withOwner(permit) { pipeline.captureBlocked("OCR_FAILURE") }
            bitmap.recycle()
            return
        }
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
                pipeline.process(blocks, chipRect, null, capturedAtMs = capturedAtMs, token = token,
                    displayBounds = display, density = density, accessibility = accessibility, appearance = appearance,
                    foregroundPackage = capturedHost, windowOwnership = windowOwnership)
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
    }

    private sealed interface ShotResult {
        data class Ok(val bitmap: Bitmap) : ShotResult
        data object RateLimited : ShotResult
        data object HardError : ShotResult
        data object NoCapability : ShotResult
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun takeScreenshot(service: AccessibilityService): ShotResult =
        awaitScreenshotResult(
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
        // How many consecutive HARD (non-rate-limit) takeScreenshot failures before we conclude the
        // device's screenshot path is broken and fall back to MediaProjection.
        private const val MAX_HARD_FAILURES = 5
    }
}
