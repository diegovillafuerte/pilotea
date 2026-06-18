package mx.kompara.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.kompara.ui.theme.KomparaTheme

/**
 * The brand-themed slider used inside the app (Ajustes → "Tu semáforo" threshold floors). A thin
 * wrapper over Material3 [Slider] that paints the thumb and active track brand emerald and the
 * inactive track the neutral surface step, so call sites stop repeating the `SliderDefaults`
 * boilerplate. The caller owns the label/value readout and drives state — this is just the control.
 *
 * The overlay keeps its own `FloorSlider`; this is the in-app `:ui` version for `ThresholdsScreen`.
 */
@Composable
fun KomparaSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

@Preview(showBackground = true, name = "KomparaSlider")
@Composable
private fun KomparaSliderPreview() {
    KomparaTheme {
        var value by remember { mutableFloatStateOf(0.6f) }
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Verde desde", style = MaterialTheme.typography.bodyMedium)
            KomparaSlider(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true, name = "KomparaSlider — deshabilitado")
@Composable
private fun KomparaSliderDisabledPreview() {
    KomparaTheme {
        KomparaSlider(
            value = 0.4f,
            onValueChange = {},
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}
