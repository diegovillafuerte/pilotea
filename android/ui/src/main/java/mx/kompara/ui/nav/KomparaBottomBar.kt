package mx.kompara.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import mx.kompara.ui.components.RootBottomBar

/** The shared v3 bar owns geometry, icons, night tint and accessible targets. */
@Composable
fun KomparaBottomBar(current: KomparaDestination, onSelect: (KomparaDestination) -> Unit, modifier: Modifier = Modifier) {
    RootBottomBar(KomparaDestination.barItems.indexOf(current).coerceAtLeast(0),
        { onSelect(KomparaDestination.barItems[it]) }, modifier)
}
