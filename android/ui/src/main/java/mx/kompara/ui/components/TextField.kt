package mx.kompara.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.kompara.ui.theme.KomparaTheme

/**
 * Kompara's labelled text field: a tonal input with a STATIC label sitting above it (never a
 * floating Material label, which the design system explicitly rejects). Supports a fixed [prefix]
 * (e.g. "+52" for the WhatsApp number), a [hint], and an [error] that takes over the helper slot
 * and reddens the border. Built on [BasicTextField] + a `decorationBox` so the prefix and
 * placeholder render exactly as the tokens call for, without Material's outlined/filled chrome.
 *
 * 52 dp tall for an easy tap with the phone on a mount. Shows [error] (red) over [hint] when both
 * are set.
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
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val borderColor = if (error != null) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.outline
    }
    Column(modifier = modifier) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    width = 1.5.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp),
                ),
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                ) {
                    if (prefix != null) {
                        Text(
                            text = prefix,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
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
                fontSize = 12.sp,
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
