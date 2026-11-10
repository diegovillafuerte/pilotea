package mx.kompara.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Semantic tokens from base-v3.css, including its final Brand block. Never wallpaper-derived.
val BrandGreen = Color(0xFF187A36)
val OnBrand = Color.White
val VerdictGreen = Color(0xFF187A36)
val VerdictYellow = Color(0xFFF2B705)
val VerdictRed = Color(0xFFD32F2F)
val OnVerdictGreen = Color.White
val OnVerdictYellow = Color(0xFF1A1300)
val OnVerdictRed = Color.White

@Immutable
data class KomparaColors(
    val paper: Color,
    val paperSecondary: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val rule: Color,
    val activeNavigation: Color,
    val brand: Color = BrandGreen,
    val onBrand: Color = OnBrand,
    val verde: Color = VerdictGreen,
    val amarillo: Color = VerdictYellow,
    val rojo: Color = VerdictRed,
    val chipInk: Color = Color(0xFF0E0F10),
    val onChip: Color = Color(0xFFEDEDEA),
    /** Kompara's K on ink (v4 kit `--k-on-dark`): the chip's head and its tray's ▲. */
    val kOnDark: Color = Color(0xFF58C47A),
)

val DayColors = KomparaColors(
    paper = Color.White, paperSecondary = Color(0xFFF2F2F0),
    ink = Color(0xFF111110), inkSecondary = Color(0xFF5C5B57), inkTertiary = Color(0xFF8A8984),
    rule = Color(0xFFE8E7E3), activeNavigation = BrandGreen,
)
val NightColors = KomparaColors(
    paper = Color(0xFF121211), paperSecondary = Color(0xFF262624),
    ink = Color(0xFFF2F1EC), inkSecondary = Color(0xFFA8A7A1), inkTertiary = Color(0xFF74736E),
    rule = Color(0xFF2C2C29), activeNavigation = Color(0xFF3FB565),
)
