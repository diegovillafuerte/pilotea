package mx.kompara.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.error
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import mx.kompara.ui.theme.KomparaType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.kompara.ui.theme.KomparaTheme

/**
 * Kompara's labelled text field: a tonal input with a STATIC label sitting above it (never a
 * floating Material label, which the design system explicitly rejects). Supports a fixed [prefix]
 * (e.g. "+52" for the WhatsApp number), a [hint], and an [error] that takes over the helper slot
 * with an ink error border. Built on [BasicTextField] + a `decorationBox` so the prefix and
 * placeholder render exactly as the tokens call for, without Material's outlined/filled chrome.
 *
 * At least 56 dp tall for an easy tap with the phone on a mount. Shows [error] (ink) over [hint] when both
 * are set.
 *
 * [readOnly] makes the field non-editable while keeping it fully visible (e.g. the WhatsApp number
 * on Tu cuenta, which the backend never updates). [enabled] = false additionally dims the field to
 * 0.45 alpha and stops it responding (e.g. inputs disabled while a save is in flight).
 */
@Composable
fun KomparaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    prefix: String? = null,
    hint: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    underlined: Boolean = false,
    suffix: String? = null,
    inlineAffixes: Boolean = false,
    cursorAtEndOnFocus: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(value)) }
    if (input.text != value) input = input.copy(text = value)
    LaunchedEffect(focused) {
        if (focused && cursorAtEndOnFocus) {
            withFrameNanos { }
            input = input.copy(selection = TextRange(input.text.length))
        }
    }
    val borderColor = if (error != null) {
        MaterialTheme.colorScheme.error
    } else if (focused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val inputStyle = if (underlined) KomparaType.carSuffix.copy(fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.bodyLarge
    Column(modifier = modifier.alpha(if (enabled) 1f else 0.45f)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        BasicTextField(
            value = input,
            onValueChange = {
                val changed = input.text != it.text
                input = it
                if (changed) onValueChange(it.text)
            },
            singleLine = singleLine,
            readOnly = readOnly,
            enabled = enabled,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            textStyle = inputStyle.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(if (inlineAffixes) ink else MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label; if (error != null) error(error) }
                .padding(top = 6.dp)
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(if (underlined) Modifier.drawBehind {
                    val stroke = (if (focused) 2.dp else 1.dp).toPx()
                    drawLine(ink, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
                } else Modifier.clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))),
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (inlineAffixes) 0.dp else 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (underlined) 0.dp else 14.dp, vertical = 10.dp),
                ) {
                    if (prefix != null) {
                        Text(
                            text = prefix,
                            color = if (inlineAffixes) ink else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = if (underlined) inputStyle else MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Box(modifier = if (inlineAffixes && suffix != null) Modifier.width(IntrinsicSize.Min) else Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                    if (suffix != null) Text(suffix, Modifier.padding(start = if (inlineAffixes) 8.dp else 0.dp),
                        style = KomparaType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        if (error != null || hint != null) {
            Text(
                text = error ?: hint!!,
                color = if (error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "TextField — con prefijo")
@Composable
private fun KomparaTextFieldPrefixPreview() {
    KomparaTheme {
        KomparaTextField(
            value = "",
            onValueChange = {},
            label = "Número de WhatsApp",
            prefix = "+52",
            placeholder = "55 1234 5678",
            keyboardOptions = KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, name = "TextField — con pista")
@Composable
private fun KomparaTextFieldHintPreview() {
    KomparaTheme {
        KomparaTextField(
            value = "Juan",
            onValueChange = {},
            label = "Tu nombre (opcional)",
            hint = "Así te saludamos en la app.",
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, name = "TextField — con error")
@Composable
private fun KomparaTextFieldErrorPreview() {
    KomparaTheme {
        KomparaTextField(
            value = "1234",
            onValueChange = {},
            label = "Código",
            error = "El código no es válido o ya venció.",
            modifier = Modifier.padding(16.dp),
        )
    }
}
