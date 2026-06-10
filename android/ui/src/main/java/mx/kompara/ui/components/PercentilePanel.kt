package mx.kompara.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.kompara.ui.R
import mx.kompara.ui.theme.KomparaTheme

/**
 * The full per-metric percentile block beneath a metric card (B-046): the 20-person [PercentileBar],
 * an optional "datos de muestra" tag when the benchmark is still synthetic, and a [locked] state.
 *
 * ## Gating (PREP — B-050 enforces)
 * When [locked] is true the bar is dimmed (a lightweight stand-in for a blur; `Modifier.blur` needs
 * API 31 and the app targets API 26) and a "Kompara Premium" hint overlays it, so a free driver sees
 * the *shape* of the insight and the upsell, never the real standing. The caller passes
 * `locked = !canSeeBenchmarks`. The real number is never composed when locked.
 *
 * @param displayPercentile driver's 1–99 display percentile (already inverted for lower-is-better).
 * @param barContentDescription localized description spoken for the unlocked bar.
 * @param isSynthetic true while the benchmark is a synthetic seed → show the sample-data caveat.
 * @param locked true to render the premium-locked stand-in instead of the real standing.
 */
@Composable
fun PercentilePanel(
    displayPercentile: Int,
    barContentDescription: String,
    isSynthetic: Boolean,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (locked) {
            LockedBar()
        } else {
            PercentileBar(
                displayPercentile = displayPercentile,
                contentDescription = barContentDescription,
            )
            if (isSynthetic) {
                Text(
                    text = stringResource(R.string.percentile_synthetic_tag),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Dimmed bar + centered "Kompara Premium" hint — the locked stand-in (B-046 gating PREP). */
@Composable
private fun LockedBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "Comparativa disponible con Kompara Premium"
            },
        contentAlignment = Alignment.Center,
    ) {
        // A neutral, position-free bar (always ~mid) so it can't leak the real standing; dimmed.
        PercentileBar(
            displayPercentile = 50,
            contentDescription = "",
            modifier = Modifier.alpha(0.20f),
        )
        LockedPercentileBadge()
    }
}

@Preview(showBackground = true, name = "PercentilePanel — sintético")
@Composable
private fun PercentilePanelSyntheticPreview() {
    KomparaTheme {
        PercentilePanel(
            displayPercentile = 78,
            barContentDescription = "Estás por encima del 78% de los choferes",
            isSynthetic = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, name = "PercentilePanel — bloqueado")
@Composable
private fun PercentilePanelLockedPreview() {
    KomparaTheme {
        PercentilePanel(
            displayPercentile = 78,
            barContentDescription = "",
            isSynthetic = false,
            locked = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}
