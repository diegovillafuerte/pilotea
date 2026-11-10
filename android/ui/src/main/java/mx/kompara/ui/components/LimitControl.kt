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
