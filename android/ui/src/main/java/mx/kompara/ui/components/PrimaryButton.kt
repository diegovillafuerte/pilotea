package mx.kompara.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.kompara.ui.theme.KomparaTheme

/**
 * The single, full-width call-to-action button used across Kompara. Tall (min 52 dp) for an easy
 * tap with the phone on a mount, brand-green fill, bold label. A thin alias over [KomparaButton]'s
 * [ButtonVariant.PRIMARY] tier so existing call sites (EmptyState + screens) keep working unchanged.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    KomparaButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        variant = ButtonVariant.PRIMARY,
        fullWidth = true,
        enabled = enabled,
    )
}

@Preview(showBackground = true, name = "PrimaryButton")
@Composable
private fun PrimaryButtonPreview() {
    KomparaTheme {
        PrimaryButton(
            text = "Activar lector",
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, name = "PrimaryButton — deshabilitado")
@Composable
private fun PrimaryButtonDisabledPreview() {
    KomparaTheme {
        PrimaryButton(
            text = "Activar lector",
            onClick = {},
            enabled = false,
            modifier = Modifier.padding(16.dp),
        )
    }
}
