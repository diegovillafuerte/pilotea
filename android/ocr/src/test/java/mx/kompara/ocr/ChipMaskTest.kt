package mx.kompara.ocr

import mx.kompara.data.service.ScreenRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChipMask] drops the floating chip's own OCR lines so the chip's "MXN…"/"$…" text is never parsed
 * as the offer fare (the self-capture feedback loop that collapsed the verdict to $0).
 */
class ChipMaskTest {

    private fun block(text: String, l: Int, t: Int, r: Int, b: Int) =
        OcrBlock(text, OcrBounds(l, t, r, b))

    @Test
    fun `a null chip rect leaves every block untouched`() {
        val blocks = listOf(block("$159.00", 100, 200, 360, 260))
        assertEquals(blocks, ChipMask.maskOwnChip(blocks, chip = null))
    }

    @Test
    fun `drops the chip's own brand + rate lines but keeps the host fare elsewhere`() {
        // Chip parked top-right; its wordmark + rate lines sit inside the chip rect. The real offer
        // fare is lower-left, clear of the chip.
        val chip = ScreenRect(left = 700, top = 40, right = 1040, bottom = 320)
        val chipBrand = block("Kompara", 720, 60, 900, 100) // inside the chip
        val chipRate = block("$15.00/km", 720, 120, 980, 180) // inside the chip
        val hostFare = block("$159.00", 80, 600, 360, 660) // far from the chip

        val kept = ChipMask.maskOwnChip(listOf(chipBrand, chipRate, hostFare), chip)

        assertEquals(listOf(hostFare), kept)
    }

    @Test
    fun `drops a line that overlaps the chip only within the inflate margin`() {
        val chip = ScreenRect(700, 40, 1040, 320)
        // A line just below the chip's bottom edge, inside DEFAULT_MARGIN_PX (shadow / aa edge).
        val grazing = block("$9.00", 800, 320 + ChipMask.DEFAULT_MARGIN_PX - 2, 1000, 380)
        assertTrue(ChipMask.maskOwnChip(listOf(grazing), chip).isEmpty())
    }

    @Test
    fun `keeps a line clear of the chip plus its margin`() {
        val chip = ScreenRect(700, 40, 1040, 320)
        val clear = block("$9.00", 800, 320 + ChipMask.DEFAULT_MARGIN_PX + 20, 1000, 420)
        assertEquals(1, ChipMask.maskOwnChip(listOf(clear), chip).size)
    }

    // The avoid()/ChipMask invariant on REAL Galaxy S25 geometry (1080x2340): because the chip is
    // positioned above the fare, masking its own rect must never drop the offer's fare or leg lines.
    // Real fixture coords: fare 103..769 x 785..881; pickup leg ~192..551 x 1339..1384; trip leg
    // ~216..687 x 1537..1590. Measured chip ~530 wide x 340 tall.
    private val fare = block("MXN137.28", 103, 785, 769, 881)
    private val pickupLeg = block("A 5 min (1.0 km)", 192, 1339, 551, 1384)
    private val tripLeg = block("Viaje: 51 min (13.0 km)", 216, 1537, 687, 1590)
    private val offer = listOf(fare, pickupLeg, tripLeg)

    @Test
    fun `chip at the top-right default does not drop the fare or legs`() {
        // x = 1080 - 530 - 8 = 542, y = 48 (TOP_INSET) -> rect (542,48,1072,388), well above the fare.
        val chip = ScreenRect(left = 542, top = 48, right = 1072, bottom = 388)
        assertEquals(offer, ChipMask.maskOwnChip(offer, chip))
    }

    @Test
    fun `chip lifted just above the fare (drag-down case) still does not drop the fare`() {
        // The avoid()-lifted slot: bottom edge = content.top - GAP(8) = 777, so y = 777 - 340 = 437.
        // The 8px mask margin extends the masked region's bottom to exactly the fare top (785) without
        // covering it (strict overlap), so the fare survives even in this tightest case.
        val chip = ScreenRect(left = 542, top = 437, right = 1072, bottom = 777)
        val kept = ChipMask.maskOwnChip(offer, chip)
        assertTrue("fare must survive the lifted-chip mask", kept.contains(fare))
        assertTrue("trip leg must survive", kept.contains(tripLeg))
    }
}
