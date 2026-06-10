package mx.kompara.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.kompara.metrics.recommendation.RecommendationType
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.VerdictGreen
import mx.kompara.ui.theme.VerdictRed
import mx.kompara.ui.theme.VerdictYellow

/**
 * One "Consejo" card on the Inicio dashboard (B-048) — the on-device port of the web's
 * `recommendation-card.tsx`. Type-styled with the verdict palette drivers already read: a verde
 * accent for [RecommendationType.POSITIVE] praise, ámbar for [RecommendationType.WARNING] (a money
 * leak), and azul for [RecommendationType.INFO] actionable tips. A leading icon + bold title + body.
 *
 * Purely presentational: the engine decides what fires and the viewmodel selects the top 3; this just
 * paints one. The whole card is wrapped by `PaywallGate` upstream when the recommendation is premium,
 * so this composable never has to know about gating.
 *
 * @param type drives the accent colour and icon.
 * @param title the short headline.
 * @param body the one-or-two-sentence detail.
 */
@Composable
fun RecommendationCard(
    type: RecommendationType,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    val accent = accentFor(type)
    val icon = iconFor(type)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The verdict-palette accent for a [RecommendationType] (verde / ámbar / azul). */
private fun accentFor(type: RecommendationType): Color = when (type) {
    RecommendationType.POSITIVE -> VerdictGreen
    RecommendationType.WARNING -> VerdictYellow
    RecommendationType.INFO -> INFO_BLUE
}

private fun iconFor(type: RecommendationType): ImageVector = when (type) {
    RecommendationType.POSITIVE -> Icons.Filled.CheckCircle
    RecommendationType.WARNING -> Icons.Filled.Warning
    RecommendationType.INFO -> Icons.Filled.Info
}

/** Azul accent for actionable tips — sits outside the verdict triple but reads as "info/acción". */
private val INFO_BLUE = Color(0xFF2D77E0)

@Preview(showBackground = true, name = "Consejos — los tres tipos")
@Composable
private fun RecommendationCardPreview() {
    KomparaTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RecommendationCard(
                type = RecommendationType.WARNING,
                title = "Dejaste ir buenos viajes",
                body = "Rechazaste 3 ofertas que sí te convenían — \$420 que se te fueron.",
            )
            RecommendationCard(
                type = RecommendationType.INFO,
                title = "Tus mejores horas",
                body = "Tu mejor bloque fue el viernes de 19:00 a 20:00: \$340 netos. Maneja más en ese horario.",
            )
            RecommendationCard(
                type = RecommendationType.POSITIVE,
                title = "¡4 semanas seguidas!",
                body = "Llevas 4 semanas registrando tus datos sin parar.",
            )
            // A spacer to show the cards keep their height with short copy.
            Box(Modifier.height(8.dp))
        }
    }
}
