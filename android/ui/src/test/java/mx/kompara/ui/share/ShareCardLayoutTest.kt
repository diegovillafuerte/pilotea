package mx.kompara.ui.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure layout MATH for the share card (B-055): margins, fit-to-width font sizing, baseline stacking. */
class ShareCardLayoutTest {

    private val story = ShareCardVariant.STORY

    @Test
    fun `variant dimensions are the platform-recommended sizes`() {
        assertEquals(1080, ShareCardVariant.STORY.width)
        assertEquals(1920, ShareCardVariant.STORY.height)
        assertEquals(1200, ShareCardVariant.LANDSCAPE.width)
        assertEquals(630, ShareCardVariant.LANDSCAPE.height)
    }

    @Test
    fun `content width is symmetric inside the side margins`() {
        val margin = ShareCardLayout.sideMargin(story.width)
        val content = ShareCardLayout.contentWidth(story.width)
        assertEquals(story.width - 2 * margin, content, 0.001f)
        assertTrue("content must be positive", content > 0f)
        assertEquals(margin, ShareCardLayout.leftX(story.width), 0.001f)
    }

    @Test
    fun `center x horizontally centres a run`() {
        val x = ShareCardLayout.centerX(1000, textWidth = 200f)
        assertEquals(400f, x, 0.001f) // (1000 - 200) / 2
    }

    @Test
    fun `fit font size shrinks text that overflows`() {
        // At refSize 100, the text measures 400px but only 200px is allowed → halve to 50.
        val size = ShareCardLayout.fitFontSize(
            widthAtRefSize = 400f,
            refSize = 100f,
            maxWidth = 200f,
            maxSize = 100f,
            minSize = 10f,
        )
        assertEquals(50f, size, 0.001f)
    }

    @Test
    fun `fit font size caps at max when text already fits`() {
        // Text fits comfortably (100px at ref 100 within 500px) → stay at the design cap (maxSize).
        val size = ShareCardLayout.fitFontSize(
            widthAtRefSize = 100f,
            refSize = 100f,
            maxWidth = 500f,
            maxSize = 100f,
            minSize = 10f,
        )
        assertEquals(100f, size, 0.001f)
    }

    @Test
    fun `fit font size never goes below the floor`() {
        val size = ShareCardLayout.fitFontSize(
            widthAtRefSize = 10000f,
            refSize = 100f,
            maxWidth = 50f,
            maxSize = 100f,
            minSize = 24f,
        )
        assertEquals(24f, size, 0.001f)
    }

    @Test
    fun `stacked baselines advance by line height plus gap`() {
        val baselines = ShareCardLayout.stackedBaselines(
            startY = 100f,
            lineHeights = listOf(40f, 60f, 20f),
            gap = 10f,
        )
        // First baseline = top(100) + h(40) = 140.
        // Next top = 100 + 40 + 10 = 150 → baseline 150 + 60 = 210.
        // Next top = 150 + 60 + 10 = 220 → baseline 220 + 20 = 240.
        assertEquals(listOf(140f, 210f, 240f), baselines)
    }

    @Test
    fun `stacked baselines empty for no lines`() {
        assertTrue(ShareCardLayout.stackedBaselines(0f, emptyList(), 10f).isEmpty())
    }

    @Test
    fun `net number is the dominant reference size`() {
        // The big peso number must out-size every other text block on the card.
        val net = ShareCardLayout.RefSizes.NET_NUMBER
        assertTrue(net > ShareCardLayout.RefSizes.HEADLINE)
        assertTrue(net > ShareCardLayout.RefSizes.FLEX)
        assertTrue(net > ShareCardLayout.RefSizes.SECONDARY)
        assertTrue(net > ShareCardLayout.RefSizes.WORDMARK)
        assertTrue(net > ShareCardLayout.RefSizes.PERIOD_LABEL)
    }
}
