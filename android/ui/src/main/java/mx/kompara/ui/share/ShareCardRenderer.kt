package mx.kompara.ui.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Renders a [ShareCardData] to a deterministic offscreen [Bitmap] (B-055) with the Android [Canvas]
 * drawing API directly — no Compose-to-bitmap, which is fragile to host-window/measurement state.
 * The brand look mirrors [mx.kompara.ui.theme] (dark surface + verde accents), with the net number as
 * the dominant element, a percentile-flex pill, the streak, and a "Kompara · Descárgala gratis"
 * footer CTA.
 *
 * All positioning is the pure [ShareCardLayout] math fed real `Paint.measureText` widths, so the
 * geometry is unit-tested without a device and the bitmap itself only needs a smoke test (renders,
 * right dimensions).
 */
object ShareCardRenderer {

    // Brand palette as ARGB ints (KomparaTheme: SurfaceDark, BrandGreen/Dark, OnSurfaceDark, etc.).
    private const val BG_TOP = 0xFF121417.toInt() // SurfaceDark
    private const val BG_BOTTOM = 0xFF06351C.toInt() // BrandGreenContainerDark (verde accent)
    private const val BRAND_GREEN = 0xFF12A150.toInt() // BrandGreen
    private const val ON_SURFACE = 0xFFF2F3F5.toInt() // OnSurfaceDark
    private const val ON_SURFACE_VARIANT = 0xFFBFC4CA.toInt() // OnSurfaceVariantDark
    private const val ON_BRAND = 0xFFFFFFFF.toInt() // OnVerdictGreen

    /**
     * Render [data] to a fresh [Bitmap] for [variant]. The bitmap is `ARGB_8888` at the variant's
     * exact pixel size. Deterministic: same input ⇒ same pixels (no time, no randomness).
     */
    fun render(data: ShareCardData, variant: ShareCardVariant): Bitmap {
        val bitmap = Bitmap.createBitmap(variant.width, variant.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas, data, variant)
        return bitmap
    }

    /** Draw [data] onto [canvas] for [variant]. Split out so a test can target a stub canvas. */
    fun draw(canvas: Canvas, data: ShareCardData, variant: ShareCardVariant) {
        val w = variant.width
        val h = variant.height
        // Scale the STORY-tuned reference sizes down for the shorter LANDSCAPE card.
        val scale = if (variant == ShareCardVariant.STORY) 1f else h / ShareCardVariant.STORY.height.toFloat()

        drawBackground(canvas, w, h)

        val sideMargin = ShareCardLayout.sideMargin(w)
        val contentWidth = ShareCardLayout.contentWidth(w)
        val gap = ShareCardLayout.blockGap(h)
        var cursorTop = h * if (variant == ShareCardVariant.STORY) 0.16f else 0.12f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ON_SURFACE }

        // Period label (e.g. "Semana del 1–7 jun").
        cursorTop = drawLine(
            canvas, data.periodLabel, sideMargin, cursorTop,
            ShareCardLayout.RefSizes.PERIOD_LABEL * scale, ON_SURFACE_VARIANT, Typeface.NORMAL, textPaint,
        ) + gap

        // Headline ("Mi semana manejando" / "Mi mes manejando").
        val headline = when (data.periodKind) {
            SharePeriodKind.WEEK -> "Mi semana manejando"
            SharePeriodKind.MONTH -> "Mi mes manejando"
        }
        cursorTop = drawLine(
            canvas, headline, sideMargin, cursorTop,
            ShareCardLayout.RefSizes.HEADLINE * scale, ON_SURFACE, Typeface.BOLD, textPaint,
        ) + gap * 1.5f

        // Net number — the dominant element, auto-fit to the content width. Redacted when hidden.
        cursorTop = if (data.netEarnings != null) {
            drawNetNumber(canvas, data.netEarnings, sideMargin, contentWidth, cursorTop, scale, textPaint) + gap
        } else {
            drawLine(
                canvas, "Ganancias ocultas", sideMargin, cursorTop,
                ShareCardLayout.RefSizes.SECONDARY * scale, ON_SURFACE_VARIANT, Typeface.ITALIC, textPaint,
            ) + gap
        }

        // Secondary metrics: trips · hours.
        val secondary = listOfNotNull(data.trips, data.hours).joinToString("  ·  ")
        if (secondary.isNotEmpty()) {
            cursorTop = drawLine(
                canvas, secondary, sideMargin, cursorTop,
                ShareCardLayout.RefSizes.SECONDARY * scale, ON_SURFACE, Typeface.NORMAL, textPaint,
            ) + gap * 1.5f
        }

        // Percentile flex pill ("Top 22% en CDMX 🚀").
        if (data.percentileFlex != null) {
            cursorTop = drawFlexPill(canvas, data.percentileFlex, sideMargin, cursorTop, scale, textPaint) + gap
        }

        // Streak ("🔥 4 semanas seguidas").
        if (data.streakLine != null) {
            cursorTop = drawLine(
                canvas, data.streakLine, sideMargin, cursorTop,
                ShareCardLayout.RefSizes.STREAK * scale, ON_SURFACE, Typeface.BOLD, textPaint,
            ) + gap
        }

        drawFooter(canvas, w, h, scale, textPaint)
    }

    private fun drawBackground(canvas: Canvas, w: Int, h: Int) {
        val bg = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
    }

    /** Draw a single left-aligned line at [top]; returns the bottom y of the line. */
    private fun drawLine(
        canvas: Canvas,
        text: String,
        x: Float,
        top: Float,
        size: Float,
        color: Int,
        style: Int,
        paint: Paint,
    ): Float {
        paint.textSize = size
        paint.color = color
        paint.typeface = Typeface.create(Typeface.DEFAULT, style)
        val baseline = top - paint.fontMetrics.ascent
        canvas.drawText(text, x, baseline, paint)
        return baseline + paint.fontMetrics.descent
    }

    private fun drawNetNumber(
        canvas: Canvas,
        text: String,
        x: Float,
        maxWidth: Float,
        top: Float,
        scale: Float,
        paint: Paint,
    ): Float {
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val ref = ShareCardLayout.RefSizes.NET_NUMBER * scale
        paint.textSize = ref
        val measured = paint.measureText(text)
        val size = ShareCardLayout.fitFontSize(
            widthAtRefSize = measured,
            refSize = ref,
            maxWidth = maxWidth,
            maxSize = ref,
            minSize = 48f * scale,
        )
        paint.textSize = size
        paint.color = BRAND_GREEN
        val baseline = top - paint.fontMetrics.ascent
        canvas.drawText(text, x, baseline, paint)
        return baseline + paint.fontMetrics.descent
    }

    private fun drawFlexPill(
        canvas: Canvas,
        text: String,
        x: Float,
        top: Float,
        scale: Float,
        paint: Paint,
    ): Float {
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = ShareCardLayout.RefSizes.FLEX * scale
        val textWidth = paint.measureText(text)
        val padH = 36f * scale
        val padV = 24f * scale
        val fm = paint.fontMetrics
        val pillHeight = (fm.descent - fm.ascent) + 2 * padV
        val pill = RectF(x, top, x + textWidth + 2 * padH, top + pillHeight)
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BRAND_GREEN }
        val radius = pillHeight / 2f
        canvas.drawRoundRect(pill, radius, radius, pillPaint)
        paint.color = ON_BRAND
        val baseline = top + padV - fm.ascent
        canvas.drawText(text, x + padH, baseline, paint)
        return top + pillHeight
    }

    private fun drawFooter(canvas: Canvas, w: Int, h: Int, scale: Float, paint: Paint) {
        val sideMargin = ShareCardLayout.sideMargin(w)
        // Wordmark.
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = ShareCardLayout.RefSizes.WORDMARK * scale
        paint.color = ON_SURFACE
        val wordmarkBaseline = h - h * 0.10f
        canvas.drawText("Kompara", sideMargin, wordmarkBaseline, paint)
        // CTA under the wordmark.
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = ShareCardLayout.RefSizes.CTA * scale
        paint.color = BRAND_GREEN
        val ctaBaseline = wordmarkBaseline + (ShareCardLayout.RefSizes.CTA * scale) + (12f * scale)
        canvas.drawText("Descárgala gratis · kompara.mx", sideMargin, ctaBaseline, paint)
    }
}
