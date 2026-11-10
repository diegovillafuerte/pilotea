package mx.kompara.testing

import android.graphics.Bitmap
import org.junit.Assert.assertTrue
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale

/**
 * The one rule a rendered frame is held to against its reviewed baseline, in `:ui` and `:overlay` (shared
 * test source, `android/test-support`). A frame matches when it is no further from its baseline than the same
 * frame is from itself on another CPU.
 *
 * Robolectric's software rasterizer is not bit-exact across CPU architectures. Measured on 2026-11-10 from CI's
 * run of 4190ee7 (Linux x86-64, Temurin 21) against the baselines recorded on Apple silicon: of 220 frames with a
 * baseline, 33 were identical and 187 differed, every pixel by 1 or 2 levels of 255, none by 3 or more. One level:
 * 388,340 pixels in all, on antialiased edges, on text and shapes blended with alpha, and across whole blended
 * surfaces (a sheet's scrim over the flat night background is (12,12,11) on one CPU and (12,12,12) on the other,
 * on 49.8 % of `imss_M3_night`). Two levels: 393 pixels in all, in 53 frames, at most 31 in one frame and at most
 * 0.0103 % of one, at antialiased edges under a blend. Layout, text and colours were the same.
 *
 * So: both ARGB_8888 and the same size; identical ([Bitmap.sameAs]) passes at once; otherwise any number of pixels
 * may differ by [ROUNDING] level, at most [MAX_SHARE_BEYOND_ROUNDING] of the pixels by more, and no channel of any
 * pixel by more than [MAX_LEVELS]. A moved edge, a different word or a missing line differs by more than 3 levels
 * somewhere; a changed colour differs by more than one level across more than 0.1 % of a frame.
 *
 * What still passes: the smallest step, one level, over any area (a colour token or a scrim's alpha moved by one
 * step). Rounding does exactly that, so the two cannot be told apart here. After such a change made on purpose,
 * record the baselines.
 */
object BaselineComparison {
    /**
     * The smallest difference an 8-bit channel can have. Rounding a blend produces it across a whole surface, so any
     * number of pixels may differ by this much.
     */
    const val ROUNDING = 1

    /** Pixels differing by more than [ROUNDING]: observed at most 0.0103 % of a frame, at antialiased edges under a blend. */
    const val MAX_SHARE_BEYOND_ROUNDING = 0.001

    /** No channel of any pixel may differ by more: observed 2 levels of 255; one more for a third stacked blend. */
    const val MAX_LEVELS = 3

    /** How two frames differ: [rounding] pixels by one level, [beyond] by more, at most [largest] levels, of [total]. */
    class Difference(val rounding: Int, val beyond: Int, val total: Int, val largest: Int) {
        val pixels: Int get() = rounding + beyond
        val shareBeyond: Double get() = beyond.toDouble() / total
        val accepted: Boolean get() = largest <= MAX_LEVELS && shareBeyond <= MAX_SHARE_BEYOND_ROUNDING
        override fun toString() = ("%d of %d pixels differ (%.3f %%), largest channel difference %d: %d by one level, %d by more (%.4f %%); " +
            "limits: any number by one level, at most %.1f %% by more, none by more than %d")
            .format(Locale.ROOT, pixels, total, pixels * 100.0 / total, largest, rounding, beyond, shareBeyond * 100,
                MAX_SHARE_BEYOND_ROUNDING * 100, MAX_LEVELS)
    }

    /**
     * Compares two ARGB_8888 frames of the same size ([assertMatches] checks both) in their stored, premultiplied bytes
     * ([Bitmap.copyPixelsToBuffer]), the space [Bitmap.sameAs] compares in, row by row with each bitmap's own
     * [Bitmap.getRowBytes] and four bytes per pixel. Not `getPixels`: un-premultiplying amplifies a one-level
     * difference at low alpha.
     */
    fun measure(baseline: Bitmap, rendered: Bitmap): Difference {
        fun bytes(bitmap: Bitmap) = ByteBuffer.allocate(bitmap.byteCount).also { bitmap.copyPixelsToBuffer(it) }.array()
        val expected = bytes(baseline)
        val actual = bytes(rendered)
        var rounding = 0
        var beyond = 0
        var largest = 0
        for (y in 0 until baseline.height) for (x in 0 until baseline.width) {
            val e = y * baseline.rowBytes + x * 4
            val a = y * rendered.rowBytes + x * 4
            var worst = 0
            for (c in 0 until 4) worst = maxOf(worst, Math.abs((expected[e + c].toInt() and 0xFF) - (actual[a + c].toInt() and 0xFF)))
            if (worst > ROUNDING) beyond++ else if (worst > 0) rounding++
            largest = maxOf(largest, worst)
        }
        return Difference(rounding, beyond, baseline.width * baseline.height, largest)
    }

    /** Fails unless [rendered] matches [baseline]; [output] is where the rendered PNG was written. */
    fun assertMatches(baseline: Bitmap, rendered: Bitmap, output: File) {
        assertTrue(
            "Changed $output: rendered ${rendered.config}, baseline ${baseline.config}; frames are compared only as ARGB_8888 " +
                "(four 8-bit premultiplied channels per pixel)",
            baseline.config == Bitmap.Config.ARGB_8888 && rendered.config == Bitmap.Config.ARGB_8888,
        )
        assertTrue(
            "Changed $output: rendered ${rendered.width}×${rendered.height}, baseline ${baseline.width}×${baseline.height}",
            baseline.width == rendered.width && baseline.height == rendered.height,
        )
        if (baseline.sameAs(rendered)) return
        val difference = measure(baseline, rendered)
        assertTrue("Changed $output: $difference", difference.accepted)
        println("Accepted with rendering differences $output: $difference")
    }
}
