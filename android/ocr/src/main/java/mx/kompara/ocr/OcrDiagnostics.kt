package mx.kompara.ocr

import android.os.SystemClock
import android.util.Log

/** Log only reason codes and measured geometry, never recognized screen text. */
internal class OcrDiagnostics(
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val write: (String) -> Unit = { Log.d("KomparaOCR", it) },
) {
    private val lastLoggedAt = mutableMapOf<String, Long>()

    @Synchronized fun blocked(reason: String, details: String = "") {
        val time = now()
        val previous = lastLoggedAt[reason]
        if (previous != null && time - previous in 0 until 5_000L) return
        lastLoggedAt[reason] = time
        write("offer visibility: $reason${if (details.isEmpty()) "" else "; $details"}")
    }
}
