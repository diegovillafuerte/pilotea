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
import kotlinx.coroutines.launch
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OfferEventBus
import mx.kompara.data.service.ScreenReaderState
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
    private val presence = CardPresenceTracker()
    private var lastOfferKey: String? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var lastOcrAt = 0L

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
        val bounds = wm.currentWindowMetrics.bounds
        val width = bounds.width()
        val height = bounds.height()
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

        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = SystemClock.elapsedRealtime()
                if (now - lastOcrAt < THROTTLE_MS) return@setOnImageAvailableListener
                lastOcrAt = now
                val bitmap = image.toBitmap(width, height)
                scope.launch { runOcr(bitmap) }
            } finally {
                image.close()
            }
        }, null)
    }

    private suspend fun runOcr(bitmap: Bitmap) {
        val blocks = runCatching { engine.recognize(bitmap) }.getOrElse {
            Log.e(TAG, "OCR failed", it); return
        }
        val joined = blocks.joinToString(" | ") { it.text }
        Log.d(TAG, "OCR(${blocks.size}): $joined")

        // Parse → publish to the shared bus so the accessibility-service-hosted overlay shows the
        // verdict. Dedup identical offers; emit NoCard once the card actually leaves the screen —
        // [CardPresenceTracker] holds the verdict through OCR-garbled frames (B-077). Frames of
        // Kompara's own UI are never parsed (the simulator's mock cards and our own stats would
        // feed back into the pipeline).
        val ownUi = KomparaUiGuard.isOwnUi(joined)
        val card = if (ownUi) null else didiParser.parse(blocks)
        // Monotonic clock for presence math (a wall-clock jump must not hide a live card or pin a
        // stale one); wall clock only for event timestamps and fixture filenames.
        val now = SystemClock.elapsedRealtime()
        if (card != null) {
            presence.onParsed(now)
            // Re-assert on EVERY successful parse, not only on a new offer: a Parsed on the bus
            // cancels any in-flight hide, so a stray NoCard from any other source can blank the
            // chip for at most one OCR frame (~300 ms). Downstream distinctUntilChanged collapses
            // identical verdicts, so steady re-assertion causes no re-render churn.
            OfferEventBus.tryEmit(
                OfferEvent.Parsed(card.platform, System.currentTimeMillis(), card),
            )
            val key = "${card.fare}_${card.pickupDistanceKm}_${card.tripDistanceKm}"
            if (key != lastOfferKey) {
                lastOfferKey = key
                Log.d(TAG, "emitted DiDi offer: fare=${card.fare} pickup=${card.pickupDistanceKm}km trip=${card.tripDistanceKm}km")
            }
        } else if (lastOfferKey != null) {
            if (presence.onMiss(!ownUi && didiParser.hasCardSignature(blocks), now)) {
                lastOfferKey = null
                OfferEventBus.tryEmit(
                    OfferEvent.NoCard(
                        DidiOcrParser.DIDI_PACKAGE,
                        System.currentTimeMillis(),
                        OfferEvent.Reason.NOT_AN_OFFER,
                    ),
                )
            }
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
        text.contains("$") && (text.contains("km", true) || text.contains("min", true))

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
            .setContentTitle("Se detuvo el lector de pantalla")
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
        // Never strand a verdict: the overlay (hosted by the accessibility service) only hides on
        // bus events, so if capture dies mid-offer we must emit the NoCard ourselves.
        if (lastOfferKey != null) {
            lastOfferKey = null
            OfferEventBus.tryEmit(
                OfferEvent.NoCard(
                    DidiOcrParser.DIDI_PACKAGE,
                    System.currentTimeMillis(),
                    OfferEvent.Reason.NOT_AN_OFFER,
                ),
            )
        }
    }

    override fun onDestroy() {
        teardown()
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
