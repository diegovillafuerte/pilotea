package mx.kompara.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.kompara.ui.theme.KomparaTheme

/**
 * The 6-cell code input used on the WhatsApp OTP verification step. A single hidden
 * [BasicTextField] sits over a row of cell boxes — typing, paste and backspace all flow through
 * the one field, while the cells just mirror [value]. This single-field-with-decorationBox pattern
 * keeps the keyboard and IME behaviour reliable (per-cell fields fight over focus). Digits only;
 * the field caps input at [length]. The cell holding the next digit (the cursor) reads as
 * brand-emerald; in [error] every cell turns the error red; otherwise filled/empty cells use the
 * neutral outline. Callers own placement; the input fills its own intrinsic width.
 */
@Composable
fun KomparaOtpInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    error: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = { raw ->
            onValueChange(raw.filter { it.isDigit() }.take(length))
        },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(length) { index ->
                    val char = value.getOrNull(index)
                    val isCursor = index == value.length
                    val borderColor = when {
                        error -> MaterialTheme.colorScheme.error
                        isCursor -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(44.dp)
                            .height(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(
                                border = BorderStroke(1.5.dp, borderColor),
                                shape = RoundedCornerShape(12.dp),
                            ),
                    ) {
                        Text(
                            // "title" token = 16/700 → titleMedium (16sp); Bold for the digit weight.
                            text = char?.toString() ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        },
    )
}

@Preview(showBackground = true, name = "OtpInput — parcial")
@Composable
private fun KomparaOtpInputPreview() {
    KomparaTheme {
        KomparaOtpInput(
            value = "1234",
            onValueChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, name = "OtpInput — vacío")
@Composable
private fun KomparaOtpInputEmptyPreview() {
    KomparaTheme {
        KomparaOtpInput(
            value = "",
            onValueChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, name = "OtpInput — error")
@Composable
private fun KomparaOtpInputErrorPreview() {
    KomparaTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            KomparaOtpInput(
                value = "9087",
                onValueChange = {},
                error = true,
            )
        }
    }
}
