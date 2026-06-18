package mx.kompara.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.kompara.ui.theme.KomparaTheme

/**
 * Kompara's single-axis filter chip (the platform switcher and similar). A fully-round pill whose
 * SELECTED state is the brand emerald — a 16% tonal fill, emerald label, and a 40% emerald hairline.
 * A bare Material3 [FilterChip] falls back to a non-brand container because `secondaryContainer`
 * isn't wired into the theme, so the selected look is set explicitly here. Unselected is the tonal
 * neutral (transparent fill, `outline` border, muted label) the design already calls for.
 */
@Composable
fun KomparaChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        shape = RoundedCornerShape(percent = 50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = primary.copy(alpha = 0.16f),
            selectedLabelColor = primary,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) primary.copy(alpha = 0.40f) else MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier,
    )
}

@Preview(showBackground = true, name = "Chips — plataforma")
@Composable
private fun KomparaChipPreview() {
    KomparaTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KomparaChip(selected = true, onClick = {}, label = "Todas")
            KomparaChip(selected = false, onClick = {}, label = "Uber")
            KomparaChip(selected = false, onClick = {}, label = "DiDi")
        }
    }
}
