package mx.kompara.ocr

import android.graphics.Bitmap
import android.graphics.Color
import mx.kompara.data.service.ScreenRect
import mx.kompara.parsers.model.HostAppearance
import mx.kompara.parsers.model.HostGeometry
import mx.kompara.parsers.model.HostRect

/** Ephemeral coarse luminance from the same screenshot as the offer; never persisted or uploaded. */
class HostAppearanceSamples private constructor(
    private val display: HostRect,
    private val capturedAtMs: Long,
    private val samples: List<Sample>,
) {
    private data class Sample(val x: Int, val y: Int, val luminance: Float)

    fun appearance(geometry: HostGeometry): HostAppearance {
        if (geometry.displayBounds != display || geometry.capturedAtMs != capturedAtMs) return HostAppearance.UNKNOWN
        // The map around the plate determines edge contrast. A white fare card or black turn banner
        // must not decide the host's treatment, nor may our own previously rendered plate.
        val map = HostRect(display.left, geometry.topChrome.bottom, display.right, geometry.offerCard.top)
        val exclusions = geometry.protectedControls + geometry.markers.map { it.outerBounds }
        val visible = samples.filter { sample ->
            sample.x in map.left until map.right && sample.y in map.top until map.bottom &&
                exclusions.none { sample.x in it.left until it.right && sample.y in it.top until it.bottom }
        }
        if (visible.size < 12) return HostAppearance.UNKNOWN
        return when {
            visible.count { it.luminance < .25f } >= visible.size * .65f -> HostAppearance.DARK
            visible.count { it.luminance > .60f } >= visible.size * .65f -> HostAppearance.LIGHT
            else -> HostAppearance.UNKNOWN
        }
    }

    companion object {
        fun capture(bitmap: Bitmap, capturedAtMs: Long, chip: ScreenRect?): HostAppearanceSamples {
            val samples = buildList {
                for (row in 0 until 24) for (column in 0 until 16) {
                    val x = ((column + .5f) * bitmap.width / 16).toInt().coerceAtMost(bitmap.width - 1)
                    val y = ((row + .5f) * bitmap.height / 24).toInt().coerceAtMost(bitmap.height - 1)
                    if (chip != null && x in chip.left until chip.right && y in chip.top until chip.bottom) continue
                    val color = bitmap.getPixel(x, y)
                    if (Color.alpha(color) < 250) continue
                    add(Sample(x, y, (.2126f * Color.red(color) + .7152f * Color.green(color) +
                        .0722f * Color.blue(color)) / 255f))
                }
            }
            return HostAppearanceSamples(HostRect(0, 0, bitmap.width, bitmap.height), capturedAtMs, samples)
        }
    }
}
