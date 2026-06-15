package mx.kompara.ocr

import mx.kompara.data.service.ScreenRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChipMask] drops the floating chip's own OCR lines so the chip's "$X/km" text is never parsed as
 * the offer fare (the self-capture feedback loop that collapsed the verdict to $0).
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
}
