package mx.kompara.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.kompara.metrics.VerdictLevel
import mx.kompara.ui.R
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.OnVerdictGreen
import mx.kompara.ui.theme.OnVerdictRed
import mx.kompara.ui.theme.OnVerdictYellow
import mx.kompara.ui.theme.VerdictGreen
import mx.kompara.ui.theme.VerdictRed
import mx.kompara.ui.theme.VerdictYellow

/**
 * The traffic-light verdict the design system speaks in. The level itself lives in `:metrics`
 * ([VerdictLevel] = GREEN / YELLOW / RED); `:ui` owns how it *looks* and *reads* in Spanish —
 * "Verde" / "Amarillo" / "Rojo" — so the chip is the single source of truth for verdict
 * styling across in-app surfaces. The colour carries the good/bad signal; the word just names it.
 */

/** Brand traffic-light fill for a verdict level. */
val VerdictLevel.brandColor: Color
    get() = when (this) {
        VerdictLevel.GREEN -> VerdictGreen
        VerdictLevel.YELLOW -> VerdictYellow
        VerdictLevel.RED -> VerdictRed
    }

/** Text/icon colour that sits on top of [brandColor]. */
val VerdictLevel.onBrandColor: Color
    get() = when (this) {
        VerdictLevel.GREEN -> OnVerdictGreen
        VerdictLevel.YELLOW -> OnVerdictYellow
        VerdictLevel.RED -> OnVerdictRed
    }

/** Spanish label shown to the chofer for a verdict level. */
@get:StringRes
val VerdictLevel.labelRes: Int
    get() = when (this) {
        VerdictLevel.GREEN -> R.string.verdict_green
        VerdictLevel.YELLOW -> R.string.verdict_yellow
        VerdictLevel.RED -> R.string.verdict_red
    }

/**
 * A pill-shaped verdict chip. Glanceable colour + Spanish word so the meaning lands even when the
 * driver only catches the colour out of the corner of their eye.
 */
@Composable
fun VerdictBadge(
    level: VerdictLevel,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(level.labelRes),
        color = level.onBrandColor,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(level.brandColor)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Preview(showBackground = true, name = "Verdict — Verde")
@Composable
private fun VerdictBadgeGreenPreview() {
    KomparaTheme { VerdictBadge(level = VerdictLevel.GREEN) }
}

@Preview(showBackground = true, name = "Verdict — Regular")
@Composable
private fun VerdictBadgeYellowPreview() {
    KomparaTheme { VerdictBadge(level = VerdictLevel.YELLOW) }
}

@Preview(showBackground = true, name = "Verdict — Rojo")
@Composable
private fun VerdictBadgeRedPreview() {
    KomparaTheme { VerdictBadge(level = VerdictLevel.RED) }
}
