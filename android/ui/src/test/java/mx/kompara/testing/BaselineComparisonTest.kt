package mx.kompara.testing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The rendered-frame rule (`android/test-support`): rendering differences between CPUs pass, anything visible fails. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BaselineComparisonTest {
    private val output = File("build/outputs/frame.png")
    private val ground = Color.rgb(200, 100, 50)
    private fun frame(width: Int = 100, height: Int = 100) = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(ground) }
    /** The first [count] pixels with their red raised by [levels]. */
    private fun shifted(count: Int, levels: Int) = frame().apply { repeat(count) { setPixel(it % width, it / width, Color.rgb(200 + levels, 100, 50)) } }
    private fun accepts(rendered: Bitmap, baseline: Bitmap = frame()) = BaselineComparison.assertMatches(baseline, rendered, output)
    private fun rejects(rendered: Bitmap, baseline: Bitmap = frame()) = assertThrows(AssertionError::class.java) { accepts(rendered, baseline) }

    @Test fun `identical frames pass`() {
        accepts(frame())
        assertEquals(0, BaselineComparison.measure(frame(), frame()).pixels)
    }

    @Test fun `one level on a few pixels passes`() {
        accepts(shifted(10, 1))
        val difference = BaselineComparison.measure(frame(), shifted(10, 1))
        assertEquals(10, difference.pixels); assertEquals(1, difference.largest)
    }

    @Test fun `three levels pass and four fail`() {
        accepts(shifted(1, 3))
        val failure = rejects(shifted(1, 4))
        listOf("1 of 10000 pixels differ (0.010 %)", "largest channel difference 4", "limits 3 levels on 5 %", "$output")
            .forEach { assertTrue("\"${failure.message}\" names $it", it in failure.message!!) }
    }

    @Test fun `one level on more than five percent of the pixels fails`() {
        accepts(shifted(500, 1))
        rejects(shifted(501, 1))
    }

    @Test fun `a different size fails`() {
        rejects(frame(height = 101))
    }

    @Test fun `a frame that is not ARGB_8888 fails even when identical`() {
        fun rgb565() = Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565).apply { eraseColor(ground) }
        val failure = rejects(rgb565(), rgb565())
        assertTrue(failure.message, "rendered RGB_565, baseline RGB_565; frames are compared only as ARGB_8888" in failure.message!!)
        rejects(rgb565())
    }

    @Test fun `a translucent pixel is compared premultiplied`() {
        val baseline = frame().apply { setPixel(0, 0, Color.argb(10, 255, 0, 0)) }
        val rendered = frame().apply { setPixel(0, 0, Color.argb(10, 230, 0, 0)) }
        assertTrue("un-premultiplied, the reds differ by more than the limit", Color.red(baseline.getPixel(0, 0)) - Color.red(rendered.getPixel(0, 0)) > BaselineComparison.MAX_LEVELS)
        assertEquals(1, BaselineComparison.measure(baseline, rendered).largest)
        accepts(rendered, baseline)
    }

    /** ReglaTest's S1 frame as the CI runner (Linux x86-64, JDK 21) and this Mac (Apple silicon, JDK 25) drew it. */
    @Test fun `the same frame drawn on two CPUs passes`() {
        fun png(machine: String) = javaClass.getResourceAsStream("/baseline-comparison/regla_S1_day_393_1.0.$machine.png")!!.use { BitmapFactory.decodeStream(it) }
        val mac = png("mac-apple-silicon")
        val ci = png("ci-linux-x86-64")
        assertFalse("the two renders are not identical", mac.sameAs(ci))
        BaselineComparison.assertMatches(mac, ci, output)
    }
}
