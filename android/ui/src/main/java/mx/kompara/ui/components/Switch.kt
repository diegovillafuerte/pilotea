package mx.kompara.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.kompara.ui.theme.KomparaTheme

/**
 * Kompara's binary toggle (notifications, EV vehicle, premium-test debug). A thin wrapper over
 * Material3 [Switch] that sets the brand colours explicitly: brand emerald track + white thumb when
 * ON, a tonal `surfaceVariant` track with an `outline` thumb and hairline when OFF. The bare
 * Material3 switch falls back to non-brand container roles, so the ON/OFF look is pinned here.
 */
@Composable
fun KomparaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = scheme.primary,
            uncheckedThumbColor = scheme.outline,
            uncheckedTrackColor = scheme.surfaceVariant,
            uncheckedBorderColor = scheme.outline,
        ),
        modifier = modifier,
    )
}

@Preview(showBackground = true, name = "KomparaSwitch — estados")
@Composable
private fun KomparaSwitchPreview() {
    KomparaTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            KomparaSwitch(checked = true, onCheckedChange = {})
            KomparaSwitch(checked = false, onCheckedChange = {})
            KomparaSwitch(checked = true, onCheckedChange = {}, enabled = false)
            KomparaSwitch(checked = false, onCheckedChange = {}, enabled = false)
        }
    }
}
