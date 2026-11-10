package mx.kompara.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import mx.kompara.ui.R
import mx.kompara.ui.components.*
import mx.kompara.ui.nav.NavigationHeader
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

/** A past day (v4 L6): Hoy's layout with a back arrow and the date. The shell keeps the bottom bar. */
@Composable
fun DayDetailScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: DayDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selected by viewModel.selectedOffer.collectAsStateWithLifecycle()
    val day = (state as? DayDetailUiState.Loaded)?.day
    val message = dayMessage(viewModel)
    Column(modifier.fillMaxSize()) {
        NavigationHeader(day?.title.orEmpty(), onBack, null)
        DayState(state, viewModel::retry) {
            DayContent(it, viewModel::openOffer, { viewModel.showLedger(true) }, Modifier.weight(1f), message.takeIf { selected == null })
        }
    }
    DaySheets(viewModel, day, message)
}

/** Loading and failure keep 0.2.20's behaviour; a loaded day draws [content]. */
@Composable
internal fun ColumnScope.DayState(state: DayDetailUiState, retry: () -> Unit, content: @Composable ColumnScope.(DayPresentation) -> Unit) {
    when (state) {
        is DayDetailUiState.Loading -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            StaticSkeleton(Modifier.size(120.dp, 20.dp)); StaticSkeleton(Modifier.size(190.dp, 72.dp))
            repeat(4) { StaticSkeleton(Modifier.fillMaxWidth().height(52.dp)) }
        }
        is DayDetailUiState.Error -> Column(Modifier.padding(20.dp)) {
            Text("No pudimos cargar tus viajes.", style = KomparaType.body)
            TextButton(retry) { Text("Reintentar", color = KomparaTheme.colors.ink) }
        }
        is DayDetailUiState.Loaded -> content(state.day)
    }
}

/** The day's money, then its offers newest first (L1, L6). The whole list scrolls to the first offer of the day. */
@Composable
internal fun DayContent(day: DayPresentation, onOffer: (Long) -> Unit, onCalculation: () -> Unit, modifier: Modifier = Modifier,
    message: (@Composable () -> Unit)? = null, below: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        message?.let { Column(Modifier.padding(horizontal = 20.dp)) { it() } }
        Hero(day.label, day.amount, day.quietLine, onCalculation.takeIf { day.calculable }, if (day.isToday) 18.dp else 8.dp)
        below?.invoke()
        if (day.rows.isNotEmpty()) {
            SectionHeader(day.section, day.rows.size.toString(), day.column)
            day.rows.forEach { row ->
                OfferLine(row.mark, appName(row.platform), day.detail(row), day.rate(row), day.dim(row), { onOffer(row.offerId) },
                    Modifier.testTag("offer-${row.offerId}"))
            }
        }
    }
}

/** The figure of a period, its label above and, under it, the line that opens Cómo se calculó. */
@Composable
internal fun Hero(label: String, amount: String?, quiet: String, onQuiet: (() -> Unit)?, top: Dp, figure: TextStyle = KomparaType.dayDisplay) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = top)) {
        Text(label, style = GananciasType.heroLabel, color = KomparaTheme.colors.inkSecondary)
        if (amount != null) Text(amount, Modifier.padding(top = 2.dp), style = figure, softWrap = false)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (onQuiet != null) Modifier.clickable(onClick = onQuiet).testTag("calculation") else Modifier)
            .padding(top = 6.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(quiet, Modifier.weight(1f), style = GananciasType.quiet, color = KomparaTheme.colors.inkSecondary)
            if (onQuiet != null) Chevron()
        }
    }
}

@Composable
internal fun Chevron(modifier: Modifier = Modifier) =
    DrawnIcon(R.drawable.v3_chev, modifier.size(20.dp), tint = KomparaTheme.colors.inkTertiary)

/** "Ofertas de hoy 11 … por km, neto" (`.sec`). */
@Composable
internal fun SectionHeader(title: String, count: String, column: String) {
    FlowRow(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = GananciasType.section)
            Text(count, Modifier.padding(start = 6.dp, bottom = 2.dp), style = GananciasType.sectionCount, color = KomparaTheme.colors.inkSecondary)
        }
        Text(column, Modifier.align(Alignment.Bottom).padding(bottom = 3.dp), style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
    }
}

/** ▲ ◆ ▼ in the verdict colours (kit `#up`, `#mid`, `#dn`); a short dash when the offer was not read. */
@Composable
internal fun VerdictShape(state: OfferMarkState, modifier: Modifier = Modifier, width: Dp = 14.dp, height: Dp = 13.dp) {
    val c = KomparaTheme.colors
    val phrase = when (state) {
        OfferMarkState.ABOVE -> "Arriba de tu meta"
        OfferMarkState.MEETS -> "Entre tu mínimo y tu meta"
        OfferMarkState.BELOW -> "Abajo de tu mínimo"
        OfferMarkState.UNKNOWN -> null
    }
    Canvas(modifier.size(width, height).then(if (phrase != null) Modifier.clearAndSetSemantics { contentDescription = phrase } else Modifier.clearAndSetSemantics {})) {
        val w = size.width; val h = size.height
        when (state) {
            OfferMarkState.ABOVE -> drawPath(Path().apply { moveTo(w / 2, 0f); lineTo(w, h); lineTo(0f, h); close() }, c.verde)
            OfferMarkState.MEETS -> drawPath(Path().apply { moveTo(w / 2, 0f); lineTo(w, h / 2); lineTo(w / 2, h); lineTo(0f, h / 2); close() }, c.amarillo)
            OfferMarkState.BELOW -> drawPath(Path().apply { moveTo(0f, 0f); lineTo(w, 0f); lineTo(w / 2, h); close() }, c.rojo)
            OfferMarkState.UNKNOWN -> drawLine(c.inkTertiary, Offset(1.dp.toPx(), h / 2), Offset(1.dp.toPx() + 12.dp.toPx(), h / 2), 2.dp.toPx())
        }
    }
}

/** One offer (`.orow`): its mark, the app's name over "14:32 · La tomaste", and the rate. Offers not taken are dimmed. */
@Composable
internal fun OfferLine(mark: OfferMarkState, app: String, detail: String, rate: String?, dim: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = KomparaTheme.colors
    val strong = if (dim) c.inkTertiary else c.ink
    val stacked = LocalDensity.current.fontScale > 1.3f
    Column(modifier.padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(28.dp)) { VerdictShape(mark) }
            Column(Modifier.weight(1f)) {
                Text(app, style = GananciasType.app, color = strong)
                Text(detail, style = GananciasType.rowDetail, color = c.inkSecondary)
                if (stacked && rate != null) Text(rate, style = GananciasType.rowRate, color = strong, softWrap = false)
            }
            if (!stacked && rate != null) Text(rate, Modifier.padding(start = 12.dp), style = GananciasType.rowRate, color = strong, softWrap = false)
        }
        Rule()
    }
}

/** "Ayer, lunes 19 oct, ganaste $1,240." with the amount strong; it opens that day. */
@Composable
internal fun DayLineText(line: DayLine, onClick: (() -> Unit)?, modifier: Modifier = Modifier, style: TextStyle = KomparaType.body) {
    Text(buildAnnotatedString {
        append(line.prefix)
        withStyle(KomparaType.bodyStrong.toSpanStyle().copy(color = KomparaTheme.colors.ink, fontSize = style.fontSize)) { append(line.amount) }
        append(line.suffix)
    }, modifier.fillMaxWidth().then(if (onClick != null) Modifier.heightIn(min = 48.dp).clickable(onClick = onClick) else Modifier)
        .wrapContentHeight(Alignment.CenterVertically), style = style, color = KomparaTheme.colors.inkSecondary)
}

/** The undo of "Quitar de mi día" and the saved-limits notice, in their own gap; never over a row or an action. */
@Composable
internal fun dayMessage(viewModel: DayDetailViewModel): (@Composable () -> Unit)? {
    val undo by viewModel.undoReceipt.collectAsStateWithLifecycle()
    val error by viewModel.actionError.collectAsStateWithLifecycle()
    LaunchedEffect(undo) { if (undo != null) { delay(6_000); viewModel.dismissMessage() } }
    val limits = LocalLimitsSnackbar.current?.takeIf { it.message != null }
    if (undo == null && error == null && limits == null) return null
    return {
        if (limits != null) { Spacer(Modifier.height(8.dp)); SaveSnackbar(limits); Spacer(Modifier.height(8.dp)) }
        if (undo != null || error != null) Snackbar(Modifier.padding(vertical = 8.dp),
            action = { TextButton(if (undo != null) viewModel::undo else viewModel::dismissMessage) {
                Text(if (undo != null) "Deshacer" else "Cerrar", color = KomparaTheme.colors.paper)
            } }, containerColor = KomparaTheme.colors.ink, contentColor = KomparaTheme.colors.paper) {
            Text(error ?: "Se canceló. Se quitó de tu día.", style = KomparaType.body)
        }
    }
}

/** The offer sheet (L7) and Cómo se calculó (L8) of one day. */
@Composable
internal fun DaySheets(viewModel: DayDetailViewModel, day: DayPresentation?, message: (@Composable () -> Unit)?) {
    val selected by viewModel.selectedOffer.collectAsStateWithLifecycle()
    val ledger by viewModel.ledger.collectAsStateWithLifecycle()
    if (day == null) return
    if (ledger) PeriodCalculationSheet(day.calculation) { viewModel.showLedger(false) }
    else selected?.let { id ->
        val model = day.offer(id)
        if (model != null) OfferDetailSheet(model, day.date.toString(), viewModel::closeOffer, viewModel::cancelOffer, message)
        else SheetScaffold("Oferta", viewModel::closeOffer) { Text("No encontramos esta oferta en el día elegido.", style = KomparaType.body) }
    }
}
