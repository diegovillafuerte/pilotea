package mx.kompara.overlay

import androidx.compose.runtime.Composable
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
    onPlacement: (OverlayPlacement) -> Unit,
) {
    SubcomposeLayout(modifier) { _ ->
        val full = subcompose("measure-full") { Regla(state, Modifier.clearAndSetSemantics {}) }.single().measure(Constraints())
        val compact = subcompose("measure-compact") { Regla(state, Modifier.clearAndSetSemantics {}, compact = true) }.single().measure(Constraints())
        val decision = OverlayPositioning.place(geometry, geometry.packageName, viewport, nowMs,
            ChipSize(full.width, full.height), ChipSize(compact.width, compact.height), saved)
        val shown = decision as? OverlayPlacement.Displayed
        val plate = shown?.let {
            subcompose("display") {
                ReglaPresence(visible, reducedMotion) {
                    VerdictChipUi(state, callbacks, compact = it.format == PlateFormat.COMPACT, darkHost = darkHost)
                }
            }.single().measure(Constraints.fixed(it.size.width, it.size.height))
        }
        layout(shown?.size?.width ?: 1, shown?.size?.height ?: 1) {
            plate?.place(0, 0)
            onPlacement(decision)
        }
    }
}
