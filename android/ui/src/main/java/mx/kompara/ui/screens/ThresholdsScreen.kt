package mx.kompara.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.RateBasis
import mx.kompara.ui.components.*
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType
import java.math.BigDecimal
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ThresholdsScreen(onBack: () -> Unit, onSaved: (String) -> Unit, modifier: Modifier = Modifier,
    viewModel: ThresholdsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.result) { state.result?.let(onSaved) }
    ThresholdsContent(state, viewModel::edit, viewModel::step, viewModel::select, viewModel::reset,
        viewModel::save, viewModel::confirmExit, onBack, modifier, viewModel::load, viewModel::choose)
}

/**
 * J2 and J7 (v4 plan 4.2, 4.8): how the driver decides and what the chip shows, the live example,
 * the three-colour band, the two limits. A switch of basis is staged until Guardar.
 */
@Composable
fun ThresholdsContent(state: ThresholdsUiState, edit: (LimitField, String) -> Unit,
    step: (LimitField, Boolean) -> Unit, select: (PreferredMetric) -> Unit, reset: () -> Unit,
    save: () -> Unit, confirm: (Boolean) -> Unit, close: () -> Unit, modifier: Modifier = Modifier,
    retry: () -> Unit = {}, choose: (RateBasis) -> Unit = {}) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val ime = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var focused by remember { mutableStateOf<LimitField?>(null) }
    val back = {
        if (!state.saving) when (limitsBack(ime, state.dirty)) {
            LimitsBack.HIDE_KEYBOARD -> { keyboard?.hide(); focus.clearFocus() }
            LimitsBack.CONFIRM -> confirm(true)
            LimitsBack.CLOSE -> close()
        }
    }
    BackHandler { back() }
    ScreenScaffold("Tu meta y tu mínimo", modifier, ScreenKind.DRILL, back, footer = {
        PrimaryButton("Guardar", save, Modifier.testTag("limits-save"), enabled = state.canSave)
        if (!ime) {
            val initial = state.initial?.copy(metric = state.draft?.metric ?: PreferredMetric.IPK)
            val label = if (state.staged) "Cancelar" else initial?.let {
                "Volver a ${ThresholdEditor.amount(it.pair.goal, it.unit)} y ${ThresholdEditor.amount(it.pair.minimum, it.unit)}"
            } ?: "Volver a lo inicial"
            TextButton(reset, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("limits-reset"), enabled = state.canReset && !state.saving) {
                Text(label, style = KomparaType.bodyStrong, color = if (state.canReset) KomparaTheme.colors.ink else KomparaTheme.colors.inkTertiary)
            }
        }
    }) {
        val draft = state.draft
        if (draft == null) {
            if (state.error != null) { InkNotice(state.error); TextButton(retry) { Text("Reintentar") } }
            else StaticSkeleton(Modifier.fillMaxWidth().height(180.dp))
            return@ScreenScaffold
        }
        SettingsChoice("Decides por", listOf("Por km", "Por hora"), draft.metric.ordinal, { if (!state.saving) select(PreferredMetric.entries[it]) })
        SettingsChoice("Muestra", listOf("Neto", "Bruto"), draft.basis.ordinal, { choose(RateBasis.entries[it]) },
            enabled = state.cost != null && !state.saving)
        Text(ThresholdEditor.basisHelper(draft.basis, draft.metric, state.cost), Modifier.padding(top = 4.dp),
            style = RowDetail, color = KomparaTheme.colors.inkSecondary)
        Column(Modifier.padding(top = 14.dp).fillMaxWidth().heightIn(min = 108.dp)
            .background(KomparaTheme.colors.paperSecondary, RoundedCornerShape(12.dp)),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (draft.metric == PreferredMetric.IPK) "Ejemplo · $148 · 12 km" else "Ejemplo · $148 · 12 km · 24 min",
                Modifier.align(Alignment.Start).padding(start = 12.dp, top = 9.dp), style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
            // At the largest font the real chip is wider than the phone; it scrolls inside the example rather than shrink.
            Box(Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 18.dp)) {
                state.preview?.let { Regla(it) }
            }
        }
        state.shown?.let { shown -> LimitsBand(ThresholdEditor.parse(shown.goal)!!, ThresholdEditor.parse(shown.minimum)!!, draft.unit, Modifier.padding(top = 20.dp)) }
        LimitField.entries.forEach { field ->
            val goal = field == LimitField.GOAL
            LimitRow(if (goal) "Tu meta" else "Tu mínimo", if (goal) draft.pair.goal else draft.pair.minimum,
                "${draft.unit.suffix} ${ThresholdEditor.basisWord(draft.basis)}", { edit(field, it) }, { step(field, false) }, { step(field, true) },
                cue = if (goal) OfferMarkState.ABOVE else OfferMarkState.BELOW,
                helper = if (goal) "Mayor que tu mínimo (${ThresholdEditor.limit(draft.pair.minimum, draft.unit)})" else "Menor que tu meta (${ThresholdEditor.limit(draft.pair.goal, draft.unit)})",
                error = state.error?.takeIf { field == state.touched },
                stepAmount = if (draft.metric == PreferredMetric.IPK) "$0.50" else "$10", enabled = !state.saving,
                onFocus = { if (it) focused = field else if (focused == field) focused = null })
        }
        val line = state.explanation ?: if (focused == null)
            "De ${if (draft.metric == PreferredMetric.IPK) "$0.50 en $0.50" else "$10 en $10"}. Toca la cifra para escribirla." else null
        line?.let { Text(it, Modifier.padding(top = 12.dp), style = RowDetail, color = KomparaTheme.colors.inkSecondary) }
    }
    if (state.confirmExit && state.draft != null && state.saved != null) Dialog(
        onDismissRequest = { if (!state.saving) confirm(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("limits-exit-dialog"),
            shape = RoundedCornerShape(4.dp), color = KomparaTheme.colors.paper,
            border = BorderStroke(1.dp, KomparaTheme.colors.rule), shadowElevation = 4.dp) {
            Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)) {
                Text("¿Guardar el cambio?", style = KomparaType.title)
                Spacer(Modifier.height(8.dp))
                Text(ThresholdEditor.changes(state.draft, state.saved), style = KomparaType.body,
                    color = KomparaTheme.colors.inkSecondary)
                Spacer(Modifier.height(4.dp))
                FlowRow(Modifier.fillMaxWidth().offset(x = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(close, enabled = !state.saving, contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("No guardar", style = KomparaType.bodyStrong, color = KomparaTheme.colors.ink)
                    }
                    TextButton(save, enabled = state.canSave, contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Guardar", style = KomparaType.bodyStrong, color = KomparaTheme.colors.ink)
                    }
                }
            }
        }
    }
}

/**
 * The kit's `.scale`: red up to the mínimo, yellow up to the meta, green to 1.5 × the meta, cut by a
 * paper tick at each limit with its amount under it. Labels move apart rather than overlap.
 */
@Composable
private fun LimitsBand(goal: BigDecimal, minimum: BigDecimal, unit: ReglaUnit, modifier: Modifier = Modifier) {
    val c = KomparaTheme.colors
    val top = goal.toFloat() * 1.5f
    val red = (minimum.toFloat() / top).coerceIn(0f, 1f)
    val yellow = (goal.toFloat() / top).coerceIn(red, 1f)
    val tick = KomparaType.rate.copy(fontSize = 14.sp, lineHeight = 18.sp)
    Layout(content = {
        Canvas(Modifier.clearAndSetSemantics {}) {
            val r = 5.dp.toPx(); val gap = 2.dp.toPx()
            drawRoundRect(c.verde, cornerRadius = CornerRadius(r))
            drawRoundRect(c.rojo, size = Size(size.width * yellow, size.height), cornerRadius = CornerRadius(r))
            drawRect(c.amarillo, Offset(size.width * red, 0f), Size(size.width * (yellow - red), size.height))
            if (red > 0f) drawRect(c.paper, Offset(size.width * red - gap / 2, 0f), Size(gap, size.height))
            drawRect(c.paper, Offset(size.width * yellow - gap / 2, 0f), Size(gap, size.height))
        }
        Text(ThresholdEditor.amount(minimum.toPlainString(), unit), style = tick)
        Text(ThresholdEditor.amount(goal.toPlainString(), unit), style = tick)
    }, modifier.fillMaxWidth()) { measurables, constraints ->
        val width = constraints.maxWidth
        val bar = measurables[0].measure(Constraints.fixed(width, 10.dp.roundToPx()))
        val low = measurables[1].measure(Constraints())
        val high = measurables[2].measure(Constraints())
        var lowX = (width * red - low.width / 2f).roundToInt()
        var highX = (width * yellow - high.width / 2f).roundToInt()
        val overlap = lowX + low.width + 6.dp.roundToPx() - highX
        if (overlap > 0) { lowX -= overlap / 2; highX += overlap - overlap / 2 }
        lowX = lowX.coerceIn(0, max(0, width - low.width)); highX = highX.coerceIn(0, max(0, width - high.width))
        val labelTop = 18.dp.roundToPx()
        layout(width, max(40.dp.roundToPx(), labelTop + max(low.height, high.height))) {
            bar.place(0, 0); low.place(lowX, labelTop); high.place(highX, labelTop)
        }
    }
}

/**
 * The kit's `.lim`: "Tu meta · ▲ desde", the figure (tap to type; the whole amount is selected), its
 * unit and basis, and two held steppers. Editing rules stay the editor's: typing never writes settings.
 */
@Composable
private fun LimitRow(label: String, value: String, unit: String, onValueChange: (String) -> Unit,
    onDecrease: () -> Unit, onIncrease: () -> Unit, cue: OfferMarkState, helper: String, error: String?,
    stepAmount: String, enabled: Boolean, onFocus: (Boolean) -> Unit) {
    val c = KomparaTheme.colors
    var focused by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf(TextFieldValue(value)) }
    if (input.text != value) input = TextFieldValue(value, TextRange(value.length))
    LaunchedEffect(focused) {
        if (focused) {
            // The initial tap places a cursor after focus dispatch; select after that gesture.
            withFrameNanos { }
            input = input.copy(selection = TextRange(0, input.text.length))
        }
    }
    val spoken = label.replaceFirstChar { it.lowercase() }
    val figure = KomparaType.number.copy(fontWeight = FontWeight.Bold, color = c.ink)
    // A text field fills its width; the figure hugs what is typed so the unit sits right after it, as drawn.
    val measurer = rememberTextMeasurer()
    val typed = with(LocalDensity.current) { measurer.measure(input.text.ifEmpty { "0" }, figure).size.width.toDp() } + 2.dp
    Column(Modifier.fillMaxWidth()) {
        FlowRow(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label, style = KomparaType.meaning.copy(fontWeight = FontWeight.SemiBold))
                    Text("·", style = KomparaType.meaning, color = c.inkSecondary)
                    VerdictCue(cue, Modifier.size(11.dp))
                    Text(if (cue == OfferMarkState.ABOVE) "desde" else "abajo de", style = KomparaType.meaning, color = c.inkSecondary)
                }
                CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(
                    handleColor = c.ink, backgroundColor = c.ink.copy(alpha = .14f))) {
                    BasicTextField(input, { input = it; onValueChange(it.text) }, enabled = enabled, singleLine = true,
                        textStyle = figure, cursorBrush = SolidColor(c.brand),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        modifier = Modifier.padding(top = 3.dp).heightIn(min = 48.dp)
                            .onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) }
                            .then(if (focused) Modifier.border(2.dp, c.brand, RoundedCornerShape(8.dp)) else Modifier)
                            .padding(horizontal = if (focused) 6.dp else 0.dp)
                            .semantics { contentDescription = label; if (error != null) error(error) },
                        decorationBox = { content -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$", style = figure)
                            Box(Modifier.width(typed)) { content() }
                            Text(unit, Modifier.padding(start = 4.dp, top = 6.dp), style = RowDetail, color = c.inkSecondary)
                        } })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RingStep(false, "Bajar $spoken $stepAmount", onDecrease, 48.dp, enabled, strong = true)
                RingStep(true, "Subir $spoken $stepAmount", onIncrease, 48.dp, enabled, strong = true)
            }
        }
        if (error != null) InkNotice(error)
        else if (focused) Text(helper, Modifier.padding(bottom = 8.dp), style = KomparaType.small, color = c.inkSecondary)
        Rule()
    }
}
