package mx.kompara.ocr

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Where a recognized line sits on screen. Plain ints (not android.graphics.Rect) so the OCR
 *  parser and its tests stay pure-JVM. */
data class OcrBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** One recognized line of text and where it sits on screen. */
data class OcrBlock(val text: String, val bounds: OcrBounds)

// 0.2.19 run, 976 frames on S25: ocrMs p50 77, max 129. CPU-bound, so slower phones scale; no stall seen.
internal const val OCR_TIMEOUT_MS = 5_000L

/** The value, or null if the task has not finished in [timeoutMs]. A failed or canceled task throws. */
internal suspend fun <T> Task<T>.awaitWithin(timeoutMs: Long): T? = withTimeoutOrNull(timeoutMs) {
    suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) cont.resume(task.result)
            else cont.resumeWithException(task.exception ?: IllegalStateException("OCR task canceled"))
        }
    }
}

/**
 * On-device OCR via ML Kit (Latin script, bundled — no network). Turns a screen bitmap into
 * recognized text lines with screen bounds, the OCR analogue of an accessibility node tree.
 */
class OcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Null on timeout: ML Kit's task cannot be cancelled and may still read [bitmap], so the caller must not recycle it. */
    suspend fun recognize(bitmap: Bitmap): List<OcrBlock>? =
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitWithin(OCR_TIMEOUT_MS)?.textBlocks?.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrBlock(line.text, OcrBounds(box.left, box.top, box.right, box.bottom))
            }
        }
}
