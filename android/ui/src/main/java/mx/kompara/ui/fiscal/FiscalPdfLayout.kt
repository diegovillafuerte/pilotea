package mx.kompara.ui.fiscal

/**
 * Pure layout MATH for the monthly fiscal PDF (B-052) — A4 page geometry, table column x-positions,
 * row-stacking, and pagination — as plain functions of the page size and (caller-measured) row count.
 * Kept Android-free so the geometry is unit-tested on the JVM; [FiscalPdfRenderer] feeds it real
 * dimensions and draws at the returned coordinates. No `android.graphics`/`pdf` import lives here.
 *
 * All lengths are in PDF points (1/72 inch), the unit `android.graphics.pdf.PdfDocument` draws in.
 */
object FiscalPdfLayout {

    /** A4 in points: 210mm × 297mm → 595 × 842 (the standard PdfDocument A4 page size). */
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842

    /** Page margin in points (~0.5 inch). */
    const val MARGIN = 36f

    /** Height reserved for the header block (wordmark + driver + month). */
    const val HEADER_HEIGHT = 96f

    /** Height of the footer disclaimer block. */
    const val FOOTER_HEIGHT = 90f

    /** Height of one table data row. */
    const val ROW_HEIGHT = 26f

    /** Height of the table header row. */
    const val TABLE_HEADER_HEIGHT = 28f

    /** Gap between major sections (header→table, table→totals, totals→footer). */
    const val SECTION_GAP = 18f

    /** The usable content width between the left and right margins. */
    fun contentWidth(pageWidth: Int = PAGE_WIDTH): Float = pageWidth - 2 * MARGIN

    /** Left content edge x. */
    fun leftX(): Float = MARGIN

    /** Right content edge x. */
    fun rightX(pageWidth: Int = PAGE_WIDTH): Float = pageWidth - MARGIN

    /** Top content edge y (below the header). */
    fun contentTopY(): Float = MARGIN + HEADER_HEIGHT

    /** The y where the footer block begins (above the bottom margin). */
    fun footerTopY(pageHeight: Int = PAGE_HEIGHT): Float = pageHeight - MARGIN - FOOTER_HEIGHT

    /**
     * The five table column **right edges** (the table is: Plataforma | Bruto | Neto | ISR | IVA),
     * with the platform name left-aligned and the four money columns right-aligned. The platform
     * column takes a fixed share of the width; the four money columns split the rest evenly.
     *
     * Returns the right-edge x of each of the 5 columns, left→right.
     */
    fun columnRightEdges(pageWidth: Int = PAGE_WIDTH): List<Float> {
        val left = leftX()
        val width = contentWidth(pageWidth)
        val platformWidth = width * PLATFORM_COL_FRACTION
        val moneyWidth = (width - platformWidth) / MONEY_COLS
        val edges = ArrayList<Float>(TOTAL_COLS)
        edges.add(left + platformWidth) // platform col right edge
        var x = left + platformWidth
        repeat(MONEY_COLS) {
            x += moneyWidth
            edges.add(x)
        }
        return edges
    }

    /** Left edge x of the platform (first) column — where its left-aligned name starts. */
    fun platformColLeftX(): Float = leftX()

    /**
     * The baseline y for [rowCount] table data rows stacked from [startY], one [ROW_HEIGHT] apart.
     * The first row's baseline sits a little below its top so text isn't clipped against the header.
     */
    fun rowBaselines(startY: Float, rowCount: Int): List<Float> {
        val baselines = ArrayList<Float>(rowCount)
        var y = startY + ROW_HEIGHT
        repeat(rowCount) {
            baselines.add(y)
            y += ROW_HEIGHT
        }
        return baselines
    }

    /**
     * How many data rows fit between [startY] and the footer top on a page of [pageHeight]. Used to
     * decide whether the report needs a second page (it shouldn't for 1–3 platforms, but the math is
     * honest so a future longer report paginates instead of overflowing the footer).
     */
    fun maxRowsBeforeFooter(startY: Float, pageHeight: Int = PAGE_HEIGHT): Int {
        val available = footerTopY(pageHeight) - startY - SECTION_GAP
        if (available <= 0f) return 0
        return (available / ROW_HEIGHT).toInt()
    }

    private const val PLATFORM_COL_FRACTION = 0.28f
    private const val MONEY_COLS = 4
    private const val TOTAL_COLS = 5
}
