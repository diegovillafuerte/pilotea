package mx.kompara.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val KomparaDarkColors = darkColorScheme(
    primary = BrandGreen,
    onPrimary = OnVerdictGreen,
    primaryContainer = BrandGreenContainerDark,
    onPrimaryContainer = BrandGreenContainerLight,
    secondary = VerdictYellow,
    onSecondary = OnVerdictYellow,
    error = VerdictRed,
    onError = OnVerdictRed,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
)

private val KomparaLightColors = lightColorScheme(
    primary = BrandGreenDark,
    onPrimary = OnVerdictGreen,
    primaryContainer = BrandGreenContainerLight,
    onPrimaryContainer = BrandGreenContainerDark,
    secondary = VerdictYellow,
    onSecondary = OnVerdictYellow,
    error = VerdictRed,
    onError = OnVerdictRed,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
)

/**
 * The Kompara theme. Owned by `:ui` so every module (and the thin `:app`) shares one design
 * language.
 *
 * Defaults to dark — drivers mostly work nights and a dark surface is easier on the eyes and the
 * battery on an always-on dashboard mount. We only drop to the light palette when the device is
 * explicitly in light mode; on a brand-new device that hasn't been switched, [isSystemInDarkTheme]
 * is false yet we still want dark, so the default OR-s in [DARK_PREFERRED].
 *
 * Dynamic (Material You) colour is intentionally NOT used: the verde/amarillo/rojo verdict is a
 * fixed brand signal that must never be recoloured by the wallpaper.
 */
@Composable
fun KomparaTheme(
    darkTheme: Boolean = isSystemInDarkTheme() || DARK_PREFERRED,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) KomparaDarkColors else KomparaLightColors,
        typography = KomparaTypography,
        content = content,
    )
}

/**
 * Product preference: when the system theme is undecided, Kompara starts dark. A future Ajustes
 * toggle (light / dark / system) can flip this per user; until then dark wins for night driving.
 */
private const val DARK_PREFERRED = true
