package mx.kompara.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import mx.kompara.ui.theme.KomparaTheme

/**
 * The emphasis tier of a [KomparaButton] — pick by importance, not by looks: at most one
 * [PRIMARY] (the single filled brand-green CTA) per surface, with [SECONDARY] (outlined),
 * [TONAL] (soft fill that belongs on a card) or [TEXT] (low-stakes inline) for the rest.
 */
enum class ButtonVariant { PRIMARY, SECONDARY, TONAL, TEXT }

/** Button height tier: [MD] is the tall full-bleed default (52 dp), [SM] is the compact 40 dp inline size. */
enum class ButtonSize { MD, SM }

// Slate-500 — the "border-strong" hairline for the SECONDARY (outlined) tier. Not a Material role
// (the theme's `outline` is the softer slate-600), so it's a raw token to match the design system.
private val BorderStrong = Color(0xFF64748B)

/**
 * Kompara's unified button across all four emphasis tiers. Radius 14 dp, bold label with a tight
 * (-0.01em) tracking, tall enough for an easy tap with the phone on a dashboard mount. The tier
 * drives the fill/border/content colours; [size] drives the height + horizontal padding + label
 * size; [fullWidth] stretches it to the container for the app's full-bleed CTAs.
 *
 * @param text the label (caller-passed; es-MX, voz "tú").
 * @param onClick fired on tap.
 * @param variant emphasis tier — see [ButtonVariant]. Defaults to [ButtonVariant.PRIMARY].
 * @param size [ButtonSize.MD] (52 dp, default) or [ButtonSize.SM] (40 dp).
 * @param fullWidth stretch to the parent width.
 * @param enabled when false the whole button dims to 0.45 alpha and stops responding.
 * @param leadingIcon optional glyph shown 8 dp before the label, tinted the content colour.
 */
@Composable
fun KomparaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    size: ButtonSize = ButtonSize.MD,
    fullWidth: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val height = if (size == ButtonSize.SM) 40.dp else 52.dp
    val hPadding = if (size == ButtonSize.SM) 14.dp else 20.dp
    val fontSize = if (size == ButtonSize.SM) 14.sp else 16.sp
    val contentColor = when (variant) {
        ButtonVariant.PRIMARY -> MaterialTheme.colorScheme.onPrimary
        ButtonVariant.SECONDARY, ButtonVariant.TONAL, ButtonVariant.TEXT -> MaterialTheme.colorScheme.primary
    }

    val shape = RoundedCornerShape(14.dp)
    val contentPadding = PaddingValues(horizontal = hPadding)
    // "disabled -> whole button alpha 0.45" — dim the entire button rather than the per-state
    // Material disabled colours, so every tier fades uniformly (matches the design system).
    val sizing = modifier
        .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
        .heightIn(min = height)
        .alpha(if (enabled) 1f else 0.45f)

    val label: @Composable () -> Unit = {
        ButtonLabel(text = text, fontSize = fontSize, leadingIcon = leadingIcon, tint = contentColor)
    }

    when (variant) {
        ButtonVariant.PRIMARY -> Button(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = contentColor,
                disabledContainerColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = contentColor,
            ),
            contentPadding = contentPadding,
            modifier = sizing,
            content = { label() },
        )

        ButtonVariant.SECONDARY -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = contentColor,
                disabledContentColor = contentColor,
            ),
            border = BorderStroke(1.5.dp, BorderStrong),
            contentPadding = contentPadding,
            modifier = sizing,
            content = { label() },
        )

        ButtonVariant.TONAL -> FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = contentColor,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = contentColor,
            ),
            contentPadding = contentPadding,
            modifier = sizing,
            content = { label() },
        )

        ButtonVariant.TEXT -> TextButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.textButtonColors(
                contentColor = contentColor,
                disabledContentColor = contentColor,
            ),
            contentPadding = contentPadding,
            modifier = sizing,
            content = { label() },
        )
    }
}

/** Bold label + optional 18 dp leading glyph (8 dp gap), shared by every tier. */
@Composable
private fun ButtonLabel(
    text: String,
    fontSize: TextUnit,
    leadingIcon: ImageVector?,
    tint: Color,
) {
    if (leadingIcon != null) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
    }
    Text(
        text = text,
        style = TextStyle(
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.01).em,
        ),
    )
}

@Preview(showBackground = true, name = "KomparaButton — tiers")
@Composable
private fun KomparaButtonPreview() {
    KomparaTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            KomparaButton(
                text = "Encender lector",
                onClick = {},
                variant = ButtonVariant.PRIMARY,
                fullWidth = true,
                leadingIcon = Icons.Filled.PlayArrow,
            )
            KomparaButton(
                text = "Probar en el simulador",
                onClick = {},
                variant = ButtonVariant.SECONDARY,
                fullWidth = true,
            )
            KomparaButton(
                text = "Configurar costos",
                onClick = {},
                variant = ButtonVariant.TONAL,
            )
            KomparaButton(
                text = "Ahora no",
                onClick = {},
                variant = ButtonVariant.TEXT,
                size = ButtonSize.SM,
            )
            KomparaButton(
                text = "Deshabilitado",
                onClick = {},
                variant = ButtonVariant.PRIMARY,
                fullWidth = true,
                enabled = false,
            )
        }
    }
}
