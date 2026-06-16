package mx.kompara.ocr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OfferEventBus
import mx.kompara.capture.lifecycle.OcrLifecycleBus
import mx.kompara.data.service.ScreenReaderState
import mx.kompara.data.service.ScreenRect
import java.io.File

/**
 * Proof-of-concept screen-capture + OCR service for the SurfaceView platforms (DiDi/inDrive), which
 * expose no accessibility text (design doc §7). Captures frames via MediaProjection, throttles to a
 * few per second, runs [OcrEngine], and logs/records the recognized offer text so we can validate
 * that ML Kit reads real DiDi/inDrive cards before building the full OCR pipeline (S-023).
 *
 * Lifecycle (B-075): Android revokes the projection on every screen lock / consent withdrawal, and
 * the grant is one-shot — the service cannot restart itself. So an unexpected stop posts a
 * tap-to-restart notification (relaunching the consent flow via [ScreenReaderState.ACTION_START]),
 * fully stops the foreground service (no stale "leyendo la pantalla" notification), and mirrors the
 * live state into [ScreenReaderState] for the Lector tab.
 */
class OcrCaptureService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = OcrEngine()
    private val didiParser = DidiOcrParser()
    private val uberParser = UberOcrParser()
    private val presence = CardPresenceTracker()
    // Smooths the OCR fare across frames of one offer so a single garbled frame (MXN137.28→37.28)
    // can't flip the chip's number/colour. Feeds ONLY the overlay chip; the B-039 ledger gets the raw
    // card (see the ledger feed below) so a stabilized value can't spawn a duplicate trip record.
    private val fareStabilizer = FareStabilizer()
    // B-039 OCR ledger: classifies each frame into offer/trip/idle lifecycle signals (transition-
    // deduped) for the trip tracker, since OCR-captured offers never reach the node-path ledger.
    private val lifecycleClassifier = OcrLifecycleClassifier()
    private var lastOfferKey: String? = null
    private var lastOfferPackage: String? = null
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
    private data class CapturedFrame(val generation: Int, val bitmap: Bitmap, val chipRect: ScreenRect?)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.let {
            if (Build.VERSION.SDK_INT >= 33) it.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") it.getParcelableExtra(EXTRA_DATA)
        }
        if (resultCode == 0 || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()

        // Re-consent while already capturing (Lector "reiniciar"): drop the old projection first.
        if (projection != null) teardown()

        val mpm = getSystemService(MediaProjectionManager::class.java)
        val mp = mpm.getMediaProjection(resultCode, data)
        if (mp == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        projection = mp
        // Android 14+ requires a registered callback. It fires for OUR teardown() too (restart,
        // onDestroy) — by then `projection` no longer points at mp, so only an external revocation
        // (keyguard, the system screen-cast chip) takes the recovery path.
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (projection !== mp) return
                teardown()
                onProjectionLost()
            }
        }, null)

        startCapture(mp)
        // The reader may have been restarted from the Lector tab rather than the stopped
        // notification — clear it so it can't claim "detenido" while capture runs.
        getSystemService(NotificationManager::class.java).cancel(STOPPED_NOTIF_ID)
        ScreenReaderState.setRunning(true)
        return START_NOT_STICKY
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
            scope.launch { for (frame in frameChannel) runOcr(frame) }
        }

        // Bind this capture session to an IMMUTABLE generation. teardown bumps captureGeneration, so a
        // callback from THIS (now-old) reader that resumes after teardown still tags its frame with
        // `generation` — not the post-teardown value — and is dropped by the consumer's in-lock check.
        // (Reading the live field per frame would let a late callback adopt the new generation.)
        val generation = ++captureGeneration

        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = SystemClock.elapsedRealtime()
                if (now - lastOcrAt < THROTTLE_MS) return@setOnImageAvailableListener
                lastOcrAt = now
                // Hand off to the sequential consumer, tagged with this session's generation; CONFLATED
                // replaces any unprocessed frame. Recycle if the channel is closed (teardown race).
                val frame = CapturedFrame(generation, image.toBitmap(width, height), ScreenReaderState.overlayChipRect)
                if (frameChannel.trySend(frame).isFailure) frame.bitmap.recycle()
            } finally {
                image.close()
            }
        }, null)
    }

    private suspend fun runOcr(frame: CapturedFrame) {
        val blocks = try {
            engine.recognize(frame.bitmap)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            // Scope cancelled (teardown) mid-recognition: ML Kit's native task may still be reading
            // the bitmap, so do NOT recycle here — GC reclaims it on shutdown rather than risk a
            // use-after-recycle. Honor the cancellation.
            throw cancellation
        } catch (t: Throwable) {
            Log.e(TAG, "OCR failed", t)
            frame.bitmap.recycle() // ML Kit finished (with an error) — safe to free.
            return
        }
        // Recognition completed; ML Kit no longer touches the bitmap and the rest works off `blocks`.
        frame.bitmap.recycle()
        // Serialize the state mutation with teardown and drop frames from a superseded capture
        // generation, so a stale pre-teardown frame can never publish to the overlay/ledger after the
        // session was closed and reset. The generation check is INSIDE the lock, so it can't race the
        // bump in teardown.
        stateMutex.withLock {
            if (frame.generation == captureGeneration) processFrame(blocks, frame.chipRect)
        }
    }

    private fun processFrame(rawBlocks: List<OcrBlock>, chipRect: ScreenRect?) {
        // Strip our own chip's text FIRST: MediaProjection captures the whole screen including the
        // floating verdict chip, and its "MXN…"/"$…" amount otherwise gets parsed as the offer fare —
        // the self-capture loop that collapsed the live verdict to $0. [chipRect] is the rect
        // snapshotted when THIS frame was captured (a null rect = chip hidden ⇒ no-op). The chip is
        // positioned/drag-constrained above the fare, so its rect covers only the header/map and this
        // never drops the fare or legs.
        val blocks = ChipMask.maskOwnChip(rawBlocks, chipRect)
        val joined = blocks.joinToString(" | ") { it.text }
        // Raw OCR text is screen content — log in debug builds only (Play data-safety posture);
        // release logcat must never expose it.
        if (BuildConfig.DEBUG) Log.d(TAG, "OCR(${blocks.size}): $joined")

        // Parse → publish to the shared bus so the accessibility-service-hosted overlay shows the
        // verdict. Dedup identical offers; emit NoCard once the card actually leaves the screen —
        // [CardPresenceTracker] holds the verdict through OCR-garbled frames (B-077). Frames of
        // Kompara's own UI are never parsed (the simulator's mock cards and our own stats would
        // feed back into the pipeline).
        val ownUi = KomparaUiGuard.isOwnUi(joined)
        // Try Uber first, then DiDi. The two OCR parsers are disjoint by fare format (Uber renders
        // "MXN…", DiDi a bare "$"), so whichever returns a card also identifies the platform —
        // `card.platform` is the host package, no separate detector needed. (B-029-OCR: Uber's offer
        // card is no longer accessibility-readable, design §7 update 2026-06-15.)
        val card = if (ownUi) null else (uberParser.parse(blocks) ?: didiParser.parse(blocks))
        // The offer-card signature (fare + leg) survives the OCR garble that fails a full parse;
        // shared by the presence tracker (hold the chip) and the lifecycle classifier (hold OFFER).
        val signature = !ownUi &&
            (uberParser.hasCardSignature(blocks) || didiParser.hasCardSignature(blocks))
        // Monotonic clock for presence math (a wall-clock jump must not hide a live card or pin a
        // stale one); wall clock only for event timestamps and fixture filenames.
        val now = SystemClock.elapsedRealtime()
        // Replace the raw parsed fare with the stabilized one so a one-frame OCR garble can't flip
        // the verdict. Keyed on the trip leg (stable), not the fare. Null when no card this frame.
        val shownCard = card?.let { fareStabilizer.onParsed(it, System.currentTimeMillis()) }
        if (shownCard != null) {
            presence.onParsed(now)
            // Re-assert on EVERY successful parse, not only on a new offer: a Parsed on the bus
            // cancels any in-flight hide, so a stray NoCard from any other source can blank the
            // chip for at most one OCR frame (~300 ms). Downstream distinctUntilChanged collapses
            // identical verdicts, so steady re-assertion causes no re-render churn.
            OfferEventBus.tryEmit(
                OfferEvent.Parsed(shownCard.platform, System.currentTimeMillis(), shownCard),
            )
            lastOfferPackage = shownCard.platform
            val key = "${shownCard.platform}_${shownCard.fare}_${shownCard.pickupDistanceKm}_${shownCard.tripDistanceKm}"
            if (key != lastOfferKey) {
                lastOfferKey = key
                // Fare/distance are screen-derived — debug-only log (see processFrame's note).
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "emitted ${shownCard.platform} offer: fare=${shownCard.fare} pickup=${shownCard.pickupDistanceKm}km trip=${shownCard.tripDistanceKm}km")
                }
            }
        } else if (lastOfferKey != null) {
            if (presence.onMiss(signature, now)) {
                // Hide the chip for whichever platform's card we last showed (NOT always DiDi).
                val gonePackage = lastOfferPackage ?: DidiOcrParser.DIDI_PACKAGE
                lastOfferKey = null
                lastOfferPackage = null
                fareStabilizer.reset() // card truly gone → forget the session so the next offer paints fresh
                OfferEventBus.tryEmit(
                    OfferEvent.NoCard(
                        gonePackage,
                        System.currentTimeMillis(),
                        OfferEvent.Reason.NOT_AN_OFFER,
                    ),
                )
            }
        }

        // B-039 OCR ledger: drive the trip lifecycle (offer → accept → trip → close) from the same
        // frames. Transition-deduped inside the classifier, so this is a no-op on most frames. Never
        // for our own UI (the classifier would mis-read Kompara's screens as idle and close a trip).
        // Attribute + gate the ledger by the foreground host: MediaProjection captures the WHOLE
        // screen, so a non-host app (WhatsApp, the shade) must NOT feed the trip ledger. A parsed
        // card's platform is the definitive host; otherwise use the accessibility service's fresh
        // foreground target app (B-039 §7.1). Null → a non-host screen → skip. Passing the host
        // through (not a bool) means trip/idle attribute to the CURRENT app, so a second target app
        // can't mutate the first's ledger. The overlay chip self-corrects on non-host screens (no
        // parse → NoCard hides it), so only the persisted ledger needs this gate.
        val hostPackage = card?.platform
            ?: ScreenReaderState.freshForegroundHost(SystemClock.elapsedRealtime(), HOST_FOREGROUND_FRESHNESS_MS)
        if (!ownUi && hostPackage != null) {
            // presence.isPresent() is the DEBOUNCED "card on screen" state (held through garble),
            // so the offer session doesn't split on a single OCR dropout — same policy as the chip.
            // The ledger gets the RAW card (unchanged from before this fix) — fare-stabilization is a
            // chip-display concern; the B-039 ledger's own fare handling is out of scope here.
            lifecycleClassifier.onFrame(joined, card, presence.isPresent(), hostPackage, System.currentTimeMillis())
                ?.let { OcrLifecycleBus.tryEmit(it) }
        }

        // Record offer frames for fixture building / spec hardening — debug builds only (never our
        // own UI). Release builds must store NO screen content, per the Play data-safety posture.
        if (BuildConfig.DEBUG && !ownUi && looksLikeOffer(joined)) {
            runCatching {
                val dir = File(getExternalFilesDir(null), "ocr-fixtures").apply { mkdirs() }
                File(dir, "ocr_${System.currentTimeMillis()}.json").writeText(blocksToJson(blocks))
            }
        }
    }

    private fun looksLikeOffer(text: String): Boolean =
        (text.contains("$") || text.contains("MXN", true)) &&
            (text.contains("km", true) || text.contains("min", true))

    private fun blocksToJson(blocks: List<OcrBlock>): String =
        blocks.joinToString(prefix = "[\n", postfix = "\n]", separator = ",\n") { b ->
            val t = b.text.replace("\\", "\\\\").replace("\"", "\\\"")
            "  {\"text\":\"$t\",\"left\":${b.bounds.left},\"top\":${b.bounds.top}," +
                "\"right\":${b.bounds.right},\"bottom\":${b.bounds.bottom}}"
        }

    private fun startAsForeground() {
        val channelId = "ocr_capture"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Lector OCR", NotificationManager.IMPORTANCE_LOW),
        )
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("Kompara está leyendo la pantalla")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
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
        val nm = getSystemService(NotificationManager::class.java)
        // Settings on an existing channel are immutable; the v1 id must go or upgraded devices
        // show a zombie "Estado del lector" entry next to the v2 channel. No-op when never created.
        nm.deleteNotificationChannel("ocr_reader_state")
        nm.createNotificationChannel(
            NotificationChannel(
                STOPPED_CHANNEL_ID,
                "Estado del lector",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                // The reader dies at screen lock — phone in pocket — so the alert must be felt,
                // not just seen (B-078).
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
            },
        )
        val restart = PendingIntent.getActivity(
            this,
            0,
            Intent(ScreenReaderState.ACTION_START).setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, STOPPED_CHANNEL_ID)
            .setContentTitle("Kompara: se detuvo el lector de pantalla")
            .setContentText("Tócame para reiniciarlo y volver a ver el semáforo en DiDi.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(restart)
            .setAutoCancel(true)
            .build()
        nm.notify(STOPPED_NOTIF_ID, notification)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun teardown() {
        virtualDisplay?.release(); virtualDisplay = null
        imageReader?.close(); imageReader = null
        projection?.stop(); projection = null
        ScreenReaderState.setRunning(false)
        // Close the session under the state lock, serialized with the frame consumer. Bumping the
        // capture generation FIRST means any frame still queued or mid-recognition is dropped at its
        // (in-lock) generation check — nothing can re-create a verdict or ledger offer after the
        // reset. runBlocking waits for at most one in-flight processFrame (recognition runs outside
        // the lock); teardown is infrequent (re-consent / screen lock / destroy).
        runBlocking {
            stateMutex.withLock {
                captureGeneration++
                // Never strand a verdict: the overlay only hides on bus events, so if capture dies
                // mid-offer we emit the NoCard ourselves — for whichever platform was last on screen.
                if (lastOfferKey != null) {
                    val gonePackage = lastOfferPackage ?: DidiOcrParser.DIDI_PACKAGE
                    lastOfferKey = null
                    lastOfferPackage = null
                    OfferEventBus.tryEmit(
                        OfferEvent.NoCard(
                            gonePackage,
                            System.currentTimeMillis(),
                            OfferEvent.Reason.NOT_AN_OFFER,
                        ),
                    )
                }
                // Close the OCR ledger session: a final idle for the active platform resolves a
                // pending offer and closes an open trip at this point (rather than letting them dangle
                // until the next offer or a rollup sweep). onCaptureEnd also resets the classifier so a
                // re-consent restart begins clean; reset presence for the same reason.
                lifecycleClassifier.onCaptureEnd(System.currentTimeMillis())
                    ?.let { OcrLifecycleBus.tryEmit(it) }
                presence.reset()
                fareStabilizer.reset()
            }
        }
    }

    override fun onDestroy() {
        teardown()
        // Close the frame channel so any buffered (conflated) bitmap is handed to onUndeliveredElement
        // and recycled, instead of leaking when the scope is cancelled. Safe: the instance is dying;
        // a restart creates a fresh service with a new channel.
        frameChannel.close()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KomparaOCR"
        private const val NOTIF_ID = 42
        // "_v2": channel settings are immutable once created; the vibration pattern (B-078) needs
        // a fresh id on devices that already created the original channel.
        private const val STOPPED_CHANNEL_ID = "ocr_reader_state_v2"
        private const val STOPPED_NOTIF_ID = 43
        // 300 ms bounds the offer→verdict latency (plus ML Kit time); ~3 fps full-screen OCR is
        // fine on the target devices and only runs while the driver has the reader on.
        private const val THROTTLE_MS = 300L
        // How recently the accessibility service must have seen a target app foreground for an OCR
        // frame (without a parsed card) to be attributed to the trip ledger. Target apps emit a11y
        // events continuously while foreground (maps/timers animate), so 8 s comfortably covers the
        // gaps while still excluding a switch to another app. NEEDS ON-DEVICE CALIBRATION.
        private const val HOST_FOREGROUND_FRESHNESS_MS = 8_000L
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
