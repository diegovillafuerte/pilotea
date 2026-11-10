package mx.kompara.testing

import android.graphics.Bitmap
import org.junit.Assert.assertTrue
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale

/**
 * The one rule a rendered frame is held to against its reviewed baseline, in `:ui` and `:overlay` (shared
 * test source, `android/test-support`). A frame matches when a person could not tell the two apart, on any CPU.
 *
 * Robolectric's software rasterizer is not bit-exact across CPU architectures. Measured on 2026-11-10, the CI
 * runner (Linux x86-64) against the baselines recorded on Apple silicon: of 22 frames, 7 were byte-identical and
 * 15 differed in 40 to 1,445 of 334,836 pixels (0.012 % to 0.43 %), by 1 level of 255 per channel and by 2 on
 * three pixels in all, only on antialiased path edges and on text or shapes blended with alpha. Layout, text and
 * colours were the same. So: both ARGB_8888 and the same size, then identical ([Bitmap.sameAs]) passes at once;
 * otherwise no channel of any pixel may differ by more than [MAX_LEVELS], and at most [MAX_SHARE] of the pixels may
 * differ at all. Anything visible (a moved edge, a changed colour, a different word) breaks one of the two.
 */
object BaselineComparison {
    /** Observed at most 2 levels of 255; one more is allowed for a third stacked blend. */
    const val MAX_LEVELS = 3

    /**
     * Observed at most 0.43 % of a frame. 5 % still catches a flat surface drifting by a level: surfaces cover far
     * more of a frame than edges and glyphs do.
     */
    const val MAX_SHARE = 0.05

    /** How two frames of the same size and config differ: [pixels] of [total] differ, by at most [largest] levels. */
    class Difference(val pixels: Int, val total: Int, val largest: Int) {
        val share: Double get() = pixels.toDouble() / total
        val accepted: Boolean get() = largest <= MAX_LEVELS && share <= MAX_SHARE
        override fun toString() = "%d of %d pixels differ (%.3f %%), largest channel difference %d; limits %d levels on %.0f %% of the pixels"
            .format(Locale.ROOT, pixels, total, share * 100, largest, MAX_LEVELS, MAX_SHARE * 100)
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
        var pixels = 0
        var largest = 0
        for (y in 0 until baseline.height) for (x in 0 until baseline.width) {
            val e = y * baseline.rowBytes + x * 4
            val a = y * rendered.rowBytes + x * 4
            var worst = 0
            for (c in 0 until 4) worst = maxOf(worst, Math.abs((expected[e + c].toInt() and 0xFF) - (actual[a + c].toInt() and 0xFF)))
            if (worst > 0) pixels++
            largest = maxOf(largest, worst)
        }
        return Difference(pixels, baseline.width * baseline.height, largest)
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
