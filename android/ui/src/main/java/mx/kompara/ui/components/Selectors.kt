package mx.kompara.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import mx.kompara.ui.R
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

@Composable
fun TextTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, unavailable: Set<Int> = emptySet()) {
    FlowRow(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        labels.forEachIndexed { i, label ->
            Column(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).selectable(i == selected, role = Role.Tab, onClick = { onSelect(i) })
                .padding(vertical = 10.dp)) {
                Box {
                    // Reserve the semibold width even when not selected, so tabs do not jump.
                    Text(label, Modifier.alpha(0f).clearAndSetSemantics {}, style = KomparaType.bodyStrong)
                    Text(label, style = if (i == selected) KomparaType.bodyStrong else KomparaType.body,
                        color = if (i in unavailable) KomparaTheme.colors.inkTertiary else if (i == selected) KomparaTheme.colors.ink else KomparaTheme.colors.inkSecondary)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(24.dp, 2.dp).background(if (i == selected) KomparaTheme.colors.activeNavigation else KomparaTheme.colors.paper))
            }
        }
    }
}

enum class PeriodUnit { WEEK, MONTH }

@Composable
fun PeriodSelector(label: String, unit: PeriodUnit, onPick: () -> Unit, onPrevious: () -> Unit,
    onNext: () -> Unit, canPrevious: Boolean, canNext: Boolean, modifier: Modifier = Modifier,
    stepArrows: Boolean = false, spreadSteps: Boolean = false, showPicker: Boolean = true) {
    val previous = stringResource(if (unit == PeriodUnit.WEEK) R.string.v3_previous_week else R.string.v3_previous_month)
    val next = stringResource(if (unit == PeriodUnit.WEEK) R.string.v3_next_week else R.string.v3_next_month)
    Column(modifier.semantics { isTraversalGroup = true }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (showPicker) Modifier.clickable(onClick = onPick) else Modifier).semantics { traversalIndex = 0f },
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = KomparaType.bodyStrong)
            if (showPicker) DrawnIcon(R.drawable.v3_chev_d)
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = if (stepArrows && !spreadSteps) Arrangement.spacedBy(12.dp) else Arrangement.SpaceBetween) {
            if (stepArrows) {
                PeriodStep(previous, canPrevious, onPrevious, true, Modifier.semantics { traversalIndex = 1f })
                PeriodStep(next, canNext, onNext, false, Modifier.semantics { traversalIndex = 2f })
            } else {
            KomparaButton(previous, onPrevious, Modifier.semantics { traversalIndex = 1f },
                variant = ButtonVariant.TEXT, enabled = canPrevious)
            KomparaButton(next, onNext, Modifier.semantics { traversalIndex = 2f },
                variant = ButtonVariant.TEXT, enabled = canNext)
            }
        }
    }
}

@Composable
private fun PeriodStep(label: String, enabled: Boolean, onClick: () -> Unit, previous: Boolean, modifier: Modifier) {
    val color = if (enabled) KomparaTheme.colors.ink else KomparaTheme.colors.inkTertiary
    Row(modifier.heightIn(min = 48.dp).clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        if (previous) DrawnIcon(R.drawable.v3_chev_l, tint = color)
        Text(label, style = KomparaType.body, color = color)
        if (!previous) DrawnIcon(R.drawable.v3_chev, tint = color)
    }
}
