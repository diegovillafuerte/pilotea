package mx.kompara.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.kompara.ui.theme.KomparaTheme

private val BarHeight = 64.dp

/**
 * Kompara's bottom navigation bar: five uniform flat tabs (Inicio · Comparar · Lector · Fiscal ·
 * Ajustes), the selected one tinted in the brand primary. No raised centre button — matches the
 * redesign prototypes; the Lector tab is a normal tab like the rest.
 *
 * @param current the currently-selected destination (drives the highlight).
 * @param onSelect invoked with the tapped destination.
 */
@Composable
fun KomparaBottomBar(
    current: KomparaDestination,
    onSelect: (KomparaDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The raised centre button is taller than the flat tabs, so the whole component grows past
    // [BarHeight]. The coloured bar Surface is docked to the bottom at exactly [BarHeight]; the
    // centre circle and its label rise into the space above it. Drawing the tabs in a sibling Box
    // (not inside the Surface) keeps that raised content from being clipped by the Surface bounds.
    // System navigation-bar insets are consumed here (Scaffold doesn't inset custom bottom bars):
    // the tabs sit above the gesture pill and a same-colour strip fills the inset area below.
    Column(modifier = modifier.fillMaxWidth()) {
        KomparaBottomBarContent(current = current, onSelect = onSelect)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            content = {},
        )
    }
}

@Composable
private fun KomparaBottomBarContent(
    current: KomparaDestination,
    onSelect: (KomparaDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(BarHeight),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            content = {},
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // All five tabs are uniform flat tabs (no raised centre button) — matches the redesign
            // prototypes; the Lector tab is a normal tab like the rest.
            KomparaDestination.barItems.forEach { destination ->
                FlatTab(
                    destination = destination,
                    selected = destination == current,
                    onClick = { onSelect(destination) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FlatTab(
    destination: KomparaDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .height(BarHeight)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = stringResource(destination.contentDescriptionRes),
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}

@Preview(showBackground = true, name = "BottomBar — Inicio activo")
@Composable
private fun KomparaBottomBarPreview() {
    KomparaTheme {
        KomparaBottomBar(current = KomparaDestination.INICIO, onSelect = {})
    }
}

@Preview(showBackground = true, name = "BottomBar — Lector activo")
@Composable
private fun KomparaBottomBarLectorPreview() {
    KomparaTheme {
        KomparaBottomBar(current = KomparaDestination.LECTOR, onSelect = {})
    }
}
