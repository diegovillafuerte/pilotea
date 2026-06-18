package mx.kompara.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.kompara.metrics.VerdictLevel
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.theme.KomparaType
import mx.kompara.ui.theme.KomparaTheme

/**
 * The workhorse of the stats surfaces: a label, a big glanceable [value] numeral, and an optional
 * trailing slot (typically a percentile [VerdictBadge] or a "Top 10 %" pill).
 *
 * The value uses [KomparaType.metricValue] so figures like "$1,234.56" read from arm's length.
 * Callers format their own numbers (e.g. via [Formatters]) and pass the finished string.
 *
 * @param badge optional composable rendered to the right of the label — a percentile/verdict chip.
 */
@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
) {
    KomparaCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = KomparaType.metricLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    badge()
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = KomparaType.metricValue,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Preview(showBackground = true, name = "MetricCard — sin badge")
@Composable
private fun MetricCardPreview() {
    KomparaTheme {
        MetricCard(
            label = "GANANCIA NETA HOY",
            value = Formatters.formatMxn(1234.56),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, name = "MetricCard — con percentil")
@Composable
private fun MetricCardWithBadgePreview() {
    KomparaTheme {
        MetricCard(
            label = "$ POR HORA",
            value = Formatters.formatPerHour(185.5),
            modifier = Modifier.padding(16.dp),
            badge = { VerdictBadge(level = VerdictLevel.GREEN) },
        )
    }
}
