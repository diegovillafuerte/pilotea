package mx.kompara.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

/** Compatibility tiers; new screens use PrimaryButton or ink text actions. */
enum class ButtonVariant { PRIMARY, SECONDARY, TONAL, TEXT }
enum class ButtonSize { MD, SM }

@Composable
fun KomparaButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY, size: ButtonSize = ButtonSize.MD,
    fullWidth: Boolean = false, enabled: Boolean = true, leadingIcon: ImageVector? = null,
    contentColor: Color? = null,
) {
    val c = KomparaTheme.colors
    val primary = variant == ButtonVariant.PRIMARY
    val fill = when (variant) {
        ButtonVariant.PRIMARY -> c.brand
        ButtonVariant.TONAL -> c.paperSecondary
        else -> Color.Transparent
    }
    val height = when {
        size == ButtonSize.SM -> 48.dp
        primary -> 56.dp
        variant == ButtonVariant.TONAL -> 52.dp
        else -> 48.dp
    }
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.then(if (fullWidth) Modifier.fillMaxWidth() else Modifier).heightIn(min = height),
        shape = RoundedCornerShape(10.dp), elevation = null,
        border = if (variant == ButtonVariant.SECONDARY) BorderStroke(1.5.dp, c.inkTertiary) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = fill, contentColor = contentColor ?: if (primary) c.onBrand else c.ink,
            disabledContainerColor = if (primary) c.paperSecondary else fill,
            disabledContentColor = c.inkTertiary,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = KomparaType.bodyStrong)
    }
}
