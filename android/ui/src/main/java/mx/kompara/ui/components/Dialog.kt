package mx.kompara.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import mx.kompara.ui.theme.KomparaTheme

/**
 * Kompara's prominent modal — the Play-required prominent-disclosure consent ("Kompara leerá tu
 * pantalla"), delete confirmations, and similar one-decision moments. A card floats over the scrim
 * with an optional brand icon, a [title], a [body], and up to two stacked actions: the primary
 * [confirmText] on top and, when [dismissText] is non-null, a quieter text action beneath it.
 *
 * Render conditionally on the caller's own `if (show)`; there is no `open` flag. The confirm action
 * reuses [PrimaryButton] (full-width, tall, brand) so it reads identically to every other CTA. All
 * copy is caller-passed — nothing here is hardcoded.
 *
 * @param onDismiss invoked for the text action AND for back-press / scrim taps, so the modal always
 *   has a way out even when [dismissText] is null.
 * @param icon optional, tinted brand emerald above the title (the disclosure dialog leads with it).
 */
@Composable
fun KomparaDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    icon: ImageVector? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = modifier,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                PrimaryButton(
                    text = confirmText,
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (dismissText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = dismissText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "KomparaDialog — disclosure")
@Composable
private fun KomparaDialogPreview() {
    KomparaTheme {
        KomparaDialog(
            title = "Kompara leerá tu pantalla",
            body = "Para mostrarte el semáforo sobre las ofertas, Kompara captura tu " +
                "pantalla y la analiza únicamente en tu teléfono.",
            confirmText = "Continuar",
            onConfirm = {},
            onDismiss = {},
            dismissText = "Ahora no",
            icon = Icons.Filled.Info,
        )
    }
}

@Preview(showBackground = true, name = "KomparaDialog — solo confirmar")
@Composable
private fun KomparaDialogConfirmOnlyPreview() {
    KomparaTheme {
        KomparaDialog(
            title = "Listo",
            body = "El lector ya está activo. Maneja como siempre y revisa el semáforo.",
            confirmText = "Entendido",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
