package mx.kompara.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Placeholder theme — replaced by the design system in B-026.
private val DarkColorScheme = darkColorScheme(
    primary = KomparaGreen,
    secondary = KomparaYellow,
    error = KomparaRed,
)

private val LightColorScheme = lightColorScheme(
    primary = KomparaGreen,
    secondary = KomparaYellow,
    error = KomparaRed,
)

@Composable
fun KomparaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
