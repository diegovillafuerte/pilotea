package mx.kompara.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mx.kompara.parsers.model.HostGeometry
import mx.kompara.parsers.model.HostGeometryAdapter
import mx.kompara.parsers.model.HostGeometryDeriver
import mx.kompara.parsers.model.HostGeometrySource
import mx.kompara.parsers.model.HostRect
import mx.kompara.parsers.model.HostTextBounds
import mx.kompara.parsers.model.MapMarker
import mx.kompara.parsers.model.MapMarkerKind
import mx.kompara.parsers.model.OfferContentBounds

/** Measured display information from the accessibility service's window manager. */
data class HostDisplayFrame(val bounds: HostRect, val density: Float, val topChrome: HostRect)

/** One in-memory node observation shared with both OCR lanes and the stopped-reader tab. */
data class HostWindowEvidence(
    val packageName: String,
    val capturedAtMs: Long,
    val displayBounds: HostRect,
    val density: Float,
    val topChrome: HostRect,
    val textBounds: List<HostTextBounds>,
    val observedCardBounds: HostRect? = null,
    val protectedControls: List<HostRect> = emptyList(),
    val markers: List<MapMarker> = emptyList(),
    val navigation: Boolean = false,
    /** OS window provenance; package/bounds alone cannot identify overlapping same-host windows. */
    val windowId: Int? = null,
) {
    fun geometryForOffer(contentBounds: OfferContentBounds? = null, atMs: Long = capturedAtMs,
        recognizedOffer: Boolean = false): HostGeometry? =
        HostGeometryDeriver.offer(packageName, atMs, displayBounds, density, textBounds,
            HostGeometrySource.ACCESSIBILITY_BOUNDS, contentBounds, topChrome, observedCardBounds,
            protectedControls, markers, navigation, recognizedOffer)

    /** If a stopped reader can still observe a card, keep the tab clear of that actual card. */
    fun idleGeometry(): HostGeometry? = geometryForOffer() ?: HostGeometryDeriver.idleHost(
        packageName, capturedAtMs, displayBounds, topChrome, protectedControls, markers, navigation)
}

/** Geometry only; no persistence, upload, host action, or independent capture owner. */
object HostGeometryEvidence {
    private val latest = MutableStateFlow<HostWindowEvidence?>(null)
    val state: StateFlow<HostWindowEvidence?> = latest.asStateFlow()

    fun publish(evidence: HostWindowEvidence?) { latest.value = evidence }

    fun current(packageName: String, nowMs: Long, displayBounds: HostRect? = null): HostWindowEvidence? =
        latest.value?.takeIf {
            it.packageName == packageName && nowMs - it.capturedAtMs in 0..1_000 &&
                (displayBounds == null || it.displayBounds == displayBounds)
        }
}

/** Preserve observed node bounds, including named map markers; unknown icons are not invented. */
fun ScreenSnapshot.hostEvidence(display: HostDisplayFrame, windowId: Int? = null): HostWindowEvidence? {
    if (packageName !in HostGeometryAdapter.SUPPORTED_HOSTS || !display.bounds.valid ||
        !display.bounds.contains(display.topChrome)) return null
    val visible = nodes.filter { display.bounds.contains(it.bounds.toHostRect()) }
    val geometryNodes = nodes.mapNotNull { node ->
        node.bounds.toHostRect().intersection(display.bounds)?.let { clipped ->
            node.copy(bounds = android.graphics.Rect(clipped.left, clipped.top, clipped.right, clipped.bottom))
        }
    }
    val text = visible.mapNotNull { node ->
        val label = node.text.orEmpty()
        if (label.isBlank() && node.viewId == null) null
        else HostTextBounds(label, node.bounds.toHostRect(), node.viewId)
    }
    val markers = geometryNodes.mapNotNull { node ->
        val id = node.viewId.orEmpty().substringAfterLast('/').lowercase()
        val label = node.text.orEmpty().trim().lowercase()
        val kind = when {
            id in setOf("pickup_marker", "pickup_pin") || label in setOf("punto de recogida", "marcador de recogida") -> MapMarkerKind.PICKUP
            id in setOf("destination_marker", "destination_pin") || label == "marcador de destino" -> MapMarkerKind.DESTINATION
            id in setOf("driver_marker", "driver_location") || label in setOf("tu ubicación", "tu ubicacion") -> MapMarkerKind.DRIVER
            id in setOf("next_manoeuvre", "next_maneuver", "next_turn_marker") || label in setOf("próxima maniobra", "proxima maniobra") -> MapMarkerKind.NEXT_MANOEUVRE
            else -> null
        }
        kind?.let { MapMarker(it, node.bounds.toHostRect()) }
    }
    val controls = geometryNodes.filter { node ->
        node.className?.endsWith("Button") == true ||
            node.text?.trim()?.lowercase() in setOf("aceptar", "rechazar", "en línea", "en linea", "conectarse", "desconectarse", "menú", "menu", "buscar") ||
            node.viewId.orEmpty().substringAfterLast('/').lowercase() in setOf("navigation_banner", "toolbar", "bottom_navigation")
    }.map { it.bounds.toHostRect() }.distinct()
    val bottomNavigation = geometryNodes.filter {
        it.viewId.orEmpty().substringAfterLast('/').lowercase() == "bottom_navigation"
    }.map { it.bounds.toHostRect() }
    val chromeLabels = setOf("en línea", "en linea", "menú", "menu", "buscar", "buscando viajes")
    val chromeControls = geometryNodes.filter { node ->
        val id = node.viewId.orEmpty().substringAfterLast('/').lowercase()
        val bounds = node.bounds.toHostRect()
        id != "bottom_navigation" && bottomNavigation.none { it.contains(bounds) } &&
            (id in setOf("toolbar", "navigation_banner") || node.text?.trim()?.lowercase() in chromeLabels ||
                bounds in controls && bounds.top <= display.topChrome.bottom)
    }.map { it.bounds.toHostRect() }
    val topChrome = display.topChrome.copy(bottom = maxOf(display.topChrome.bottom,
        chromeControls.maxOfOrNull { it.bottom } ?: display.topChrome.bottom))
    // Use actual card containers only when their resource identity names the offer; never mistake
    // the full-screen Compose root for a card just because it contains offer text.
    val card = visible.filter {
        it.viewId.orEmpty().substringAfterLast('/').lowercase() in setOf("offer_card", "request_card", "trip_offer_card") &&
            it.bounds.height() < display.bounds.height
    }.minByOrNull { it.bounds.width().toLong() * it.bounds.height() }?.bounds?.toHostRect()
    return HostWindowEvidence(packageName, timestampMs, display.bounds, display.density, topChrome,
        text, card, controls, markers, markers.any { it.kind == MapMarkerKind.NEXT_MANOEUVRE } ||
            geometryNodes.any { it.viewId.orEmpty().substringAfterLast('/') == "navigation_banner" }, windowId)
}

private fun android.graphics.Rect.toHostRect() = HostRect(left, top, right, bottom)
