package mx.kompara.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
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
import mx.kompara.ui.BuildConfig
import mx.kompara.ui.R
import mx.kompara.ui.components.EmptyState
import mx.kompara.ui.components.KomparaSwitch
import mx.kompara.ui.components.PrimaryButton
import mx.kompara.ui.stats.AjustesViewModel
import mx.kompara.ui.theme.KomparaTheme

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
    onOpenReferral: () -> Unit = {},
    onOpenThresholds: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
    viewModel: AjustesViewModel = hiltViewModel(),
) {
    val fiscalSummaryEnabled by viewModel.fiscalMonthlySummaryEnabled.collectAsStateWithLifecycle()
    val shareReminderEnabled by viewModel.shareWeeklyReminderEnabled.collectAsStateWithLifecycle()
    val debugPremiumEnabled by viewModel.debugPremiumEnabled.collectAsStateWithLifecycle()
    AjustesContent(
        modifier = modifier,
        onOpenSimulator = onOpenSimulator,
        onOpenCostProfile = onOpenCostProfile,
        onOpenHistory = onOpenHistory,
        onOpenReferral = onOpenReferral,
        onOpenThresholds = onOpenThresholds,
        onOpenHelp = onOpenHelp,
        onOpenAccount = onOpenAccount,
        fiscalSummaryEnabled = fiscalSummaryEnabled,
        onFiscalSummaryToggled = viewModel::setFiscalMonthlySummaryEnabled,
        shareReminderEnabled = shareReminderEnabled,
        onShareReminderToggled = viewModel::setShareWeeklyReminderEnabled,
        debugPremiumEnabled = debugPremiumEnabled,
        onDebugPremiumToggled = viewModel::setDebugPremium,
    )
}

@Composable
private fun AjustesContent(
    modifier: Modifier = Modifier,
    onOpenSimulator: () -> Unit = {},
    onOpenCostProfile: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenReferral: () -> Unit = {},
    onOpenThresholds: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
    fiscalSummaryEnabled: Boolean = true,
    onFiscalSummaryToggled: (Boolean) -> Unit = {},
    shareReminderEnabled: Boolean = true,
    onShareReminderToggled: (Boolean) -> Unit = {},
    debugPremiumEnabled: Boolean = false,
    onDebugPremiumToggled: (Boolean) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        PrimaryButton(
            text = stringResource(R.string.account_entry_title),
            onClick = onOpenAccount,
        )
        PrimaryButton(
            text = stringResource(R.string.cost_editor_title),
            onClick = onOpenCostProfile,
            modifier = Modifier.padding(bottom = 0.dp),
        )
        PrimaryButton(text = stringResource(R.string.thresholds_title), onClick = onOpenThresholds)
        PrimaryButton(text = stringResource(R.string.history_title), onClick = onOpenHistory)
        PrimaryButton(text = stringResource(R.string.ajustes_open_simulator), onClick = onOpenSimulator)
        PrimaryButton(text = stringResource(R.string.referral_entry_title), onClick = onOpenReferral)
        PrimaryButton(text = stringResource(R.string.help_title), onClick = onOpenHelp)

        SettingToggleRow(
            label = stringResource(R.string.fiscal_settings_monthly_summary),
            checked = fiscalSummaryEnabled,
            onToggled = onFiscalSummaryToggled,
        )
        SettingToggleRow(
            label = stringResource(R.string.share_settings_weekly_reminder),
            checked = shareReminderEnabled,
            onToggled = onShareReminderToggled,
        )
        // Debug-only: unlock the paywall to preview the premium experience (Play Billing is
        // unavailable in dev). Stripped from release builds via BuildConfig.DEBUG.
        if (BuildConfig.DEBUG) {
            SettingToggleRow(
                label = stringResource(R.string.ajustes_debug_premium),
                checked = debugPremiumEnabled,
                onToggled = onDebugPremiumToggled,
            )
        }
    }
}

@Composable
private fun SettingToggleRow(label: String, checked: Boolean, onToggled: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = 12.dp),
        )
        KomparaSwitch(checked = checked, onCheckedChange = onToggled)
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
