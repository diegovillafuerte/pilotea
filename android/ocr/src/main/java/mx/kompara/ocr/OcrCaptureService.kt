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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mx.kompara.data.service.ScreenReaderState
import mx.kompara.data.service.ScreenRect
import java.util.concurrent.atomic.AtomicBoolean

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

    private val failureStopped = AtomicBoolean(false)
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
    // A frame is tagged with the capture generation it was grabbed in. teardown bumps the generation
    // (under [stateMutex]) so any frame already queued or mid-recognition from before is DROPPED at
    // its generation check instead of mutating the overlay/ledger after the session was reset.
    @Volatile private var captureGeneration = 0
    // Serializes each frame's state mutation with teardown's close+reset. Recognition runs OUTSIDE
    // the lock (it's the slow part and touches no shared state), so the lock is held only for the
    // brief parse+emit, and teardown's runBlocking can't stall the lifecycle callback for long.
    private val stateMutex = Mutex()

    // chipRect is snapshotted at CAPTURE time so the mask matches THESE pixels: OCR is async, and a
    // drag/attach/detach between capture and parse would otherwise misalign the live rect with the
    // frame (leak the old chip block, or blank host text at the new spot). See [ChipMask].
    private data class CapturedFrame(val generation: Int, val bitmap: Bitmap, val chipRect: ScreenRect?, val token: Long,
        val capturedAtMs: Long, val density: Float, val accessibility: mx.kompara.capture.HostWindowEvidence?)

    override fun onCreate() {
        super.onCreate()
        scope.launch(CoroutineName("MediaProjection readiness")) {
            readiness.state.collect { state ->
                if (!state.canRead) stateMutex.withLock { pipeline.onCaptureEnd(System.currentTimeMillis()) }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (failureStopped.get()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
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
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            stopAfterFailure("MediaProjection start", error)
        }
        return START_NOT_STICKY
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        startAsForeground()

        // Re-consent while already capturing (Lector "reiniciar"): drop the old projection first.
        if (projection != null) teardown(restarting = true)

        val mpm = getSystemService(MediaProjectionManager::class.java)
        val mp = mpm.getMediaProjection(resultCode, data)
        if (mp == null) {
            onProjectionLost()
            return
        }
        projection = mp
        // Android 14+ requires a registered callback. It fires for OUR teardown() too (restart,
        // onDestroy) — by then `projection` no longer points at mp, so only an external revocation
        // (keyguard, the system screen-cast chip) takes the recovery path.
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (projection !== mp) return
                onProjectionLost()
            }
        }, null)

        startCapture(mp)
        // The reader may have been restarted from the Lector tab rather than the stopped
        // notification — clear it so it can't claim "detenido" while capture runs.
        getSystemService(NotificationManager::class.java).cancel(STOPPED_NOTIF_ID)
        readiness.captureRunning(true)
        ScreenReaderState.setRunning(true)
        val reason = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> "API below 30"
            ScreenReaderState.silentLaneFailed.value -> "silent lane unavailable"
            else -> "driver granted screen capture"
        }
        Log.i(TAG, "MediaProjection lane active: $reason")
    }

    private fun startCapture(mp: MediaProjection) {
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
        virtualDisplay = mp.createVirtualDisplay(
            "kompara-ocr",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null, null,
        )

        // Launch the single frame consumer once for the service's lifetime; a re-consent reuses it
        // (the scope outlives teardown). Processing frames one at a time, in order, is what serializes
        // the whole stateful pipeline.
        if (!consumerStarted) {
            consumerStarted = true
            scope.launch(CoroutineName("MediaProjection frame pipeline")) {
                for (frame in frameChannel) runOcr(frame)
            }
        }

        // Bind this capture session to an IMMUTABLE generation. teardown bumps captureGeneration, so a
        // callback from THIS (now-old) reader that resumes after teardown still tags its frame with
        // `generation` — not the post-teardown value — and is dropped by the consumer's in-lock check.
        // (Reading the live field per frame would let a late callback adopt the new generation.)
        val generation = ++captureGeneration

        reader.setOnImageAvailableListener({ r ->
            if (imageReader !== r || failureStopped.get()) return@setOnImageAvailableListener
            try {
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    if (!readiness.canRead()) {
                        diagnostics.blocked("MediaProjection readiness/canRead false")
                        return@setOnImageAvailableListener
                    }
                    val token = readiness.captureToken()
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastOcrAt < THROTTLE_MS) return@setOnImageAvailableListener
                    lastOcrAt = now
                    // Hand off to the sequential consumer, tagged with this session's generation; CONFLATED
                    // replaces any unprocessed frame. Recycle if the channel is closed (teardown race).
                    val frame = CapturedFrame(generation, image.toBitmap(width, height), ScreenReaderState.overlayChipRect, token,
                        System.currentTimeMillis(), resources.displayMetrics.density,
                        mx.kompara.capture.HostGeometryEvidence.state.value)
                    if (frameChannel.trySend(frame).isFailure) frame.bitmap.recycle()
                } finally {
                    image.close()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                stopAfterFailure("MediaProjection image callback", error)
            }
        }, null)
    }

    private suspend fun runOcr(frame: CapturedFrame) {
        if (!readiness.canRead(token = frame.token)) {
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
            frame.bitmap.recycle() // ML Kit finished (with an error) — safe to free.
            return
        }
        // Retain only coarse luminance evidence from this frame; no bitmap crosses the pipeline.
        val appearance = try {
            HostAppearanceSamples.capture(frame.bitmap, frame.capturedAtMs, frame.chipRect)
        } finally {
            frame.bitmap.recycle()
        }
        // Serialize the state mutation with teardown and drop frames from a superseded capture
        // generation, so a stale pre-teardown frame can never publish to the overlay/ledger after the
        // session was closed and reset. The generation check is INSIDE the lock, so it can't race the
        // bump in teardown. Delegates the whole parse → verdict → ledger step to the shared pipeline.
        stateMutex.withLock {
            if (frame.generation == captureGeneration && readiness.canRead(token = frame.token)) {
                pipeline.process(blocks, frame.chipRect, if (BuildConfig.DEBUG) getExternalFilesDir(null) else null,
                    capturedAtMs = frame.capturedAtMs, token = frame.token, displayBounds = display,
                    density = frame.density, accessibility = frame.accessibility, appearance = appearance)
            } else {
                diagnostics.blocked("MediaProjection stale generation or readiness/canRead false")
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
        // One shared incident owner handles this and permission loss. Paused/denied notifications
        // must not bypass that owner through a second OCR-specific notification path.
        teardown()
        cleanup("foreground notification") { stopForeground(STOP_FOREGROUND_REMOVE) }
        cleanup("capture service") { stopSelf() }
    }

    /** A failed supervised child still needs a handler: otherwise Android kills the shared process. */
    private fun stopAfterFailure(component: String, error: Throwable) {
        if (error is CancellationException || !failureStopped.compareAndSet(false, true)) return
        Log.e(TAG, "$component failed; stopping MediaProjection lane (${error.javaClass.simpleName})")
        scope.cancel()
        // Service/window callbacks stay on main. The failed child has already released stateMutex.
        mainHandler.post { onProjectionLost() }
    }

    private inline fun cleanup(component: String, block: () -> Unit) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Log.e(TAG, "$component cleanup failed (${error.javaClass.simpleName})")
        }
    }

    private fun teardown(restarting: Boolean = false) {
        val oldDisplay = virtualDisplay
        virtualDisplay = null
        cleanup("virtual display") { oldDisplay?.release() }
        val oldReader = imageReader
        imageReader = null
        cleanup("image reader") { oldReader?.close() }
        val oldProjection = projection
        projection = null
        cleanup("projection") { oldProjection?.stop() }
        ScreenReaderState.setRunning(false)
        cleanup("reader state") {
            readiness.captureRunning(false, intentionalStop = restarting)
            if (failureStopped.get()) readiness.captureFailed()
        }
        // Close the session under the state lock, serialized with the frame consumer. Bumping the
        // capture generation FIRST means any frame still queued or mid-recognition is dropped at its
        // (in-lock) generation check — nothing can re-create a verdict or ledger offer after the
        // reset. runBlocking waits for at most one in-flight processFrame (recognition runs outside
        // the lock); teardown is infrequent (re-consent / screen lock / destroy).
        cleanup("frame pipeline") {
            runBlocking {
                stateMutex.withLock {
                    captureGeneration++
                    // Close the session under the state lock: emit a trailing NoCard so no verdict is
                    // stranded, resolve/close the OCR ledger, and reset every stateful collaborator so a
                    // re-consent restart begins clean. All handled by the shared pipeline.
                    pipeline.onCaptureEnd(System.currentTimeMillis())
                }
            }
        }
    }

    override fun onDestroy() {
        teardown()
        // Cancel the frame channel so any buffered (conflated) bitmap is handed to onUndeliveredElement
        // and recycled, instead of leaking when the scope is cancelled. Safe: the instance is dying;
        // a restart creates a fresh service with a new channel.
        frameChannel.cancel()
        scope.cancel()
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
