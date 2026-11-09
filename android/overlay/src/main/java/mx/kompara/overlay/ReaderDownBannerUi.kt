package mx.kompara.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import mx.kompara.parsers.model.HostGeometry

/** Opaque Compose/Flutter nodes cannot prove an OCR-read card disappeared when capture stops. */
internal fun stoppedTabGeometry(observed: HostGeometry?, lastOffer: HostGeometry?, admitted: HostGeometry?,
    host: String, viewport: OverlayViewport, nowMs: Long): HostGeometry? {
    if (OverlayPositioning.geometryProblem(observed, host, viewport, nowMs) != null) return null
    observed!!
    if (!observed.isIdleHost) return observed
    // The admitted tab lives for at most five seconds. Retain its observed card exclusion through
    // that visit, but revalidate host/display/insets and merge newly observed controls/markers.
    val card = admitted?.takeIf { !it.isIdleHost &&
        OverlayPositioning.geometryProblem(it, host, viewport, it.capturedAtMs) == null }
        // After a stall this geometry is at least 10 s old, and its card can stay up until the parse ceiling.
        ?: lastOffer?.takeIf { !it.isIdleHost && nowMs - it.capturedAtMs in 0..OverlayController.PARSE_CEILING_MS &&
            OverlayPositioning.geometryProblem(it, host, viewport, nowMs, checkAge = false) == null }
        ?: return observed
    return card.copy(topChrome = observed.topChrome.copy(bottom = maxOf(observed.topChrome.bottom, card.topChrome.bottom)),
        protectedControls = (card.protectedControls + observed.protectedControls).distinct(),
        markers = (card.markers + observed.markers).distinct(), navigation = card.navigation || observed.navigation)
}

/** Same passive renderer used by the debug whole-frame gallery. */
@Composable
fun ReaderStoppedTab(modifier: Modifier = Modifier) = mx.kompara.ui.components.ReaderStoppedTab(modifier)

/** A foreground visit, not a capture-state emission, arms one five-second tab. */
class ReaderStoppedTabGate {
    private var host: String? = null
    private var shown = false
    private var until: Long? = null
    fun visible(host: String?, eligible: Boolean, nowMs: Long): Boolean {
        if (this.host != host) { this.host = host; shown = false; until = null }
        if (host == null || !eligible) { until = null; return false }
        if (!shown) { shown = true; until = nowMs + 5_000 }
        return until?.let { nowMs < it } == true
    }

    fun remainingMs(nowMs: Long): Long = ((until ?: nowMs) - nowMs).coerceAtLeast(0)
}

/** A requested WindowManager position is not proof of the tab's position on screen. */
internal sealed interface StoppedTabWindowDecision {
    data object Hide : StoppedTabWindowDecision
    data object WaitForLayout : StoppedTabWindowDecision
    data object Show : StoppedTabWindowDecision
    data class Move(val windowPosition: OverlayPosition) : StoppedTabWindowDecision
}

internal fun stoppedTabWindowDecision(
    geometry: HostGeometry,
    host: String,
    viewport: OverlayViewport,
    size: ChipSize,
    actualPosition: OverlayPosition,
    requestedPosition: OverlayPosition,
    layoutPending: Boolean,
    lastMoveFrom: OverlayPosition? = null,
): StoppedTabWindowDecision {
    // Admission checked freshness before capture stopped. Keep that bounded five-second visit;
    // recheck its measured dimensions/insets without pretending capture still supplies frames.
    val placed = OverlayPositioning.place(geometry, host, viewport, geometry.capturedAtMs, size, size,
        dockFallback = false)
        as? OverlayPlacement.Displayed ?: return StoppedTabWindowDecision.Hide
    if (layoutPending || lastMoveFrom == actualPosition) return StoppedTabWindowDecision.WaitForLayout
    if (actualPosition == placed.position) return StoppedTabWindowDecision.Show
    val origin = OverlayPosition(actualPosition.x - requestedPosition.x, actualPosition.y - requestedPosition.y)
    return StoppedTabWindowDecision.Move(placed.position.inWindow(origin))
}
