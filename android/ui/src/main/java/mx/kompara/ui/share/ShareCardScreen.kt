package mx.kompara.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.R
import mx.kompara.ui.components.KomparaButton
import mx.kompara.ui.components.KomparaSwitch
import mx.kompara.ui.theme.BrandGreen
import mx.kompara.ui.theme.BrandGreenDark
import mx.kompara.ui.theme.BrandGreenLight
import mx.kompara.ui.theme.KomparaType

/**
 * The "Tu mes" share screen (B-055, route [mx.kompara.ui.nav.KomparaDestination.SHARE_CARD_ROUTE]):
 * a Wrapped-style emerald-gradient hero rendered INLINE in Compose (so the text/colours match the
 * design system exactly), a hide-amounts toggle, and the "Compartir en WhatsApp" CTA.
 *
 * The inline hero is the on-screen *preview*; sharing still produces the deterministic [ShareCardRenderer]
 * bitmap (the variant machinery lives on in the ViewModel) so the shared PNG is unchanged.
 */
@Composable
fun ShareCardScreen(
    modifier: Modifier = Modifier,
    viewModel: ShareCardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShareCardContent(
        state = state,
        onHideAmounts = viewModel::setHideAmounts,
        onShare = viewModel::share,
        modifier = modifier,
    )
}

@Composable
private fun ShareCardContent(
    state: ShareCardUiState,
    onHideAmounts: (Boolean) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.share_card_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.loading || state.data == null) {
            Spacer(Modifier.height(48.dp))
            CircularProgressIndicator()
        } else {
            TuMesHeroCard(data = state.data)

            KomparaButton(
                text = stringResource(R.string.share_card_whatsapp_cta),
                onClick = onShare,
                fullWidth = true,
                leadingIcon = Icons.Filled.Share,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.share_card_hide_amounts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
                KomparaSwitch(checked = state.hideAmounts, onCheckedChange = onHideAmounts)
            }
        }
    }
}

/**
 * The Wrapped-style emerald hero: brand row, "Ganancia neta del mes", the big net number, and the
 * 2×2 brag grid (Tu lugar / Mejor app / Mejor día / Racha). White text on the brand-emerald gradient
 * (never a verdict colour). Cells with no data render a graceful dash rather than a blank.
 */
@Composable
private fun TuMesHeroCard(data: ShareCardData) {
    val whiteHigh = Color.White.copy(alpha = 0.95f)
    val whiteMid = Color.White.copy(alpha = 0.90f)
    val whiteLow = Color.White.copy(alpha = 0.85f)
    val dash = stringResource(R.string.share_card_value_dash)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(listOf(BrandGreenDark, BrandGreen, BrandGreenLight)),
            )
            .padding(22.dp),
    ) {
        // Brand row — "K  Kompara · Junio" (just the month, no year): the K logomark tile (white on a
        // translucent-white tile, matching KomparaWordmark's onEmerald variant) + the brand-row text.
        val monthOnly = data.periodLabel.substringBefore(' ')
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_kompara_logomark),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = stringResource(R.string.share_card_brand_row, monthOnly),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = whiteHigh,
            )
        }

        Text(
            text = stringResource(R.string.share_card_net_label),
            style = MaterialTheme.typography.bodyMedium,
            color = whiteMid,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = data.netEarnings ?: stringResource(R.string.share_card_net_hidden),
            style = KomparaType.metricValueLarge.copy(fontSize = 46.sp, lineHeight = 48.sp),
            color = Color.White,
        )

        // 2×2 brag grid.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BragRow(
                left = stringResource(R.string.share_card_label_place) to
                    (ShareCardComposer.placeFlex(data.percentileFlex) ?: dash),
                right = stringResource(R.string.share_card_label_best_app) to (data.bestApp ?: dash),
                labelColor = whiteLow,
            )
            BragRow(
                left = stringResource(R.string.share_card_label_best_day) to (data.bestDay ?: dash),
                right = stringResource(R.string.share_card_label_streak) to streakValue(data, dash),
                labelColor = whiteLow,
            )
        }
    }
}

/** The compact "Racha" grid value ("🔥 4 sem"), derived from the streak line, or a dash. */
private fun streakValue(data: ShareCardData, dash: String): String {
    val line = data.streakLine ?: return dash
    // streakLine is "🔥 N semanas seguidas" / "🔥 1 semana seguida"; the grid wants "🔥 N sem".
    val n = line.filter { it.isDigit() }
    return if (n.isEmpty()) dash else "🔥 $n sem"
}

@Composable
private fun BragRow(
    left: Pair<String, String>,
    right: Pair<String, String>,
    labelColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BragCell(label = left.first, value = left.second, labelColor = labelColor, modifier = Modifier.weight(1f))
        BragCell(label = right.first, value = right.second, labelColor = labelColor, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BragCell(
    label: String,
    value: String,
    labelColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = labelColor,
        )
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight(800),
            color = Color.White,
        )
    }
}
