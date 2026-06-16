package mx.kompara.overlay

/**
 * The overlay window's top-left position, in screen pixels from the top-left origin. Plain ints so
 * the positioning/clamping math is pure and JVM-unit-testable (no android.graphics types).
 *
 * @property x distance from the left screen edge, px
 * @property y distance from the top screen edge, px
 */
data class OverlayPosition(val x: Int, val y: Int)

/**
 * The offer's fare + leg block region (screen px, top-left origin) the chip must not cover. Sourced
 * from the OCR parser's `OfferCard.contentBounds`; null when bounds are unavailable (node path).
 */
data class ContentRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Which horizontal edge the chip snaps to after a drag. The chip always lives flush against one
 * side (drivers flick it out of the way of the map), so a drag resolves to LEFT or RIGHT by which
 * half of the screen the chip's centre ended up in.
 */
enum class SnapEdge { LEFT, RIGHT }

/**
 * Pure geometry for placing, clamping, and snapping the overlay chip. No Android dependencies so
 * the safe-zone and snap math can be exhaustively unit-tested.
 *
 * ## Safe zone (acceptance criterion: "never obstruct the host app's Accept button area")
 * The host apps render their big Accept button across the bottom of the screen. We reserve the
 * bottom [SAFE_ZONE_BOTTOM_FRACTION] of the screen height and never let the chip's *bottom* edge
 * intrude into it, so a mis-aimed tap can never land on our chip instead of Accept. We also keep
 * the chip fully on-screen horizontally and below a small top inset (status bar / notch).
 */
object OverlayPositioning {

    /** Fraction of screen height, measured from the bottom, that the chip may never overlap. */
    const val SAFE_ZONE_BOTTOM_FRACTION: Double = 0.25

    /** Top inset (px) reserved for the status bar / notch so the chip never hides under it. */
    const val TOP_INSET_PX: Int = 48

    /** Side margin (px) kept when the chip is snapped flush to an edge. */
    const val EDGE_MARGIN_PX: Int = 8

    /** The lowest y (px) the chip's *top* edge may take given the screen and chip heights. */
    fun maxTop(screenHeight: Int, chipHeight: Int): Int {
        val safeBottomTop = bottomSafeZoneTop(screenHeight)
        // The chip's bottom (top + chipHeight) must not pass into the safe zone.
        return (safeBottomTop - chipHeight).coerceAtLeast(TOP_INSET_PX)
    }

    /** The y (px) at which the bottom safe zone begins (everything at/below this is reserved). */
    fun bottomSafeZoneTop(screenHeight: Int): Int =
        (screenHeight * (1.0 - SAFE_ZONE_BOTTOM_FRACTION)).toInt()

    /**
     * Clamp a desired [position] so the chip stays fully on screen, below the top inset, and with
     * its bottom edge out of the bottom safe zone.
     */
    fun clamp(
        position: OverlayPosition,
        screenWidth: Int,
        screenHeight: Int,
        chipWidth: Int,
        chipHeight: Int,
    ): OverlayPosition {
        val maxX = (screenWidth - chipWidth).coerceAtLeast(0)
        val maxY = maxTop(screenHeight, chipHeight)
        val x = position.x.coerceIn(0, maxX)
        val y = position.y.coerceIn(TOP_INSET_PX, maxY.coerceAtLeast(TOP_INSET_PX))
        return OverlayPosition(x, y)
    }

    /** Which edge a chip at [position] should snap to, decided by its horizontal centre. */
    fun edgeFor(position: OverlayPosition, screenWidth: Int, chipWidth: Int): SnapEdge {
        val centre = position.x + chipWidth / 2
        return if (centre < screenWidth / 2) SnapEdge.LEFT else SnapEdge.RIGHT
    }

    /**
     * Snap [position] flush to the nearer horizontal edge (keeping [EDGE_MARGIN_PX]) and clamp the
     * vertical to the safe area. This is what a drag-release resolves to.
     */
    fun snapToEdge(
        position: OverlayPosition,
        screenWidth: Int,
        screenHeight: Int,
        chipWidth: Int,
        chipHeight: Int,
    ): OverlayPosition {
        val snappedX = when (edgeFor(position, screenWidth, chipWidth)) {
            SnapEdge.LEFT -> EDGE_MARGIN_PX
            SnapEdge.RIGHT -> (screenWidth - chipWidth - EDGE_MARGIN_PX)
        }
        return clamp(
            OverlayPosition(snappedX, position.y),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            chipWidth = chipWidth,
            chipHeight = chipHeight,
        )
    }

    /**
     * Default starting position: top-right corner, just below the top inset. Computed from the
     * measured screen + chip size so it is already edge-snapped and inside the safe area.
     */
    fun defaultPosition(
        screenWidth: Int,
        screenHeight: Int,
        chipWidth: Int,
        chipHeight: Int,
    ): OverlayPosition = clamp(
        OverlayPosition(x = screenWidth - chipWidth - EDGE_MARGIN_PX, y = TOP_INSET_PX),
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        chipWidth = chipWidth,
        chipHeight = chipHeight,
    )

    /** Gap (px) kept between the lifted chip and the offer content it dodges. */
    const val GAP_PX: Int = 8

    /**
     * Keep the chip off the offer's fare/leg [content]. Returns [desired] unchanged only when there
     * is no content or the chip sits ENTIRELY ABOVE the content's top edge — the clean top-right slot
     * and any drag the driver left up there. Otherwise (the chip overlaps the fare/legs, OR is parked
     * in the card body below the fare) it is lifted to just above the content, keeping the driver's x.
     *
     * ## Why above is the only safe region (on-device, 2026-06-15)
     * The chip held steady when it sat above the fare but blinked whenever it sat below it. Below the
     * fare you are still inside the tall offer card (rating, pickup leg, address, trip leg, Aceptar);
     * the opaque chip then occludes a leg the screen-capture OCR must re-read every frame, the parser
     * (which requires the trip leg) fails, and the verdict drops out — the flicker. Above the fare is
     * clear map / card header, where nothing the parser needs is ever covered. So there is no BELOW
     * fallback: a "below" slot is never clear on a full-height card.
     *
     * Only the vertical position is nudged (x, the driver's chosen side, is kept) and the result is
     * clamped to the safe area. If the fare sits so high that the chip can't clear it above the top
     * inset (never seen on a real card — the fare renders well down the screen), [clamp] docks it at
     * the top inset: the least-bad slot, still mostly above the fare, and the chip is never hidden.
     * Pure.
     */
    fun avoid(
        desired: OverlayPosition,
        content: ContentRect?,
        screenWidth: Int,
        screenHeight: Int,
        chipWidth: Int,
        chipHeight: Int,
    ): OverlayPosition {
        if (content == null || desired.y + chipHeight <= content.top) return desired
        return clamp(
            OverlayPosition(desired.x, content.top - chipHeight - GAP_PX),
            screenWidth, screenHeight, chipWidth, chipHeight,
        )
    }
}
