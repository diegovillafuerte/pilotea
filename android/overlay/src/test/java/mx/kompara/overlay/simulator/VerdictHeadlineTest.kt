package mx.kompara.overlay.simulator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.kompara.metrics.VerdictLevel
import mx.kompara.overlay.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The simulator headline copy must follow the **live** verdict level so the text and the chip colour
 * never contradict after the playground floor re-grades the chip (B-074 F4). Runs under Robolectric
 * to resolve the real es-MX strings, asserting each level picks the matching headline (verde/amarillo/
 * rojo) rather than the demo's fixed shape.
 */
@RunWith(AndroidJUnit4::class)
class VerdictHeadlineTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `green level uses the verde headline`() {
        assertEquals(R.string.sim_verdict_good, headlineResFor(VerdictLevel.GREEN))
        assertHeadlineStartsWith(VerdictLevel.GREEN, "Verde")
    }

    @Test
    fun `yellow level uses the amarillo headline`() {
        assertEquals(R.string.sim_verdict_marginal, headlineResFor(VerdictLevel.YELLOW))
        assertHeadlineStartsWith(VerdictLevel.YELLOW, "Amarillo")
    }

    @Test
    fun `red level uses the rojo headline`() {
        assertEquals(R.string.sim_verdict_bad, headlineResFor(VerdictLevel.RED))
        assertHeadlineStartsWith(VerdictLevel.RED, "Rojo")
    }

    private fun assertHeadlineStartsWith(level: VerdictLevel, prefix: String) {
        // The %1$s arg is the net $/km figure; only the leading verdict word matters here.
        val text = context.getString(headlineResFor(level), "\$7.00/km")
        assertEquals(
            "headline for $level should read \"$prefix…\"",
            prefix,
            text.substringBefore(':'),
        )
    }
}
