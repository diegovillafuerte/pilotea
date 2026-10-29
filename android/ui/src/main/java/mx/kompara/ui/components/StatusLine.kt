package mx.kompara.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

/** Status is not a verdict. These six drawings always accompany caller-provided words. */
enum class StatusGlyph { HEALTHY, WAITING, OFF, PAUSED, DONE, ATTENTION }

@Composable
fun StatusIcon(glyph: StatusGlyph, modifier: Modifier = Modifier) {
    val c = KomparaTheme.colors
    Canvas(modifier.size(16.dp).clearAndSetSemantics {}) {
        scale(size.width / 16f, size.height / 16f, pivot = Offset.Zero) {
            when (glyph) {
                StatusGlyph.HEALTHY -> {
                    drawCircle(c.verde.copy(alpha = .16f), 9f, Offset(8f, 8f))
                    drawCircle(c.verde, 5f, Offset(8f, 8f))
                }
                StatusGlyph.WAITING -> drawCircle(c.ink, 5.5f, Offset(8f, 8f), style = Stroke(2f))
                StatusGlyph.OFF -> {
                    drawCircle(c.ink, 6f, Offset(8f, 8f), style = Stroke(2f))
                    drawLine(c.ink, Offset(3.8f, 3.8f), Offset(12.2f, 12.2f), 2f)
                }
                StatusGlyph.PAUSED -> {
                    drawRect(c.ink, Offset(3.5f, 2.5f), Size(3f, 11f))
                    drawRect(c.ink, Offset(9.5f, 2.5f), Size(3f, 11f))
                }
                StatusGlyph.DONE -> drawPath(Path().apply {
                    moveTo(2.5f, 8.5f); lineTo(6.5f, 12.5f); lineTo(13.5f, 4f)
                }, c.ink, style = Stroke(2.2f))
                StatusGlyph.ATTENTION -> {
                    drawCircle(c.ink, 6f, Offset(8f, 8f), style = Stroke(1.8f))
                    drawLine(c.ink, Offset(8f, 4f), Offset(8f, 8.5f), 1.8f)
                    drawCircle(c.ink, 1f, Offset(8f, 11.5f))
                }
            }
        }
    }
}

@Composable
fun StatusLine(text: String, glyph: StatusGlyph, modifier: Modifier = Modifier) {
    Row(modifier.heightIn(min = 48.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusIcon(glyph)
        Text(text, style = KomparaType.bodyStrong, color = KomparaTheme.colors.ink)
    }
}

@Composable
fun StatusDoor(text: String, detail: String, glyph: StatusGlyph, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusIcon(glyph)
        Column(Modifier.weight(1f)) {
            Text(text, style = KomparaType.bodyStrong)
            Text(detail, style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
        }
        DrawnIcon(mx.kompara.ui.R.drawable.v3_chev)
    }
}
