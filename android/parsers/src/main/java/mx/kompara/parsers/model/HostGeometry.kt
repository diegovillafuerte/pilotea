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
    fun intersection(other: HostRect): HostRect? =
        if (!valid || !other.valid) null else HostRect(maxOf(left, other.left), maxOf(top, other.top),
            minOf(right, other.right), minOf(bottom, other.bottom)).takeIf { it.valid }
}

@Serializable
enum class HostOrientation { PORTRAIT, LANDSCAPE }

@Serializable
enum class MapMarkerKind { PICKUP, DESTINATION, DRIVER, NEXT_MANOEUVRE }

@Serializable
data class MapMarker(val kind: MapMarkerKind, val outerBounds: HostRect)

@Serializable
enum class HostAppearance { UNKNOWN, LIGHT, DARK }

@Serializable
enum class HostGeometrySource { HOST_LAYOUT, OCR_TEXT_BOUNDS, ACCESSIBILITY_BOUNDS, OCR_AND_ACCESSIBILITY, IDLE_HOST }

/** Bounds-only provenance: an OCR envelope cannot prove safety where our own chip hid anchors. */
@Serializable
data class HostOcrGeometryEvidence(
    val overlayMask: HostRect?,
    /** Heading/upper control evidence only. A fare cannot establish the empty region above it. */
    val upperAnchors: List<HostRect>,
    val routeAnchors: List<HostRect>,
    val independentlyMeasuredCard: Boolean,
)

/** Measured or conservatively derived bounds for ONE frame, in physical display pixels. */
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
    val source: HostGeometrySource = HostGeometrySource.HOST_LAYOUT,
    /** C6 uses the display foot when no offer is present; this boundary is not an offer card. */
    val isIdleHost: Boolean = false,
    /** Appearance of this captured host, never the Android or Kompara theme preference. */
    val appearance: HostAppearance = HostAppearance.UNKNOWN,
    val ocrEvidence: HostOcrGeometryEvidence? = null,
    /** The visible headline fare, when read. Its glyph height scales the header clearance above it. */
    val fareBounds: HostRect? = null,
    /** Host chrome that may be tappable (Trip Radar pill): foot/higher slots avoid it, the dock may cover it. */
    val softObstacles: List<HostRect> = emptyList(),
)

/**
 * Optional full-layout evidence. Completeness is diagnostic: known controls/markers are exclusions,
 * but missing icon semantics or a new host layout must not disable a measured safe band.
 */
data class HostLayoutObservation(
    val geometry: HostGeometry,
    val controlsComplete: Boolean,
    val markersComplete: Boolean,
)

class HostGeometryAdapter(@Suppress("UNUSED_PARAMETER") validatedLayouts: Set<Pair<String, String>> = emptySet()) {
    fun adapt(packageName: String, capturedAtMs: Long, observation: HostLayoutObservation?): HostGeometry? {
        val observed = observation ?: return null
        val g = observed.geometry
        if (packageName !in SUPPORTED_HOSTS || g.packageName != packageName ||
            g.capturedAtMs != capturedAtMs) return null
        if (!g.displayBounds.contains(g.offerCard) || !g.displayBounds.contains(g.topChrome) ||
            (g.protectedControls + g.markers.map { it.outerBounds }).any { !g.displayBounds.contains(it) }) return null
        return g
    }

    companion object {
        val SUPPORTED_HOSTS = setOf("com.ubercab.driver", "com.didiglobal.driver")
    }
}
