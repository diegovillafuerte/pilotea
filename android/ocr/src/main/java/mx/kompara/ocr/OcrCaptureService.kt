package mx.kompara.ocr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import mx.kompara.data.service.HostForegroundObservation
import mx.kompara.data.service.ScreenReaderState
import mx.kompara.data.service.ScreenRect

/**
 * Proof-of-concept screen-capture + OCR service for the SurfaceView platforms (DiDi/inDrive), which
 * expose no accessibility text (design doc §7). Captures frames via MediaProjection, throttles to a
 * few per second, runs [OcrEngine], and publishes recognized offers through [OfferFramePipeline].
 * Debug fixtures can validate that ML Kit reads real DiDi/inDrive cards; logcat contains no text.
 *
 * Lifecycle (B-075): Android revokes the projection on every screen lock / consent withdrawal, and
 * the grant is one-shot — the service cannot restart itself. So an unexpected stop posts a
 * shared readiness incident (delivered by the app's watchdog),
 * fully stops the foreground service (no stale "leyendo la pantalla" notification), and mirrors the
 * live state into [ScreenReaderState] for the Lector tab.
 */
@dagger.hilt.android.AndroidEntryPoint
class OcrCaptureService : Service() {

    private val lifecycle = ProjectionLifecycle()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val exceptionHandler = CoroutineExceptionHandler { context, error ->
        stopAfterFailure(context[CoroutineName]?.name ?: "capture coroutine", error)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
    private val diagnostics = OcrDiagnostics()
    private val engine = OcrEngine()
    // All parse → verdict → ledger logic lives in the shared, capture-source-agnostic pipeline so this
    // MediaProjection lane and the API 30+ [SilentScreenshotLane] can never drift (B-091).
    @javax.inject.Inject lateinit var readiness: mx.kompara.data.service.ReaderReadinessRepository
    @javax.inject.Inject lateinit var coordinator: CaptureCoordinator
    private val ownership get() = coordinator.ownership
    private var captureSession: CaptureSession? = null
    private var burstHost: String? = null
    private var burstGeneration = 0L
    private val pipeline by lazy { OfferFramePipeline(readiness) }
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var lastOcrAt = 0L
    // OCR frames run through a SINGLE sequential consumer: each frame is fully handled (parse +
    // overlay emit + ledger signal) before the next, so the shared state (presence / lastOffer* /
    // lifecycleClassifier) and the persisted ledger can't be raced or written out of order by
    // concurrent ML Kit completions. CONFLATED keeps only the latest frame — stale ones are dropped
    // under load, which is what we want (we only ever care about the current screen). Full-screen
    // bitmaps are native-heap-heavy, so onUndeliveredElement recycles any frame the channel drops
    // (conflation replacement, or buffered frames when the channel is closed on destroy).
    private val frameChannel = Channel<CapturedFrame>(
        capacity = Channel.CONFLATED,
        onUndeliveredElement = { it.bitmap.recycle() },
    )
    private var consumerStarted = false
    // Session/permit checks and pipeline publication share the arbiter's monitor with both lanes.
    // Slow recognition remains outside the monitor; an obsolete completion cannot publish.

    // chipRect is snapshotted at CAPTURE time so the mask matches THESE pixels: OCR is async, and a
    // drag/attach/detach between capture and parse would otherwise misalign the live rect with the
    // frame (leak the old chip block, or blank host text at the new spot). See [ChipMask].
    private data class CapturedFrame(val permit: CapturePermit, val host: String, val burst: Long, val bitmap: Bitmap, val chipRect: ScreenRect?, val token: Long,
        val capturedAtMs: Long, val density: Float, val accessibility: mx.kompara.capture.HostWindowEvidence?,
        val windowOwnership: mx.kompara.capture.HostWindowOwnership?)

    override fun onCreate() {
        super.onCreate()
        scope.launch(CoroutineName("MediaProjection readiness")) {
            readiness.state.collect { state ->
                if (!state.canRead) ownership.serialized {
                    if (!readiness.canRead()) captureSession?.let { ownership.permit(it) }?.let { permit ->
                        ownership.withOwner(permit) { closePipeline() }
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!lifecycle.isRunning) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            beginIntentionalStop()
            readiness.pauseUntilTomorrow()
            teardown(restarting = true)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.let {
            if (Build.VERSION.SDK_INT >= 33) it.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") it.getParcelableExtra(EXTRA_DATA)
        }
        if (resultCode == 0 || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startProjection(resultCode, data)
        } catch (error: Throwable) {
            stopAfterFailure("MediaProjection start", error)
        }
        return START_NOT_STICKY
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        lateinit var mp: MediaProjection
        lateinit var surface: CaptureSurface
        var session: CaptureSession? = null
        val started = ProjectionStartup().start(
            prepare = {
                startAsForeground()
                // Re-consent drops the old one-shot grant before preparing its replacement.
                if (projection != null) teardown(restarting = true)
                mp = checkNotNull(getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data))
                projection = mp
                // Android 14+ requires this callback. Our teardown clears projection before stop().
                mp.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        if (projection !== mp) return
                        onProjectionLost()
                    }
                }, null)
                surface = createCaptureSurface()
            },
            createDisplay = {
                mp.createVirtualDisplay(
                    "kompara-ocr",
                    surface.width, surface.height, surface.density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface.reader.surface,
                    null, null,
                ).also { virtualDisplay = it }
            },
            wireReader = {
                wireReader(surface) { session }
                startFrameConsumer()
            },
            register = {
                ownership.serialized {
                    // An immediately exited consumer must never become a retained fallback.
                    check(lifecycle.isRunning && scope.isActive)
                    session = ownership.start(CaptureLane.PROJECTION, ::closePipeline).also { captureSession = it }
                }
            },
            failure = { stopAfterFailure("MediaProjection start", it) },
        )
        if (!started) return
        // The reader may have been restarted from the Lector tab rather than the stopped
        // notification — clear it so it can't claim "detenido" while capture runs.
        getSystemService(NotificationManager::class.java).cancel(STOPPED_NOTIF_ID)
        val reason = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> "API below 30"
            ScreenReaderState.silentLaneFailed.value -> "silent lane unavailable"
            else -> "driver granted screen capture"
        }
        Log.i(TAG, "MediaProjection session available: $reason; silent capture has priority")
    }

    private data class CaptureSurface(val reader: ImageReader, val width: Int, val height: Int, val density: Int)

    private fun createCaptureSurface(): CaptureSurface {
        val wm = getSystemService(WindowManager::class.java)
        // minSdk is 26, but currentWindowMetrics is API 30+. Mirror OverlayController.screenWidth/
        // screenHeight: WindowMetrics on R+, the deprecated getRealSize fallback on API 26–29.
        val width: Int
        val height: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            width = bounds.width()
            height = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val size = android.graphics.Point().also { wm.defaultDisplay.getRealSize(it) }
            width = size.x
            height = size.y
        }
        val density = resources.displayMetrics.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        return CaptureSurface(reader, width, height, density)
    }

    private fun startFrameConsumer() {
        // Launch the single frame consumer once for the service's lifetime; a re-consent reuses it
        // (the scope outlives teardown). Processing frames one at a time, in order, is what serializes
        // the whole stateful pipeline.
        if (!consumerStarted) {
            consumerStarted = true
            scope.launch(CoroutineName("MediaProjection frame pipeline")) {
                for (frame in frameChannel) runOcr(frame)
            }.invokeOnCompletion { error ->
                // A cancelled OCR continuation need not cancel the supervisor. Never retain a
                // supposedly available fallback after its only consumer has exited (even before
                // the body started). This service owns the consumer across projection sessions.
                if (scope.isActive) stopAfterFailure("MediaProjection frame consumer exited", error ?: IllegalStateException())
            }
        }
    }

    private fun wireReader(surface: CaptureSurface, session: () -> CaptureSession?) {
        val (reader, width, height) = surface
        reader.setOnImageAvailableListener({ r ->
            if (imageReader !== r || !lifecycle.isRunning) return@setOnImageAvailableListener
            try {
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    // A parked projection drains images without allocating bitmaps or doing OCR.
                    // Keep the one-shot platform grant intact for automatic fallback after stand-down.
                    val currentSession = session() ?: return@setOnImageAvailableListener
                    val permit = ownership.permit(currentSession) ?: return@setOnImageAvailableListener
                    val now = SystemClock.elapsedRealtime()
                    val observation = ScreenReaderState.liveForegroundObservation()
                    val host = (observation as? HostForegroundObservation.Target)?.packageName
                    val readingAllowed = readiness.canRead() && (host == null || readiness.canRead(host))
                    if (host == null || !readingAllowed) {
                        diagnostics.blocked(if (host == null) "HOST_NOT_FOREGROUND" else "READINESS")
                        ownership.withOwner(permit) {
                            if (CaptureHostPolicy.mustEnd(observation, true, readingAllowed)) closePipeline()
                            else pipeline.onCaptureUncertain(System.currentTimeMillis())
                        }
                        return@setOnImageAvailableListener
                    }
                    val burst = ownership.serialized {
                        if (!ownership.withOwner(permit) {
                            if (burstHost != null && burstHost != host) closePipeline()
                            pipeline.onCaptureStart()
                            burstHost = host
                        }) null else burstGeneration
                    } ?: return@setOnImageAvailableListener
                    val token = readiness.captureToken()
                    if (now - lastOcrAt < THROTTLE_MS) return@setOnImageAvailableListener
                    lastOcrAt = now
                    // Hand off to the sequential consumer, tagged with this session's generation; CONFLATED
                    // replaces any unprocessed frame. Recycle if the channel is closed (teardown race).
                    val capturedAtMs = System.currentTimeMillis()
                    val frame = CapturedFrame(permit, host, burst, image.toBitmap(width, height), ScreenReaderState.overlayChipRect, token,
                        capturedAtMs, resources.displayMetrics.density,
                        mx.kompara.capture.HostGeometryEvidence.state.value,
                        mx.kompara.capture.HostWindowOwnershipSource.current(host, capturedAtMs))
                    if (frameChannel.trySend(frame).isFailure) frame.bitmap.recycle()
                } finally {
                    image.close()
                }
            } catch (error: Throwable) {
                stopAfterFailure("MediaProjection image callback", error)
            }
        }, null)
    }

    private suspend fun runOcr(frame: CapturedFrame) {
        if (ownership.permit(frame.permit.session) != frame.permit || !readiness.canRead(token = frame.token)) {
            diagnostics.blocked("MediaProjection readiness/canRead false or stale capture token")
            frame.bitmap.recycle()
            return
        }
        val display = mx.kompara.parsers.model.HostRect(0, 0, frame.bitmap.width, frame.bitmap.height)
        val blocks = try {
            engine.recognize(frame.bitmap)
        } catch (cancellation: CancellationException) {
            // Scope cancelled (teardown) mid-recognition: ML Kit's native task may still be reading
            // the bitmap, so do NOT recycle here — GC reclaims it on shutdown rather than risk a
            // use-after-recycle. Honor the cancellation.
            throw cancellation
        } catch (t: Throwable) {
            Log.e(TAG, "OCR failed (${t.javaClass.simpleName})")
            ownership.withOwner(frame.permit) { pipeline.captureBlocked("OCR_FAILURE") }
            frame.bitmap.recycle() // ML Kit finished (with an error) — safe to free.
            return
        }
        // Retain only coarse luminance evidence from this frame; no bitmap crosses the pipeline.
        val appearance = try {
            HostAppearanceSamples.capture(frame.bitmap, frame.capturedAtMs, frame.chipRect)
        } finally {
            frame.bitmap.recycle()
        }
        // Ownership is checked inside the shared monitor: teardown/handoff cannot race publication.
        // A resumed projection gets a fresh permit, so even its own pre-handoff frames are dropped.
        ownership.withOwner(frame.permit) {
            val current = ScreenReaderState.liveForegroundObservation()
            val currentHost = (current as? HostForegroundObservation.Target)?.packageName
            val currentWindows = mx.kompara.capture.HostWindowOwnershipSource.current(frame.host, System.currentTimeMillis())
            if (burstGeneration == frame.burst && burstHost == frame.host && readiness.canRead(token = frame.token) &&
                currentHost == frame.host && frame.windowOwnership?.samePixels(currentWindows) == true) {
                pipeline.process(blocks, frame.chipRect, if (BuildConfig.DEBUG) getExternalFilesDir(null) else null,
                    capturedAtMs = frame.capturedAtMs, token = frame.token, displayBounds = display,
                    density = frame.density, accessibility = frame.accessibility, appearance = appearance,
                    foregroundPackage = frame.host, windowOwnership = frame.windowOwnership)
            } else {
                diagnostics.blocked("MediaProjection stale generation or readiness/canRead false")
                if (burstGeneration == frame.burst && burstHost == frame.host && currentHost != frame.host) {
                    if (current == HostForegroundObservation.Unknown) {
                        pipeline.onCaptureUncertain(System.currentTimeMillis())
                    } else {
                        closePipeline()
                    }
                } else if (burstGeneration == frame.burst && burstHost == frame.host) {
                    pipeline.onCaptureUncertain(System.currentTimeMillis())
                }
            }
        }
    }

    private fun startAsForeground() {
        val channelId = "ocr_capture"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, getString(R.string.capture_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = captureNotification(this)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    /**
     * Android revoked the projection (screen lock is the everyday case — STOP_REASON_KEYGUARD).
     * The grant is one-shot, so recovery needs the driver: post a tap-to-restart notification, then
     * stop the service entirely so the foreground "leyendo la pantalla" notification can't linger
     * stale (B-075).
     */
    private fun onProjectionLost() {
        if (lifecycle.isIntentionalStop) return
        // One shared incident owner handles this and permission loss. Paused/denied notifications
        // must not bypass that owner through a second OCR-specific notification path.
        teardown(failed = true)
        cleanup("foreground notification") { stopForeground(STOP_FOREGROUND_REMOVE) }
        cleanup("capture service") { stopSelf() }
    }

    /** A failed supervised child still needs a handler: otherwise Android kills the shared process. */
    private fun stopAfterFailure(component: String, error: Throwable) {
        if (!ownership.serialized { lifecycle.fail() }) return
        Log.e(TAG, "$component failed; stopping MediaProjection lane (${error.javaClass.simpleName})")
        scope.cancel()
        // Service/window callbacks stay on main. The failed child has released the ownership monitor.
        if (Looper.myLooper() == mainHandler.looper) onProjectionLost()
        else mainHandler.post { onProjectionLost() }
    }

    private inline fun cleanup(component: String, block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            // Every platform resource still needs its release attempt, including on cancellation.
            Log.e(TAG, "$component cleanup failed (${error.javaClass.simpleName})")
        }
    }

    private fun closePipeline() {
        burstGeneration++
        burstHost = null
        try {
            pipeline.onCaptureEnd(System.currentTimeMillis())
        } catch (error: Throwable) {
            // A cleanup failure cannot skip ownership release or take down the replacement lane.
            Log.e(TAG, "frame pipeline cleanup failed (${error.javaClass.simpleName})")
        }
    }

    private fun teardown(restarting: Boolean = false, failed: Boolean = false) {
        ownership.serialized {
            val session = captureSession
            if (session != null) ownership.stop(session, failed = failed, intentional = restarting)
            else if (failed) ownership.startupFailed()
            captureSession = null
        }
        val oldDisplay = virtualDisplay
        virtualDisplay = null
        cleanup("virtual display") { oldDisplay?.release() }
        val oldReader = imageReader
        imageReader = null
        cleanup("image reader") { oldReader?.close() }
        val oldProjection = projection
        projection = null
        cleanup("projection") { oldProjection?.stop() }
    }

    private fun beginIntentionalStop() {
        ownership.serialized { lifecycle.stopIntentionally() }
        // Consumer completion and late callbacks must not report shutdown as a capture failure.
        scope.cancel()
    }

    override fun onDestroy() {
        beginIntentionalStop()
        teardown(restarting = true)
        // Cancel the frame channel so any buffered (conflated) bitmap is handed to onUndeliveredElement
        // and recycled, instead of leaking when the scope is cancelled. Safe: the instance is dying;
        // a restart creates a fresh service with a new channel.
        frameChannel.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KomparaOCR"
        private const val NOTIF_ID = 42
        // Cancel the retired OCR-specific outage notice; shared incident delivery owns recovery.
        private const val STOPPED_NOTIF_ID = 43
        // 300 ms bounds the offer→verdict latency (plus ML Kit time); ~3 fps full-screen OCR is
        // fine on the target devices and only runs while the driver has the reader on.
        private const val THROTTLE_MS = 300L
        const val ACTION_STOP = "mx.kompara.action.STOP_SCREEN_READER"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, OcrCaptureService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            context.startForegroundService(intent)
        }
    }
}
