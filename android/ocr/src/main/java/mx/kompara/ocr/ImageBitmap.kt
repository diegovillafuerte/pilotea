package mx.kompara.ocr

import android.graphics.Bitmap
import android.media.Image

/**
 * Convert an RGBA_8888 [Image] (from the capture ImageReader) to a [Bitmap], accounting for the
 * row stride padding the reader adds, then cropping back to the real width.
 */
fun Image.toBitmap(width: Int, height: Int): Bitmap {
    val plane = planes[0]
    val buffer = plane.buffer
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val rowPadding = rowStride - pixelStride * width
    val padded = Bitmap.createBitmap(
        width + rowPadding / pixelStride,
        height,
        Bitmap.Config.ARGB_8888,
    )
    padded.copyPixelsFromBuffer(buffer)
    return if (rowPadding == 0) {
        padded
    } else {
        Bitmap.createBitmap(padded, 0, 0, width, height).also { if (it != padded) padded.recycle() }
    }
}
