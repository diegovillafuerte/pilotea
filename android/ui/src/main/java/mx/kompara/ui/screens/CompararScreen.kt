package mx.kompara.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.data.model.City
import mx.kompara.metrics.compare.ComparisonMetric
import mx.kompara.ui.R
import mx.kompara.ui.components.*
import mx.kompara.ui.paywall.GateSurface
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType
import java.time.LocalDate

@Composable
fun CompararScreen(modifier: Modifier = Modifier, onUpgrade: (GateSurface) -> Unit = {},
    onImport: () -> Unit = {}, viewModel: CompararViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    CompararContent(state, viewModel::selectScope, viewModel::openSheet, { viewModel.step(-1) }, { viewModel.step(1) },
        onImport, { onUpgrade(GateSurface.COMPARE) }, viewModel::retry, modifier)
    sheet?.let { id ->
        SheetScaffold(compareSheetTitle(id), viewModel::closeSheet) {
            CompareSheetContent(id, state, viewModel::openSheet, viewModel::selectMetric, viewModel::selectApp,
                viewModel::selectCity, viewModel::selectWeek, viewModel::setContribution, onImport)
        }
    }
}

@Composable
fun CompararContent(state: CompareUiState, onScope: (CompareScope) -> Unit, onSheet: (String) -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit, onImport: () -> Unit, onUpgrade: () -> Unit,
    onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val c = state.comparison
    Column(modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        TextTabs(listOf("Tus apps", "Tu ciudad"), if (state.choice.scope == CompareScope.APPS) 0 else 1,
            { onScope(if (it == 0) CompareScope.APPS else CompareScope.CITY) },
            unavailable = if (c?.singleApp != null) setOf(0) else emptySet())
        PeriodSelector(if (c?.status == ComparisonStatus.UNCONFIRMED) weekRange(state.weekStart, state.today) else weekSelectorLabel(state.weekStart, state.today),
            PeriodUnit.WEEK, { onSheet("picker") }, onPrevious, onNext, true, state.weekStart < state.today.monday(), stepArrows = true, spreadSteps = LocalDensity.current.fontScale <= 1f, showPicker = false)
        Spacer(Modifier.height(12.dp))
        CompareDoor(c?.selector ?: if (state.choice.scope == CompareScope.APPS) "Tus apps · ${state.choice.metric.label} · aprox."
            else "${appName(state.choice.app)} · ${CompareUiText.cityName(state.choice.city)} · ${state.choice.metric.label}",
            R.drawable.v3_chev_d, Modifier.testTag("compare-context")) { onSheet("context") }
        when {
            state.loading -> {
                StaticSkeleton(Modifier.fillMaxWidth().height(54.dp)); Spacer(Modifier.height(20.dp))
                StaticSkeleton(Modifier.fillMaxWidth(.8f).height(16.dp))
            }
            state.error -> {
                Text("No pudimos abrir esta semana", style = KomparaType.headline)
                KomparaButton("Reintentar", onRetry, variant = ButtonVariant.TEXT)
            }
            c != null -> {
                Text(c.finding, style = KomparaType.headline)
                CompareDoor(c.qualifier, R.drawable.v3_chev, Modifier.testTag("compare-data")) { onSheet("data") }
                Spacer(Modifier.height(24.dp))
                ComparisonChart(c)
                c.difference?.let { value ->
                    Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Diferencia", style = KomparaType.bodyStrong); Text(value, style = KomparaType.compareLabel)
                    }
                }
                Text(c.basis, Modifier.padding(top = 20.dp, bottom = 12.dp), style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
                if (state.choice.scope == CompareScope.CITY) {
                    Rule(); ListRow("Aportar al promedio", onClick = { onSheet("contribution") }); Rule()
                }
                if (c.status == ComparisonStatus.EMPTY) {
                    Spacer(Modifier.height(20.dp)); KomparaButton("Trae tu resumen", onImport, variant = ButtonVariant.TEXT)
                }
            }
        }
        if (state.offline) Text("Sin conexión · tus datos siguen en este teléfono", Modifier.padding(top = 24.dp), style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
    }
    if (!state.loading && !state.error && c != null && (c.status == ComparisonStatus.UNCONFIRMED || c.locked)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp)) {
            // S17 owns the no-card trial contract; never advertise the legacy Play trial here.
            if (c.status == ComparisonStatus.UNCONFIRMED) PrimaryButton("Confirmar con mi resumen", onImport)
            else PrimaryButton("Ver Premium", onUpgrade)
        }
    }
    }
}

@Composable
private fun CompareDoor(label: String, icon: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
        DrawnIcon(icon, tint = KomparaTheme.colors.inkSecondary)
    }
}

/** Masked counterparts are removed before scaling/semantics: even the own bar cannot leak them. */
@Composable
fun ComparisonChart(comparison: WeeklyComparison, modifier: Modifier = Modifier) {
    val colors = KomparaTheme.colors
    val values = comparison.values
    val scale = BarScale.of(values.filterNot { it.locked }.map { it.value?.toDouble() })
    val description = comparison.choice.metric.label + (if (comparison.choice.scope == CompareScope.APPS) ", sin el coche" else "") + ": " +
        values.joinToString(", ") { row -> row.label + " " + when {
            row.locked -> "Con Premium"
            row.value != null -> CompareUiText.rate(row.value, comparison.choice.metric) + (row.qualifier?.let { " $it" } ?: "")
            else -> row.waiting ?: "sin datos"
        } } + (comparison.difference?.let { ", diferencia $it" } ?: "")
    Column(modifier.fillMaxWidth().testTag("compare-chart").clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(if (comparison.showBars) 24.dp else 16.dp)) {
        values.forEach { row ->
            val color = if (row.own) colors.ink else colors.inkSecondary
            Column {
                BoxWithConstraints {
                    val density = LocalDensity.current
                    val measure = rememberTextMeasurer()
                    val label = row.label + "  " + (row.value?.let { CompareUiText.rate(it, comparison.choice.metric) } ?: row.waiting ?: "") + " " + row.qualifier.orEmpty()
                    val required = with(density) { measure.measure(label, KomparaType.compareLabel).size.width.toDp() } + 20.dp
                    val fraction = if (scale.minimum >= 0 && !row.locked && comparison.showBars) row.value?.toDouble()?.let { scale.fraction(it) } ?: 1f else 1f
                    val fit = if (maxWidth * fraction >= required) fraction else 1f
                    FlowRow(Modifier.fillMaxWidth(fit), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(row.label, style = KomparaType.bodyStrong.copy(fontSize = 17.sp), color = color)
                        if (row.qualifier == "· sin confirmar") Text("· sin confirmar", style = KomparaType.body.copy(fontSize = 17.sp), color = colors.inkSecondary)
                    }
                    if (row.locked) DrawnIcon(R.drawable.v3_lock, tint = colors.inkTertiary)
                    else FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (row.value != null) Text(CompareUiText.rate(row.value, comparison.choice.metric), style = KomparaType.compareLabel, color = color)
                        else Text(row.waiting ?: "—", style = KomparaType.body.copy(fontSize = 17.sp), color = colors.inkTertiary)
                        if (row.qualifier == "sin el coche") Text("sin el coche", style = KomparaType.body.copy(fontSize = 17.sp), color = colors.inkSecondary)
                    }
                }
                }
                if (comparison.showBars) {
                    Spacer(Modifier.height(8.dp))
                    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                        if (!row.locked) {
                            val zero = size.width * scale.fraction(0.0)
                            row.value?.toDouble()?.let { value ->
                                val end = size.width * scale.fraction(value)
                                val left = minOf(zero, end); val width = kotlin.math.abs(end - zero)
                                val ink = if (row.own) colors.ink else colors.inkTertiary
                                drawRoundRect(ink, Offset(left, 0f), Size(width, size.height), CornerRadius(4.dp.toPx()))
                                if (width > 0) drawRect(ink, Offset(if (value < 0) zero - minOf(width, 4.dp.toPx()) else zero, 0f), Size(minOf(width, 4.dp.toPx()), size.height))
                            }
                            if (scale.minimum < 0) drawLine(colors.inkSecondary, Offset(zero, 0f), Offset(zero, size.height), 1.dp.toPx())
                        }
                    }
                }
            }
        }
    }
}

fun compareSheetTitle(id: String): String = when (id) {
    "data" -> "De dónde salen estos datos"
    "contribution" -> "Aportar al promedio"
    "cities" -> "Tu ciudad"
    "picker" -> "Elegir semana"
    "single" -> "Tus apps"
    else -> "Qué quieres comparar"
}

@Composable
fun CompareSheetContent(id: String, state: CompareUiState, onSheet: (String) -> Unit,
    onMetric: (ComparisonMetric) -> Unit, onApp: (String) -> Unit, onCity: (City) -> Unit,
    onWeek: (LocalDate) -> Unit, onContribution: (Boolean) -> Unit, onImport: () -> Unit) {
    val c = state.comparison
    when (id) {
        "data" -> {
            Text(weekRange(state.weekStart, state.today), style = KomparaType.bodyStrong)
            c?.details?.forEach { Text(it, Modifier.padding(top = 12.dp), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary) }
        }
        "contribution" -> {
            Text("Sumar mis totales, sin mi nombre, al promedio de ${CompareUiText.cityName(state.choice.city)}", style = KomparaType.body)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Aportar al promedio", Modifier.weight(1f), style = KomparaType.bodyStrong)
                KomparaSwitch(state.contributing, onContribution, Modifier.semantics { contentDescription = "Aportar al promedio" })
            }
            Text("Solo cuentan tus semanas confirmadas con resumen; las estimaciones del lector no se suman", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
            if (state.saveError) Text("No pudimos guardar el cambio. Inténtalo otra vez.", style = KomparaType.body)
        }
        "picker" -> WeekPickerContent((state.weeks + state.weekStart).distinct().sortedDescending().map { WeekChoice(it) },
            state.weekStart, state.today, onWeek, onImport)
        "cities" -> City.entries.forEach { city -> ListRow(CompareUiText.cityName(city), detail = if (city == state.choice.city) "Seleccionada" else null, onClick = { onCity(city) }) }
        "single" -> {
            val app = c?.singleApp ?: state.choice.app
            Text("Esa semana solo registraste ${appName(app)}", style = KomparaType.body)
            Text("Conecta ${if (app == "UBER") "DiDi" else "Uber"} para comparar tus apps", Modifier.padding(top = 12.dp), style = KomparaType.body)
        }
        else -> {
            if (state.choice.scope == CompareScope.CITY) {
                listOf("UBER", "DIDI").forEach { app -> ListRow(appName(app), detail = if (app == state.choice.app) "Seleccionada" else null, onClick = { onApp(app) }) }
                ListRow("Ciudad", detail = CompareUiText.cityName(state.choice.city), onClick = { onSheet("cities") }); Rule()
            }
            ComparisonMetric.entries.forEach { metric ->
                val reason = c?.unavailable?.get(metric)
                Column(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clickable(enabled = reason == null) { onMetric(metric) }.padding(vertical = 12.dp)) {
                    Text(metric.label, style = if (metric == state.choice.metric) KomparaType.bodyStrong else KomparaType.body,
                        color = if (reason == null) KomparaTheme.colors.ink else KomparaTheme.colors.inkTertiary)
                    reason?.let { Text(it, style = KomparaType.small, color = KomparaTheme.colors.inkTertiary) }
                }
            }
        }
    }
}
