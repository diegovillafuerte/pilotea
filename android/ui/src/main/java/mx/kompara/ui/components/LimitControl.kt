package mx.kompara.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mx.kompara.ui.R
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

@Composable
fun VerdictCue(state: OfferMarkState, modifier: Modifier = Modifier) {
    val c = KomparaTheme.colors
    Canvas(modifier.size(13.dp).clearAndSetSemantics {}) {
        scale(size.width / 30f, size.height / 30f, pivot = Offset.Zero) {
            val path = Path().apply {
                when (state) {
                    OfferMarkState.ABOVE -> { moveTo(15f, 2.5f); lineTo(29f, 27f); lineTo(1f, 27f) }
                    OfferMarkState.MEETS -> { moveTo(15f, 1f); lineTo(29f, 15f); lineTo(15f, 29f); lineTo(1f, 15f) }
                    OfferMarkState.BELOW -> { moveTo(1f, 3f); lineTo(29f, 3f); lineTo(15f, 27.5f) }
                    OfferMarkState.UNKNOWN -> return@scale
                }; close()
            }
            drawPath(path, when (state) { OfferMarkState.ABOVE -> c.verde; OfferMarkState.MEETS -> c.amarillo; else -> c.rojo })
        }
    }
}

@Composable
internal fun RingStep(increase: Boolean, description: String, onClick: () -> Unit, diameter: Dp, enabled: Boolean, strong: Boolean = false) {
    val c = KomparaTheme.colors
    val click by rememberUpdatedState(onClick)
    Box(Modifier.size(diameter).clip(CircleShape).border(1.5.dp, if (strong) c.ink else c.inkTertiary, CircleShape)
        .pointerInput(enabled) { if (enabled) detectTapGestures(onPress = {
            coroutineScope {
                click()
                val repeat = launch { delay(400); while (true) { click(); delay(120) } }
                try { tryAwaitRelease() } finally { repeat.cancel() }
            }
        }) }
        .onKeyEvent { if (enabled && it.type == KeyEventType.KeyUp && (it.key == Key.Enter || it.key == Key.Spacebar)) { click(); true } else false }
        .focusable(enabled)
        .semantics { contentDescription = description; role = Role.Button
            if (!enabled) disabled() else onClick { click(); true } }, contentAlignment = Alignment.Center) {
        Text(if (increase) "+" else "−", Modifier.clearAndSetSemantics {}, style = KomparaType.headline,
            color = if (enabled) c.ink else c.inkTertiary)
    }
}

/** Stateless input: parsing, validation, stepping and draft persistence belong to S12's editor. */
@Composable
fun LimitControl(label: String, value: String, unit: String, onValueChange: (String) -> Unit,
    onDecrease: () -> Unit, onIncrease: () -> Unit, modifier: Modifier = Modifier,
    cue: OfferMarkState = OfferMarkState.ABOVE, helper: String? = null, error: String? = null,
    stepLabel: String = stringResource(R.string.v3_limit_step), stepAmount: String = "$0.50", enabled: Boolean = true,
    editorLayout: Boolean = false, onFocus: (Boolean) -> Unit = {}) {
    if (editorLayout) {
        EditorLimitControl(label, value, unit, onValueChange, onDecrease, onIncrease, modifier, cue,
            helper, error, stepLabel, stepAmount, enabled, onFocus)
        return
    }
    val c = KomparaTheme.colors
    var focused by remember { mutableStateOf(false) }
    val spokenLimit = label.removeSuffix(" ·").replaceFirstChar { it.lowercase() }
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = KomparaType.bodyStrong)
            VerdictCue(cue)
            Text(stringResource(if (cue == OfferMarkState.ABOVE) R.string.v3_from else R.string.v3_below_from), style = KomparaType.body)
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(value, onValueChange, enabled = enabled, singleLine = true,
                textStyle = KomparaType.number.copy(color = c.ink), cursorBrush = SolidColor(c.brand),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.widthIn(min = 160.dp, max = 220.dp).heightIn(min = 48.dp)
                    .onFocusChanged { focused = it.isFocused }.clip(RoundedCornerShape(8.dp))
                    .background(c.paperSecondary).border(if (focused) 2.dp else 0.dp,
                        if (focused) c.brand else c.paperSecondary, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .semantics { contentDescription = label; if (error != null) error(error) },
                decorationBox = { input ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$", style = KomparaType.number)
                        Box(Modifier.weight(1f)) { input() }
                        Text(unit, style = KomparaType.body, color = c.inkSecondary)
                    }
                })
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RingStep(false, stringResource(R.string.v3_decrease, "$spokenLimit $stepAmount"), onDecrease, 48.dp, enabled)
                    RingStep(true, stringResource(R.string.v3_increase, "$spokenLimit $stepAmount"), onIncrease, 48.dp, enabled)
                }
                Text(stepLabel, style = KomparaType.small, color = c.inkSecondary)
            }
        }
        if (error != null) InkNotice(error) else if (helper != null) Text(helper, style = KomparaType.small, color = c.inkSecondary)
    }
}

@Composable
fun StepperField(value: String, unit: String, label: String, onDecrease: () -> Unit, onIncrease: () -> Unit,
    modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        RingStep(false, stringResource(R.string.v3_decrease, label), onDecrease, 56.dp, true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = KomparaType.efficiency)
            Text(unit, Modifier.padding(start = 6.dp), style = KomparaType.body)
        }
        RingStep(true, stringResource(R.string.v3_increase, label), onIncrease, 56.dp, true)
    }
}

/** G2's filled, selected-in-place field. Large text wraps controls without shrinking a target. */
@Composable
private fun EditorLimitControl(label: String, value: String, unit: String, onValueChange: (String) -> Unit,
    onDecrease: () -> Unit, onIncrease: () -> Unit, modifier: Modifier, cue: OfferMarkState,
    helper: String?, error: String?, stepLabel: String, stepAmount: String, enabled: Boolean, onFocus: (Boolean) -> Unit) {
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
    val spoken = label.removeSuffix(" ·").replaceFirstChar { it.lowercase() }
    Column(modifier.fillMaxWidth().border(1.dp, c.rule, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
        BoxWithConstraints {
            val stacked = maxWidth < 280.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.15f
            @Composable fun field(modifier: Modifier) {
                Column(modifier) {
                    FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, style = KomparaType.bodyStrong)
                        VerdictCue(cue)
                        Text(if (cue == OfferMarkState.ABOVE) "desde" else "abajo de", style = KomparaType.body, color = c.inkSecondary)
                    }
                    Spacer(Modifier.height(4.dp))
                    BasicTextField(input, { input = it; onValueChange(it.text) }, enabled = enabled, singleLine = true,
                        textStyle = KomparaType.number.copy(color = c.ink), cursorBrush = SolidColor(c.brand),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        modifier = Modifier.widthIn(max = if (stacked) 240.dp else 155.dp).heightIn(min = 48.dp)
                            .onFocusChanged {
                                focused = it.isFocused; onFocus(it.isFocused)
                            }.clip(RoundedCornerShape(8.dp)).background(c.paperSecondary)
                            .border(if (focused) 2.dp else 0.dp, if (focused) c.brand else c.paperSecondary, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .semantics { contentDescription = label.removeSuffix(" ·"); if (error != null) error(error) },
                        decorationBox = { content -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$", style = KomparaType.number)
                            Box(Modifier.weight(1f)) { content() }
                            Text(unit, style = KomparaType.body, color = c.inkSecondary)
                        } })
                }
            }
            @Composable fun steps() {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RingStep(false, "Bajar $spoken $stepAmount", onDecrease, 48.dp, enabled, strong = true)
                        RingStep(true, "Subir $spoken $stepAmount", onIncrease, 48.dp, enabled, strong = true)
                    }
                    Text(stepLabel, Modifier.padding(top = 4.dp), style = KomparaType.small, color = c.inkSecondary)
                }
            }
            if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { field(Modifier.fillMaxWidth()); steps() }
            else Row(verticalAlignment = Alignment.CenterVertically) { field(Modifier.weight(1f)); steps() }
        }
        if (error != null) InkNotice(error)
        else if (focused && helper != null) Text(helper, Modifier.padding(top = 4.dp), style = KomparaType.small, color = c.inkSecondary)
    }
}
