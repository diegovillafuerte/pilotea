package mx.kompara.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import mx.kompara.ui.R

/**
 * Inter — the Kompara brand typeface, shared with the marketing website (which loads Inter via
 * next/font). Bundled as a single variable font (`res/font/inter.ttf`, OFL-1.1 — see
 * `android/licenses/Inter-OFL.txt`); each weight is instantiated from the font's weight axis via
 * [FontVariation], so the whole family ships in one file.
 */
@OptIn(ExperimentalTextApi::class)
private fun interWeight(weight: FontWeight) =
    Font(
        resId = R.font.inter,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

val Inter = FontFamily(
    interWeight(FontWeight.Normal),
    interWeight(FontWeight.Medium),
    interWeight(FontWeight.SemiBold),
    interWeight(FontWeight.Bold),
    interWeight(FontWeight.Black),
)

/**
 * Kompara typography. The numbers are the product, so the scale is tuned around glanceability:
 * the standard Material display sizes are kept for headings, but the key money/metric figures use
 * the bold, tabular [KomparaType.metricValue] / [KomparaType.metricValueLarge] styles so a chofer
 * reads "$1,234.56" from arm's length without focusing. All app text is set in [Inter].
 */
val KomparaTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        // Headings are tracked tight (design tokens: --tracking-tight -0.02em).
        letterSpacing = (-0.02).em,
    ),
    titleLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    // titleMedium / titleSmall / labelLarge / bodySmall were previously undefined → Compose fell back
    // to the Material default (Roboto). Defined here in Inter so the Comparar cards (S-024) and
    // RecommendationCard render in the brand typeface.
    titleMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

/**
 * Extra glanceable styles outside the Material scale, for the big numerals that dominate the
 * metric surfaces. Heavy weight + large size so the figure is legible on a dashboard mount.
 */
object KomparaType {
    /** The hero number on a screen (e.g. net earnings today). */
    val metricValueLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Black,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        textAlign = TextAlign.Start,
        // Tabular figures so "$1,234.56" columns stay aligned (design: money = tabular figures).
        fontFeatureSettings = "tnum",
    )

    /** A prominent figure inside a [mx.kompara.ui.components.MetricCard]. */
    val metricValue = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        // Tabular figures so "$1,234.56" columns stay aligned (design: money = tabular figures).
        fontFeatureSettings = "tnum",
    )

    /** The small upper-case label that sits above a metric value. */
    val metricLabel = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        // The uppercase metric label carries slight tracking (design: metricLabel letterSpacing 0.04em).
        letterSpacing = 0.04.em,
    )
}
