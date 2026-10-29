package mx.kompara.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.R
import mx.kompara.ui.components.*
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType
import java.time.LocalDate

@Composable
fun DayDetailScreen(onBack: () -> Unit, onLimits: () -> Unit, modifier: Modifier = Modifier,
    viewModel: DayDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selected by viewModel.selectedOffer.collectAsStateWithLifecycle()
    val ledger by viewModel.ledger.collectAsStateWithLifecycle()
    val filtered by viewModel.filtered.collectAsStateWithLifecycle()
    val undo by viewModel.undoReceipt.collectAsStateWithLifecycle()
    val error by viewModel.actionError.collectAsStateWithLifecycle()
    LaunchedEffect(undo) { if (undo != null) { kotlinx.coroutines.delay(6_000); viewModel.dismissMessage() } }
    val day = (state as? DayDetailUiState.Loaded)?.day
    val context = LocalContext.current
    val back = { if (filtered) viewModel.filter(false) else onBack() }
    BackHandler(filtered && selected == null && !ledger) { viewModel.filter(false) }
    val actionMessage: (@Composable () -> Unit)? = if (undo != null || error != null) ({
        Snackbar(Modifier.padding(vertical = 8.dp),
            action = { TextButton(if (undo != null) viewModel::undo else viewModel::dismissMessage) {
                Text(if (undo != null) "Deshacer" else "Cerrar", color = KomparaTheme.colors.paper)
            } }, containerColor = KomparaTheme.colors.ink, contentColor = KomparaTheme.colors.paper) {
            Text(error ?: "Se canceló. Se quitó de tu día.", style = KomparaType.body)
        }
    }) else null
    val limitsSnackbar = LocalLimitsSnackbar.current
    val message: (@Composable () -> Unit)? = if (limitsSnackbar?.message != null) ({
        Spacer(Modifier.height(8.dp)); SaveSnackbar(limitsSnackbar); Spacer(Modifier.height(8.dp))
        actionMessage?.invoke()
    }) else actionMessage
    Column(modifier.fillMaxSize()) {
        DayHeader(day?.title ?: state.dayIso.ifBlank { "Hoy" }, back,
            previous = { day?.let { viewModel.selectDay(it.date.minusDays(1)) } },
            next = { day?.let { viewModel.selectDay(it.date.plusDays(1)) } },
            canStep = day != null, canNext = day != null && day.date < day.today,
            pick = { day?.let {
                DatePickerDialog(context, { _, y, m, d -> viewModel.selectDay(LocalDate.of(y, m + 1, d)) },
                    it.date.year, it.date.monthValue - 1, it.date.dayOfMonth).apply {
                    datePicker.maxDate = it.money.window.asOf.toEpochMilli()
                }.show()
            } })
        when (state) {
            is DayDetailUiState.Loading -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                StaticSkeleton(Modifier.size(170.dp, 22.dp)); StaticSkeleton(Modifier.size(190.dp, 74.dp))
                repeat(4) { StaticSkeleton(Modifier.fillMaxWidth().height(56.dp)) }
            }
            is DayDetailUiState.Error -> Column(Modifier.padding(20.dp)) {
                Text("No pudimos cargar tus números.", style = KomparaType.body)
                TextButton(viewModel::retry) { Text("Reintentar") }
            }
            is DayDetailUiState.Loaded -> if (day != null) {
                if (selected == null) message?.invoke()
                key(day.date, filtered) {
                    DayDetailContent(day, filtered, { viewModel.filter(!filtered) }, viewModel::openOffer,
                        { viewModel.showLedger(true) }, Modifier.weight(1f))
                }
            }
        }
    }
    if (day != null) {
        if (ledger) DayCalculationSheet(dayCalculation(day.money)) { viewModel.showLedger(false) }
        else if (selected != null) {
            val model = day.offer(selected!!)
            if (model != null) OfferDetailSheet(model, day.date.toString(), viewModel::closeOffer,
                { viewModel.showLedger(true) }, onLimits, viewModel::cancelOffer, message)
            else SheetScaffold("Oferta", viewModel::closeOffer) { Text("No encontramos esta oferta en el día elegido.") }
        }
    }
}

@Composable
fun DayHeader(label: String, onBack: () -> Unit, previous: () -> Unit, next: () -> Unit,
    canStep: Boolean, canNext: Boolean, pick: () -> Unit, modifier: Modifier = Modifier) {
    val large = LocalDensity.current.fontScale > 1.3f
    @Composable fun RowScope.heading() {
        IconButton(onBack, Modifier.size(48.dp)) { DrawnIcon(R.drawable.v3_back, description = "Volver") }
        Text("Tu día", Modifier.weight(1f), style = KomparaType.title)
    }
    @Composable fun steps(modifier: Modifier) {
        Row(modifier.semantics { isTraversalGroup = true }, verticalAlignment = Alignment.CenterVertically) {
            IconButton(previous, Modifier.size(48.dp), enabled = canStep) {
                DrawnIcon(R.drawable.v3_chev_l, description = "Día anterior a $label")
            }
            Text(label, Modifier.weight(1f).heightIn(min = 48.dp).clickable(enabled = canStep, onClick = pick)
                .wrapContentHeight().semantics { traversalIndex = -1f }, style = KomparaType.bodyStrong)
            IconButton(next, Modifier.size(48.dp), enabled = canNext) {
                DrawnIcon(R.drawable.v3_chev, description = "Día siguiente a $label",
                    tint = if (canNext) KomparaTheme.colors.inkSecondary else KomparaTheme.colors.inkTertiary)
            }
        }
    }
    // Read the date before its controls; keep all targets 48dp, grow the header on narrow/large text.
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (large || maxWidth < 340.dp) Column {
            Row(Modifier.heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) { heading() }
            steps(Modifier.fillMaxWidth())
        } else Row(Modifier.heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            heading()
            steps(Modifier.width(210.dp))
        }
    }
}

@Composable
fun DayDetailContent(day: DayPresentation, filtered: Boolean, onFilter: () -> Unit,
    onOffer: (Long) -> Unit, onCalculation: () -> Unit, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    val rows = if (filtered) day.missedAbove else day.rows
    val keepPredicateTogether = LocalDensity.current.fontScale <= 1.3f
    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().testTag("day-offers"), state = list,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp)) {
            item {
                if (day.amount != null) PeriodHero(day.label, day.amount, day.quietLine, onCalculation)
                else Text(day.emptyMessage.orEmpty(), Modifier.padding(vertical = 16.dp), style = KomparaType.body)
                if (day.money.trips > 0 && day.money.hoursOnline.signum() == 0)
                    Text("Sin turno detectado: aún no podemos calcular tus horas.", Modifier.padding(vertical = 12.dp), style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
                if (day.missedAbove.isNotEmpty()) {
                    Rule()
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onFilter).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OfferMark(OfferMarkState.ABOVE)
                        Text(if (filtered) "Ver todas las ofertas del día" else
                            "${day.missedAbove.size} ${if (day.missedAbove.size == 1) "oferta arriba de tu meta se fue" else "ofertas arriba de tu meta se fueron"} sin tomar".let { if (keepPredicateTogether) it.replace("se fueron sin tomar", mx.kompara.ui.format.Formatters.unbroken("se fueron sin tomar")) else it },
                            Modifier.weight(1f), style = KomparaType.body)
                        DrawnIcon(R.drawable.v3_chev)
                    }
                    Rule()
                }
                FlowRow(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (filtered) "Arriba de tu meta · ${rows.size}" else day.section, style = KomparaType.section)
                    Text(day.column, Modifier.align(Alignment.Bottom), style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
                }
            }
            items(rows, key = { it.offerId }) { row ->
                OfferRow(row.outcomeLabel, "${day.clock(row)} · ${day.meaning(row)}", row.rate, row.mark,
                    { onOffer(row.offerId) }, Modifier.testTag("offer-${row.offerId}"))
                Rule()
            }
        }
        if (list.canScrollForward) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(24.dp)
            .background(Brush.verticalGradient(listOf(KomparaTheme.colors.paper.copy(alpha = 0f), KomparaTheme.colors.paper))))
    }
}
