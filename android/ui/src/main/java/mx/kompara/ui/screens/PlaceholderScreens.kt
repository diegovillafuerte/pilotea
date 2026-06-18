package mx.kompara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.BuildConfig
import mx.kompara.ui.R
import mx.kompara.ui.components.KomparaSwitch
import mx.kompara.ui.stats.AjustesViewModel
import mx.kompara.ui.theme.KomparaTheme

/**
 * Ajustes: the settings list — account, costs, semáforo, simulator, referral, help — plus the
 * notification toggles and the debug-only premium override. Each row launches its destination
 * (B-040 / B-037 / B-069). One tonal list-row per entry, matching the design system's Ajustes screen.
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_ajustes),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))

        SettingsNavRow(stringResource(R.string.account_entry_title), onOpenAccount)
        SettingsNavRow(stringResource(R.string.cost_editor_title), onOpenCostProfile)
        SettingsNavRow(stringResource(R.string.thresholds_title), onOpenThresholds)
        SettingsNavRow(stringResource(R.string.history_title), onOpenHistory)
        SettingsNavRow(stringResource(R.string.ajustes_open_simulator), onOpenSimulator)
        SettingsNavRow(stringResource(R.string.referral_entry_title), onOpenReferral)
        SettingsNavRow(stringResource(R.string.help_title), onOpenHelp)

        Spacer(Modifier.height(8.dp))
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

/** A tonal settings row (surface-card, 12dp radius, 16/14 padding); optionally tappable. */
@Composable
private fun SettingsRowSurface(
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shaped = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceContainer)
    Row(
        modifier = (if (onClick != null) shaped.clickable(onClick = onClick) else shaped)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A navigation row: label + chevron, taps through to a destination. */
@Composable
private fun SettingsNavRow(label: String, onClick: () -> Unit) {
    SettingsRowSurface(onClick = onClick) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A settings row with a trailing toggle. */
@Composable
private fun SettingToggleRow(label: String, checked: Boolean, onToggled: (Boolean) -> Unit) {
    SettingsRowSurface {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        KomparaSwitch(checked = checked, onCheckedChange = onToggled)
    }
}

@Preview(showBackground = true, name = "Ajustes — lista")
@Composable
private fun AjustesScreenPreview() {
    KomparaTheme { AjustesContent() }
}

@Preview(showBackground = true, name = "Lector — placeholder")
@Composable
private fun LectorScreenPreview() {
    KomparaTheme { LectorScreen() }
}
