package mx.kompara.overlay

import mx.kompara.ui.components.ReglaBand
import mx.kompara.ui.components.ReglaModel
import kotlin.math.roundToInt

/**
 * v4 (WP-B): the chip as [MeasuredVerdictChip] measures it, from ReglaTest's measured dp widths at
 * font scales 1, 1.3 and 2 (the plain pill "$9.49", the chip with both counter-offers, with one,
 * and the unread pill); heights are 56 and 90 dp × font scale. The placer's larger size is the chip
 * with its tray, the smaller the plain pill; an offer without counter-offers measures the plain pill twice.
 */
internal object S25ChipSizes {
    private val widthsDp = mapOf(1f to Widths(205, 207, 205, 158), 1.3f to Widths(243, 254, 243, 190), 2f to Widths(337, 385, 337, 283))
    private data class Widths(val pill: Int, val trayBoth: Int, val trayOne: Int, val unread: Int)

    /** (larger, smaller) in px for [regla] at [fontScale] on a [density] display (the S25: 3). */
    fun of(regla: ReglaModel, fontScale: Float = 1f, density: Float = 3f): Pair<ChipSize, ChipSize> {
        val w = widthsDp.getValue(fontScale)
        fun px(dp: Float) = (dp * density).roundToInt()
        val short = px(56 * fontScale)
        val tall = px(90 * fontScale)
        val plain = ChipSize(px((if (regla.band is ReglaBand.Unavailable) w.unread else w.pill).toFloat()), short)
        val full = when {
            regla.trayMinimum != null && regla.trayGoal != null -> ChipSize(px(w.trayBoth.toFloat()), tall)
            regla.hasTray -> ChipSize(px(w.trayOne.toFloat()), tall)
            else -> plain
        }
        return full to plain
    }

    /** The plain pill "$9.49" at [fontScale] on the S25: the smallest rated plate. */
    fun pill(fontScale: Float = 1f): ChipSize = ChipSize((widthsDp.getValue(fontScale).pill * 3f).roundToInt(), (56 * fontScale * 3f).roundToInt())
}
