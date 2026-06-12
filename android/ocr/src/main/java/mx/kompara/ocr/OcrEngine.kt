package mx.kompara.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Where a recognized line sits on screen. Plain ints (not android.graphics.Rect) so the OCR
 *  parser and its tests stay pure-JVM. */
data class OcrBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** One recognized line of text and where it sits on screen. */
data class OcrBlock(val text: String, val bounds: OcrBounds)

/**
 * On-device OCR via ML Kit (Latin script, bundled — no network). Turns a screen bitmap into
 * recognized text lines with screen bounds, the OCR analogue of an accessibility node tree.
 */
class OcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): List<OcrBlock> =
        suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    val blocks = result.textBlocks.flatMap { block ->
                        block.lines.mapNotNull { line ->
                            val box = line.boundingBox ?: return@mapNotNull null
                            OcrBlock(line.text, OcrBounds(box.left, box.top, box.right, box.bottom))
                        }
                    }
                    cont.resume(blocks)
                }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
}
