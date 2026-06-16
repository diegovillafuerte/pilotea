package mx.kompara.ocr

import mx.kompara.data.service.ScreenRect

/**
 * Strips the driver-facing Kompara chip's own text out of an OCR frame before parsing.
 *
 * MediaProjection mirrors the WHOLE screen, so a captured frame contains our floating verdict chip
 * composited on top of the host offer. The chip prints an "MXN…"/"$…" amount that the parsers' fare
 * regex happily matches, so the chip's own number gets read as the offer's fare — a self-capture
 * feedback loop that drove the live verdict to $0 (the displayed value feeds back in as the next
 * frame's fare and collapses). [mx.kompara.overlay] publishes the chip's current rect to
 * [mx.kompara.data.service.ScreenReaderState.overlayChipRect]; this drops every OCR line that
 * overlaps that rect (inflated by a small margin for the brand strip + drop shadow) so the parsers
 * never see the chip.
 *
 * Works because the chip is positioned (and drag-constrained) to sit ABOVE the fare — so its rect
 * covers only the card header / map, and dropping those blocks never removes the fare or legs. If a
 * transient frame does catch the chip over the fare, that frame's fare line is masked, the parse
 * fails, and [CardPresenceTracker] holds the last-good verdict (never a false $0) until the chip is
 * repositioned off it.
 *
 * Why not `FLAG_SECURE` on the overlay window: that would blank the chip from ALL screen capture —
 * including the DRIVER's own screenshots, which the brand strip exists to enable. Masking only OUR
 * capture keeps the chip fully shareable. Pure + JVM-testable.
 */
object ChipMask {

    /** Inflate (px) applied around the chip rect — covers anti-aliased edges, the drop shadow, and
     *  small overlay-vs-capture coordinate skew (status bar / cutout). */
    const val DEFAULT_MARGIN_PX = 8

    /**
     * Drop every [OcrBlock] whose bounds overlap [chip] (inflated by [marginPx]). A null [chip] means
     * the chip is hidden, so there is nothing to mask and [blocks] is returned unchanged.
     */
    fun maskOwnChip(
        blocks: List<OcrBlock>,
        chip: ScreenRect?,
        marginPx: Int = DEFAULT_MARGIN_PX,
    ): List<OcrBlock> {
        if (chip == null) return blocks
        val left = chip.left - marginPx
        val top = chip.top - marginPx
        val right = chip.right + marginPx
        val bottom = chip.bottom + marginPx
        return blocks.filterNot { it.bounds.overlaps(left, top, right, bottom) }
    }

    private fun OcrBounds.overlaps(l: Int, t: Int, r: Int, b: Int): Boolean =
        left < r && right > l && top < b && bottom > t
}
