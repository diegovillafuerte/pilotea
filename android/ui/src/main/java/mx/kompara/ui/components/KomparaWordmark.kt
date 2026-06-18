package mx.kompara.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.kompara.ui.R
import mx.kompara.ui.theme.BrandGreen

/**
 * The shared Kompara brand lockup (S-024): the emerald "K" logomark tile + the wordmark. Extracted to
 * `:ui` so every screen (and any future tab brand bar) renders the same mark — previously the only
 * lockup was a private `BrandStrip` in the overlay module. Especially used on the Comparar shareable
 * hero, the screenshot-worthy surface.
 *
 * @param onEmerald when true, render the lockup for an emerald-gradient surface (the Tu Mes / Comparar
 *   share heroes): the K glyph sits on a translucent-white tile and the wordmark is white — never a
 *   verdict colour. When false (default) it's the neutral-surface lockup (emerald tile, onSurface text).
 */
@Composable
fun KomparaWordmark(
    modifier: Modifier = Modifier,
    onEmerald: Boolean = false,
    wordmarkColor: Color = if (onEmerald) Color.White else MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (onEmerald) Color.White.copy(alpha = 0.18f) else BrandGreen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_kompara_logomark),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = stringResource(R.string.brand_name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = wordmarkColor,
        )
    }
}
