package mx.kompara.parsers.model

import kotlinx.serialization.Serializable

/** All bounds are physical display pixels, including marker strokes (not a soft map halo). */
@Serializable
data class HostRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    val valid get() = width > 0 && height > 0
    fun contains(other: HostRect) = other.valid && left <= other.left && top <= other.top &&
        right >= other.right && bottom >= other.bottom
    fun overlaps(other: HostRect) = left < other.right && right > other.left &&
        top < other.bottom && bottom > other.top
    fun expanded(px: Int) = HostRect(left - px, top - px, right + px, bottom + px)
}

@Serializable
enum class HostOrientation { PORTRAIT, LANDSCAPE }

@Serializable
enum class MapMarkerKind { PICKUP, DESTINATION, DRIVER, NEXT_MANOEUVRE }

@Serializable
data class MapMarker(val kind: MapMarkerKind, val outerBounds: HostRect)

/** Evidence for ONE frame. Fare/leg text bounds are deliberately not part of this contract. */
@Serializable
data class HostGeometry(
    val packageName: String,
    val layoutId: String,
    val capturedAtMs: Long,
    val displayBounds: HostRect,
    val orientation: HostOrientation,
    val topChrome: HostRect,
    val offerCard: HostRect,
    val protectedControls: List<HostRect>,
    val markers: List<MapMarker>,
    val navigation: Boolean = false,
)

/**
 * A replay-certified extractor must supply the full card, chrome, controls and a COMPLETE marker
 * inventory. null means not observed; empty means observed absent. Never infer an empty inventory
 * or card boundary from OCR text. No Uber/DiDi layout is certified in the current release (TD-048).
 */
data class HostLayoutObservation(
    val geometry: HostGeometry,
    val controlsComplete: Boolean,
    val markersComplete: Boolean,
)

class HostGeometryAdapter(private val validatedLayouts: Set<Pair<String, String>> = emptySet()) {
    fun adapt(packageName: String, capturedAtMs: Long, observation: HostLayoutObservation?): HostGeometry? {
        val observed = observation ?: return null
        val g = observed.geometry
        if (packageName !in SUPPORTED_HOSTS || g.packageName != packageName ||
            g.capturedAtMs != capturedAtMs || (packageName to g.layoutId) !in validatedLayouts ||
            !observed.controlsComplete || !observed.markersComplete) return null
        if (!g.displayBounds.contains(g.offerCard) || !g.displayBounds.contains(g.topChrome) ||
            g.topChrome.bottom > g.offerCard.top || g.protectedControls.isEmpty() ||
            (g.protectedControls + g.markers.map { it.outerBounds }).any { !g.displayBounds.contains(it) } ||
            (g.navigation && g.markers.none { it.kind == MapMarkerKind.NEXT_MANOEUVRE })) return null
        return g
    }

    companion object {
        val SUPPORTED_HOSTS = setOf("com.ubercab.driver", "com.didiglobal.driver")
    }
}
