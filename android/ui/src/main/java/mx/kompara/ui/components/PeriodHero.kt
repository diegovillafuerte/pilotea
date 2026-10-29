package mx.kompara.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mx.kompara.ui.R
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

/** Absence is supplied by the owner; never turn missing or loading money into $0. */
@Composable
fun PeriodHero(label: String, value: String?, quietLine: String, onCalculation: () -> Unit,
    modifier: Modifier = Modifier, day: Boolean = true, plot: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
        if (value != null) {
            if (day) Text(value, style = KomparaType.dayDisplay, softWrap = false)
            else FigureRow(value, plot = plot ?: {})
        }
        HeroQuietLine(quietLine, onCalculation)
    }
}

@Composable
fun HeroQuietLine(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
        DrawnIcon(R.drawable.v3_chev)
    }
}

/** Flow wraps the chart below a long figure at narrow widths/large fonts; text is never shrunk. */
@Composable
fun FigureRow(value: String, modifier: Modifier = Modifier, plot: @Composable () -> Unit) {
    FlowRow(modifier.fillMaxWidth().heightIn(min = 88.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(value, Modifier.align(Alignment.Bottom), style = KomparaType.periodFigure, softWrap = false)
        Box(Modifier.width(168.dp).align(Alignment.Bottom)) { plot() }
    }
}
