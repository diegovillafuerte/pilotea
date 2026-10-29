package mx.kompara.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.R
import mx.kompara.ui.components.*
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun MonthSummaryScreen(onWeek: (LocalDate?) -> Unit, onImss: () -> Unit, onImport: () -> Unit,
    onUpgrade: () -> Unit, viewModel: FiscalViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    var anchors by remember { mutableStateOf(WeekSheetAnchors()) }
    val windowHeight = LocalView.current.rootView.height.toFloat()
    LaunchedEffect(state.month, state.period?.today) { viewModel.selectComparisonWeek() }
    MonthSummaryContent(state, viewModel::openSheet, { viewModel.step(-1) }, { viewModel.step(1) },
        { onWeek(null) }, onImss, { onWeek(it) }, onUpgrade, viewModel::retry,
        onAnchor = { name, bottom -> anchors = anchors.updated(name, bottom) })
    val month = state.period
    if (sheet != null && month != null) SheetScaffold(monthSheetTitle(sheet!!, month), viewModel::closeSheet,
        monthSheetSize(sheet!!, windowHeight, anchors)) {
        MonthSheetContent(sheet!!, month, { viewModel.closeSheet(); onWeek(it) }, viewModel::selectMonth, onImport)
    }
}

@Composable
fun MonthSummaryContent(state: FiscalUiState, onSheet: (String) -> Unit, onPrevious: () -> Unit,
    onNext: () -> Unit, onWeek: () -> Unit, onImss: () -> Unit, onImportedWeek: (LocalDate) -> Unit,
    onUpgrade: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier,
    onAnchor: (String, Float) -> Unit = { _, _ -> }) {
    val month = state.period
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        TextTabs(listOf("Semana", "Mes"), 1, { if (it == 0) onWeek() })
        PeriodSelector(month?.selector ?: monthName(state.month).replaceFirstChar { it.titlecase() }, PeriodUnit.MONTH,
            { onSheet("picker") }, onPrevious, onNext, canPrevious = month?.let { !it.importOnly || it.month > it.choices.min() } ?: true, canNext = month?.open == false, stepArrows = true, spreadSteps = true,
            modifier = Modifier.onGloballyPositioned { onAnchor("picker", it.boundsInWindow().bottom) })
        Spacer(Modifier.height(12.dp))
        when {
            state.loading -> {
                StaticSkeleton(Modifier.width(210.dp).height(18.dp))
                Spacer(Modifier.height(32.dp))
                StaticSkeleton(Modifier.width(145.dp).height(40.dp))
                Spacer(Modifier.height(24.dp))
                StaticSkeleton(Modifier.fillMaxWidth().height(36.dp))
            }
            state.error -> {
                Text("No pudimos abrir este mes", style = KomparaType.headline)
                KomparaButton("Reintentar", onRetry, variant = ButtonVariant.TEXT)
            }
            month == null || !month.hasData -> Text("Todavía no hay viajes este mes", style = KomparaType.headline)
            else -> {
                Text(month.label, style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
                Spacer(Modifier.height(16.dp))
                if (month.importOnly) Text(month.amount ?: "—", style = KomparaType.periodFigure)
                else BoxWithConstraints(Modifier.fillMaxWidth()) {
                  WeekFigureRow(month.amount ?: "—", month.bars.any { (it.value ?: 0.0) < 0 },
                    minOf(maxWidth, (168 * androidx.compose.ui.platform.LocalDensity.current.fontScale).dp)) {
                    if (month.locked) {
                        Column { Text("Por semana · Premium", style = KomparaType.small)
                            Text("Gratis el mes en curso.", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
                    } else Column(Modifier.testTag("month-bars")) {
                        PeriodBars(month.bars, onClick = { onSheet("weeks") }, showNegativeLabels = true, fixedBarWidth = 16.dp, dashedUnknown = true)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            month.bars.forEach { Text(it.label, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = KomparaType.small,
                                color = if (it.label == "hoy") KomparaTheme.colors.ink else KomparaTheme.colors.inkSecondary) }
                        }
                    }
                  }
                }
                HeroQuietLine(month.quietLine, { onSheet("calculation") }, Modifier.testTag("month-calculation")
                    .onGloballyPositioned { onAnchor("days", it.boundsInWindow().bottom) })
                month.imports.forEach { record ->
                    EarningsDoor("${monthRange(record.coverage.start, record.coverage.endInclusive)} · importada", R.drawable.v3_doc,
                        if (month.importOnly) "Sin detalle por día" else "Te pagó ${record.payment?.let(Formatters::formatMxnWhole) ?: "—"} · no entra en el total") {
                        onImportedWeek(record.coverage.start.monday())
                    }
                }
                month.snapshot.unallocated.forEach { record ->
                    EarningsDoor("${weekRange(record.coverage.start, month.today)} · importada", R.drawable.v3_doc,
                        "Sin detalle por día · no se reparte entre meses") { onImportedWeek(record.coverage.start.monday()) }
                }
                if (!month.importOnly) {
                    if (month.locked) KomparaButton("Ver Premium", onUpgrade, variant = ButtonVariant.TEXT)
                    else EarningsDoor("Por semana", R.drawable.v3_cal, modifier = Modifier.onGloballyPositioned { onAnchor("calculation", it.boundsInWindow().bottom) }) { onSheet("weeks") }
                }
                EarningsDoor(month.imssLabel, R.drawable.v3_imss, onClick = onImss)
                Rule()
            }
        }
        if (state.offline) {
            Text("Sin conexión", Modifier.padding(top = 24.dp), style = KomparaType.body)
            Text("Todo lo de esta pantalla está en tu teléfono.", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
        }
    }
}

@Composable
fun EarningsDoor(label: String, icon: Int, detail: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier) {
        Rule()
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DrawnIcon(icon)
            Column(Modifier.weight(1f)) {
                Text(label, style = KomparaType.body)
                detail?.let { Text(it, style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
            }
            DrawnIcon(R.drawable.v3_chev)
        }
    }
}

@Composable
fun MonthSheetContent(sheet: String, month: MonthPresentation, onWeek: (LocalDate) -> Unit,
    onMonth: (YearMonth) -> Unit, onImport: () -> Unit) {
    when (sheet) {
        "calculation" -> DayCalculationContent(monthCalculation(month))
        "picker" -> MonthPickerContent(month.choices, month.month, month.today, onMonth, onImport)
        "weeks" -> {
            Text("Lo que cae en ${monthName(month.month)}, ${month.basis}", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
            Spacer(Modifier.height(12.dp))
            Rule()
            month.weeks.forEachIndexed { index, week ->
                Column(Modifier.testTag("month-week-$index").clickable { onWeek(week.window.start.monday()) }) {
                    CalculationLedger(listOf(CalculationRow(month.weekLabel(week), week.periodResult?.let(Formatters::formatMxnWhole) ?: "—", month.weekNote(week))))
                }
                Rule()
            }
            CalculationLedger(listOf(CalculationRow(month.totalLabel, month.amount ?: "—", month.roundingNote, total = true)))
        }
    }
}

@Composable
fun MonthPickerContent(choices: List<YearMonth>, selected: YearMonth, today: LocalDate, onSelect: (YearMonth) -> Unit, onImport: () -> Unit) {
    choices.forEach { month ->
        Rule()
        Row(verticalAlignment = Alignment.CenterVertically) {
            ListRow(monthLabel(month, today) + if (month == YearMonth.from(today)) " · en curso" else "", Modifier.weight(1f), onClick = { onSelect(month) })
            if (month == selected) StatusIcon(StatusGlyph.DONE)
        }
    }
    Spacer(Modifier.height(24.dp))
    ListRow("Traer un resumen", onClick = onImport)
}
fun monthSheetTitle(sheet: String, month: MonthPresentation) = when (sheet) {
    "picker" -> "Elegir mes"
    "weeks" -> "Por semana · ${monthName(month.month)}"
    else -> "Cómo se calculó · ${monthName(month.month)}"
}

/** Keep the underlying sentence whole, including after font growth; scroll the ledger inside. */
@Composable
fun monthSheetSize(sheet: String, windowHeight: Float, anchors: WeekSheetAnchors): Modifier {
    val anchor = when (sheet) { "weeks" -> anchors.days; "picker" -> anchors.picker; else -> anchors.calculation }
    if (anchor <= 0f || anchor >= windowHeight) return Modifier
    return Modifier.height(with(androidx.compose.ui.platform.LocalDensity.current) { (windowHeight - anchor).toDp() })
}
