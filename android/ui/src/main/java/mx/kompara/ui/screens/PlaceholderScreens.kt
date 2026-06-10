package mx.kompara.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.R
import mx.kompara.ui.components.EmptyState
import mx.kompara.ui.components.PrimaryButton
import mx.kompara.ui.stats.AjustesViewModel
import mx.kompara.ui.theme.KomparaTheme

/**
 * Placeholder content for the tabs that don't yet have a real screen (Comparar, Lector). The Inicio
 * tab renders [mx.kompara.ui.screens.InicioDashboardScreen] (B-040); the Fiscal tab renders
 * [mx.kompara.ui.screens.FiscalScreen] (B-051 IMSS tracker); Ajustes is a small launcher into the
 * cost-profile editor, history and the simulator. Copy lives in `strings.xml`.
 */

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

/**
 * Ajustes: a small launcher into the cost-profile editor (B-040), the history weeks list, and the
 * offer simulator (B-037). The full settings list lands in a later UI task.
 */
@Composable
fun AjustesScreen(
    modifier: Modifier = Modifier,
    onOpenSimulator: () -> Unit = {},
    onOpenCostProfile: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    viewModel: AjustesViewModel = hiltViewModel(),
) {
    val fiscalSummaryEnabled by viewModel.fiscalMonthlySummaryEnabled.collectAsStateWithLifecycle()
    AjustesContent(
        modifier = modifier,
        onOpenSimulator = onOpenSimulator,
        onOpenCostProfile = onOpenCostProfile,
        onOpenHistory = onOpenHistory,
        fiscalSummaryEnabled = fiscalSummaryEnabled,
        onFiscalSummaryToggled = viewModel::setFiscalMonthlySummaryEnabled,
    )
}

@Composable
private fun AjustesContent(
    modifier: Modifier = Modifier,
    onOpenSimulator: () -> Unit = {},
    onOpenCostProfile: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    fiscalSummaryEnabled: Boolean = true,
    onFiscalSummaryToggled: (Boolean) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        PrimaryButton(
            text = stringResource(R.string.cost_editor_title),
            onClick = onOpenCostProfile,
            modifier = Modifier.padding(bottom = 0.dp),
        )
        PrimaryButton(text = stringResource(R.string.history_title), onClick = onOpenHistory)
        PrimaryButton(text = stringResource(R.string.ajustes_open_simulator), onClick = onOpenSimulator)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.fiscal_settings_monthly_summary),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(end = 12.dp),
            )
            Switch(checked = fiscalSummaryEnabled, onCheckedChange = onFiscalSummaryToggled)
        }
    }
}

@Preview(showBackground = true, name = "Ajustes — launcher")
@Composable
private fun AjustesScreenPreview() {
    KomparaTheme { AjustesContent() }
}

@Preview(showBackground = true, name = "Lector — placeholder")
@Composable
private fun LectorScreenPreview() {
    KomparaTheme { LectorScreen() }
}
