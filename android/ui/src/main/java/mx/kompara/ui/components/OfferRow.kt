package mx.kompara.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

enum class OfferMarkState { ABOVE, MEETS, BELOW, UNKNOWN }

@Composable
fun OfferMark(state: OfferMarkState, modifier: Modifier = Modifier, inRow: Boolean = false) {
    val c = KomparaTheme.colors
    Canvas(modifier.size(if (inRow) 18.dp else 16.dp, if (inRow) 28.dp else 24.dp)
        .clip(RoundedCornerShape(3.dp)).clearAndSetSemantics {}) {
        val y1 = (if (inRow) 9.dp else 8.dp).toPx(); val y2 = (if (inRow) 17.dp else 14.dp).toPx()
        when (state) {
            OfferMarkState.ABOVE -> drawRect(c.verde, size = Size(size.width, y1))
            OfferMarkState.MEETS -> drawRect(c.amarillo, Offset(0f, y1 + 1.dp.toPx()), Size(size.width, y2 - y1 - 1.dp.toPx()))
            OfferMarkState.BELOW -> drawRect(c.rojo, Offset(0f, y2 + 1.dp.toPx()), Size(size.width, size.height - y2))
            OfferMarkState.UNKNOWN -> Unit
        }
        drawLine(c.inkTertiary, Offset(0f, y1), Offset(size.width, y1), 1.dp.toPx())
        drawLine(c.inkTertiary, Offset(0f, y2), Offset(size.width, y2), 1.dp.toPx())
        val inset = .75.dp.toPx()
        drawRoundRect(if (state == OfferMarkState.UNKNOWN) c.inkTertiary else c.inkSecondary,
            Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), CornerRadius(3.dp.toPx()),
            style = Stroke(1.5.dp.toPx(), pathEffect = if (state == OfferMarkState.UNKNOWN)
                PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null))
    }
}

/** Layout accepts display data, never recalculates or recolours historical offers. */
@Composable
fun OfferRow(outcome: String, timeAndMeaning: String, rate: String?, state: OfferMarkState,
    onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 66.dp).clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        OfferMark(state, inRow = true)
        BoxWithConstraints(Modifier.weight(1f)) {
            val stacked = LocalDensity.current.fontScale > 1.3f || maxWidth < 250.dp
            val copy: @Composable () -> Unit = {
                Text(outcome, style = KomparaType.outcome)
                Text(timeAndMeaning, style = KomparaType.meaning, color = KomparaTheme.colors.inkSecondary)
            }
            if (stacked) {
                Column {
                    copy()
                    if (state != OfferMarkState.UNKNOWN && rate != null) Text(rate, style = KomparaType.rate, softWrap = false)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) { copy() }
                    if (state != OfferMarkState.UNKNOWN && rate != null) Text(rate, style = KomparaType.rate, softWrap = false)
                }
            }
        }
    }
}
