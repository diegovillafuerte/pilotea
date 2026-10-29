package mx.kompara.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.*
import mx.kompara.ui.theme.*
import kotlin.math.max
import kotlin.math.roundToInt

/** Fixed reading order; all content swaps in one composition, without animating verdict changes.
 * Geometry scales once with fontScale; text remains in sp. Long labels grow the measured plate,
 * never shrink a figure. Placement must use the resulting size (including at large font settings).
 */
@Composable
fun Regla(
    model: ReglaModel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    darkHost: Boolean = false,
) {
    val colors = KomparaTheme.colors
    val scale = LocalDensity.current.fontScale
    val shape = RoundedCornerShape((14 * scale).dp)
    val rated = model.band as? ReglaBand.Rated
    val missing = (model.band as? ReglaBand.Unavailable)?.input
    val bandColor = when (rated?.position) {
        ReglaPosition.ABOVE -> colors.verde
        ReglaPosition.MEETS -> colors.amarillo
        ReglaPosition.BELOW -> colors.rojo
        null -> colors.chipGrey
    }
    val onBand = when (rated?.position) {
        ReglaPosition.MEETS -> OnVerdictYellow
        null -> colors.onChip
        else -> Color.White
    }
    val markerColor = when (rated?.position) {
        ReglaPosition.ABOVE -> colors.trayGreen
        ReglaPosition.MEETS -> colors.trayYellow
        ReglaPosition.BELOW -> colors.trayRed
        null -> Color.Transparent
    }
    val bandHeight = if (compact) 38 else 56
    val moneySize = if (compact) 26 else 30
    val headlineSize = if (compact) 21 else 24
    val limitSize = if (compact) 14 else 15
    val lineHeight = if (compact) 16 else 18
    val main = buildAnnotatedString {
        if (rated != null) {
            append(requireNotNull(model.rateText))
            withStyle(SpanStyle(fontFamily = Barlow, fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)) {
                append("\u00a0${model.unit.suffix}\u00a0neto")
            }
        } else {
            append(requireNotNull(missing).headline)
            if (compact) withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.onChip.copy(alpha = .72f))) {
                append(" · ${missing.reason}")
            }
        }
    }
    fun limitText(label: String) = buildAnnotatedString {
        val amount = label.indexOf('$')
        withStyle(SpanStyle(color = colors.onChip.copy(alpha = if (rated == null) .50f else .72f))) { append(label.take(amount)) }
        withStyle(SpanStyle(color = colors.onChip.copy(alpha = if (rated == null) .72f else 1f))) { append(label.drop(amount)) }
    }
    val edge = if (rated == null) colors.chipGreyEdge else colors.chipEdge
    Layout(
        modifier = modifier
            .wrapContentSize(unbounded = true)
            .shadow((10 * scale).dp, shape, clip = false,
                ambientColor = Color.Black.copy(alpha = if (darkHost) .5f else .24f),
                spotColor = Color.Black.copy(alpha = if (darkHost) .5f else .2f))
            .clip(shape)
            .then(if (darkHost) Modifier.border(1.dp, edge, shape) else Modifier)
            .drawBehind {
                val s = density * scale
                drawRect(colors.chipInk)
                drawRect(bandColor, size = Size(size.width, bandHeight * s))
                if (rated != null) {
                    val cueSize = (if (compact) 24 else 30) * s
                    val x = (if (compact) 12 else 13) * s
                    val y = (if (compact) 7 else 13) * s
                    val path = Path().apply {
                        fun point(px: Float, py: Float) = Offset(x + px / 30 * cueSize, y + py / 30 * cueSize)
                        val points = when (rated.position) {
                            ReglaPosition.ABOVE -> listOf(point(15f, 2.5f), point(29f, 27f), point(1f, 27f))
                            ReglaPosition.MEETS -> listOf(point(15f, 1f), point(29f, 15f), point(15f, 29f), point(1f, 15f))
                            ReglaPosition.BELOW -> listOf(point(1f, 3f), point(29f, 3f), point(15f, 27.5f))
                        }
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }; close()
                    }
                    drawPath(path, onBand)
                    val markerY = when (rated.position) {
                        ReglaPosition.ABOVE -> if (compact) 43f else 64f
                        ReglaPosition.MEETS -> if (compact) 57f else 81.5f
                        ReglaPosition.BELOW -> if (compact) 70.5f else 99f
                    }
                    drawRoundRect(markerColor, Offset(size.width - 40 * s, markerY * s),
                        Size(22 * s, (if (compact) 4 else 5) * s), CornerRadius(2 * s))
                }
            }
            .semantics { isTraversalGroup = true },
        content = {
            BasicText(main, style = reglaType(if (rated != null) moneySize else headlineSize,
                if (compact) (if (rated != null) 26 else 22) else 30, onBand), softWrap = false,
                modifier = Modifier.clearAndSetSemantics {})
            BasicText(if (compact) "" else rated?.position?.phrase ?: requireNotNull(missing).reason,
                style = reglaType(17, 19, if (rated == null) colors.onChip.copy(alpha = .72f) else onBand,
                    if (rated == null) FontWeight.Medium else FontWeight.SemiBold),
                softWrap = false, modifier = Modifier.clearAndSetSemantics {})
            BasicText(limitText(model.goalLabel), style = reglaType(limitSize, lineHeight, colors.onChip),
                softWrap = false, modifier = Modifier.semantics { traversalIndex = 1f })
            BasicText(limitText(model.minimumLabel), style = reglaType(limitSize, lineHeight, colors.onChip),
                softWrap = false, modifier = Modifier.semantics { traversalIndex = 2f })
            repeat(2) {
                Canvas(Modifier) {
                    val s = density * scale
                    drawLine(colors.onChip.copy(alpha = if (rated == null) .45f else .75f),
                        Offset(0f, s), Offset(size.width, s), strokeWidth = 2 * s,
                        pathEffect = if (rated == null) PathEffect.dashPathEffect(floatArrayOf(5 * s, 4 * s)) else null)
                }
            }
            // One accessible image spans the entire band; the cue and both visual text runs are
            // decorative children. Limits remain separate real text, after this image in traversal.
            Box(Modifier.clearAndSetSemantics {
                contentDescription = model.spokenBand; role = Role.Image; traversalIndex = 0f
            })
        },
    ) { children, _ ->
        val s = density * scale
        fun px(value: Number) = (value.toFloat() * s).roundToInt()
        val text = children.take(4).map { it.measure(Constraints()) }
        val textX = if (rated == null) 14 else if (compact) 46 else 54
        val ruleBase = if (model.unit == ReglaUnit.HOUR) 131 else if (model.limitsHaveCents) 123 else 104
        val ruleX = max(px(ruleBase), max(text[2].width, text[3].width) + px(22))
        val width = maxOf(px(240), px(textX + 14) + max(text[0].width, text[1].width), ruleX + px(30 + 42))
        val height = px(if (compact) 80 else 112)
        val rules = children.subList(4, 6).map { it.measure(Constraints.fixed(width - ruleX - px(42), px(2))) }
        val bandSemantics = children[6].measure(Constraints.fixed(width, px(bandHeight)))
        layout(width, height) {
            bandSemantics.place(0, 0)
            text[0].place(px(textX), px(if (compact) (if (rated == null) 8 else 6) else 4))
            text[1].place(px(textX), px(34))
            text[2].place(px(14), px(if (compact) 43 else 65))
            text[3].place(px(14), px(if (compact) 59 else 85))
            rules[0].place(ruleX, px(if (compact) 50 else 73))
            rules[1].place(ruleX, px(if (compact) 66 else 93))
        }
    }
}

private fun reglaType(size: Int, height: Int, color: Color, weight: FontWeight = FontWeight.SemiBold) = TextStyle(
    fontFamily = BarlowSemiCondensed, fontWeight = weight, fontSize = size.sp, lineHeight = height.sp,
    fontFeatureSettings = "tnum, lnum", color = color,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

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
