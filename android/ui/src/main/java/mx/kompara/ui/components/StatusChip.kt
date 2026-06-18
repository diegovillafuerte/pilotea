package mx.kompara.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.VerdictGreen
import mx.kompara.ui.theme.VerdictRed
import mx.kompara.ui.theme.VerdictYellow

/** The state a [KomparaStatusChip] reports — coverage, connection and similar on/off conditions. */
enum class StatusLevel { OK, WARNING, ERROR, NEUTRAL }

/** Brand colour that carries a status level. The dot, hairline and label all derive from it. */
private val StatusLevel.color: Color
    @Composable
    get() = when (this) {
        StatusLevel.OK -> VerdictGreen
        StatusLevel.WARNING -> VerdictYellow
        StatusLevel.ERROR -> VerdictRed
        StatusLevel.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }

/**
 * A small tinted status pill — a coloured dot plus a caller-supplied label for coverage / connection
 * states ("Cubierto este mes", "En camino", "No cubierto", "Importado"). The colour is never the only
 * signal: it always rides alongside the label so the meaning survives a glance or a colour-blind eye.
 * Not a verdict — use [VerdictBadge] for the verde/amarillo/rojo semáforo.
 */
@Composable
fun KomparaStatusChip(
    label: String,
    level: StatusLevel,
    modifier: Modifier = Modifier,
) {
    val color = level.color
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.35f)), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Spacer(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Preview(showBackground = true, name = "StatusChip — estados")
@Composable
private fun KomparaStatusChipPreview() {
    KomparaTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KomparaStatusChip(label = "Cubierto este mes", level = StatusLevel.OK)
            KomparaStatusChip(label = "En camino", level = StatusLevel.WARNING)
            KomparaStatusChip(label = "No cubierto", level = StatusLevel.ERROR)
            KomparaStatusChip(label = "Importado", level = StatusLevel.NEUTRAL)
        }
    }
}
