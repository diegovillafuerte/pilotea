package mx.kompara.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

/** One real editable field (paste/IME/TalkBack), decorated with six underlined digits. */
@Composable
fun KomparaOtpInput(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    length: Int = 6, error: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val c = KomparaTheme.colors
    BasicTextField(value, { raw -> onValueChange(raw.filter { it in '0'..'9' }.take(length)) },
        modifier.fillMaxWidth().heightIn(min = 56.dp).onFocusChanged { focused = it.isFocused },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = KomparaType.number.copy(color = c.ink),
        decorationBox = { inner ->
            Box {
                // The actual text field must be invoked even though its glyphs are decorated below.
                Box(Modifier.matchParentSize().alpha(0f)) { inner() }
                Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(length) { i ->
                        Column(Modifier.weight(1f).heightIn(min = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(value.getOrNull(i)?.toString() ?: "", Modifier.heightIn(min = 54.dp), style = KomparaType.number)
                            Box(Modifier.fillMaxWidth().height(2.dp).background(when {
                                error -> c.ink
                                focused && i == value.length.coerceAtMost(length - 1) -> c.brand
                                else -> c.rule
                            }))
                        }
                    }
                }
            }
        })
}
