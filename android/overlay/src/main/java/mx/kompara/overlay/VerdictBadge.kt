package mx.kompara.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mx.kompara.metrics.Verdict

/**
 * The in-window verdict badge drawn over a host app offer card. Rendered via a
 * `TYPE_ACCESSIBILITY_OVERLAY` from the capture service (android-technical-design.md §1); this
 * is the Compose surface only — the window plumbing arrives with the service in B-027.
 */
@Composable
fun VerdictBadge(verdict: Verdict, modifier: Modifier = Modifier) {
    val color: Color = VerdictColors.forVerdict(verdict.level)
    Text(
        text = verdict.level.name,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
