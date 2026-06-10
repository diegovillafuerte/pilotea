package mx.kompara.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import mx.kompara.ui.theme.KomparaTheme

/**
 * The empty/placeholder surface shown when a screen has no data yet: a large icon, a [title], a
 * supportive [body], and an optional call-to-action. Used by every placeholder tab in the nav
 * shell and reused wherever a list comes up empty.
 *
 * @param ctaText when non-null (with [onCtaClick]), renders a [PrimaryButton] beneath the body.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    ctaText: String? = null,
    onCtaClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (ctaText != null && onCtaClick != null) {
            Spacer(modifier = Modifier.height(28.dp))
            PrimaryButton(
                text = ctaText,
                onClick = onCtaClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true, name = "EmptyState — con CTA")
@Composable
private fun EmptyStatePreview() {
    KomparaTheme {
        EmptyState(
            icon = Icons.Filled.Info,
            title = "Sin datos todavía",
            body = "Activa el lector y maneja — tus números aparecen solos.",
            ctaText = "Activar lector",
            onCtaClick = {},
        )
    }
}

@Preview(showBackground = true, name = "EmptyState — sin CTA")
@Composable
private fun EmptyStateNoCtaPreview() {
    KomparaTheme {
        EmptyState(
            icon = Icons.Filled.Info,
            title = "Ajustes en construcción",
            body = "Aquí podrás configurar tus plataformas, costos y umbrales de ganancia.",
        )
    }
}
