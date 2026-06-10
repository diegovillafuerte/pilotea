package mx.kompara.ui.share

/**
 * The two share-card aspect variants (B-055): a 9:16 portrait for WhatsApp/Instagram **stories** and
 * a 1.91:1 **landscape** for link-preview / Facebook cards. The dimensions match the platforms'
 * recommended sizes so the card renders crisp at preview sizes.
 */
enum class ShareCardVariant(val width: Int, val height: Int) {
    /** 1080×1920 — vertical story. */
    STORY(1080, 1920),

    /** 1200×630 — horizontal link/social preview. */
    LANDSCAPE(1200, 630),
}

/**
 * Pure layout MATH for the share card (B-055) — text-fit and element-position calculators as plain
 * functions of the variant dimensions and the (already-measured) text widths. Kept Android-free so
 * the geometry is unit-tested on the JVM; [ShareCardRenderer] feeds it real `Paint.measureText`
 * widths and draws at the returned coordinates. No `android.graphics` import lives here.
 *
 * All lengths are in device pixels in the card's own coordinate space (origin top-left).
 */
object ShareCardLayout {

    /** Outer margin as a fraction of the card width — keeps content off the edges on every variant. */
    private const val SIDE_MARGIN_FRACTION = 0.083 // ~90px on a 1080-wide story.

    /** Vertical gap between stacked text blocks, as a fraction of the card height. */
    private const val BLOCK_GAP_FRACTION = 0.028

    /** Left (and symmetric right) margin in px for [width]. */
    fun sideMargin(width: Int): Float = width * SIDE_MARGIN_FRACTION.toFloat()

    /** The usable content width between the side margins. */
    fun contentWidth(width: Int): Float = width - 2 * sideMargin(width)

    /** The vertical gap between stacked blocks for [height]. */
    fun blockGap(height: Int): Float = height * BLOCK_GAP_FRACTION.toFloat()

    /** The x of a left-aligned block. */
    fun leftX(width: Int): Float = sideMargin(width)

    /** The x that horizontally centres a run of [textWidth] within the card. */
    fun centerX(width: Int, textWidth: Float): Float = (width - textWidth) / 2f

    /**
     * The largest font size (px) at which [text] fits within [maxWidth], given that the text measures
     * [widthAtRefSize] px at [refSize] px. Text width scales ~linearly with font size, so we scale the
     * reference size down by the overflow ratio. Clamped to [minSize]..[maxSize] so the big net number
     * never collapses to nothing nor blows past the design cap. Pure — the caller measures
     * [widthAtRefSize] with the real Paint.
     */
    fun fitFontSize(
        widthAtRefSize: Float,
        refSize: Float,
        maxWidth: Float,
        maxSize: Float,
        minSize: Float,
    ): Float {
        require(refSize > 0f) { "refSize must be positive" }
        if (widthAtRefSize <= 0f) return maxSize.coerceIn(minSize, maxSize)
        // Width grows linearly with size: widthAt(size) ≈ widthAtRefSize * size / refSize.
        // Largest size that still fits: maxWidth = widthAtRefSize * size / refSize.
        val fitting = maxWidth * refSize / widthAtRefSize
        return fitting.coerceIn(minSize, maxSize)
    }

    /**
     * The baseline y for a block of [lineHeights] stacked from [startY] downward, separated by
     * [gap]. Returns one baseline per line. A text baseline sits at `top + ascent`, so each entry is
     * `runningTop + lineHeight` (we treat lineHeight as the ascent-to-next-top advance). The list is
     * empty for no lines. Pure geometry the renderer turns into `drawText` y-coordinates.
     */
    fun stackedBaselines(startY: Float, lineHeights: List<Float>, gap: Float): List<Float> {
        val baselines = ArrayList<Float>(lineHeights.size)
        var top = startY
        for (h in lineHeights) {
            baselines.add(top + h)
            top += h + gap
        }
        return baselines
    }

    /**
     * The default reference font sizes (px) for each text block, sized for the STORY variant and
     * scaled by the renderer for LANDSCAPE. Exposed so tests can assert the hierarchy (the net number
     * is the biggest element on the card).
     */
    object RefSizes {
        const val PERIOD_LABEL = 44f
        const val HEADLINE = 56f
        const val NET_NUMBER = 180f
        const val SECONDARY = 52f
        const val FLEX = 64f
        const val STREAK = 48f
        const val WORDMARK = 60f
        const val CTA = 40f
    }
}
