package mx.kompara.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalKomparaColors = staticCompositionLocalOf { DayColors }
object KomparaTheme {
    val colors: KomparaColors @Composable get() = LocalKomparaColors.current
}

/**
 * Paper/ink also owns errors and text actions. Only brand actions and verdicts carry colour. The theme follows the
 * system; a LIGHT/DARK choice stored by 0.2.x is ignored (docs/v4-simple-launch-plan.md §3).
 */
@Composable
fun KomparaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val dark = darkTheme
    val c = if (dark) NightColors else DayColors
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = c.brand, onPrimary = c.onBrand,
        primaryContainer = c.paperSecondary, onPrimaryContainer = c.ink,
        secondary = c.ink, onSecondary = c.paper,
        secondaryContainer = c.paperSecondary, onSecondaryContainer = c.ink,
        tertiary = c.ink, onTertiary = c.paper,
        tertiaryContainer = c.paperSecondary, onTertiaryContainer = c.ink,
        error = c.ink, onError = c.paper, errorContainer = c.paperSecondary, onErrorContainer = c.ink,
        background = c.paper, onBackground = c.ink, surface = c.paper, onSurface = c.ink,
        surfaceVariant = c.paperSecondary, onSurfaceVariant = c.inkSecondary,
        surfaceTint = c.paper, surfaceDim = c.paperSecondary, surfaceBright = c.paper,
        surfaceContainerLowest = c.paper, surfaceContainerLow = c.paper,
        surfaceContainer = c.paperSecondary, surfaceContainerHigh = c.paperSecondary,
        surfaceContainerHighest = c.paperSecondary,
        outline = c.inkTertiary, outlineVariant = c.rule,
        inverseSurface = c.ink, inverseOnSurface = c.paper, inversePrimary = c.onBrand,
    )
    CompositionLocalProvider(LocalKomparaColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = KomparaTypography, content = content)
    }
}
