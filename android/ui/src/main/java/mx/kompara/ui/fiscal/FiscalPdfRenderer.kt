package mx.kompara.ui.fiscal

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/**
 * Renders a [FiscalReportData] to a single-page A4 PDF using `android.graphics.pdf.PdfDocument`
 * (B-052) — **no new dependency**, just the platform PDF API. Pure presentation: every figure is
 * already formatted, every position comes from [FiscalPdfLayout], so this file only owns paints and
 * `drawText`/`drawLine` calls. Deterministic layout (the layout math is unit-tested separately; a
 * Robolectric smoke test asserts this produces a >0-byte, 1-page document).
 *
 * Layout, top→bottom: header (wordmark + title + driver + month) → per-platform table
 * (Plataforma | Bruto | Neto | ISR ret. | IVA ret.) → month-totals + YTD rows → total-withheld +
 * IMSS coverage line → disclaimer/rates/generated footer.
 */
object FiscalPdfRenderer {

    /** Write the report as a PDF into [out]. Closes the document; the caller owns [out]. */
    fun render(data: FiscalReportData, out: OutputStream) {
        val doc = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(
                FiscalPdfLayout.PAGE_WIDTH,
                FiscalPdfLayout.PAGE_HEIGHT,
                1,
            ).create()
            val page = doc.startPage(pageInfo)
            drawPage(page.canvas, data)
            doc.finishPage(page)
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    /**
     * Draw the whole report onto [canvas] (the page's own coordinate space). Exposed (vs. the
     * [PdfDocument] wrapper above) so the drawing — every `drawText`/`drawLine`, the wrapping, the
     * column placement — is smoke-testable on a real [android.graphics.Canvas] under Robolectric,
     * which can't construct a [PdfDocument] (no native PDF runtime) but renders Canvas fine. Returns
     * nothing; the smoke test asserts it draws without throwing for full and sparse reports.
     */
    fun drawPage(canvas: android.graphics.Canvas, data: FiscalReportData) {
        drawHeader(canvas, data)
        val tableTop = drawTable(canvas, data)
        val footerStart = drawSummaryLines(canvas, data, tableTop)
        drawFooter(canvas, data, footerStart)
    }

    // ─── header ──────────────────────────────────────────────────────────────────────────────────

    private fun drawHeader(canvas: android.graphics.Canvas, data: FiscalReportData) {
        val left = FiscalPdfLayout.leftX()
        var y = FiscalPdfLayout.MARGIN + 22f
        canvas.drawText(data.wordmark, left, y, wordmarkPaint)
        y += 24f
        canvas.drawText(data.title, left, y, titlePaint)
        y += 18f
        data.driverName?.let {
            canvas.drawText(it, left, y, subtlePaint)
            y += 16f
        }
        canvas.drawText(data.monthLabel, left, y, labelPaint)

        // Header rule under the block.
        val ruleY = FiscalPdfLayout.MARGIN + FiscalPdfLayout.HEADER_HEIGHT - 6f
        canvas.drawLine(left, ruleY, FiscalPdfLayout.rightX(), ruleY, rulePaint)
    }

    // ─── table ───────────────────────────────────────────────────────────────────────────────────

    /** Draws the column headers + per-platform rows; returns the y just below the last row. */
    private fun drawTable(canvas: android.graphics.Canvas, data: FiscalReportData): Float {
        val edges = FiscalPdfLayout.columnRightEdges()
        val platformLeft = FiscalPdfLayout.platformColLeftX()
        var y = FiscalPdfLayout.contentTopY()

        // Header row: platform header left-aligned, money headers right-aligned to their column edges.
        canvas.drawText(data.columnHeaders.first(), platformLeft, y + 18f, headerCellPaint)
        data.columnHeaders.drop(1).forEachIndexed { i, h ->
            canvas.drawText(h, edges[i + 1], y + 18f, headerCellPaintRight)
        }
        y += FiscalPdfLayout.TABLE_HEADER_HEIGHT
        canvas.drawLine(platformLeft, y, FiscalPdfLayout.rightX(), y, rulePaint)

        // Data rows.
        val baselines = FiscalPdfLayout.rowBaselines(y, data.rows.size)
        data.rows.forEachIndexed { i, row ->
            val baseY = baselines[i]
            canvas.drawText(row.label, platformLeft, baseY, cellPaint)
            row.moneyCells().forEachIndexed { c, cell ->
                canvas.drawText(cell, edges[c + 1], baseY, cellPaintRight)
            }
        }
        return if (baselines.isEmpty()) y else baselines.last() + 8f
    }

    // ─── totals + IMSS lines ─────────────────────────────────────────────────────────────────────

    /** Draws the totals rows + withheld + IMSS line; returns the y where the footer should start. */
    private fun drawSummaryLines(
        canvas: android.graphics.Canvas,
        data: FiscalReportData,
        tableBottom: Float,
    ): Float {
        val edges = FiscalPdfLayout.columnRightEdges()
        val platformLeft = FiscalPdfLayout.platformColLeftX()
        var y = tableBottom + 6f
        canvas.drawLine(platformLeft, y, FiscalPdfLayout.rightX(), y, rulePaint)
        y += FiscalPdfLayout.ROW_HEIGHT

        fun totalsRow(row: FiscalReportRow) {
            canvas.drawText(row.label, platformLeft, y, totalLabelPaint)
            row.moneyCells().forEachIndexed { c, cell ->
                canvas.drawText(cell, edges[c + 1], y, totalCellPaint)
            }
            y += FiscalPdfLayout.ROW_HEIGHT
        }
        totalsRow(data.monthTotals)
        totalsRow(data.ytdTotals)

        y += FiscalPdfLayout.SECTION_GAP
        canvas.drawText(data.monthWithheldTotal.let { "≈ $it" }, platformLeft, y, labelPaint)
        // (the leading label is part of ratesNote/strings; we keep the figure prominent here)
        y += 18f
        canvas.drawText(data.imssCoverageLine, platformLeft, y, subtlePaint)
        return y + FiscalPdfLayout.SECTION_GAP
    }

    // ─── footer ──────────────────────────────────────────────────────────────────────────────────

    private fun drawFooter(canvas: android.graphics.Canvas, data: FiscalReportData, startY: Float) {
        val left = FiscalPdfLayout.leftX()
        val width = FiscalPdfLayout.contentWidth()
        // Pin the footer to the bottom of the page so it always sits at the foot regardless of rows.
        var y = maxOf(startY, FiscalPdfLayout.footerTopY())
        canvas.drawLine(left, y, FiscalPdfLayout.rightX(), y, rulePaint)
        y += 14f
        data.approximationNote?.let {
            y = drawWrapped(canvas, it, left, y, width, footnotePaint)
        }
        y = drawWrapped(canvas, data.ratesNote, left, y, width, footnotePaint)
        y = drawWrapped(canvas, data.disclaimer, left, y, width, footnotePaint)
        canvas.drawText(data.generatedOn, left, y, footnotePaint)
    }

    /**
     * Word-wrap [text] within [maxWidth], drawing each line; returns the y below the last line. Simple
     * greedy wrap on spaces — enough for the few footnote sentences, and keeps the layout deterministic.
     */
    private fun drawWrapped(
        canvas: android.graphics.Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
    ): Float {
        val words = text.split(' ')
        val line = StringBuilder()
        var y = startY
        val lineHeight = paint.textSize + 3f
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line.toString(), x, y, paint)
                y += lineHeight
                line.setLength(0)
                line.append(word)
            } else {
                line.setLength(0)
                line.append(candidate)
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line.toString(), x, y, paint)
            y += lineHeight
        }
        return y
    }

    // ─── paints ──────────────────────────────────────────────────────────────────────────────────

    private val ink = Color.rgb(0x1A, 0x1A, 0x1A)
    private val subtleInk = Color.rgb(0x66, 0x66, 0x66)
    private val accent = Color.rgb(0x00, 0x6E, 0x52) // Kompara green-ish; cosmetic only.

    private val wordmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent; textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink; textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 12f }
    private val subtlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = subtleInk; textSize = 10f }
    private val headerCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink; textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val headerCellPaintRight = Paint(headerCellPaint).apply { textAlign = Paint.Align.RIGHT }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 11f }
    private val cellPaintRight = Paint(cellPaint).apply { textAlign = Paint.Align.RIGHT }
    private val totalLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink; textSize = 11f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val totalCellPaint = Paint(totalLabelPaint).apply { textAlign = Paint.Align.RIGHT }
    private val footnotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = subtleInk; textSize = 8f }
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xCC, 0xCC, 0xCC); strokeWidth = 0.7f
    }
}
