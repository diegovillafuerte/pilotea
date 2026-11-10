package mx.kompara.ui.share

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import dagger.hilt.android.qualifiers.ApplicationContext
import mx.kompara.ui.export.ExportFonts
import javax.inject.Inject

/** This bitmap is displayed as an Image and encoded unchanged on Share. No second Compose card. */
class ShareCardRenderer @Inject constructor(@ApplicationContext context: Context) {
    private val fonts = ExportFonts(context)
    private val ink = Color.rgb(0x11, 0x11, 0x10)
    private val secondary = Color.rgb(0x5c, 0x5b, 0x57)
    private val tertiary = Color.rgb(0x8a, 0x89, 0x84)
    private fun paint(size: Float, face: Typeface = fonts.body, color: Int = ink) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; typeface = face; this.color = color; fontFeatureSettings = "tnum, lnum"
    }
    private fun paragraph(text: String, width: Float, size: Float, line: Float, face: Typeface = fonts.body, color: Int = ink): StaticLayout {
        val p = paint(size, face, color)
        // Simple line breaking is StaticLayout.Builder's default; Layout.BREAK_STRATEGY_SIMPLE is not the
        // LineBreaker constant setBreakStrategy declares (lint WrongConstant), and LineBreaker needs API 29.
        return StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt())
            .setIncludePad(false)
            .setLineSpacing(line - (p.fontMetrics.descent - p.fontMetrics.ascent), 1f).build()
    }
    fun render(data: ShareCardData): Bitmap {
        // Long dates/copy can enlarge the layout, preserving the artefact's 4:5 ratio.
        var width = 353f
        while (!draw(null, data, width)) width += 32f
        return Bitmap.createBitmap(ShareCardLayout.WIDTH, ShareCardLayout.HEIGHT, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            canvas.scale(it.width / width, it.height / ShareCardLayout.height(width))
            check(draw(canvas, data, width))
        }
    }
    /** Measure and draw through the exact same path; facts stay clear of all flowing content. */
    private fun draw(canvas: Canvas?, data: ShareCardData, width: Float): Boolean {
        val height = ShareCardLayout.height(width)
        val left = ShareCardLayout.INSET
        val content = width - left * 2
        canvas?.drawRoundRect(0f, 0f, width, height, ShareCardLayout.RADIUS, ShareCardLayout.RADIUS, paint(1f, color = Color.WHITE))
        canvas?.drawRoundRect(.5f, .5f, width - .5f, height - .5f, ShareCardLayout.RADIUS, ShareCardLayout.RADIUS,
            paint(1f, color = Color.rgb(0xe8, 0xe7, 0xe3)).apply { style = Paint.Style.STROKE; strokeWidth = 1f })
        var y = left
        fun text(value: String, size: Float, line: Float, face: Typeface = fonts.body, color: Int = ink, available: Float = content): Float {
            val layout = paragraph(value, available, size, line, face, color)
            canvas?.save(); canvas?.translate(left, y); canvas?.let(layout::draw); canvas?.restore()
            y += layout.height
            return layout.height.toFloat()
        }
        val k = paint(18f, fonts.headline, Color.rgb(0x18, 0x7a, 0x36))
        val header = paint(13f, fonts.strong)
        val small = paint(13f, color = secondary)
        val site = "kompara.mx"
        val baseline = y - k.fontMetrics.ascent
        canvas?.drawText("K", left, baseline, k)
        val start = left + k.measureText("K") + 6f
        val headerWidth = content - (start - left) - small.measureText(site) - 8f
        canvas?.drawText("Kompara", start, baseline, header)
        val period = " · ${data.periodLabel}"
        val fits = header.measureText("Kompara") + small.measureText(period) <= headerWidth
        var headerHeight = 22f
        if (fits) canvas?.drawText(period, start + header.measureText("Kompara"), baseline, small)
        else {
            val heading = paragraph(data.periodLabel, headerWidth, 13f, 18f, color = secondary)
            canvas?.save(); canvas?.translate(start, y + 22f); canvas?.let(heading::draw); canvas?.restore()
            headerHeight += heading.height
        }
        canvas?.drawText(site, width - left - small.measureText(site), baseline, small)
        y += headerHeight
        y += if (data.hideAmounts) 84f else 12f
        text(data.finding, 24f, 30f, fonts.headline)
        y += if (data.hideAmounts) 16f else 8f
        text(data.source, 16f, 22f, color = secondary)
        if (data.netEarnings != null) {
            y += 16f; text(requireNotNull(data.netLabel), 13f, 18f, color = secondary)
            y += 4f; text(data.netEarnings, 32f, 36f, fonts.number)
        }
        for (bar in data.bars) {
            y += 12f
            val p = paint(17f, fonts.number, if (bar.own) ink else secondary)
            val base = y - p.fontMetrics.ascent
            canvas?.drawText(bar.label, left, base, p)
            canvas?.drawText(bar.amount, width - left - p.measureText(bar.amount), base, p)
            y += 26f
            canvas?.drawRect(left, y, left + content * bar.fraction, y + 16f, paint(1f, color = if (bar.own) ink else tertiary))
            y += 16f
        }
        if (data.basis.isNotEmpty()) { y += if (data.hideAmounts) 16f else 8f; text(data.basis, 13f, 18f, color = secondary) }
        val rows = 1 + (if (data.hours != null) 1 else 0) + (if (data.confirmed) 1 else 0)
        val foot = ShareCardLayout.factTop(height, rows)
        if (y + 20f > foot) return false
        y = foot
        fun fact(label: String, value: String) {
            val p = paint(16f); val v = paint(16f, fonts.strong)
            val baselineY = y - p.fontMetrics.ascent
            canvas?.drawText(label, left, baselineY, p)
            canvas?.drawText(value, width - left - v.measureText(value), baselineY, v)
            y += 32f
        }
        fact("Viajes", data.trips)
        data.hours?.let { fact(requireNotNull(data.hoursLabel), it) }
        if (data.confirmed) {
            val p = paint(16f, fonts.strong)
            val label = "Semana confirmada con mi resumen"
            if (p.measureText(label) > content - 22f) return false
            val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; strokeWidth = 1.8f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
            canvas?.drawPath(Path().apply { moveTo(left + 2f, y + 11f); lineTo(left + 6f, y + 15f); lineTo(left + 14f, y + 6f) }, mark)
            canvas?.drawText(label, left + 22f, y - p.fontMetrics.ascent, p)
        }
        return true
    }
}
