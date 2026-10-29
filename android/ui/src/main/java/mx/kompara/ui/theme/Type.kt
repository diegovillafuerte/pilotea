package mx.kompara.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import mx.kompara.ui.R

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
)
val BarlowSemiCondensed = FontFamily(
    Font(R.font.barlow_semi_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_semi_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_semi_condensed_bold, FontWeight.Bold),
)

private fun ui(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = Barlow, fontWeight = weight, fontSize = size.sp, lineHeight = height.sp,
)
private fun money(size: Int, height: Int, weight: FontWeight = FontWeight.SemiBold) = TextStyle(
    fontFamily = BarlowSemiCondensed, fontWeight = weight, fontSize = size.sp, lineHeight = height.sp,
    fontFeatureSettings = "tnum, lnum",
)

object KomparaType {
    val rootTitle = money(30, 36, FontWeight.Bold)
    val dayDisplay = money(72, 74, FontWeight.Bold)
    val periodFigure = money(40, 44, FontWeight.Bold)
    val headline = money(24, 30, FontWeight.Bold)
    val section = money(22, 28, FontWeight.Bold)
    val number = money(32, 36)
    val title = ui(20, 26, FontWeight.SemiBold)
    val outcome = ui(18, 24, FontWeight.SemiBold)
    val meaning = ui(15, 20)
    val body = ui(16, 22)
    val bodyStrong = ui(16, 22, FontWeight.SemiBold)
    val rate = money(16, 22)
    val small = ui(13, 18)
    val smallStrong = ui(13, 18, FontWeight.SemiBold)
    val efficiency = money(56, 56)
    val carSuffix = money(20, 26, FontWeight.Medium)
    val compareLabel = money(17, 22)
}

val KomparaTypography = Typography(
    displayLarge = KomparaType.dayDisplay,
    displayMedium = KomparaType.periodFigure,
    displaySmall = KomparaType.number,
    headlineLarge = KomparaType.rootTitle,
    headlineMedium = KomparaType.headline,
    headlineSmall = KomparaType.section,
    titleLarge = KomparaType.title,
    titleMedium = KomparaType.bodyStrong,
    titleSmall = KomparaType.smallStrong,
    bodyLarge = KomparaType.body,
    bodyMedium = KomparaType.body,
    bodySmall = KomparaType.small,
    labelLarge = KomparaType.bodyStrong,
    labelMedium = KomparaType.smallStrong,
    labelSmall = KomparaType.small,
)
