package mx.kompara.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.R
import mx.kompara.ui.components.*
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType
import java.time.LocalDate

/** Every door Ganancias opens; the live graph and the fixtures supply the same callbacks. */
data class GananciasActions(
    val period: (Int) -> Unit = {},
    val semaforo: () -> Unit = {},
    val offer: (Long) -> Unit = {},
    val calculation: () -> Unit = {},
    val day: (LocalDate) -> Unit = {},
    val previousWeek: () -> Unit = {},
    val nextWeek: () -> Unit = {},
    val weekCalculation: () -> Unit = {},
    val limits: () -> Unit = {},
    val costs: () -> Unit = {},
    val openUber: () -> Unit = {},
    val recovery: () -> Unit = {},
    val battery: () -> Unit = {},
    val retry: () -> Unit = {},
)

/** The Ganancias tab (v4 L1–L5): how much the driver has kept, and whether the semáforo is on. */
@Composable
fun GananciasScreen(onSemaforo: () -> Unit, onDay: (LocalDate) -> Unit, onLimits: () -> Unit, onCosts: () -> Unit,
    onRecovery: () -> Unit, onBattery: () -> Unit, modifier: Modifier = Modifier,
    viewModel: GananciasViewModel = hiltViewModel(), today: DayDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val week by viewModel.week.collectAsStateWithLifecycle()
    val weekSheet by viewModel.weekSheet.collectAsStateWithLifecycle()
    val day by today.uiState.collectAsStateWithLifecycle()
    val selected by today.selectedOffer.collectAsStateWithLifecycle()
    val loaded = (day as? DayDetailUiState.Loaded)?.day
    val message = dayMessage(today)
    val context = LocalContext.current
    GananciasContent(state, period, day, week, GananciasActions(viewModel::selectPeriod, onSemaforo, today::openOffer,
        { today.showLedger(true) }, onDay, { viewModel.step(-1) }, { viewModel.step(1) }, { viewModel.showWeekCalculation(true) },
        onLimits, onCosts, { HoyHostLinks.openUber(context) }, { when (state.off?.action) {
            OffAction.SEMAFORO -> onSemaforo()
            OffAction.RESUME -> viewModel.resume()
            else -> onRecovery()
        } },
        onBattery, { viewModel.retry(); today.retry() }), modifier, message.takeIf { selected == null })
    if (state.mode == HoyMode.DAY) DaySheets(today, loaded, message)
    val drawn = week.week
    if (weekSheet && drawn != null) PeriodCalculationSheet(drawn.calculation) { viewModel.showWeekCalculation(false) }
}

/** A week opened from its notice: Semana alone, under the shell's back arrow. */
@Composable
fun WeekScreen(onDay: (LocalDate) -> Unit, modifier: Modifier = Modifier, viewModel: GananciasViewModel = hiltViewModel()) {
    val week by viewModel.week.collectAsStateWithLifecycle()
    val sheet by viewModel.weekSheet.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        WeekBody(week, GananciasActions(day = onDay, previousWeek = { viewModel.step(-1) }, nextWeek = { viewModel.step(1) },
            weekCalculation = { viewModel.showWeekCalculation(true) }, retry = viewModel::retry))
    }
    val drawn = week.week
    if (sheet && drawn != null) PeriodCalculationSheet(drawn.calculation) { viewModel.showWeekCalculation(false) }
}

@Composable
fun GananciasContent(state: GananciasUiState, period: Int, today: DayDetailUiState, week: WeekUiState, actions: GananciasActions,
    modifier: Modifier = Modifier, message: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxSize()) {
        state.status?.let { StatusRow(it, actions.semaforo) }
        when (state.mode) {
            HoyMode.LOADING -> Column(Modifier.padding(20.dp)) { StaticSkeleton(Modifier.fillMaxWidth().height(160.dp)) }
            HoyMode.ERROR -> Column(Modifier.padding(20.dp)) {
                Text("No pudimos cargar tus viajes.", style = KomparaType.body)
                KomparaButton("Reintentar", actions.retry, variant = ButtonVariant.TEXT, contentColor = KomparaTheme.colors.ink)
            }
            HoyMode.OFF -> ReaderOffBody(state, actions, message)
            HoyMode.FIRST_DAY -> FirstDayBody(requireNotNull(state.firstDay), actions)
            HoyMode.DAY -> {
                PeriodTabs(period, actions.period)
                if (period == 0) DayState(today, actions.retry) { day ->
                    DayContent(day, actions.offer, actions.calculation, Modifier.weight(1f), message,
                        below = state.yesterday?.takeIf { day.rows.isEmpty() }?.let { line -> {
                            DayLineText(line, { actions.day(line.day) }, Modifier.padding(horizontal = 20.dp).padding(top = 8.dp))
                        } })
                } else WeekBody(week, actions)
            }
        }
    }
}

/** One line: the semáforo's state, opening the Semáforo screen while it works (`.st`). */
@Composable
private fun StatusRow(status: GananciasStatus, onClick: () -> Unit) {
    val c = KomparaTheme.colors
    val opens = status.opens
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (opens) Modifier.clickable(onClick = onClick) else Modifier)
        .semantics(mergeDescendants = true) {}.padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusIcon(status.glyph)
        Text(buildAnnotatedString {
            withStyle(GananciasType.statusStrong.toSpanStyle()) { append(status.title) }
            status.detail?.let { withStyle(GananciasType.status.toSpanStyle().copy(color = c.inkSecondary)) { append(" · $it") } }
        }, Modifier.weight(1f), style = GananciasType.status, color = c.ink)
        if (opens) Chevron()
    }
}

/** "Hoy | Semana" (`.per`): two text tabs over one rule, the chosen one underlined in green. */
@Composable
private fun PeriodTabs(selected: Int, onSelect: (Int) -> Unit) {
    val c = KomparaTheme.colors
    Box {
        Rule(Modifier.align(Alignment.BottomStart))
        Row(Modifier.padding(horizontal = 20.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            listOf("Hoy", "Semana").forEachIndexed { i, label ->
                val on = i == selected
                val line = c.activeNavigation
                Box(Modifier.heightIn(min = 48.dp).selectable(on, role = Role.Tab, onClick = { onSelect(i) })
                    .drawBehind { if (on) drawRect(line, Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx())) }) {
                    Text(label, Modifier.padding(top = 12.dp, bottom = 13.dp), style = if (on) KomparaType.bodyStrong else KomparaType.body,
                        color = if (on) c.ink else c.inkSecondary)
                }
            }
        }
    }
}

/** L2: shown until the first offer has ever been read; the two defaults one tap from being changed. */
@Composable
private fun ColumnScope.FirstDayBody(first: FirstDay, actions: GananciasActions) {
    val c = KomparaTheme.colors
    val snackbar = LocalLimitsSnackbar.current?.takeIf { it.message != null }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
        Text("Listo. Abre tu app de viajes.", Modifier.padding(top = 20.dp), style = KomparaType.headline)
        Text("En tu primera oferta verás el semáforo.", Modifier.padding(top = 8.dp), style = KomparaType.body, color = c.inkSecondary)
        // The example pill sits centred in its 108 dp box (`.mini`). With large text it moves under its label and grows
        // no further than the kit's 1.3× pill (`.pill.xl`), so it stays whole on a narrow phone.
        val density = LocalDensity.current
        val stacked = density.fontScale > 1.3f
        Box(Modifier.padding(top = 14.dp).fillMaxWidth().heightIn(min = 108.dp).clip(RoundedCornerShape(12.dp)).background(c.paperSecondary)) {
            Text("Ejemplo", Modifier.padding(start = 12.dp, top = 9.dp), style = KomparaType.small, color = c.inkSecondary)
            Box(Modifier.align(Alignment.Center).padding(top = if (stacked) 28.dp else 0.dp).horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 12.dp)) {
                CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.3f))) { Regla(first.example) }
            }
        }
        Spacer(Modifier.height(20.dp))
        FirstDayRow(first.limits, first.limitsDetail, null, actions.limits)
        first.cost?.let { FirstDayRow(it, first.costDetail, "Ajustar", actions.costs) }
    }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 18.dp)) {
        if (snackbar != null) { SaveSnackbar(snackbar); Spacer(Modifier.height(8.dp)) }
        PrimaryButton("Abrir Uber para probar", actions.openUber)
    }
}

@Composable
private fun FirstDayRow(label: String, detail: String?, action: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = KomparaType.body)
            detail?.let { Text(it, Modifier.padding(top = 2.dp), style = GananciasType.rowDetail, color = KomparaTheme.colors.inkSecondary) }
        }
        action?.let { Text(it, style = GananciasType.status, color = KomparaTheme.colors.inkSecondary) }
        Chevron()
    }
    Rule()
}

/** L3: the tab's body becomes the cause, one primary button and, unless the driver stopped it, one secondary. */
@Composable
private fun ColumnScope.ReaderOffBody(state: GananciasUiState, actions: GananciasActions, message: (@Composable () -> Unit)?) {
    val off = requireNotNull(state.off)
    val c = KomparaTheme.colors
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
        message?.invoke()
        Text(off.headline, Modifier.padding(top = 20.dp), style = KomparaType.headline)
        Text(off.body, Modifier.padding(top = 8.dp), style = KomparaType.body, color = c.inkSecondary)
        Rule(Modifier.padding(top = 22.dp))
        Spacer(Modifier.height(10.dp))
        state.yesterday?.let { DayLineText(it, { actions.day(it.day) }) }
        state.today?.let { DayLineText(it, { actions.day(it.day) }, style = GananciasType.rowDetail) }
        if (state.todayUnread) Text("Hoy no hemos leído ofertas.", Modifier.padding(top = 4.dp), style = GananciasType.rowDetail, color = c.inkSecondary)
    }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PrimaryButton(off.primary, actions.recovery)
        if (off.secondary) KomparaButton(GananciasStats.SECONDARY, actions.battery, variant = ButtonVariant.TEXT, fullWidth = true, contentColor = c.ink)
    }
}

/** Semana (v4 L4, L5): the week between two arrows, its figure, seven bars into the days, and the car. */
@Composable
internal fun ColumnScope.WeekBody(state: WeekUiState, actions: GananciasActions) {
    val week = state.week
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        WeekNavigation(week?.title ?: weekRange(state.weekStart), week?.open == false, actions)
        when {
            state.loading && week == null -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StaticSkeleton(Modifier.size(150.dp, 56.dp)); StaticSkeleton(Modifier.fillMaxWidth().height(120.dp))
            }
            state.error || week == null -> Column(Modifier.padding(20.dp)) {
                Text("No pudimos abrir esta semana", style = KomparaType.headline)
                KomparaButton("Reintentar", actions.retry, variant = ButtonVariant.TEXT, contentColor = KomparaTheme.colors.ink)
            }
            else -> {
                Hero(week.label, week.amount, week.quietLine, actions.weekCalculation.takeIf { week.calculable }, 6.dp, GananciasType.weekFigure)
                WeekBars(week.bars, actions.day)
                week.car?.let { CarBar(it) }
            }
        }
    }
}

@Composable
private fun WeekNavigation(title: String, canNext: Boolean, actions: GananciasActions) {
    val c = KomparaTheme.colors
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(actions.previousWeek, Modifier.size(48.dp)) {
            DrawnIcon(R.drawable.v3_chev, Modifier.size(20.dp).scale(-1f, 1f), description = "Semana anterior", tint = c.ink)
        }
        Text(title, Modifier.weight(1f), style = GananciasType.statusStrong, textAlign = TextAlign.Center)
        IconButton(actions.nextWeek, Modifier.size(48.dp), enabled = canNext) {
            DrawnIcon(R.drawable.v3_chev, Modifier.size(20.dp), description = "Semana siguiente", tint = if (canNext) c.ink else c.rule)
        }
    }
}

/** Heights relative to the best day; the best is ink and carries its amount. Tap a day to open it. */
@Composable
private fun WeekBars(bars: List<WeekBar>, onDay: (LocalDate) -> Unit) {
    val c = KomparaTheme.colors
    // Room above the bars for the best day's amount, which overhangs its column as the kit's `em` does.
    val label = with(LocalDensity.current) { GananciasType.barLabel.lineHeight.toDp() } + 5.dp
    Column(Modifier.padding(top = 14.dp).padding(horizontal = 15.5.dp)) {
        Row(Modifier.fillMaxWidth().height(120.dp + label).testTag("week-bars")) {
            bars.forEach { bar ->
                val height = (120 * bar.fraction).dp.coerceAtLeast(2.dp)
                Box(Modifier.weight(1f).fillMaxHeight().then(if (bar.elapsed) Modifier.clickable { onDay(bar.date) } else Modifier)
                    .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(bar.date.dayTitle(bar.date), bar.value).joinToString(": ") }
                    .padding(horizontal = 4.5.dp)) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(height)
                        .background(if (bar.best) c.ink else c.rule, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                    bar.label?.let { Text(it, Modifier.align(Alignment.BottomCenter).padding(bottom = height + 5.dp).wrapContentWidth(unbounded = true)
                        .clearAndSetSemantics {}, style = GananciasType.barLabel, softWrap = false) }
                }
            }
        }
        Rule(Modifier.padding(horizontal = 4.5.dp))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            bars.forEach { bar ->
                Text(bar.letter, Modifier.weight(1f).clearAndSetSemantics {}, style = if (bar.best) GananciasType.axisStrong else GananciasType.axis,
                    color = if (bar.best) c.ink else c.inkSecondary, textAlign = TextAlign.Center)
            }
        }
    }
}

/** The week's car bill as one bar: the hatched part is the car, the rest is the driver's (or what is still missing). */
@Composable
private fun CarBar(car: WeekCar) {
    val c = KomparaTheme.colors
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp).testTag("week-car")) {
        Row(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (car.hatched > 0f) Canvas(Modifier.weight(car.hatched).fillMaxHeight()) {
                drawRect(c.rule)
                val step = 6.dp.toPx() * 1.4142f
                var x = -size.height
                while (x < size.width) {
                    drawLine(c.inkTertiary, Offset(x, size.height), Offset(x + size.height, 0f), 3.dp.toPx())
                    x += step
                }
            }
            if (car.hatched < 1f) Box(Modifier.weight(1f - car.hatched).fillMaxHeight().background(if (car.covered) c.ink else c.rule))
        }
        Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(car.label, style = GananciasType.carLabel, color = c.inkSecondary)
                Text(car.amount, style = GananciasType.carValue)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(car.restLabel, style = GananciasType.carLabel, color = c.inkSecondary)
                Text(car.rest, style = GananciasType.carValue)
            }
        }
    }
}
