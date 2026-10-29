package mx.kompara.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import mx.kompara.ui.theme.KomparaTheme

/** Kit's 44×26 track inside a real 48dp target. */
@Composable
fun KomparaSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = KomparaTheme.colors
    Box(modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(44.dp, 26.dp).background(if (checked) c.brand else c.paperSecondary, CircleShape)
            .then(if (!checked) Modifier.border(1.5.dp, c.inkTertiary, CircleShape) else Modifier)) {
            Box(Modifier.align(if (checked) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 3.dp)
                .size(20.dp).background(if (checked) c.onBrand else c.inkTertiary, CircleShape))
        }
    }
}
