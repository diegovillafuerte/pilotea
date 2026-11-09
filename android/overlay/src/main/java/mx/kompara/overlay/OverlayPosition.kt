package mx.kompara.overlay

import mx.kompara.data.service.ReaderReadinessReducer
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
 * only when even the docked compact plate would touch one of those. 0.2.17: a host decline control
 * (DiDi's pill or round ✕) is a protected control in every tier, and the dock moves below it.
 */
object OverlayPositioning {
    /**
     * Capture age only bounds the stopped-tab banner's admission. A live chip is never detached
     * for age: held frames and rate-limit retries are normal; presence and the overlay's
     * no-frame watchdog bound it instead.
     */
    const val MAX_GEOMETRY_AGE_MS = 2_500L

    /** The only host whose chip may take touches (0.2.18, user decision: never on Uber). */
    private const val DRAGGABLE_HOST = ReaderReadinessReducer.DIDI
    /** OCR boxes jitter 1-3 px and the decline latch only grows: a placed dock slot tolerates this much. */
    private const val KEPT_DECLINE_JITTER_DP = 4

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
     * chip docked, so within one offer the chip never moves down, except once when a host decline
     * control is first recognized under it (0.2.17: it then moves below it). [dock] skips the foot/higher
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
            if (saved != null && valid(saved, size, g, viewport, docked = savedDocked, kept = savedDocked))
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
     * A [docked] plate only has to clear system/host controls and the fare and legs. [kept] marks a
     * docked slot already placed for this offer: it keeps it through OCR jitter of a decline rect.
     */
    fun valid(p: OverlayPosition, size: ChipSize, g: HostGeometry, v: OverlayViewport,
        ignoreMarkers: Boolean = false, docked: Boolean = false, kept: Boolean = false): Boolean {
        if (size.width <= 0 || size.height <= 0) return false
        val rect = p.bounds(size)
        val gap = v.dp(16)
        val safe = v.safeBounds
        if (rect.left < maxOf(safe.left, v.bounds.left + v.dp(12)) ||
            rect.right > minOf(safe.right, v.bounds.right - v.dp(12)) ||
            rect.top < maxOf(safe.top, g.topChrome.bottom) + gap ||
            rect.bottom > safe.bottom - gap) return false
        val declineGap = if (docked && kept) gap - v.dp(KEPT_DECLINE_JITTER_DP) else gap
        if (g.protectedControls.any { rect.overlaps(it.expanded(if (it in g.declineControls) declineGap else gap)) }) return false
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

    /**
     * Main's top-right default, then left: below top chrome and any control in the top corners.
     * 0.2.17: a host decline control (live, latched or DiDi's reserved area) pushes a slot it overlaps
     * down below it, wherever it starts (DiDi's pill starts below the dock line). Only decline controls above the
     * fare/legs may push, so the dock never jumps below the fare; pushing for any control drove the
     * right slot mid-card in the replay. Valid slots are tried top-most first (ties: right, then left).
     */
    private fun dockSlots(size: ChipSize, g: HostGeometry, v: OverlayViewport): List<OverlayPosition> {
        val gap = v.dp(16)
        val top = maxOf(v.safeBounds.top, g.topChrome.bottom) + gap
        val criticalTop = dockCritical(g).minOfOrNull { it.top } ?: g.offerCard.top
        val corners = (g.protectedControls.filter { it.top < top } +
            g.declineControls.filter { it.bottom <= criticalTop }).distinct().sortedBy { it.bottom }
        return footSlots(size, g, v).map { slot ->
            var y = top
            for (control in corners) if (OverlayPosition(slot.x, y).bounds(size).overlaps(control.expanded(gap)))
                y = maxOf(y, control.bottom + gap)
            OverlayPosition(slot.x, y)
        }.filter { valid(it, size, g, v, docked = true) }.sortedBy { it.y }
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

    /**
     * 0.2.17 (user decision 2026-11-07, "only in a clear slot"): the chip takes touches (drag) only
     * in a strict tier and only where it covers no protected control and no soft host chrome, live
     * or latched (unexpanded rects). Docked, or over any host control, its window is
     * FLAG_NOT_TOUCHABLE so taps reach DiDi. 0.2.18: never on Uber (user decision 2026-11-08: its
     * Radar pill can appear under the normal slot before the next capture), and on DiDi only once
     * [seenClear] (see [PlacementEvidence.seenClear]) says a host control there could have been seen.
     * The controller adds what only it knows: the current foreground and the frame's offer identity.
     */
    fun touchable(p: OverlayPlacement.Displayed, g: HostGeometry, seenClear: Boolean): Boolean =
        g.packageName == DRAGGABLE_HOST && seenClear && !p.docked &&
            (g.protectedControls + g.softObstacles).none { p.position.bounds(p.size).overlaps(it) }

    /** On DiDi, a strict, valid slot over no host chrome: the only place a drag may move to or end in. */
    fun clearSlot(p: OverlayPosition, size: ChipSize, g: HostGeometry, v: OverlayViewport, overMarker: Boolean = false): Boolean =
        g.packageName == DRAGGABLE_HOST && valid(p, size, g, v, overMarker) &&
            (g.protectedControls + g.softObstacles).none { p.bounds(size).overlaps(it) }

    /** Reject an unsafe drag rather than teleporting across an exclusion; a docked chip never drags. */
    fun drag(current: OverlayPlacement.Displayed, desired: OverlayPosition, g: HostGeometry, v: OverlayViewport): OverlayPosition =
        desired.takeIf { !current.docked && clearSlot(it, current.size, g, v, current.overMarker) } ?: current.position

    fun snapToEdge(current: OverlayPlacement.Displayed, g: HostGeometry, v: OverlayViewport): OverlayPosition {
        if (current.docked) return current.position
        val foot = footSlots(current.size, g, v)
        val x = if (edgeFor(current.position, v, current.size) == SnapEdge.RIGHT) foot[0].x else foot[1].x
        return OverlayPosition(x, current.position.y).takeIf {
            clearSlot(it, current.size, g, v, current.overMarker)
        } ?: current.position
    }
}

/**
 * Per-offer placement evidence, reset on every new session. Fail-open replacement for the
 * 0.2.8–0.2.11 proof gates (initial boundary, two-capture settle, masked-anchor recovery), which
 * suppressed the first frame of every real offer and livelocked the chip on back-to-back cards.
 *
 * The card ceiling is the lowest of: the derived envelope top; the VISIBLE fare top minus a
 * clearance that scales with the offer's smallest fare height read so far (S25: border 176 px above
 * a 109 px fare; DiDi's countdown wipe inflates a single frame's box); the first leg minus the gap;
 * every card header/close anchor minus the gap. Within one offer it never moves down, and a frame
 * moves it up only with a limit more than [CEILING_JITTER_DP] above the held ceiling; that frame
 * then latches its limit. Smaller rises are OCR jitter and leave the held value, so they cannot
 * creep. An unread fare uses the last fare rect moved by the routes' rigid shift. With no upper
 * anchor ever observed for this instance (every real DiDi card), or with only legs visible, the
 * chip docks. A previously seen header/close that vanished under our own mask raises the ceiling
 * to its displaced position, or docks when the displacement is unknown. Nothing here detaches.
 *
 * 0.2.17 latches, exclusions only (the 0.2.13 lesson: never wait for an anchor): every host decline
 * control seen above the card this offer (DiDi's "✕ Rechazo permitido" pill or round ✕; our own
 * chip hides the pill after the first frame) stays a protected/decline control, and every soft
 * obstacle seen this offer (Uber's floating Radar pill) stays a soft obstacle, each as a plain union
 * merged with fresh observations of the same control. A missing label (under our chip, an OCR
 * dropout) or a soft-hidden frame is not a reset: a pill that dropped out whenever a smaller plate
 * stopped masking it let FULL grow over it on alternate frames (round 2, raw replay). Neither latch
 * shifts with the routes, lowers the ceiling, pins a reference or docks; both end only in [reset]
 * (new session, Hidden, parse ceiling) or on a host or display change, never on a C11 detach.
 *
 * 0.2.18: on a DiDi offer in portrait every Decision also carries the calibrated [declineReservation],
 * so the decline control is kept clear on the first frame, before or without any OCR of it. It is
 * an exclusion too: not latched, never an anchor or ceiling input, and it can never cost a chip
 * ([withoutReservation]). [seenClear] tracks what this session's frames could see under our own plate.
 */
internal class PlacementEvidence {
    private var ceiling: Int? = null
    private var upperSeen = false
    private var smallestFareHeight = Int.MAX_VALUE
    private var lastFare: HostRect? = null
    private var lastFareRoutes: List<HostRect> = emptyList()
    private var reference: List<HostRect> = emptyList()
    private var referenceRoutes: List<HostRect> = emptyList()
    private var declines: List<HostRect> = emptyList()
    private var softSeen: List<HostRect> = emptyList()
    private var reservationWaived = false
    /** The latest frame's geometry without the reservation, while that frame may try to do without it. */
    private var unreserved: HostGeometry? = null
    private var waiverTrial = false
    /** This session's own-chip rects at capture; a frame with none saw the whole screen. */
    private var ownChipRects: List<HostRect> = emptyList()
    private var frameWithoutChip = false
    /** Host and display the evidence belongs to: rects from another host or display are meaningless. */
    private var scope: Triple<String, HostRect, HostOrientation>? = null

    /**
     * [geometry] carries the ceiling as its card top and the latched decline/soft controls; [dock]
     * asks for the top dock tier; [latched] counts the latched decline rects (TEMP DIAG).
     */
    data class Decision(val geometry: HostGeometry, val dock: Boolean, val ceiling: Int, val latched: Int = 0)

    fun observe(next: HostGeometry, viewport: OverlayViewport, identityUnresolved: Boolean = false): Decision {
        val frameScope = Triple(next.packageName, next.displayBounds, next.orientation)
        if (scope != frameScope) { reset(); scope = frameScope }
        val ownChip = next.ocrEvidence?.overlayMask
        // A frame that may be another offer says nothing about what lies under this offer's plate.
        if (!identityUnresolved) {
            if (ownChip == null) frameWithoutChip = true else if (ownChip !in ownChipRects) ownChipRects = ownChipRects + ownChip
        }
        // Host chrome above this frame's derived card only: Uber's in-card ✕ is not latched.
        next.declineControls.filter { it.bottom <= next.offerCard.top }.forEach { declines = union(declines, it) }
        next.softObstacles.forEach { softSeen = union(softSeen, it) }
        val evidence = next.ocrEvidence
        if (next.source == HostGeometrySource.HOST_LAYOUT || evidence == null) {
            val top = minOf(ceiling ?: next.offerCard.top, next.offerCard.top)
            ceiling = top
            upperSeen = true
            return Decision(latched(next.copy(offerCard = next.offerCard.copy(top = top)), viewport, fareRead = next.fareBounds != null),
                dock = false, ceiling = top, latched = declines.size)
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
        if (!identityUnresolved) next.fareBounds?.let { smallestFareHeight = minOf(smallestFareHeight, it.height) }
        fare?.let { limits += it.top - maxOf(gap, ((if (identityUnresolved) it.height else minOf(smallestFareHeight, it.height)) * HEADER_CLEARANCE_FARE_HEIGHTS).roundToInt()) }
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
        val held = ceiling
        val frameLimit = limits.min()
        val top = maxOf(next.displayBounds.top,
            if (held != null && held - frameLimit <= viewport.dp(CEILING_JITTER_DP)) held else frameLimit)
        ceiling = top
        if (next.fareBounds != null) { lastFare = next.fareBounds; lastFareRoutes = routes }
        if (!lost && upper.isNotEmpty()) { reference = upper; referenceRoutes = routes }
        return Decision(latched(next.copy(offerCard = next.offerCard.copy(top = top), fareBounds = fare), viewport,
            fareRead = next.fareBounds != null), dock, top, declines.size)
    }

    /** Plain union with every latched rect it overlaps: the latch stays a superset of each frame's rect. */
    private fun union(latch: List<HostRect>, rect: HostRect): List<HostRect> {
        var merged = rect
        var rest = latch
        while (true) {
            val (hit, miss) = rest.partition { it.overlaps(merged) }
            if (hit.isEmpty()) break
            merged = hit.fold(merged) { a, b -> HostRect(minOf(a.left, b.left), minOf(a.top, b.top),
                maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
            rest = miss
        }
        return rest + merged
    }

    /**
     * Every decision carries both latches (the soft latch already holds this frame's soft obstacles)
     * and the reservation, which stays a separate source: what the frame observed or latched is
     * [unreserved] whatever its value, so dropping the reservation can never drop one of those.
     */
    private fun latched(g: HostGeometry, viewport: OverlayViewport, fareRead: Boolean): HostGeometry {
        val observed = g.copy(protectedControls = (g.protectedControls + declines).distinct(),
            declineControls = (g.declineControls + declines).distinct(), softObstacles = softSeen)
        val area = declineReservation(g, viewport)?.takeUnless { reservationWaived }
        unreserved = observed.takeIf { area != null && fareRead }
        waiverTrial = false
        return if (area == null) observed else observed.copy(protectedControls = (observed.protectedControls + area).distinct(),
            declineControls = (observed.declineControls + area).distinct())
    }

    /**
     * DiDi's decline area above the card, from the viewport (S25 at density 3, safe top 91: the union
     * of the pill's OCR over the 6 recorded offers is (479,156)-(994,214), which gives (477,154)-(996,214);
     * its 16 dp keep-out (429,106)-(1044,262) spans the visible pill body's height). Round-✕ cards get it
     * too: the form is unknown before OCR. Off-calibration displays (landscape) rely on OCR alone.
     */
    private fun declineReservation(g: HostGeometry, v: OverlayViewport): HostRect? {
        if (g.packageName != ReaderReadinessReducer.DIDI || v.orientation != HostOrientation.PORTRAIT) return null
        val right = v.bounds.right
        val top = v.safeBounds.top
        return HostRect(right - v.dp(201), top + v.dp(21), right - v.dp(28), top + v.dp(41)).intersection(g.displayBounds)
    }

    /**
     * The reservation never costs a chip: when nothing fits with it on a frame whose fare this frame
     * read (a fare rebuilt from an earlier frame is no such evidence, and an unread fare is transient),
     * placement runs once more on this frame's observed and latched controls. Returns that geometry,
     * or null when there is nothing to try. It is waived, for the rest of the offer, only if the trial
     * places a chip ([commitWaiver]); a C11 it did not cause keeps it, and C11 keeps its contract.
     */
    fun withoutReservation(): HostGeometry? = unreserved?.also { unreserved = null; waiverTrial = true }

    /** The trial placed a chip: only the reservation stood in the way. */
    fun commitWaiver() {
        if (waiverTrial) reservationWaived = true
        waiverTrial = false
    }

    /** This offer's plate may sit where a decline control was never read: it takes no touches. */
    val waived: Boolean get() = reservationWaived

    /**
     * Whether some frame of this session was captured with none of our own plate over [footprint],
     * so a host control under it could have been read. A window that persisted into this session
     * (back-to-back offers) hides what is beneath it from every frame until the chip moves.
     */
    fun seenClear(footprint: HostRect): Boolean = frameWithoutChip || ownChipRects.any { !it.overlaps(footprint) }

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
        ceiling = null; upperSeen = false; smallestFareHeight = Int.MAX_VALUE; lastFare = null; lastFareRoutes = emptyList()
        reference = emptyList(); referenceRoutes = emptyList(); declines = emptyList(); softSeen = emptyList()
        reservationWaived = false; unreserved = null; waiverTrial = false
        ownChipRects = emptyList(); frameWithoutChip = false
        scope = null
    }

    companion object {
        /** Live S25 cards: border 176 px and badge 128 px above a 109 px fare top, ~1.6 fare heights. */
        const val HEADER_CLEARANCE_FARE_HEIGHTS = 1.6
        /** OCR jitter in the card limits (0.2.19 S25: 1-5 px, the fare top 10 under DiDi's wipe). */
        const val CEILING_JITTER_DP = 4
    }
}
