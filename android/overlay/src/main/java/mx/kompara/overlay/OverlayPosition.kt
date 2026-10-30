package mx.kompara.overlay

import mx.kompara.parsers.model.HostGeometry
import mx.kompara.parsers.model.HostGeometrySource
import mx.kompara.parsers.model.HostOrientation
import mx.kompara.parsers.model.HostRect
import kotlin.math.abs
import kotlin.math.roundToInt

/** Screen pixels throughout; conversion to WindowManager's origin happens only at the boundary. */
data class OverlayPosition(val x: Int, val y: Int) {
    fun bounds(size: ChipSize) = HostRect(x, y, x + size.width, y + size.height)
    fun inWindow(origin: OverlayPosition) = OverlayPosition(x - origin.x, y - origin.y)
}
data class ChipSize(val width: Int, val height: Int)
data class OverlayInsets(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0)
data class OverlayViewport(
    val bounds: HostRect,
    val density: Float,
    val orientation: HostOrientation,
    val systemInsets: OverlayInsets = OverlayInsets(),
    val backInsets: OverlayInsets = OverlayInsets(),
) {
    fun dp(value: Int) = (value * density).roundToInt()
    /** The plate itself stays outside Back's touch strip; no gesture exclusion request is needed. */
    val safeBounds get() = HostRect(bounds.left + maxOf(systemInsets.left, backInsets.left),
        bounds.top + systemInsets.top, bounds.right - maxOf(systemInsets.right, backInsets.right),
        bounds.bottom - systemInsets.bottom)
}
enum class SnapEdge { LEFT, RIGHT }
enum class PlateFormat { FULL, COMPACT }
sealed interface OverlayPlacement {
    data class Displayed(val position: OverlayPosition, val size: ChipSize, val format: PlateFormat,
        val overMarker: Boolean = false) : OverlayPlacement
    data class Suppressed(val cause: String) : OverlayPlacement
    data class Unsupported(val cause: String) : OverlayPlacement
}

/** Frame-derived card bounds, with external dp margins that never scale with the font. */
object OverlayPositioning {
    // takeScreenshot can need a 1s rate-limit retry after its 700ms throttle plus OCR work.
    // Still bounded if capture stalls; host/viewport changes and NoCard hide independently.
    const val MAX_GEOMETRY_AGE_MS = 2_500L

    /** Refresh capture age/source without briefly hiding an unchanged, already settled window. */
    fun sameLayout(previous: HostGeometry?, next: HostGeometry): Boolean =
        previous?.copy(capturedAtMs = next.capturedAtMs, layoutId = next.layoutId, source = next.source,
            appearance = next.appearance) == next

    /** A chip mask is missing evidence, never evidence that the host's upper band is empty. */
    fun lostMaskedAnchors(previous: HostGeometry?, next: HostGeometry, viewport: OverlayViewport): Boolean {
        if (next.source == HostGeometrySource.HOST_LAYOUT) return false
        val after = next.ocrEvidence ?: return false
        val mask = after.overlayMask ?: return false
        val priorGeometry = previous?.takeIf {
            it.packageName == next.packageName && it.displayBounds == next.displayBounds
        }
        val tolerance = viewport.dp(8)
        // Controls can move independently from the route and heading. A formerly observed close
        // disappearing under OCR cannot be explained away by stationary route anchors. Current
        // accessibility controls stay in this list even beneath the mask, so positive node evidence
        // still validates them and ordinary placement can avoid their actual bounds.
        if (priorGeometry?.protectedControls?.any { old -> next.protectedControls.none { fresh ->
            abs(old.left - fresh.left) <= tolerance && abs(old.top - fresh.top) <= tolerance
        } } == true) return true
        if (after.independentlyMeasuredCard) return false
        val before = priorGeometry?.ocrEvidence ?: return after.upperAnchors.isEmpty()
        val visibleUpper = after.upperAnchors.filterNot { it.overlaps(mask) }
        val visibleRoutes = after.routeAnchors.filterNot { it.overlaps(mask) }
        if (before.upperAnchors.isEmpty()) {
            // An unobscured card may have no recognized heading at all. Stable route anchors and
            // a mask wholly above that observed envelope keep recovery from cycling every frame.
            val stableRoutes = before.routeAnchors.isNotEmpty() &&
                before.routeAnchors.size == visibleRoutes.size &&
                before.routeAnchors.zip(visibleRoutes).all { (old, fresh) ->
                    abs(old.left - fresh.left) <= viewport.dp(8) &&
                        abs(old.top - fresh.top) <= viewport.dp(8)
                }
            return visibleUpper.isEmpty() && !(stableRoutes && mask.bottom <= previous!!.offerCard.top)
        }
        // An unchanged visible header/amount can establish the upper boundary without route text.
        if (before.upperAnchors.all { old -> visibleUpper.any { fresh ->
            abs(old.left - fresh.left) <= tolerance && abs(old.top - fresh.top) <= tolerance
        } }) return false
        // Do not guess which leg disappeared or pair pickup with trip. A malformed line, dropout,
        // or non-rigid movement requires a capture with this region exposed.
        if (before.routeAnchors.isEmpty() || before.routeAnchors.size != visibleRoutes.size) return true
        val dx = visibleRoutes.first().left - before.routeAnchors.first().left
        val dy = visibleRoutes.first().top - before.routeAnchors.first().top
        if (before.routeAnchors.zip(visibleRoutes).any { (old, fresh) ->
            abs(fresh.left - old.left - dx) > tolerance || abs(fresh.top - old.top - dy) > tolerance
        }) return true
        return before.upperAnchors.any { old ->
            val displaced = HostRect(old.left + dx, old.top + dy, old.right + dx, old.bottom + dy)
            displaced.overlaps(mask) && visibleUpper.none { it.overlaps(displaced.expanded(tolerance)) }
        }
    }

    fun geometryProblem(g: HostGeometry?, host: String, viewport: OverlayViewport, nowMs: Long): String? = when {
        g == null -> "UNVALIDATED_GEOMETRY"
        g.packageName != host -> "HOST_CHANGED"
        g.displayBounds != viewport.bounds || g.orientation != viewport.orientation -> "DISPLAY_CHANGED"
        nowMs - g.capturedAtMs !in 0..MAX_GEOMETRY_AGE_MS -> "STALE_GEOMETRY"
        !g.displayBounds.contains(g.offerCard) || !g.displayBounds.contains(g.topChrome) ||
            (g.protectedControls + g.markers.map { it.outerBounds }).any { !g.displayBounds.contains(it) } -> "INCOMPLETE_GEOMETRY"
        else -> null
    }

    fun place(g: HostGeometry?, host: String, viewport: OverlayViewport, nowMs: Long,
        full: ChipSize, compact: ChipSize, saved: OverlayPosition? = null): OverlayPlacement {
        geometryProblem(g, host, viewport, nowMs)?.let { return OverlayPlacement.Unsupported(it) }
        g!!
        for ((format, size) in listOf(PlateFormat.FULL to full, PlateFormat.COMPACT to compact)) {
            if (saved != null && valid(saved, size, g, viewport))
                return OverlayPlacement.Displayed(saved, size, format)
            candidates(size, g, viewport, includeMarkers = true).firstOrNull()?.let {
                return OverlayPlacement.Displayed(it, size, format)
            }
        }
        // Controls always win. Only markers may be covered, and only by compact at the foot.
        footSlots(compact, g, viewport).firstOrNull { valid(it, compact, g, viewport, ignoreMarkers = true) }?.let {
            return OverlayPlacement.Displayed(it, compact, PlateFormat.COMPACT, overMarker = true)
        }
        // A control-blocked foot is not permission for a marker alone to suppress the plate.
        candidates(compact, g, viewport, includeMarkers = false).firstOrNull()?.let {
            return OverlayPlacement.Displayed(it, compact, PlateFormat.COMPACT, overMarker = true)
        }
        return OverlayPlacement.Suppressed(if (g.navigation) "NAVIGATION" else "NO_SAFE_SPACE")
    }

    fun valid(p: OverlayPosition, size: ChipSize, g: HostGeometry, v: OverlayViewport,
        ignoreMarkers: Boolean = false): Boolean {
        if (size.width <= 0 || size.height <= 0) return false
        val rect = p.bounds(size)
        val gap = v.dp(16)
        val safe = v.safeBounds
        if (rect.left < maxOf(safe.left, v.bounds.left + v.dp(12)) ||
            rect.right > minOf(safe.right, v.bounds.right - v.dp(12)) ||
            rect.top < maxOf(safe.top, g.topChrome.bottom) + gap ||
            rect.bottom > minOf(safe.bottom, g.offerCard.top) - gap) return false
        if (g.protectedControls.any { rect.overlaps(it.expanded(gap)) }) return false
        return ignoreMarkers || g.markers.none { rect.overlaps(it.outerBounds.expanded(gap)) }
    }

    private fun footSlots(size: ChipSize, g: HostGeometry, v: OverlayViewport): List<OverlayPosition> {
        val y = minOf(g.offerCard.top, v.safeBounds.bottom) - v.dp(16) - size.height
        return listOf(OverlayPosition(minOf(v.bounds.right - v.dp(12), v.safeBounds.right) - size.width, y),
            OverlayPosition(maxOf(v.bounds.left + v.dp(12), v.safeBounds.left), y))
    }

    private fun candidates(size: ChipSize, g: HostGeometry, v: OverlayViewport, includeMarkers: Boolean): List<OverlayPosition> {
        val foot = footSlots(size, g, v)
        val obstacles = g.protectedControls + if (includeMarkers) g.markers.map { it.outerBounds } else emptyList()
        // The nearest higher clear position has its bottom at an exclusion's upper edge.
        val higher = foot.flatMap { slot ->
            obstacles.map { OverlayPosition(slot.x, it.top - v.dp(16) - size.height) }
                .filter { it.y < slot.y }.distinct().sortedByDescending { it.y }
        }
        return (foot + higher).filter { valid(it, size, g, v, ignoreMarkers = !includeMarkers) }
    }

    fun edgeFor(p: OverlayPosition, v: OverlayViewport, size: ChipSize) =
        if (p.x + size.width / 2 < v.bounds.left + v.bounds.width / 2) SnapEdge.LEFT else SnapEdge.RIGHT

    /** Reject an unsafe drag rather than teleporting across an exclusion. */
    fun drag(current: OverlayPlacement.Displayed, desired: OverlayPosition, g: HostGeometry, v: OverlayViewport): OverlayPosition =
        desired.takeIf { valid(it, current.size, g, v, current.overMarker) } ?: current.position

    fun snapToEdge(current: OverlayPlacement.Displayed, g: HostGeometry, v: OverlayViewport): OverlayPosition {
        val foot = footSlots(current.size, g, v)
        val x = if (edgeFor(current.position, v, current.size) == SnapEdge.RIGHT) foot[0].x else foot[1].x
        return OverlayPosition(x, current.position.y).takeIf {
            valid(it, current.size, g, v, current.overMarker)
        } ?: current.position
    }
}

/**
 * Retains the last unobscured upper anchors across partial OCR frames. Without a measured card
 * edge, an unknown masked header may extend above the mask itself. No band is then proven safe:
 * suppress the plate for one unobscured capture instead of placing it on an inferred empty area.
 */
internal class PlacementEvidence {
    private var reference: HostGeometry? = null

    data class Decision(val geometry: HostGeometry, val recover: Boolean)

    fun observe(next: HostGeometry, viewport: OverlayViewport, previous: HostGeometry? = null): Decision {
        val before = (reference ?: previous)?.takeIf {
            it.packageName == next.packageName && it.displayBounds == next.displayBounds
        }
        val recover = OverlayPositioning.lostMaskedAnchors(before, next, viewport)
        if (!recover) {
            val after = next.ocrEvidence
            val old = before?.ocrEvidence
            // A missing upper anchor is retained even if steady legs currently establish safety.
            // It may be the only evidence that exposes a later movement beneath the mask.
            if (next.source == HostGeometrySource.HOST_LAYOUT || after == null ||
                after.independentlyMeasuredCard || after.overlayMask == null ||
                old == null || after.upperAnchors.size >= old.upperAnchors.size) reference = next
            else reference = before
            return Decision(next, false)
        }
        reference = before
        return Decision(next.copy(offerCard = next.offerCard.copy(top = next.topChrome.bottom)), true)
    }

    fun reset() { reference = null }
}
