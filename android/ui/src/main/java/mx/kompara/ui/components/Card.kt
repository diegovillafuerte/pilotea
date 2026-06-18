package mx.kompara.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.kompara.ui.theme.KomparaTheme

/** The surface step a [KomparaCard] sits on. In-app cards separate by colour, never by shadow. */
enum class CardTone { DEFAULT, VARIANT }

/**
 * The standard Kompara card: a tonal container with NO shadow (design tokens — surfaces separate by
 * a colour step: background → surfaceContainer → surfaceVariant). Wraps Material3 [Card] with the
 * brand colours and zeroed elevation so call sites stop repeating the `CardDefaults` boilerplate.
 * Callers own the interior padding (canonical 16dp).
 *
 * @param tone DEFAULT = surfaceContainer (the standard card), VARIANT = surfaceVariant (a tonal lift).
 */
@Composable
fun KomparaCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.DEFAULT,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = when (tone) {
                CardTone.DEFAULT -> MaterialTheme.colorScheme.surfaceContainer
                CardTone.VARIANT -> MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

@Preview(showBackground = true, name = "KomparaCard — tonos")
@Composable
private fun KomparaCardPreview() {
    KomparaTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            KomparaCard {
                Text("surfaceContainer", modifier = Modifier.padding(16.dp))
            }
            KomparaCard(tone = CardTone.VARIANT) {
                Text("surfaceVariant", modifier = Modifier.padding(16.dp))
            }
        }
    }
}
