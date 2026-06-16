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

    // --- avoid(): keep the chip off the offer's fare (the OCR-occlusion blink fix) ---
    // Real Galaxy S25 geometry from the captured session: 1080x2340, measured chip ~530x340, and the
    // fare+leg block union at top=785..bottom=1590.
    private val s25W = 1080
    private val s25H = 2340
    private val s25ChipW = 530
    private val s25ChipH = 340
    private val fareContent = ContentRect(left = 103, top = 785, right = 769, bottom = 1590)

    private fun overlapsContent(p: OverlayPosition, c: ContentRect) =
        p.x < c.right && p.x + s25ChipW > c.left && p.y < c.bottom && p.y + s25ChipH > c.top

    @Test
    fun `avoid leaves the collision-free top-right default unchanged`() {
        // The clean-install default (y=48) sits above the fare (785), so a fresh chip never blinks.
        val def = OverlayPositioning.defaultPosition(s25W, s25H, s25ChipW, s25ChipH)
        assertEquals(def, OverlayPositioning.avoid(def, fareContent, s25W, s25H, s25ChipW, s25ChipH))
    }

    @Test
    fun `avoid lifts a chip parked on the fare above it, keeping the driver's side`() {
        val blinkRect = OverlayPosition(x = 550, y = 913) // the exact on-device blink position
        val resolved = OverlayPositioning.avoid(blinkRect, fareContent, s25W, s25H, s25ChipW, s25ChipH)
        assertEquals(550, resolved.x) // x (the driver's chosen side) is preserved
        assertTrue("lifted chip must clear the fare", !overlapsContent(resolved, fareContent))
        assertTrue("chip is lifted ABOVE the fare", resolved.y < fareContent.top)
    }

    @Test
    fun `avoid is a no-op when content bounds are null`() {
        val p = OverlayPosition(550, 913)
        assertEquals(p, OverlayPositioning.avoid(p, null, s25W, s25H, s25ChipW, s25ChipH))
    }

    @Test
    fun `avoided position never lands in the bottom safe zone`() {
        val resolved = OverlayPositioning.avoid(OverlayPosition(550, 913), fareContent, s25W, s25H, s25ChipW, s25ChipH)
        assertTrue(resolved.y + s25ChipH <= OverlayPositioning.bottomSafeZoneTop(s25H))
    }

    @Test
    fun `avoid lifts a chip parked in the card body BELOW the fare above it (never below)`() {
        // The on-device flicker: the chip sits below the fare but still inside the tall card (over a
        // leg). Our old "no overlap with the fare+leg union -> leave it" left it there to occlude a
        // leg every other frame. It must now be lifted ABOVE the content — never dropped below.
        val parkedLow = OverlayPosition(x = 550, y = 1300) // below the fare (785), in the card body
        val resolved = OverlayPositioning.avoid(parkedLow, fareContent, s25W, s25H, s25ChipW, s25ChipH)
        assertEquals(550, resolved.x) // driver's side kept
        assertTrue("chip is lifted ABOVE the fare", resolved.y + s25ChipH <= fareContent.top)
        assertTrue("never parked in/below the card body", resolved.y < fareContent.top)
    }

    @Test
    fun `avoid clamps to the top inset when the fare sits too high to clear above it`() {
        // Pathological: fare pushed near the top (top=200) so even a fully-lifted chip can't clear it
        // above the inset. clamp() docks it at the top inset — the least-bad slot, chip still shown.
        // Real cards render the fare well down the screen (~785), so this branch is never hit live.
        val highContent = ContentRect(left = 103, top = 200, right = 769, bottom = 1000)
        val resolved = OverlayPositioning.avoid(OverlayPosition(550, 500), highContent, s25W, s25H, s25ChipW, s25ChipH)
        assertEquals(OverlayPositioning.TOP_INSET_PX, resolved.y)
    }
}
