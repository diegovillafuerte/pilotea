package mx.kompara.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.InicioViewModel
import mx.kompara.ui.R
import mx.kompara.ui.components.EmptyState
import mx.kompara.ui.components.WatchdogBanner
import mx.kompara.ui.onboarding.WatchdogState
import mx.kompara.ui.theme.KomparaTheme

/**
 * The placeholder content for each top-level tab. Real screens land in later UI tasks; for now
 * every tab shows a Spanish [EmptyState] so the shell is fully navigable and on-brand. Copy lives
 * in `strings.xml`.
 */

@Composable
fun InicioScreen(
    modifier: Modifier = Modifier,
    viewModel: InicioViewModel = hiltViewModel(),
) {
    val watchdogState by viewModel.watchdogState.collectAsStateWithLifecycle()
    Column(modifier = modifier) {
        if (watchdogState == WatchdogState.DROPPED) {
            WatchdogBanner(onReEnable = viewModel::reEnableReader)
        }
        EmptyState(
            icon = Icons.Filled.Home,
            title = stringResource(R.string.inicio_empty_title),
            body = stringResource(R.string.inicio_empty_body),
            ctaText = stringResource(R.string.inicio_empty_cta),
            onCtaClick = {},
        )
    }
}

@Composable
fun CompararScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.AutoMirrored.Filled.List,
        title = stringResource(R.string.comparar_empty_title),
        body = stringResource(R.string.comparar_empty_body),
        ctaText = stringResource(R.string.comparar_empty_cta),
        onCtaClick = {},
        modifier = modifier,
    )
}

@Composable
fun LectorScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Filled.PlayArrow,
        title = stringResource(R.string.lector_empty_title),
        body = stringResource(R.string.lector_empty_body),
        ctaText = stringResource(R.string.lector_empty_cta),
        onCtaClick = {},
        modifier = modifier,
    )
}

@Composable
fun FiscalScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Filled.DateRange,
        title = stringResource(R.string.fiscal_empty_title),
        body = stringResource(R.string.fiscal_empty_body),
        ctaText = stringResource(R.string.fiscal_empty_cta),
        onCtaClick = {},
        modifier = modifier,
    )
}

@Composable
fun AjustesScreen(
    modifier: Modifier = Modifier,
    onOpenSimulator: () -> Unit = {},
) {
    // The Ajustes tab is still a placeholder, but it already surfaces the offer simulator (B-037)
    // so drivers can see what Kompara does before their first shift. The CTA opens the simulator
    // route; the real settings list lands in a later UI task.
    EmptyState(
        icon = Icons.Filled.Settings,
        title = stringResource(R.string.ajustes_empty_title),
        body = stringResource(R.string.ajustes_empty_body),
        ctaText = stringResource(R.string.ajustes_open_simulator),
        onCtaClick = onOpenSimulator,
        modifier = modifier,
    )
}

@Preview(showBackground = true, name = "Inicio — placeholder")
@Composable
private fun InicioScreenPreview() {
    // Render the empty state directly: InicioScreen() resolves a hiltViewModel() which the preview
    // renderer can't construct.
    KomparaTheme {
        EmptyState(
            icon = Icons.Filled.Home,
            title = stringResource(R.string.inicio_empty_title),
            body = stringResource(R.string.inicio_empty_body),
            ctaText = stringResource(R.string.inicio_empty_cta),
            onCtaClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Lector — placeholder")
@Composable
private fun LectorScreenPreview() {
    KomparaTheme { LectorScreen() }
}
