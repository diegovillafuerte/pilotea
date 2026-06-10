package mx.kompara.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive math for clamping, the bottom safe zone, and snap-to-edge. A 1080x1920 portrait screen
 * stands in for a typical phone; the chip is 360x280 px.
 */
class OverlayPositioningTest {

    private val screenW = 1080
    private val screenH = 1920
    private val chipW = 360
    private val chipH = 280

    @Test
    fun `bottom safe zone is the lower 25 percent of the screen`() {
        // 1920 * 0.75 = 1440 -> everything at/below 1440 is reserved.
        assertEquals(1440, OverlayPositioning.bottomSafeZoneTop(screenH))
    }

    @Test
    fun `clamp keeps the chip bottom out of the safe zone`() {
        val clamped = OverlayPositioning.clamp(
            OverlayPosition(x = 100, y = 1800), // way down into the safe zone
            screenW, screenH, chipW, chipH,
        )
        // bottom edge must not pass 1440
        assertTrue("chip bottom intrudes into safe zone", clamped.y + chipH <= 1440)
        assertEquals(1440 - chipH, clamped.y)
    }

    @Test
    fun `clamp keeps the chip on screen horizontally and below the top inset`() {
        val offRight = OverlayPositioning.clamp(OverlayPosition(5000, 0), screenW, screenH, chipW, chipH)
        assertEquals(screenW - chipW, offRight.x)
        assertEquals(OverlayPositioning.TOP_INSET_PX, offRight.y)

        val offLeft = OverlayPositioning.clamp(OverlayPosition(-200, -200), screenW, screenH, chipW, chipH)
        assertEquals(0, offLeft.x)
        assertEquals(OverlayPositioning.TOP_INSET_PX, offLeft.y)
    }

    @Test
    fun `edgeFor picks the nearer side by chip centre`() {
        assertEquals(SnapEdge.LEFT, OverlayPositioning.edgeFor(OverlayPosition(10, 0), screenW, chipW))
        assertEquals(SnapEdge.RIGHT, OverlayPositioning.edgeFor(OverlayPosition(900, 0), screenW, chipW))
    }

    @Test
    fun `snapToEdge pins to left or right flush with the margin`() {
        val snappedLeft = OverlayPositioning.snapToEdge(OverlayPosition(50, 300), screenW, screenH, chipW, chipH)
        assertEquals(OverlayPositioning.EDGE_MARGIN_PX, snappedLeft.x)

        val snappedRight = OverlayPositioning.snapToEdge(OverlayPosition(800, 300), screenW, screenH, chipW, chipH)
        assertEquals(screenW - chipW - OverlayPositioning.EDGE_MARGIN_PX, snappedRight.x)
    }

    @Test
    fun `snapToEdge also enforces the bottom safe zone`() {
        val snapped = OverlayPositioning.snapToEdge(OverlayPosition(800, 1900), screenW, screenH, chipW, chipH)
        assertTrue(snapped.y + chipH <= 1440)
    }

    @Test
    fun `default position is top-right inside the safe area`() {
        val def = OverlayPositioning.defaultPosition(screenW, screenH, chipW, chipH)
        assertEquals(screenW - chipW - OverlayPositioning.EDGE_MARGIN_PX, def.x)
        assertEquals(OverlayPositioning.TOP_INSET_PX, def.y)
        assertTrue(def.y + chipH <= 1440)
    }
}
