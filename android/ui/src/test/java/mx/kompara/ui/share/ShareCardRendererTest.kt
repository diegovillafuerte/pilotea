package mx.kompara.ui.share

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bitmap render smoke test for the share card (B-055): the renderer must produce a bitmap of the
 * expected dimensions without crashing, for every variant and for both the amounts-visible and
 * redacted paths. Runs under Robolectric so `android.graphics.Bitmap`/`Canvas` exist on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class ShareCardRendererTest {

    private fun card(hideAmounts: Boolean) = ShareCardData(
        periodLabel = "Semana del 1–7 jun",
        periodKind = SharePeriodKind.WEEK,
        netEarnings = if (hideAmounts) null else "$3,450.00",
        trips = "38 viajes",
        hours = "22.5 h",
        percentileFlex = "Top 22% en CDMX 🚀",
        streakLine = "🔥 4 semanas seguidas",
        hideAmounts = hideAmounts,
    )

    @Test
    fun `story variant renders at 1080x1920`() {
        val bitmap = ShareCardRenderer.render(card(hideAmounts = false), ShareCardVariant.STORY)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
        assertFalse(bitmap.isRecycled)
    }

    @Test
    fun `landscape variant renders at 1200x630`() {
        val bitmap = ShareCardRenderer.render(card(hideAmounts = false), ShareCardVariant.LANDSCAPE)
        assertEquals(1200, bitmap.width)
        assertEquals(630, bitmap.height)
    }

    @Test
    fun `redacted card still renders without crash`() {
        val bitmap = ShareCardRenderer.render(card(hideAmounts = true), ShareCardVariant.STORY)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
    }

    @Test
    fun `sparse card (no flex, no streak, no hours) renders`() {
        val sparse = ShareCardData(
            periodLabel = "Junio 2026",
            periodKind = SharePeriodKind.MONTH,
            netEarnings = "$0.00",
            trips = "0 viajes",
            hours = null,
            percentileFlex = null,
            streakLine = null,
            hideAmounts = false,
        )
        val bitmap = ShareCardRenderer.render(sparse, ShareCardVariant.LANDSCAPE)
        assertEquals(1200, bitmap.width)
        assertEquals(630, bitmap.height)
    }
}
