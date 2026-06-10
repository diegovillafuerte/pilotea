package mx.kompara.ui.nav

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.kompara.ui.theme.KomparaTheme

private val BarHeight = 64.dp
private val CenterButtonSize = 60.dp

/**
 * Kompara's bottom navigation bar. Four flat tabs plus a raised, filled circle for the centre
 * [KomparaDestination.LECTOR] action — the product's signature gesture, echoing the web MVP's
 * "Subir" button so a chofer's thumb always lands on the reader.
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
            KomparaDestination.barItems.forEach { destination ->
                if (destination.isCenter) {
                    CenterTab(
                        destination = destination,
                        selected = destination == current,
                        onClick = { onSelect(destination) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
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

@Composable
private fun CenterTab(
    destination: KomparaDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The reader button is always the brand circle. The column flows from the raised circle down to
    // the label; the parent Box is taller than the bar so the circle rises above it, and the bottom
    // padding drops the label onto the same baseline as the flat tabs' labels.
    val circleColor = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier.padding(bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(CenterButtonSize)
                .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(circleColor)
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = stringResource(destination.contentDescriptionRes),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
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
