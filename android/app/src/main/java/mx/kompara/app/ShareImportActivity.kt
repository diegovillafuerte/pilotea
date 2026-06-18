package mx.kompara.app

import android.content.ContentResolver
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.kompara.sync.api.ImportFile
import mx.kompara.sync.imports.Importer
import mx.kompara.ui.imports.PendingSharedImport
import mx.kompara.ui.imports.ShareTargetClassifier
import mx.kompara.ui.imports.SharedImportBuffer
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

/**
 * Inbound OS share-target for earnings files (PR-D3). A driver inside Uber Driver / DiDi / Gmail taps
 * the system **Share** sheet and picks "Kompara — Importar ganancias"; this (translucent, no-UI)
 * activity receives the [Intent.ACTION_SEND] / [Intent.ACTION_SEND_MULTIPLE], drains the shared bytes,
 * pre-classifies the platform, and bounces into the main shell's import flow with the file already
 * picked — skipping the document-picker round-trip. The import flow then requires an explicit
 * "Continuar" tap before any upload (the share entry never auto-spends the driver's parse quota).
 *
 * **Security posture (matches the documents-only / StopClub legal model):**
 *  - Exported (it must be, to appear in the share sheet) but does NOTHING privileged: it only reads the
 *    bytes the user explicitly shared, never the other app's screen, and **never** the MediaProjection /
 *    [OcrConsentActivity] path.
 *  - **Signed-out short-circuit:** the session is checked FIRST; a signed-out share never touches the
 *    untrusted streams at all — we just open the app to sign up.
 *  - **URI provenance:** only `content://` URIs from a *foreign* authority are read; non-content schemes
 *    and our own package / FileProvider authorities are rejected, so a direct caller can't make us read
 *    arbitrary URIs the process can resolve (incl. our own files).
 *  - **Untrusted input is bounded on every axis:** Parcelable extras are unmarshalled inside
 *    [runCatching] (no crash on a malformed extra); the URI fan-out is capped ([MAX_SHARED_URIS]); a
 *    cheap MIME pass reads ONLY the one/two files the classifier needs; each is read with a bounded loop
 *    that aborts past the 10 MB cap. A hostile provider that blocks `getType`/`open`/`read`/`query`
 *    can't pin us: the whole read runs on a separate worker with a hard [Future][java.util.concurrent.
 *    Future] deadline ([READ_TIMEOUT_MS]) — on timeout we cancel the [CancellationSignal] AND close the
 *    open descriptor (which unblocks a stuck `read()`), so the coroutine always reaches `finish()`. (A
 *    provider that ignores cancel + close + interrupt could still leak its one worker thread — an
 *    accepted, low-severity local-DoS residual: TD-038.) All I/O is off the main thread (no ANR).
 *  - The transient share read-grant is copied into memory ([ImportFile]) here, never a persisted URI.
 */
@AndroidEntryPoint
class ShareImportActivity : ComponentActivity() {

    @Inject
    lateinit var sharedImportBuffer: SharedImportBuffer

    @Inject
    lateinit var importer: Importer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sourceIntent = intent
        lifecycleScope.launch {
            try {
                if (!importer.isSignedIn()) {
                    // Never touch the untrusted streams when signed-out — just open the app to sign up.
                    Toast.makeText(this@ShareImportActivity, R.string.share_import_needs_account, Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@ShareImportActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return@launch
                }

                val pending = withContext(Dispatchers.IO) { readShareWithDeadline(sourceIntent) }

                if (pending == null) {
                    Toast.makeText(this@ShareImportActivity, R.string.share_import_unsupported, Toast.LENGTH_LONG).show()
                } else {
                    sharedImportBuffer.set(pending)
                    // Fresh-root the shell so onCreate runs and the import-route deep link is honoured even
                    // when the app is already open; the share is an intentional new entry point, so
                    // resetting the back stack to "you're importing now" is the expected behaviour.
                    startActivity(
                        Intent(this@ShareImportActivity, MainActivity::class.java).apply {
                            putExtra(MainActivity.EXTRA_OPEN_IMPORT, true)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e // genuine lifecycle cancellation — never swallow it
            } catch (_: Exception) {
                Toast.makeText(this@ShareImportActivity, R.string.share_import_unsupported, Toast.LENGTH_LONG).show()
            } finally {
                finish()
            }
        }
    }

    /**
     * Run the untrusted [resolveShare] on a dedicated worker with a hard wall-clock deadline. On timeout
     * we trip the [CancellationSignal] (unblocks `open`/`query`) AND close the currently-open descriptor
     * (unblocks a stuck `read()`), then abandon the worker — so a blocking provider can never keep this
     * coroutine (or the invisible activity) alive.
     */
    private fun readShareWithDeadline(intent: Intent): PendingSharedImport? {
        val signal = CancellationSignal()
        val openFd = AtomicReference<AssetFileDescriptor?>(null)
        val future = try {
            SHARE_READ_EXECUTOR.submit(Callable { resolveShare(intent, signal, openFd) })
        } catch (_: RejectedExecutionException) {
            // All reader slots are occupied — the only way that happens is workers still stuck on a
            // hostile/blocking provider. Reject this share rather than grow the pool (caps the leak).
            return null
        }
        return try {
            future.get(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            signal.cancel()
            runCatching { openFd.get()?.close() } // unblock a read() stuck on a non-terminating stream
            future.cancel(true)
            null
        } catch (_: Exception) {
            // resolveShare threw (ExecutionException-wrapped provider failure) → treat as unsupported.
            future.cancel(true)
            null
        }
    }

    /**
     * Resolve the share to a [PendingSharedImport] (or null): cap the URI fan-out, keep only foreign
     * `content://` URIs, do a cheap MIME pass, [ShareTargetClassifier.plan] which files to read, then
     * bounded-read only those (cancellable via [signal]; current descriptor tracked in [openFd] so the
     * deadline can close it).
     */
    private fun resolveShare(
        intent: Intent,
        signal: CancellationSignal,
        openFd: AtomicReference<AssetFileDescriptor?>,
    ): PendingSharedImport? {
        val uris = extractUris(intent).filter { it.isAcceptableShare() }.take(MAX_SHARED_URIS)
        val accepted = uris.mapNotNull { uri ->
            val mime = runCatching { contentResolver.getType(uri) }.getOrNull()?.normalizeMime()
            if (mime != null && mime in ShareTargetClassifier.ACCEPTED_MIME) uri to mime else null
        }
        val plan = ShareTargetClassifier.plan(accepted.map { it.second }) ?: return null
        val files = plan.indices.mapNotNull { i ->
            val (uri, mime) = accepted[i]
            readSharedFile(uri, mime, signal, openFd)
        }
        if (files.size != plan.platform.fileCount) return null
        return PendingSharedImport(platform = plan.platform, files = files)
    }

    /**
     * Accept only a `content://` URI from a *foreign* authority. Rejects non-content schemes (e.g.
     * `file://`) and our own package / FileProvider authorities, so a direct caller can't redirect us at
     * URIs the Kompara process can already resolve.
     */
    private fun Uri.isAcceptableShare(): Boolean {
        if (scheme != ContentResolver.SCHEME_CONTENT) return false
        val auth = authority ?: return false
        return auth != packageName && auth != "$packageName.fileprovider"
    }

    /**
     * Pull the shared content URI(s) off the intent for both the single- and multi-file share actions.
     * Parcelable extras come from an untrusted app, so unmarshalling them is wrapped in [runCatching] —
     * a BadParcelableException / ClassCastException fails closed to an empty list, never a crash.
     */
    @Suppress("DEPRECATION")
    private fun extractUris(intent: Intent): List<Uri> = runCatching {
        when (intent.action) {
            Intent.ACTION_SEND ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
                } else {
                    listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
                }

            Intent.ACTION_SEND_MULTIPLE ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }.orEmpty()

            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    /**
     * Drain [uri] (already known to be [mime]) into an in-memory [ImportFile] with a BOUNDED read that
     * aborts as soon as the stream exceeds the 10 MB cap. Tracks the open descriptor in [openFd] so the
     * deadline watchdog can close it to unblock a stuck `read()`. Returns null on any failure, an empty
     * stream, or an oversize stream.
     */
    private fun readSharedFile(
        uri: Uri,
        mime: String,
        signal: CancellationSignal,
        openFd: AtomicReference<AssetFileDescriptor?>,
    ): ImportFile? {
        val bytes = runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r", signal)?.use { afd ->
                openFd.set(afd)
                afd.createInputStream()?.use { input ->
                    val buffer = ByteArray(READ_CHUNK_BYTES)
                    val out = java.io.ByteArrayOutputStream()
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_FILE_BYTES) return null // exceeds the cap → drop, never fully buffered
                        out.write(buffer, 0, read)
                    }
                    out.toByteArray()
                }
            }
        }.getOrNull().also { openFd.set(null) } ?: return null
        if (bytes.isEmpty()) return null
        val name = queryDisplayName(uri, signal) ?: defaultName(mime)
        return ImportFile(fileName = name, mimeType = mime, bytes = bytes)
    }

    private fun queryDisplayName(uri: Uri, signal: CancellationSignal): String? =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null, signal)
                ?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
                }
        }.getOrNull()

    private companion object {
        /** Mirror of the backend's per-file size limit; an oversize share is dropped, not uploaded. */
        const val MAX_FILE_BYTES = 10 * 1024 * 1024

        /** Cap the URI fan-out from a hostile ACTION_SEND_MULTIPLE before we read anything. */
        const val MAX_SHARED_URIS = 8

        /** Hard wall-clock deadline on the whole untrusted read (worker abandoned past this). */
        const val READ_TIMEOUT_MS = 15_000L

        /** Streaming read buffer for the bounded copy. */
        const val READ_CHUNK_BYTES = 64 * 1024

        /** At most this many concurrent share reads; further shares are rejected while these are busy. */
        const val MAX_CONCURRENT_READS = 2

        /**
         * Reads run on a small, BOUNDED daemon pool so the coroutine's `Future.get(timeout)` can abandon
         * a blocked provider call (`getType`/`open`/`read`/`query` have no uniform cancellation). The
         * [SynchronousQueue] + cap means a flood of hostile launches can never grow the pool: once
         * [MAX_CONCURRENT_READS] workers are stuck, additional submits are rejected (→ "unsupported"),
         * so a malicious local provider can leak at most [MAX_CONCURRENT_READS] daemon threads total —
         * not unbounded. (Full elimination needs process isolation; see TD-038.)
         */
        val SHARE_READ_EXECUTOR: ExecutorService = ThreadPoolExecutor(
            0,
            MAX_CONCURRENT_READS,
            KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            ThreadFactory { runnable -> Thread(runnable, "kompara-share-read").apply { isDaemon = true } },
        )

        /** Idle reader threads retire after this long. */
        const val KEEP_ALIVE_SECONDS = 30L

        /** Some sources report `image/jpg`; the backend (and our accepted set) use the canonical `image/jpeg`. */
        fun String.normalizeMime(): String = if (this == "image/jpg") "image/jpeg" else this

        fun defaultName(mime: String): String = when (mime) {
            "application/pdf" -> "import.pdf"
            "image/png" -> "import.png"
            "image/webp" -> "import.webp"
            else -> "import.jpg"
        }
    }
}
