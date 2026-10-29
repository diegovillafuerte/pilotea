package mx.kompara.overlay

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import mx.kompara.ui.components.Regla
import mx.kompara.ui.components.ReglaModel

/** Only the overlay window owns drag. Tap and long-press have no action. */
data class VerdictChipCallbacks(
    val onDrag: (dxPx: Float, dyPx: Float) -> Unit = { _, _ -> },
    val onDragEnd: () -> Unit = {},
)

@Composable
fun VerdictChipUi(
    state: ReglaModel,
    callbacks: VerdictChipCallbacks = VerdictChipCallbacks(),
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    darkHost: Boolean = false,
) {
    val latest = rememberUpdatedState(callbacks)
    Regla(state, modifier.pointerInput(Unit) {
        detectDragGestures(
            onDrag = { change, amount -> change.consume(); latest.value.onDrag(amount.x, amount.y) },
            onDragEnd = { latest.value.onDragEnd() },
        )
    }, compact = compact, darkHost = darkHost)
}
