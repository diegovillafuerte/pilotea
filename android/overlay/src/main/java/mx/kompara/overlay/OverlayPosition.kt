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
/** Which tier validates a placement; DOCK is main's fail-open top slot (a kept/saved slot keeps its tier). */
enum class PlacementRule { FOOT, HIGHER, MARKER, DOCK }
sealed interface OverlayPlacement {
    data class Displayed(val position: OverlayPosition, val size: ChipSize, val format: PlateFormat,
        val overMarker: Boolean = false, val rule: PlacementRule = PlacementRule.FOOT) : OverlayPlacement {
        val docked get() = rule == PlacementRule.DOCK
    }
    data class Suppressed(val cause: String) : OverlayPlacement
    data class Unsupported(val cause: String) : OverlayPlacement
}

/**
 * Frame-derived card bounds, with external dp margins that never scale with the font.
 * Preferred: v3's foot slots under the card ceiling, then higher slots; fail-open: main's top
 * dock, which may cover soft chrome but never the fare, legs, close or accept. "No cupo" (C11)
 * only when even the docked compact plate would touch one of those.
 */
object OverlayPositioning {
    /**
     * Capture age only bounds the stopped-tab banner's admission. A live chip is never detached
     * for age: held frames and rate-limit retries are normal; presence and the overlay's
     * no-frame watchdog bound it instead.
     */
    const val MAX_GEOMETRY_AGE_MS = 2_500L

    /** Refresh capture age/source without briefly hiding an unchanged, already settled window. */
    fun sameLayout(previous: HostGeometry?, next: HostGeometry): Boolean =
        previous?.copy(capturedAtMs = next.capturedAtMs, layoutId = next.layoutId, source = next.source,
            appearance = next.appearance) == next

    fun geometryProblem(g: HostGeometry?, host: String, viewport: OverlayViewport, nowMs: Long,
        checkAge: Boolean = true): String? = when {
        g == null -> "UNVALIDATED_GEOMETRY"
        g.packageName != host -> "HOST_CHANGED"
        g.displayBounds != viewport.bounds || g.orientation != viewport.orientation -> "DISPLAY_CHANGED"
        checkAge && nowMs - g.capturedAtMs !in 0..MAX_GEOMETRY_AGE_MS -> "STALE_GEOMETRY"
        !g.displayBounds.contains(g.offerCard) || !g.displayBounds.contains(g.topChrome) ||
            (g.protectedControls + g.markers.map { it.outerBounds }).any { !g.displayBounds.contains(it) } -> "INCOMPLETE_GEOMETRY"
        else -> null
    }

    /**
     * [saved] is the current safe position (or the driver's slot); [savedDocked] keeps a docked
     * chip docked, so within one offer the chip never moves down. [dock] skips the foot/higher
     * tiers when this instance's upper boundary is unobserved. [dockFallback] false keeps the
     * stopped-reader tab's original contract (hidden rather than docked).
     */
    fun place(g: HostGeometry?, host: String, viewport: OverlayViewport, nowMs: Long,
        full: ChipSize, compact: ChipSize, saved: OverlayPosition? = null,
        dock: Boolean = false, savedDocked: Boolean = false, dockFallback: Boolean = true): OverlayPlacement {
        geometryProblem(g, host, viewport, nowMs)?.let { return OverlayPlacement.Unsupported(it) }
        g!!
        val sizes = listOf(PlateFormat.FULL to full, PlateFormat.COMPACT to compact)
        for ((format, size) in sizes) {
            if (saved != null && valid(saved, size, g, viewport, docked = savedDocked))
                return OverlayPlacement.Displayed(saved, size, format,
                    rule = if (savedDocked) PlacementRule.DOCK else PlacementRule.FOOT)
            if (!dock) candidates(size, g, viewport, includeMarkers = true).firstOrNull()?.let { (position, rule) ->
                return OverlayPlacement.Displayed(position, size, format, rule = rule)
            }
        }
        if (!dock) {
            // Controls always win. Only markers may be covered, and only by compact at the foot.
            footSlots(compact, g, viewport).firstOrNull { valid(it, compact, g, viewport, ignoreMarkers = true) }?.let {
                return OverlayPlacement.Displayed(it, compact, PlateFormat.COMPACT, overMarker = true,
                    rule = PlacementRule.MARKER)
            }
            // A control-blocked foot is not permission for a marker alone to suppress the plate.
            candidates(compact, g, viewport, includeMarkers = false).firstOrNull()?.let { (position, _) ->
                return OverlayPlacement.Displayed(position, compact, PlateFormat.COMPACT, overMarker = true,
                    rule = PlacementRule.MARKER)
            }
        }
        // Fail-open: main's top dock. Never hidden for lack of room above a wrongly derived card.
        if (dockFallback || dock) for ((format, size) in sizes) dockSlots(size, g, viewport).firstOrNull()?.let {
            return OverlayPlacement.Displayed(it, size, format, rule = PlacementRule.DOCK)
        }
        return OverlayPlacement.Suppressed(if (g.navigation) "NAVIGATION" else "NO_SAFE_SPACE")
    }

    /**
     * Strict tiers stay under the card ceiling and clear of controls, soft chrome and markers.
     * A [docked] plate only has to clear system/host controls and the fare and legs.
     */
    fun valid(p: OverlayPosition, size: ChipSize, g: HostGeometry, v: OverlayViewport,
        ignoreMarkers: Boolean = false, docked: Boolean = false): Boolean {
        if (size.width <= 0 || size.height <= 0) return false
        val rect = p.bounds(size)
        val gap = v.dp(16)
        val safe = v.safeBounds
        if (rect.left < maxOf(safe.left, v.bounds.left + v.dp(12)) ||
            rect.right > minOf(safe.right, v.bounds.right - v.dp(12)) ||
            rect.top < maxOf(safe.top, g.topChrome.bottom) + gap ||
            rect.bottom > safe.bottom - gap) return false
        if (g.protectedControls.any { rect.overlaps(it.expanded(gap)) }) return false
        if (docked) return dockCritical(g).none { rect.overlaps(it.expanded(gap)) }
        if (rect.bottom > minOf(safe.bottom, g.offerCard.top) - gap) return false
        if (g.softObstacles.any { rect.overlaps(it.expanded(gap)) }) return false
        return ignoreMarkers || g.markers.none { rect.overlaps(it.outerBounds.expanded(gap)) }
    }

    /**
     * What even the dock may never cover: the fare and the legs. An unread fare lies somewhere
     * between the lowest card header and the first leg, so that whole band is protected. Without
     * any fare/leg evidence (a measured host layout), the whole card.
     */
    private fun dockCritical(g: HostGeometry): List<HostRect> {
        val evidence = g.ocrEvidence ?: return listOfNotNull(g.fareBounds ?: g.offerCard)
        val firstRoute = evidence.routeAnchors.minOfOrNull { it.top }
        val unreadFare = if (g.fareBounds == null && firstRoute != null) {
            val above = evidence.upperAnchors.filter { it.bottom <= firstRoute }.maxOfOrNull { it.bottom }
                ?: minOf(g.offerCard.top, firstRoute)
            HostRect(g.displayBounds.left, above, g.displayBounds.right, firstRoute).takeIf { it.valid }
        } else null
        return listOfNotNull(g.fareBounds, unreadFare) + evidence.routeAnchors
    }

    private fun footSlots(size: ChipSize, g: HostGeometry, v: OverlayViewport): List<OverlayPosition> {
        val y = minOf(g.offerCard.top, v.safeBounds.bottom) - v.dp(16) - size.height
        return listOf(OverlayPosition(minOf(v.bounds.right - v.dp(12), v.safeBounds.right) - size.width, y),
            OverlayPosition(maxOf(v.bounds.left + v.dp(12), v.safeBounds.left), y))
    }

    /** Main's top-right default, then left: below top chrome and any control in the top corners. */
    private fun dockSlots(size: ChipSize, g: HostGeometry, v: OverlayViewport): List<OverlayPosition> {
        val gap = v.dp(16)
        val top = maxOf(v.safeBounds.top, g.topChrome.bottom) + gap
        val corners = g.protectedControls.filter { it.top < top }.sortedBy { it.bottom }
        return footSlots(size, g, v).map { slot ->
            var y = top
            for (control in corners) if (OverlayPosition(slot.x, y).bounds(size).overlaps(control.expanded(gap)))
                y = maxOf(y, control.bottom + gap)
            OverlayPosition(slot.x, y)
        }.filter { valid(it, size, g, v, docked = true) }
    }

    private fun candidates(size: ChipSize, g: HostGeometry, v: OverlayViewport,
        includeMarkers: Boolean): List<Pair<OverlayPosition, PlacementRule>> {
        val foot = footSlots(size, g, v)
        val obstacles = g.protectedControls + g.softObstacles +
            if (includeMarkers) g.markers.map { it.outerBounds } else emptyList()
        // The nearest higher clear position has its bottom at an exclusion's upper edge.
        val higher = foot.flatMap { slot ->
            obstacles.map { OverlayPosition(slot.x, it.top - v.dp(16) - size.height) }
                .filter { it.y < slot.y }.distinct().sortedByDescending { it.y }
        }
        return (foot.map { it to PlacementRule.FOOT } + higher.map { it to PlacementRule.HIGHER })
            .filter { valid(it.first, size, g, v, ignoreMarkers = !includeMarkers) }
    }

    fun edgeFor(p: OverlayPosition, v: OverlayViewport, size: ChipSize) =
        if (p.x + size.width / 2 < v.bounds.left + v.bounds.width / 2) SnapEdge.LEFT else SnapEdge.RIGHT

    /** Reject an unsafe drag rather than teleporting across an exclusion. */
    fun drag(current: OverlayPlacement.Displayed, desired: OverlayPosition, g: HostGeometry, v: OverlayViewport): OverlayPosition =
        desired.takeIf { valid(it, current.size, g, v, current.overMarker, current.docked) } ?: current.position

    fun snapToEdge(current: OverlayPlacement.Displayed, g: HostGeometry, v: OverlayViewport): OverlayPosition {
        val foot = footSlots(current.size, g, v)
        val x = if (edgeFor(current.position, v, current.size) == SnapEdge.RIGHT) foot[0].x else foot[1].x
        return OverlayPosition(x, current.position.y).takeIf {
            valid(it, current.size, g, v, current.overMarker, current.docked)
        } ?: current.position
    }
}

/**
 * Per-offer placement evidence, reset on every new session. Fail-open replacement for the
 * 0.2.8–0.2.11 proof gates (initial boundary, two-capture settle, masked-anchor recovery), which
 * suppressed the first frame of every real offer and livelocked the chip on back-to-back cards.
 *
 * The card ceiling is the lowest of: the derived envelope top; the VISIBLE fare top minus a
 * clearance that scales with its glyph height (S25: border 176 px above a 109 px fare); the first
 * leg minus the gap; every card header/close anchor minus the gap. Within one offer it only moves
 * up. An unread fare uses the last fare rect moved by the routes' rigid shift. With no upper anchor
 * ever observed for this instance (every real DiDi card), or with only legs visible, the chip
 * docks. A previously seen header/close that vanished under our own mask raises the ceiling to
 * its displaced position, or docks when the displacement is unknown. Nothing here detaches.
 */
internal class PlacementEvidence {
    private var ceiling: Int? = null
    private var upperSeen = false
    private var lastFare: HostRect? = null
    private var lastFareRoutes: List<HostRect> = emptyList()
    private var reference: List<HostRect> = emptyList()
    private var referenceRoutes: List<HostRect> = emptyList()

    /** [geometry] carries the ceiling as its card top; [dock] asks for the top dock tier. */
    data class Decision(val geometry: HostGeometry, val dock: Boolean, val ceiling: Int)

    fun observe(next: HostGeometry, viewport: OverlayViewport): Decision {
        val evidence = next.ocrEvidence
        if (next.source == HostGeometrySource.HOST_LAYOUT || evidence == null) {
            val top = minOf(ceiling ?: next.offerCard.top, next.offerCard.top)
            ceiling = top
            upperSeen = true
            return Decision(next.copy(offerCard = next.offerCard.copy(top = top)), dock = false, ceiling = top)
        }
        val gap = viewport.dp(16)
        val tolerance = viewport.dp(8)
        val mask = evidence.overlayMask
        val routes = evidence.routeAnchors
        // The Trip Radar pill floats outside the card: a soft obstacle, never a card boundary.
        val upper = evidence.upperAnchors.filterNot { anchor -> next.softObstacles.any { it.overlaps(anchor) } }
        var dock = false
        val limits = mutableListOf(next.offerCard.top)
        val fare = next.fareBounds ?: lastFare?.let { old ->
            rigidShift(lastFareRoutes, routes, tolerance)?.let { (dx, dy) -> old.moved(dx, dy) }
        }
        fare?.let { limits += it.top - maxOf(gap, (it.height * HEADER_CLEARANCE_FARE_HEIGHTS).roundToInt()) }
        routes.minOfOrNull { it.top }?.let { limits += it - gap }
        upper.forEach { limits += it.top - gap }
        var lost = false
        if (mask != null && reference.isNotEmpty()) {
            val shift = rigidShift(referenceRoutes, routes, tolerance)
            for (old in reference) {
                val displaced = shift?.let { (dx, dy) -> old.moved(dx, dy) } ?: old
                if (upper.any { near(it, displaced, tolerance) } || !displaced.expanded(tolerance).overlaps(mask)) continue
                lost = true
                if (shift != null) limits += displaced.top - gap else dock = true
            }
        }
        if (upper.isNotEmpty() || evidence.independentlyMeasuredCard) upperSeen = true
        if (!upperSeen || fare == null && upper.isEmpty() && !evidence.independentlyMeasuredCard) dock = true
        val top = maxOf(next.displayBounds.top, minOf(ceiling ?: Int.MAX_VALUE, limits.min()))
        ceiling = top
        if (next.fareBounds != null) { lastFare = next.fareBounds; lastFareRoutes = routes }
        if (!lost && upper.isNotEmpty()) { reference = upper; referenceRoutes = routes }
        return Decision(next.copy(offerCard = next.offerCard.copy(top = top), fareBounds = fare), dock, top)
    }

    private fun rigidShift(before: List<HostRect>, after: List<HostRect>, tolerance: Int): Pair<Int, Int>? {
        if (before.isEmpty() || before.size != after.size) return null
        val dx = after.first().left - before.first().left
        val dy = after.first().top - before.first().top
        return (dx to dy).takeIf { before.zip(after).all { (old, fresh) ->
            abs(fresh.left - old.left - dx) <= tolerance && abs(fresh.top - old.top - dy) <= tolerance
        } }
    }

    private fun near(a: HostRect, b: HostRect, tolerance: Int) =
        abs(a.left - b.left) <= tolerance && abs(a.top - b.top) <= tolerance

    private fun HostRect.moved(dx: Int, dy: Int) = HostRect(left + dx, top + dy, right + dx, bottom + dy)

    fun reset() {
        ceiling = null; upperSeen = false; lastFare = null; lastFareRoutes = emptyList()
        reference = emptyList(); referenceRoutes = emptyList()
    }

    companion object {
        /** Live S25 cards: border 176 px and badge 128 px above a 109 px fare top, ~1.6 fare heights. */
        const val HEADER_CLEARANCE_FARE_HEIGHTS = 1.6
    }
}
