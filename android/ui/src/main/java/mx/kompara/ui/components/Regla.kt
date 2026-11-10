package mx.kompara.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.*
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.theme.*
import kotlin.math.min

/**
 * The semáforo pill (v4 plan 4.3, 4.4): Kompara's K on an ink head, the verdict colour with its
 * shape, the rate and the two-line unit; the counter-offer tray under it when [ReglaModel.hasTray].
 * [compact] draws the plain pill without the tray (the placer's smaller size). Geometry scales once
 * with fontScale; text stays in sp and grows the width, never shrinks a figure. The 2 dp paper ring
 * is inside the measured size, so placement, the mask and touch use what is drawn.
 */
@Composable
fun Regla(
    model: ReglaModel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    darkHost: Boolean = false,
) {
    val shown = if (compact) model.copy(counterOffers = null) else model
    val colors = KomparaTheme.colors
    val scale = LocalDensity.current.fontScale
    fun d(value: Number) = (value.toFloat() * scale).dp
    val rated = shown.band as? ReglaBand.Rated
    val outer = RoundedCornerShape(d(16))
    Box(
        modifier
            .wrapContentSize(unbounded = true)
            .shadow(d(8), outer, clip = false,
                ambientColor = Color.Black.copy(alpha = if (darkHost) .5f else .24f),
                spotColor = Color.Black.copy(alpha = if (darkHost) .5f else .28f))
            .clip(outer)
            .background(Color.White)
            .height(d(if (shown.hasTray) 90 else 56))
            .width(IntrinsicSize.Max),
    ) {
        Column(Modifier.fillMaxSize().padding(d(2)).clip(RoundedCornerShape(d(14))).background(colors.chipInk)) {
            Row(Modifier.fillMaxWidth().then(if (shown.hasTray) Modifier.height(d(52)) else Modifier.weight(1f)),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(d(34)).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    PillText("K", pillType(BarlowSemiCondensed, FontWeight.Bold, 22, colors.kOnDark))
                }
                if (rated == null) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = .24f)))
                    PillText((shown.band as ReglaBand.Unavailable).input.headline,
                        pillType(Barlow, FontWeight.SemiBold, 18, colors.onChip), Modifier.padding(start = d(15) - 1.dp, end = d(18)))
                } else {
                    val (body, onBody) = when (rated.position) {
                        ReglaPosition.ABOVE -> colors.verde to OnVerdictGreen
                        ReglaPosition.MEETS -> colors.amarillo to OnVerdictYellow
                        ReglaPosition.BELOW -> colors.rojo to OnVerdictRed
                    }
                    Row(Modifier.weight(1f).fillMaxHeight().background(body).padding(start = d(12), end = d(16)),
                        verticalAlignment = Alignment.CenterVertically) {
                        VerdictShape(rated.position, onBody, Modifier.size(d(17), d(15)))
                        Spacer(Modifier.width(d(9)))
                        PillText(requireNotNull(shown.rateText),
                            pillType(BarlowSemiCondensed, FontWeight.Bold, 35, onBody).copy(letterSpacing = (-.35).sp))
                        Spacer(Modifier.width(d(5)))
                        Column(Modifier.padding(top = d(1)), verticalArrangement = Arrangement.spacedBy(d(3))) {
                            PillText(shown.unit.suffix, pillType(Barlow, FontWeight.SemiBold, 15, onBody))
                            PillText(shown.basisWord, pillType(Barlow, FontWeight.SemiBold, 12, onBody.copy(alpha = .9f)))
                        }
                    }
                }
            }
            if (shown.hasTray) Row(Modifier.fillMaxWidth().weight(1f).padding(start = d(12), end = d(16), bottom = d(1)),
                verticalAlignment = Alignment.CenterVertically) {
                // Barlow 500 is not bundled; 400 is the nearest weight that ships.
                PillText("Desde", pillType(Barlow, FontWeight.Normal, 13, colors.onChip.copy(alpha = .72f)))
                Spacer(Modifier.width(d(8)))
                val amount = pillType(BarlowSemiCondensed, FontWeight.Bold, 19, Color.White)
                shown.trayMinimum?.let {
                    VerdictShape(ReglaPosition.MEETS, colors.amarillo, Modifier.size(d(11), d(10)))
                    Spacer(Modifier.width(d(7)))
                    PillText(Formatters.formatMxnWhole(it.toBigDecimal()), amount)
                }
                shown.trayGoal?.let {
                    if (shown.trayMinimum != null) Spacer(Modifier.width(d(16)))
                    VerdictShape(ReglaPosition.ABOVE, colors.kOnDark, Modifier.size(d(11), d(10)))
                    Spacer(Modifier.width(d(7)))
                    PillText(Formatters.formatMxnWhole(it.toBigDecimal()), amount)
                }
            }
        }
        // One accessible image spans the whole chip; the drawn words stay out of the reading order.
        Box(Modifier.matchParentSize().clearAndSetSemantics { contentDescription = shown.spokenBand; role = Role.Image })
    }
}

@Composable
private fun PillText(text: String, style: TextStyle, modifier: Modifier = Modifier) =
    BasicText(text, modifier.semantics { hideFromAccessibility() }, style, softWrap = false, maxLines = 1)

private fun pillType(family: FontFamily, weight: FontWeight, size: Int, color: Color) = TextStyle(
    fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = size.sp,
    fontFeatureSettings = "tnum, lnum", color = color,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** The kit's ▲ ◆ ▼ symbols (viewBox 16 × 14, 16 × 16, 16 × 14), fitted and centred in [modifier]'s box. */
@Composable
private fun VerdictShape(position: ReglaPosition, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val box = if (position == ReglaPosition.MEETS) 16f else 14f
        val unit = min(size.width / 16f, size.height / box)
        val origin = Offset((size.width - 16f * unit) / 2, (size.height - box * unit) / 2)
        fun at(x: Float, y: Float) = Offset(origin.x + x * unit, origin.y + y * unit)
        val points = when (position) {
            ReglaPosition.ABOVE -> listOf(at(8f, 0f), at(16f, 14f), at(0f, 14f))
            ReglaPosition.MEETS -> listOf(at(8f, 0f), at(16f, 8f), at(8f, 16f), at(0f, 8f))
            ReglaPosition.BELOW -> listOf(at(0f, 0f), at(16f, 0f), at(8f, 14f))
        }
        drawPath(Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }; close()
        }, color)
    }
}

/** Only presence animates. Swapping [content]'s model never crossfades colours or old amounts. */
@Composable
fun ReglaPresence(visible: Boolean, reducedMotion: Boolean, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    val rise = with(LocalDensity.current) { ReglaMotion.riseDp(reducedMotion).dp.roundToPx() }
    val duration = ReglaMotion.enterMs(reducedMotion)
    val enter = fadeIn(tween(duration, easing = LinearOutSlowInEasing)) +
        slideInVertically(tween(duration, easing = LinearOutSlowInEasing)) { rise }
    AnimatedVisibility(state, enter = enter, exit = fadeOut(tween(ReglaMotion.EXIT_MS))) { content() }
}
