package mx.kompara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.data.settings.RateBasis
import mx.kompara.ui.components.*
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

@Composable
fun CostProfileScreen(onBack: () -> Unit, efficiency: Boolean = false,
    modifier: Modifier = Modifier, viewModel: CostProfileViewModel = hiltViewModel(), carModel: CarCostViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val car by carModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onBack() }
    if (efficiency && state.saved?.isEv != true && state.saved?.direct != true) {
        EfficiencyContent(state, onBack, viewModel::edit, { viewModel.save(finish = true) }, { viewModel.open(CostField.DIRECT) }, modifier)
    } else CostProfileContent(state, onBack, viewModel::open, viewModel::load, modifier, carModel::open, viewModel::switchBasis)
    state.field?.let { field ->
        SheetScaffold(field.title, viewModel::dismiss) {
            CostInputContent(state, viewModel::edit, { viewModel.save(finish = efficiency) })
        }
    }
    if (car.open) CarCostSheet(car, carModel::amount, carModel::date, { carModel.save() }, carModel::dismiss,
        carModel::confirmRemove, { carModel.save(remove = true) }, carModel::open)
    if (state.confirmHistory) AlertDialog(onDismissRequest = viewModel::dismissHistory,
        title = { Text("¿Aplicar también a tus viajes anteriores?", style = KomparaType.title) },
        text = { Text("Desde ${state.historyFrom?.let { costDate(java.time.LocalDate.parse(it)) }}. Conservamos el precio de cada fecha y el semáforo que viste.", style = KomparaType.body) },
        confirmButton = { TextButton({ viewModel.save(efficiency, applyHistory = true) }) { Text("Aplicar también", color = KomparaTheme.colors.ink) } },
        dismissButton = { TextButton({ viewModel.save(efficiency, applyHistory = false) }) { Text("Solo desde hoy", color = KomparaTheme.colors.ink) } })
}

/**
 * J3: the cost per km as the headline, the same neto/bruto choice as J2 applied at once, 0.2.20's
 * editors, then the car's weekly amount (v4 plan 4.8).
 */
@Composable
fun CostProfileContent(state: CostEditorState, back: () -> Unit, open: (CostField) -> Unit,
    retry: () -> Unit = {}, modifier: Modifier = Modifier, openCar: () -> Unit = {}, choose: (RateBasis) -> Unit = {}) {
    ScreenScaffold("Tus costos", modifier, ScreenKind.DRILL, back) {
        val inputs = state.saved
        if (inputs == null) {
            if (state.error != null) { InkNotice(state.error); TextButton(retry) { Text("Reintentar", color = KomparaTheme.colors.ink) } }
            else StaticSkeleton(Modifier.fillMaxWidth().height(200.dp))
            return@ScreenScaffold
        }
        val cost = CostPreview.compute(inputs)?.perKm
        Spacer(Modifier.height(8.dp))
        Text("Manejar te cuesta", style = KomparaType.meaning, color = KomparaTheme.colors.inkSecondary)
        CostRate(cost?.let(Formatters::formatMxn) ?: "Sin confirmar")
        Spacer(Modifier.height(10.dp))
        SettingsChoice("El semáforo muestra", listOf("Neto", "Bruto"), state.basis.ordinal, { choose(RateBasis.entries[it]) },
            labelWidth = 0.dp, enabled = cost != null && !state.switching)
        Text("Neto resta este costo en el semáforo. Bruto no.", Modifier.padding(top = 8.dp), style = RowDetail, color = KomparaTheme.colors.inkSecondary)
        state.basisResult?.let { Text(it, Modifier.padding(top = 4.dp), style = RowDetail, color = KomparaTheme.colors.ink) }
        Spacer(Modifier.height(8.dp))
        if (inputs.direct) {
            AjustesRow("Combustible", value = "Costo directo", onClick = { open(CostField.FUEL) })
            AjustesRow("Costo por km", value = "${inputs.directPerKm.let(::costDecimal)?.let(Formatters::formatMxn) ?: "—"} · incluye desgaste", onClick = { open(CostField.DIRECT) })
        } else {
            if (inputs.isEv) {
                AjustesRow("Combustible", value = "Eléctrico", onClick = { open(CostField.FUEL) })
                AjustesRow("Consumo de energía", value = "${inputs.kwhPer100Km} kWh por 100 km", onClick = { open(CostField.CONSUMPTION) })
                AjustesRow("Precio de la energía", value = "${costDecimal(inputs.costPerKwhMxn)?.let(Formatters::formatMxn) ?: "—"} por kWh", onClick = { open(CostField.TARIFF) })
            } else {
                AjustesRow("Rendimiento", value = "${inputs.rendimientoKmPerLitre} km por litro" + if (state.typical) " · típico" else "", onClick = { open(CostField.EFFICIENCY) })
                // The fuel sheet holds the price and the gasoline/electric choice, so an electric car stays reachable.
                AjustesRow("Gasolina", value = "${costDecimal(inputs.gasPricePerLitreMxn)?.let(Formatters::formatMxn) ?: "—"} por litro" + if (state.typical) " · referencia" else "", onClick = { open(CostField.FUEL) })
            }
            AjustesRow("Desgaste", value = "${costDecimal(inputs.maintenancePerKmMxn)?.let(Formatters::formatMxn) ?: "—"} por km", onClick = { open(CostField.WEAR) })
        }
        AjustesRow("Escribir mi costo por km", onClick = { open(CostField.DIRECT) })
        Text("El coche", Modifier.padding(top = 28.dp, bottom = 4.dp), style = KomparaType.section.copy(fontSize = 20.sp, lineHeight = 24.sp))
        AjustesRow("Renta, crédito o seguro", state.car, onClick = openCar)
        Text("Lo ves en tu semana. No cambia el semáforo.", Modifier.padding(top = 12.dp), style = RowDetail, color = KomparaTheme.colors.inkSecondary)
        state.error?.let { InkNotice(it) }
    }
}

@Composable
private fun CostRate(value: String) {
    Text(buildAnnotatedString {
        append("$value ")
        withStyle(SpanStyle(fontFamily = KomparaType.body.fontFamily, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal, color = KomparaTheme.colors.inkSecondary)) { append("por km") }
    }, Modifier.padding(top = 2.dp), style = KomparaType.efficiency.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
}

/** Filled, labelled numeric field. It grows with the native font scale instead of clipping. */
@Composable
internal fun CostNumberField(label: String, value: String, change: (String) -> Unit, modifier: Modifier = Modifier, prefix: String = "", grouped: Boolean = false) {
    Column(modifier.fillMaxWidth().background(KomparaTheme.colors.paperSecondary, RoundedCornerShape(8.dp))
        .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 5.dp)) {
        Text(label, style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (prefix.isNotEmpty()) Text(prefix, style = KomparaType.title)
            BasicTextField(value, change, Modifier.weight(1f).heightIn(min = 32.dp).semantics { contentDescription = label },
                textStyle = KomparaType.number.copy(fontSize = 20.sp, lineHeight = 26.sp, color = KomparaTheme.colors.ink), singleLine = true,
                visualTransformation = if (grouped) mx.kompara.ui.format.DecimalGrouping else androidx.compose.ui.text.input.VisualTransformation.None,
                cursorBrush = SolidColor(KomparaTheme.colors.brand), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
    }
}

@Composable
fun CostInputContent(state: CostEditorState, edit: (CostProfileInputs) -> Unit, save: () -> Unit) {
    val draft = state.draft ?: return
    val field = state.field ?: return
    if (field == CostField.FUEL) {
        SettingsValueRow("Gasolina", glyph = if (!draft.isEv && !draft.direct) StatusGlyph.DONE else null,
            onClick = { edit(draft.copy(isEv = false, direct = false)) })
        SettingsValueRow("Eléctrico", glyph = if (draft.isEv && !draft.direct) StatusGlyph.DONE else null,
            onClick = { edit(draft.copy(isEv = true, direct = false)) })
        Spacer(Modifier.height(16.dp))
        if (draft.isEv) {
            CostNumberField("kWh por 100 km", draft.kwhPer100Km, { edit(draft.copy(kwhPer100Km = it)) })
            Spacer(Modifier.height(8.dp))
            CostNumberField("Precio por kWh", draft.costPerKwhMxn, { edit(draft.copy(costPerKwhMxn = it)) }, prefix = "$")
        } else {
            CostNumberField("km por litro", draft.rendimientoKmPerLitre, { edit(draft.copy(rendimientoKmPerLitre = it)) })
            Spacer(Modifier.height(8.dp))
            CostNumberField("Precio por litro", draft.gasPricePerLitreMxn, { edit(draft.copy(gasPricePerLitreMxn = it)) }, prefix = "$")
        }
        Spacer(Modifier.height(8.dp))
        CostNumberField("Desgaste por km", draft.maintenancePerKmMxn, { edit(draft.copy(maintenancePerKmMxn = it)) }, prefix = "$")
    } else when (field) {
        CostField.EFFICIENCY -> CostNumberField("km por litro", draft.rendimientoKmPerLitre, { edit(draft.copy(rendimientoKmPerLitre = it)) })
        CostField.PRICE -> CostNumberField("Precio por litro", draft.gasPricePerLitreMxn, { edit(draft.copy(gasPricePerLitreMxn = it)) }, prefix = "$")
        CostField.WEAR -> CostNumberField("Desgaste por km", draft.maintenancePerKmMxn, { edit(draft.copy(maintenancePerKmMxn = it)) }, prefix = "$")
        CostField.CONSUMPTION -> CostNumberField("kWh por 100 km", draft.kwhPer100Km, { edit(draft.copy(kwhPer100Km = it)) })
        CostField.TARIFF -> CostNumberField("Precio por kWh", draft.costPerKwhMxn, { edit(draft.copy(costPerKwhMxn = it)) }, prefix = "$")
        CostField.DIRECT -> { CostNumberField("Costo por km", draft.directPerKm, { edit(draft.copy(directPerKm = it, direct = true)) }, prefix = "$")
            Text("Incluye combustible o energía y desgaste.", Modifier.padding(top = 8.dp), style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
        else -> Unit
    }
    Spacer(Modifier.height(16.dp))
    CostPreview.compute(draft)?.let { Text("Manejar te cuesta ${Formatters.formatMxn(it.perKm)} por km", style = KomparaType.bodyStrong) }
    Text("Aplica desde hoy", Modifier.padding(vertical = 12.dp), style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
    state.error?.let { InkNotice(it) }
    PrimaryButton(if (state.saving) "Guardando…" else "Guardar", save, enabled = !state.saving, modifier = Modifier.testTag("cost-save"))
}

@Composable
fun EfficiencyContent(state: CostEditorState, back: () -> Unit, edit: (CostProfileInputs) -> Unit,
    save: () -> Unit, direct: () -> Unit, modifier: Modifier = Modifier) {
    val draft = state.draft
    ScreenScaffold("", modifier, ScreenKind.DRILL, back, footer = {
        PrimaryButton(if (state.saving) "Guardando…" else if (draft == state.saved) "Usar ${draft?.rendimientoKmPerLitre.orEmpty()} km/L" else "Continuar", save,
            enabled = draft != null && !state.saving)
        TextButton(direct, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Ya sé mi costo por km", style = KomparaType.bodyStrong, color = KomparaTheme.colors.ink) }
    }) {
        if (draft == null) { state.error?.let { InkNotice(it) } ?: StaticSkeleton(Modifier.fillMaxWidth().height(200.dp)); return@ScreenScaffold }
        Spacer(Modifier.height(32.dp))
        Text("¿Cuánto rinde tu coche?", style = KomparaType.headline)
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            fun step(delta: Int) { val v = (costDecimal(draft.rendimientoKmPerLitre) ?: java.math.BigDecimal(12)).add(java.math.BigDecimal(delta)).coerceIn(java.math.BigDecimal(4), java.math.BigDecimal(40)); edit(draft.copy(rendimientoKmPerLitre = v.stripTrailingZeros().toPlainString())) }
            IconButton({ step(-1) }, Modifier.size(56.dp).border(1.5.dp, KomparaTheme.colors.inkTertiary, CircleShape).semantics { contentDescription = "Bajar rendimiento" }) { Text("−", style = KomparaType.number) }
            BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
                val inline = maxWidth >= (110 * fontScale).dp
                @Composable fun value(modifier: Modifier) {
                    BasicTextField(draft.rendimientoKmPerLitre, { edit(draft.copy(rendimientoKmPerLitre = it)) },
                        modifier.semantics { contentDescription = "Rendimiento en km/L" },
                        textStyle = KomparaType.efficiency.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = KomparaTheme.colors.ink),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }
                if (inline) Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                    value(Modifier.width((68 * fontScale).dp).alignByBaseline())
                    Text("km/L", Modifier.alignByBaseline(), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
                } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    value(Modifier.fillMaxWidth())
                    Text("km/L", style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
                }
            }
            IconButton({ step(1) }, Modifier.size(56.dp).border(1.5.dp, KomparaTheme.colors.inkTertiary, CircleShape).semantics { contentDescription = "Subir rendimiento" }) { Text("+", style = KomparaType.number) }
        }
        if (state.typical) Text("Típico de un sedán", Modifier.fillMaxWidth().padding(top = 16.dp), style = KomparaType.small,
            color = KomparaTheme.colors.inkSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp)); Rule(); Spacer(Modifier.height(24.dp))
        Text("Gasolina ${costDecimal(draft.gasPricePerLitreMxn)?.let(Formatters::formatMxn) ?: "—"} por litro", style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
        Text("Desgaste ${costDecimal(draft.maintenancePerKmMxn)?.let(Formatters::formatMxn) ?: "—"} por km", Modifier.padding(top = 8.dp), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
        CostPreview.compute(draft)?.let {
            Text("Manejar te cuesta ${Formatters.formatMxn(it.perKm)} por km", Modifier.padding(top = 20.dp), style = KomparaType.bodyStrong)
            Text("Viaje del ejemplo: unos ${Formatters.formatMxnWhole(it.sampleCost)}", Modifier.padding(top = 8.dp), style = KomparaType.body)
        }
        state.error?.let { InkNotice(it) }
    }
}
