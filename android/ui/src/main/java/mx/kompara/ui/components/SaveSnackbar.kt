package mx.kompara.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import mx.kompara.ui.R
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

val LocalLimitsSnackbar = staticCompositionLocalOf<SaveSnackbarState?> { null }

/** Hold above the editor/opener navigation boundary. Call only once the repository save succeeds. */
@Stable
class SaveSnackbarState {
    var message: String? by mutableStateOf(null)
        private set
    var sequence by mutableLongStateOf(0)
        private set
    fun saved(message: String) { this.message = message; sequence++ }
    fun clear() { message = null }
}

@Composable
fun SaveSnackbar(state: SaveSnackbarState, modifier: Modifier = Modifier) {
    val message = state.message ?: return
    LaunchedEffect(state.sequence) { delay(4_000); state.clear() }
    SaveSnackbarContent(message, modifier)
}

/** Place in a measured free slot, or reserve space in the footer; never overlay a data row. */
@Composable
fun SaveSnackbarContent(message: String, modifier: Modifier = Modifier) {
    val c = KomparaTheme.colors
    Row(modifier.fillMaxWidth().background(c.ink, RoundedCornerShape(10.dp))
        .padding(horizontal = 16.dp, vertical = 14.dp).semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DrawnIcon(R.drawable.v3_check, Modifier.size(16.dp), tint = c.paper)
        Text(message, style = KomparaType.bodyStrong, color = c.paper)
    }
}

data class OccupiedRow(val top: Float, val bottom: Float)

/** All coordinates in dp, after system insets. Null means the caller must reserve a new slot. */
fun snackbarTop(bottom: Float, height: Float, footerTop: Float?, rows: List<OccupiedRow>, top: Float = 0f): Float? {
    require(height >= 0 && bottom >= top)
    var candidate = (footerTop?.minus(8f) ?: (bottom - 16f)) - height
    for (row in rows.sortedByDescending { it.bottom }) {
        if (candidate < row.bottom + 8f && candidate + height > row.top - 8f) candidate = row.top - 8f - height
    }
    return candidate.takeIf { it >= top }
}
