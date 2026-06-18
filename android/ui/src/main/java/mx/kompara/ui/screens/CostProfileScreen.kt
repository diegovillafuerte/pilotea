package mx.kompara.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.R
import mx.kompara.ui.components.CardTone
import mx.kompara.ui.components.KomparaCard
import mx.kompara.ui.components.KomparaSwitch
import mx.kompara.ui.components.KomparaTextField
import mx.kompara.ui.components.PrimaryButton
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.stats.CostPreviewResult
import mx.kompara.ui.stats.CostProfileInputs
import mx.kompara.ui.stats.CostProfileViewModel
import mx.kompara.ui.theme.KomparaTheme

/**
 * The cost-profile editor (B-040 req 4): rendimiento + gas price (or EV kWh fields), maintenance,
 * insurance and rent, with a live "tu costo por km: $X.XX" preview and its effect on a sample trip.
 * Saving persists the profile and recomputes historical net.
 *
 * @param onSaved called after a successful save (the host pops back to Ajustes).
 */
@Composable
fun CostProfileScreen(
    modifier: Modifier = Modifier,
    onSaved: () -> Unit = {},
    viewModel: CostProfileViewModel = hiltViewModel(),
) {
    val inputs by viewModel.inputs.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    LaunchedEffect(saved) { if (saved) onSaved() }

    CostProfileContent(
        inputs = inputs,
        preview = viewModel.preview(inputs),
        onEvToggled = viewModel::onEvToggled,
        onRendimientoChanged = viewModel::onRendimientoChanged,
        onGasPriceChanged = viewModel::onGasPriceChanged,
        onKwhPer100Changed = viewModel::onKwhPer100Changed,
        onCostPerKwhChanged = viewModel::onCostPerKwhChanged,
        onMaintenanceChanged = viewModel::onMaintenanceChanged,
        onInsuranceChanged = viewModel::onInsuranceChanged,
        onRentChanged = viewModel::onRentChanged,
        onSave = viewModel::save,
        modifier = modifier,
    )
}

@Composable
private fun CostProfileContent(
    inputs: CostProfileInputs,
    preview: CostPreviewResult,
    onEvToggled: (Boolean) -> Unit,
    onRendimientoChanged: (String) -> Unit,
    onGasPriceChanged: (String) -> Unit,
    onKwhPer100Changed: (String) -> Unit,
    onCostPerKwhChanged: (String) -> Unit,
    onMaintenanceChanged: (String) -> Unit,
    onInsuranceChanged: (String) -> Unit,
    onRentChanged: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.cost_editor_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.cost_editor_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PreviewCard(preview)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.cost_editor_ev_toggle), style = MaterialTheme.typography.bodyLarge)
            KomparaSwitch(checked = inputs.isEv, onCheckedChange = onEvToggled)
        }

        if (inputs.isEv) {
            NumberField(stringResource(R.string.cost_editor_kwh_per_100), inputs.kwhPer100Km, onKwhPer100Changed)
            NumberField(stringResource(R.string.cost_editor_cost_per_kwh), inputs.costPerKwhMxn, onCostPerKwhChanged)
        } else {
            NumberField(stringResource(R.string.cost_editor_rendimiento), inputs.rendimientoKmPerLitre, onRendimientoChanged)
            NumberField(stringResource(R.string.cost_editor_gas_price), inputs.gasPricePerLitreMxn, onGasPriceChanged)
        }

        NumberField(stringResource(R.string.cost_editor_maintenance), inputs.maintenancePerKmMxn, onMaintenanceChanged)
        NumberField(stringResource(R.string.cost_editor_insurance), inputs.insurancePerDayMxn, onInsuranceChanged)
        NumberField(stringResource(R.string.cost_editor_rent), inputs.rentPerDayMxn, onRentChanged)

        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            text = stringResource(R.string.cost_editor_save),
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PreviewCard(preview: CostPreviewResult) {
    KomparaCard(
        modifier = Modifier.fillMaxWidth(),
        tone = CardTone.VARIANT,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.cost_editor_preview_per_km, Formatters.formatMxn(preview.marginalCostPerKmMxn)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.cost_editor_preview_sample,
                    Formatters.formatMxn(preview.sampleFareMxn),
                    Formatters.formatKm(preview.sampleKm),
                    Formatters.formatMxn(preview.sampleNetMxn),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    KomparaTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(showBackground = true, name = "Cost profile — gasolina")
@Composable
private fun CostProfilePreview() {
    KomparaTheme {
        CostProfileContent(
            inputs = CostProfileInputs(
                rendimientoKmPerLitre = "14",
                gasPricePerLitreMxn = "24.5",
                maintenancePerKmMxn = "0.8",
                insurancePerDayMxn = "30",
                rentPerDayMxn = "150",
            ),
            preview = mx.kompara.ui.stats.CostPreview.compute(
                isEv = false,
                rendimientoKmPerLitre = 14.0,
                gasPricePerLitreMxn = 24.5,
                kwhPer100Km = 0.0,
                costPerKwhMxn = 0.0,
                maintenancePerKmMxn = 0.8,
            ),
            onEvToggled = {}, onRendimientoChanged = {}, onGasPriceChanged = {},
            onKwhPer100Changed = {}, onCostPerKwhChanged = {}, onMaintenanceChanged = {},
            onInsuranceChanged = {}, onRentChanged = {}, onSave = {},
        )
    }
}
