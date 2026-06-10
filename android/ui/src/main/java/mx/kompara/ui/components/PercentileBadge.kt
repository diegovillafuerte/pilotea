package mx.kompara.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.kompara.ui.R
import mx.kompara.ui.theme.BrandGreen
import mx.kompara.ui.theme.KomparaTheme

/**
 * The "Top X%" percentile pill shown on a metric card (B-046). For a higher-is-better metric a 78th
 * display-percentile driver reads "Top 22%". The caller passes the already-computed [topPercent]
 * (`100 - displayPercentile`, floored at 1 — see `PercentileResult.topPercent`), so the inversion for
 * commission-style metrics is already baked in upstream and this component is purely presentational.
 *
 * @param topPercent the "top X%" figure (1–99).
 * @param contentDescription localized spoken description, e.g. "Mejor que el 78% de los choferes".
 */
@Composable
fun PercentileBadge(
    topPercent: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.metric_percentile_format, topPercent),
        color = MaterialTheme.colorScheme.onPrimary,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(BrandGreen)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    )
}

/**
 * The locked variant of the percentile badge (B-046 gating PREP; B-050 enforces). Renders a neutral
 * "Kompara Premium" pill in place of the real "Top X%" so a free driver sees the *value* of the gated
 * feature without leaking the actual number. Tapping is wired by the caller (a future paywall route).
 */
@Composable
fun LockedPercentileBadge(
    modifier: Modifier = Modifier,
) {
    Text(
        text = "🔒 " + stringResource(R.string.percentile_premium_hint),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Preview(showBackground = true, name = "PercentileBadge — Top 22%")
@Composable
private fun PercentileBadgePreview() {
    KomparaTheme {
        PercentileBadge(
            topPercent = 22,
            contentDescription = "Mejor que el 78% de los choferes",
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, name = "PercentileBadge — bloqueado")
@Composable
private fun LockedPercentileBadgePreview() {
    KomparaTheme {
        LockedPercentileBadge(modifier = Modifier.padding(16.dp))
    }
}
