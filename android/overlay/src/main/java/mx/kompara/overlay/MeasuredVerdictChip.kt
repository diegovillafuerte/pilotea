package mx.kompara.overlay

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.clearAndSetSemantics
import mx.kompara.parsers.model.HostGeometry
import mx.kompara.ui.components.Regla
import mx.kompara.ui.components.ReglaModel
import mx.kompara.ui.components.ReglaPresence

/** Measure both real plates, including long labels, BEFORE choosing a slot or drawing anything. */
@Composable
internal fun MeasuredVerdictChip(
    state: ReglaModel,
    geometry: HostGeometry,
    viewport: OverlayViewport,
    nowMs: Long,
    saved: OverlayPosition?,
    visible: Boolean,
    reducedMotion: Boolean,
    darkHost: Boolean,
    callbacks: VerdictChipCallbacks,
    modifier: Modifier = Modifier,
    dock: Boolean = false,
    savedDocked: Boolean = false,
    /** The displayed plate's presence, so the host can tell an entered plate from one still at alpha 0. */
    onPresence: (MutableTransitionState<Boolean>) -> Unit = {},
    onPlacement: (OverlayPlacement) -> Unit,
) {
    SubcomposeLayout(modifier) { _ ->
        val full = subcompose("measure-full") { Regla(state, Modifier.clearAndSetSemantics {}) }.single().measure(Constraints())
        val compact = subcompose("measure-compact") { Regla(state, Modifier.clearAndSetSemantics {}, compact = true) }.single().measure(Constraints())
        val decision = OverlayPositioning.place(geometry, geometry.packageName, viewport, nowMs,
            ChipSize(full.width, full.height), ChipSize(compact.width, compact.height), saved, dock, savedDocked)
        val shown = decision as? OverlayPlacement.Displayed
        val plate = shown?.let {
            subcompose("display") {
                // WP-AB: composed visible, the plate is present in its first composition, with no entrance, so the
                // traversal that reveals the overlay's window already draws all of it (on the S25 some windows drew
                // no frame after that one). Later exits, and entries after them, still animate.
                val presence = remember { MutableTransitionState(visible) }
                SideEffect { onPresence(presence) }
                ReglaPresence(visible, reducedMotion, presence) {
                    VerdictChipUi(state, callbacks, compact = it.format == PlateFormat.COMPACT, darkHost = darkHost)
                }
                // A plate composed hidden, or one whose exit has ended, emits no node: nothing to measure or place.
            }.firstOrNull()?.measure(Constraints.fixed(it.size.width, it.size.height))
        }
        // Without a plate the layout is the 1 px of an undisplayed chip. onPlacement still says where the chip would
        // go; the host sees a window that is not the placed size and holds it hidden (OverlayController, PLATE_ABSENT).
        val size = shown?.size?.takeIf { plate != null }
        layout(size?.width ?: 1, size?.height ?: 1) {
            plate?.place(0, 0)
            onPlacement(decision)
        }
    }
}
