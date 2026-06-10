package mx.kompara.ui.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.R
import mx.kompara.ui.components.PrimaryButton

/**
 * The share-card preview screen (B-055, route [mx.kompara.ui.nav.KomparaDestination.SHARE_CARD_ROUTE]):
 * shows the rendered "Tu Semana" card, a hide-amounts toggle, a story/landscape variant toggle, and
 * the "Compartir" button (WhatsApp-preferred share sheet).
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
        onVariant = viewModel::setVariant,
        onShare = viewModel::share,
        modifier = modifier,
    )
}

@Composable
private fun ShareCardContent(
    state: ShareCardUiState,
    onHideAmounts: (Boolean) -> Unit,
    onVariant: (ShareCardVariant) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.share_card_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.loading || state.bitmap == null) {
            Spacer(Modifier.height(48.dp))
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = state.bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.share_card_preview_desc),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (state.variant == ShareCardVariant.STORY) 420.dp else 220.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )

            VariantToggle(selected = state.variant, onVariant = onVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.share_card_hide_amounts),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Switch(checked = state.hideAmounts, onCheckedChange = onHideAmounts)
            }

            PrimaryButton(text = stringResource(R.string.share_card_share_cta), onClick = onShare)
        }
    }
}

@Composable
private fun VariantToggle(
    selected: ShareCardVariant,
    onVariant: (ShareCardVariant) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == ShareCardVariant.STORY,
            onClick = { onVariant(ShareCardVariant.STORY) },
            label = { Text(stringResource(R.string.share_card_variant_story)) },
        )
        FilterChip(
            selected = selected == ShareCardVariant.LANDSCAPE,
            onClick = { onVariant(ShareCardVariant.LANDSCAPE) },
            label = { Text(stringResource(R.string.share_card_variant_landscape)) },
        )
    }
}
