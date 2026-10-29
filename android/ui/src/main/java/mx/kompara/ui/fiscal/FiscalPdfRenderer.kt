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
class FiscalPdfRenderer(context: android.content.Context) {
    private val fonts = mx.kompara.ui.export.ExportFonts(context)

    /** Write a paginated factual report; the caller owns the output stream. */
    fun render(data: FiscalReportData, out: OutputStream) {
        val doc = PdfDocument()
        try {
            val pages = if (data.recordedRows != null) recordedPages(data) else listOf(emptyList())
            pages.forEachIndexed { index, lines ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(FiscalPdfLayout.PAGE_WIDTH, FiscalPdfLayout.PAGE_HEIGHT, index + 1).create())
                if (data.recordedRows != null) {
                    drawHeader(page.canvas, data)
                    drawRecorded(page.canvas, lines)
                } else drawPage(page.canvas, data)
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally { doc.close() }
    }

    /** Canvas entry for layout QA. Factual reports use the same first-page lines as export. */
    fun drawPage(canvas: android.graphics.Canvas, data: FiscalReportData) {
        drawHeader(canvas, data)
        if (data.recordedRows != null) {
            drawRecorded(canvas, recordedPages(data).first())
            return
        }
        val tableTop = drawTable(canvas, data)
        val footerStart = drawSummaryLines(canvas, data, tableTop)
        drawFooter(canvas, data, footerStart)
    }

    internal data class RecordedLine(val text: String, val value: String? = null, val height: Float = 16f)

    internal fun recordedPages(data: FiscalReportData): List<List<RecordedLine>> {
        val lines = buildList {
            data.recordedRows.orEmpty().forEach { (label, value) ->
                val wrapped = wrapLines(label, FiscalPdfLayout.contentWidth() - cellPaintRight.measureText(value) - 16f, cellPaint)
                wrapped.forEachIndexed { index, text -> add(RecordedLine(text, if (index == 0) value else null,
                    if (index == wrapped.lastIndex) 28f else 16f)) }
            }
            (data.sourceNotes + data.imssCoverageLine + data.generatedOn).forEach { note ->
                val wrapped = wrapLines(note, FiscalPdfLayout.contentWidth(), subtlePaint)
                wrapped.forEachIndexed { index, text -> add(RecordedLine(text, height = if (index == wrapped.lastIndex) 22f else 14f)) }
            }
        }
        val pages = mutableListOf<MutableList<RecordedLine>>(mutableListOf())
        var y = FiscalPdfLayout.contentTopY() + 20f
        for (line in lines) {
            if (y + line.height > FiscalPdfLayout.PAGE_HEIGHT - FiscalPdfLayout.MARGIN) {
                pages.add(mutableListOf()); y = FiscalPdfLayout.contentTopY() + 20f
            }
            pages.last().add(line); y += line.height
        }
        return pages
    }

    private fun drawRecorded(canvas: android.graphics.Canvas, lines: List<RecordedLine>) {
        var y = FiscalPdfLayout.contentTopY() + 20f
        for (line in lines) {
            canvas.drawText(line.text, FiscalPdfLayout.leftX(), y, if (line.value != null) cellPaint else subtlePaint)
            line.value?.let { canvas.drawText(it, FiscalPdfLayout.rightX(), y, cellPaintRight) }
            y += line.height
        }
    }

    private fun wrapLines(text: String, width: Float, paint: Paint): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        for (word in text.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > width && line.isNotEmpty()) { lines.add(line); line = word }
            else line = candidate
        }
        if (line.isNotEmpty()) lines.add(line)
        return lines
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

    private val ink = Color.rgb(0x11, 0x11, 0x10)
    private val subtleInk = Color.rgb(0x5C, 0x5B, 0x57)
    private val accent = Color.rgb(0x18, 0x7A, 0x36)

    private val wordmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent; textSize = 20f; typeface = fonts.strong
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink; textSize = 14f; typeface = fonts.strong
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 12f; typeface = fonts.body }
    private val subtlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = subtleInk; textSize = 10f; typeface = fonts.body }
    private val headerCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink; textSize = 10f; typeface = fonts.strong
    }
    private val headerCellPaintRight = Paint(headerCellPaint).apply { textAlign = Paint.Align.RIGHT }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 11f; typeface = fonts.body }
    private val cellPaintRight = Paint(cellPaint).apply { textAlign = Paint.Align.RIGHT; typeface = fonts.number; fontFeatureSettings = "tnum, lnum" }
    private val totalLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink; textSize = 11f; typeface = fonts.strong
    }
    private val totalCellPaint = Paint(totalLabelPaint).apply { textAlign = Paint.Align.RIGHT; typeface = fonts.number; fontFeatureSettings = "tnum, lnum" }
    private val footnotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = subtleInk; textSize = 8f; typeface = fonts.body }
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xE8, 0xE7, 0xE3); strokeWidth = 0.7f
    }
}
