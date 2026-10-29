package mx.kompara.ui.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.R
import mx.kompara.ui.components.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

@Composable
fun ShareCardScreen(onClose: () -> Unit, viewModel: ShareCardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShareCardContent(state, onClose, viewModel::setHideAmounts, { viewModel.share() }, { viewModel.share(false) }, viewModel::retry)
}

@Composable
fun ShareCardContent(state: ShareCardUiState, onClose: () -> Unit = {}, onHideAmounts: (Boolean) -> Unit = {},
    onWhatsApp: () -> Unit = {}, onMore: () -> Unit = {}, onRetry: () -> Unit = {}) {
    TaskScaffold(stringResource(R.string.v3_share_title), onClose, footer = {
        PrimaryButton(stringResource(R.string.share_card_whatsapp_cta), onWhatsApp, enabled = state.canShare && !state.sharing)
        KomparaButton(stringResource(R.string.v3_share_more), onMore, variant = ButtonVariant.TEXT,
            fullWidth = true, enabled = state.canShare && !state.sharing)
    }) {
        Text(stringResource(R.string.v3_share_preview), Modifier.padding(top = 8.dp), style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
        Spacer(Modifier.height(8.dp))
        if (state.bitmap != null && state.data != null) {
            Box(Modifier.fillMaxWidth().layout { measurable, constraints ->
                val gutter = 20.dp.roundToPx()
                val child = measurable.measure(constraints.copy(minWidth = constraints.minWidth + 2 * gutter, maxWidth = constraints.maxWidth + 2 * gutter))
                layout(constraints.maxWidth, child.height) { child.placeRelative(-gutter, 0) }
            }.background(KomparaTheme.colors.paperSecondary).padding(horizontal = 20.dp, vertical = 12.dp)) {
                Image(state.bitmap.asImageBitmap(), contentDescription = state.data.description,
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 5f))
            }
        } else if (state.loading) {
            Text("Preparando tu resumen…", Modifier.padding(vertical = 32.dp), style = KomparaType.body)
        }
        state.error?.let {
            Text(it, Modifier.padding(vertical = 16.dp), style = KomparaType.body)
            if (state.data == null) KomparaButton("Reintentar", onRetry, variant = ButtonVariant.TEXT)
        }
        Spacer(Modifier.height(16.dp))
        Rule()
        val label = stringResource(R.string.v3_share_hide)
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f).padding(end = 12.dp), style = KomparaType.body)
            KomparaSwitch(state.hideAmounts, onHideAmounts, Modifier.semantics { contentDescription = label })
        }
        Rule()
    }
}
