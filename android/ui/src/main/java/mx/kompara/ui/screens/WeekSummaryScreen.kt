package mx.kompara.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.R
import mx.kompara.ui.components.*
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType
import java.time.LocalDate

@Composable
fun WeekSummaryScreen(
    modifier: Modifier = Modifier,
    onImport: () -> Unit,
    onDay: (LocalDate) -> Unit,
    onMonth: () -> Unit,
    onCosts: () -> Unit,
    onUpgrade: () -> Unit,
    viewModel: WeekSummaryViewModel = hiltViewModel(),
    onImportReview: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    var anchors by remember { mutableStateOf(WeekSheetAnchors()) }
    val windowHeight = LocalView.current.rootView.height.toFloat()
    WeekSummaryContent(state, viewModel::openSheet, { viewModel.step(-1) }, { viewModel.step(1) }, onMonth,
        onCosts, onUpgrade, viewModel::retry, modifier, onAnchor = { name, bottom -> anchors = anchors.updated(name, bottom) }, onImportReview = onImportReview)
    val week = state.week
    if (sheet != null && week != null && (sheet in listOf("picker", "calculation") || !week.locked)) {
        SheetScaffold(weekSheetTitle(sheet!!, week), viewModel::closeSheet, weekSheetSize(sheet!!, windowHeight, anchors)) {
            WeekSheetContent(sheet!!, week, onDay, viewModel::selectWeek, onImport)
        }
    } else if (sheet == "picker") {
        SheetScaffold("Elegir semana", viewModel::closeSheet) {
            WeekPickerContent(listOf(WeekChoice(state.weekStart)), state.weekStart, state.weekStart,
                viewModel::selectWeek, onImport)
        }
    }
}

@Composable
fun WeekSummaryContent(
    state: WeekSummaryUiState,
    onSheet: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMonth: () -> Unit,
    onCosts: () -> Unit,
    onUpgrade: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onAnchor: (String, Float) -> Unit = { _, _ -> },
    onImportReview: (String) -> Unit = {},
) {
    val week = state.week
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        TextTabs(listOf("Semana", "Mes"), 0, { if (it == 1) onMonth() })
        PeriodSelector(week?.let { weekSelectorLabel(it.start, it.today) } ?: weekRange(state.weekStart), PeriodUnit.WEEK,
            { onSheet("picker") }, onPrevious, onNext, canPrevious = true, canNext = week?.open == false, stepArrows = true, modifier = Modifier.onGloballyPositioned { onAnchor("picker", it.boundsInWindow().bottom) })
        Spacer(Modifier.height(12.dp))
        when {
            state.loading -> WeekSkeleton()
            state.error -> {
                Text("No pudimos abrir esta semana", style = KomparaType.headline)
                KomparaButton("Reintentar", onRetry, variant = ButtonVariant.TEXT)
            }
            week == null || !week.hasData -> {
                Text("Todavía no hay viajes esta semana", style = KomparaType.headline)
                Text("Aquí verás lo que te dejan tus viajes.", Modifier.padding(top = 12.dp), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
            }
            else -> {
                Text(week.label, style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
                Spacer(Modifier.height(16.dp))
                WeekFigureRow(week.amount ?: "—", week.bars.any { (it.value ?: 0.0) < 0 }) {
                    when {
                        week.locked -> Column(Modifier.testTag("week-locked")) {
                            Text("Por día · Premium", style = KomparaType.small)
                            Text("Gratis esta semana y las 3 anteriores.", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
                        }
                        week.money.paymentOnly.isNotEmpty() -> Text("Sin detalle por día", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
                        else -> WeekBars(week, Modifier.onGloballyPositioned { onAnchor("days", it.boundsInWindow().bottom) }) { onSheet("days") }
                    }
                }
                if (week.money.contributions.isNotEmpty() || week.money.carConfigured && week.money.paymentOnly.isEmpty()) {
                    HeroQuietLine(week.quietLine, { onSheet("calculation") }, Modifier.testTag("week-calculation").onGloballyPositioned { onAnchor("calculation", it.boundsInWindow().bottom) })
                }
                week.money.paymentOnly.forEach { row ->
                    Text("${appName(row.record.app)} · pago leído del resumen: ${row.record.payment?.let(mx.kompara.ui.format.Formatters::formatMxnWhole) ?: "—"}", style = KomparaType.body)
                    Text((if (row.record.paymentBasis == mx.kompara.data.accounting.PaymentBasis.UNCONFIRMED) "Sin confirmar" else "Guardado en este teléfono") +
                        (if (row.record.km == null) " · faltan los kilómetros." else " · faltan los costos de esa semana.") +
                        if (week.money.periodResult != null) " No entra en el total." else " No podemos calcular lo que te quedó.",
                        style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
                }
                week.snapshot.statements.filter { it.id.startsWith("statement:") }.forEach { statement ->
                    WeekDoor("Ver lo leído de ${appName(statement.app)}", R.drawable.v3_doc,
                        onClick = { onImportReview(statement.id.removePrefix("statement:")) })
                }
                when {
                    week.locked -> {
                        Spacer(Modifier.height(32.dp))
                        // Promotional no-card trials are capability-gated until S17's server contract.
                        PrimaryButton("Ver Premium", onUpgrade)
                    }
                    week.open -> week.dailyCarLine?.let { WeekDoor(it, R.drawable.v3_info, onClick = onCosts) }
                    else -> {
                        week.differences.forEach { difference -> WeekDoor("Cuadrar con tu resumen de ${appName(difference.app)}", R.drawable.v3_doc,
                            onClick = { onSheet("statement:${difference.app}") }) }
                        if (week.money.paymentOnly.isEmpty()) WeekDoor("Por día", R.drawable.v3_cal, onClick = { onSheet("days") })
                        if (week.apps.isNotEmpty()) WeekDoor("Por app", R.drawable.v3_cmp, week.finding, { onSheet("apps") })
                    }
                }
                Rule()
            }
        }
        if (state.offline && !state.loading) {
            Spacer(Modifier.height(24.dp))
            val color = KomparaTheme.colors.inkTertiary
            Canvas(Modifier.fillMaxWidth().height(2.dp)) { drawLine(color, Offset.Zero, Offset(size.width, 0f), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))) }
            Text("Sin conexión", Modifier.padding(top = 12.dp), style = KomparaType.body)
            Text("Todo lo de esta pantalla está en tu teléfono.", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
        }
    }
}

@Composable
private fun WeekDoor(label: String, icon: Int, detail: String? = null, onClick: (() -> Unit)? = null) {
    Rule()
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DrawnIcon(icon)
        Row(Modifier.weight(1f).heightIn(min = 56.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = KomparaType.body)
                detail?.let { Text(it, style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
            }
            if (onClick != null) DrawnIcon(R.drawable.v3_chev)
        }
    }
}

@Composable
fun WeekBars(week: WeekPresentation, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.testTag("week-bars")) {
        PeriodBars(week.bars, onClick = onClick, showBestLabel = !week.open, showNegativeLabels = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("L", "M", "M", "J", "V", "S", "D").forEach { Text(it, style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
        }
    }
}

@Composable
private fun WeekSkeleton() {
    Text("Te quedaron esa semana · aprox.", style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
    Spacer(Modifier.height(24.dp))
    Row(Modifier.fillMaxWidth().height(104.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
        StaticSkeleton(Modifier.width(111.dp).height(28.dp))
        Column(Modifier.width(168.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { repeat(7) { StaticSkeleton(Modifier.width(16.dp).height(30.dp)) } }
            Rule()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("L", "M", "M", "J", "V", "S", "D").forEach { Text(it, style = KomparaType.small) } }
        }
    }
    Spacer(Modifier.height(24.dp))
    StaticSkeleton(Modifier.width(128.dp).height(12.dp))
    Spacer(Modifier.height(12.dp))
    StaticSkeleton(Modifier.fillMaxWidth(.8f).height(12.dp))
    Spacer(Modifier.height(16.dp))
    WeekDoor("Cuadrar con tu resumen de Uber", R.drawable.v3_doc)
    WeekDoor("Por día", R.drawable.v3_cal)
    WeekDoor("Por app", R.drawable.v3_cmp)
    StaticSkeleton(Modifier.width(161.dp).height(12.dp))
}

@Composable
internal fun WeekFigureRow(value: String, negative: Boolean, plotWidth: androidx.compose.ui.unit.Dp = 168.dp, plot: @Composable () -> Unit) {
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(value, Modifier.align(androidx.compose.ui.Alignment.Bottom).padding(bottom = ((if (negative) 26 else 8) * fontScale).dp),
            style = KomparaType.periodFigure, softWrap = false)
        Box(Modifier.width(plotWidth).align(androidx.compose.ui.Alignment.Bottom)) { plot() }
    }
}

/** Sheet edges follow measured row boundaries. Long content scrolls inside the available space. */
data class WeekSheetAnchors(val days: Float = 0f, val calculation: Float = 0f, val picker: Float = 0f) {
    fun updated(name: String, value: Float) = when (name) {
        "days" -> copy(days = value); "picker" -> copy(picker = value); else -> copy(calculation = value)
    }
}
@Composable
fun weekSheetSize(sheet: String, windowHeight: Float, anchors: WeekSheetAnchors): Modifier {
    val anchor = when (sheet) { "days" -> anchors.days; "picker" -> anchors.picker; else -> anchors.calculation }
    if (anchor <= 0f || windowHeight <= anchor) return Modifier
    val height = with(LocalDensity.current) { (windowHeight - anchor).toDp() }
    return if (sheet == "days") Modifier.heightIn(max = height) else Modifier.heightIn(min = height)
}
