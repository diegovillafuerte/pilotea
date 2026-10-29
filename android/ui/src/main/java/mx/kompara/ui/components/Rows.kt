package mx.kompara.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import mx.kompara.ui.R
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

@Composable
fun DrawnIcon(@DrawableRes resource: Int, modifier: Modifier = Modifier,
    description: String? = null, tint: Color = KomparaTheme.colors.inkSecondary) {
    Icon(painterResource(resource), description, modifier.size(24.dp), tint = tint)
}

@Composable
fun Rule(modifier: Modifier = Modifier, total: Boolean = false) {
    Box(modifier.fillMaxWidth().height(if (total) 2.dp else 1.dp)
        .background(if (total) KomparaTheme.colors.ink else KomparaTheme.colors.rule))
}

@Composable
fun ListRow(label: String, modifier: Modifier = Modifier, detail: String? = null,
    value: String? = null, onClick: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.semantics(mergeDescendants = true) {})
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, style = KomparaType.bodyStrong)
                if (detail != null) Text(detail, style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
            }
            if (value != null) Text(value, style = KomparaType.rate, softWrap = false)
        }
        if (onClick != null) DrawnIcon(R.drawable.v3_chev)
    }
}

@Composable
fun Observation(text: String, modifier: Modifier = Modifier, detail: String? = null,
    @DrawableRes icon: Int = R.drawable.v3_info, onClick: (() -> Unit)? = null) {
    Column(modifier) {
        Rule()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DrawnIcon(icon)
            ListRow(text, Modifier.weight(1f), detail = detail, onClick = onClick)
        }
        Rule()
    }
}

@Composable
fun InkNotice(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 12.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusIcon(StatusGlyph.ATTENTION)
        Text(text, style = KomparaType.body)
    }
}

@Composable
fun StaticSkeleton(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.v3_loading)
    Box(modifier.clip(RoundedCornerShape(2.dp)).background(KomparaTheme.colors.paperSecondary)
        .semantics { contentDescription = label })
}

@Composable
fun MaskedValue(description: String, modifier: Modifier = Modifier) {
    Text("•••", modifier.clip(RoundedCornerShape(4.dp)).background(KomparaTheme.colors.paperSecondary)
        .padding(horizontal = 12.dp, vertical = 4.dp).clearAndSetSemantics { contentDescription = description },
        style = KomparaType.number)
}

@Composable
fun StateLegend(selected: OfferMarkState?, modifier: Modifier = Modifier,
    onSelect: ((OfferMarkState) -> Unit)? = null) {
    val labels = listOf(R.string.v3_above, R.string.v3_meets, R.string.v3_below, R.string.v3_unread)
    Column(modifier) {
        OfferMarkState.entries.forEachIndexed { index, state ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .then(if (onSelect != null) Modifier.clickable { onSelect(state) } else Modifier)
                .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OfferMark(state)
                // The categorical cue is supplementary; the phrase is the accessible meaning.
                if (state != OfferMarkState.UNKNOWN) VerdictCue(state)
                Text(stringResource(labels[index]), style = if (selected == state) KomparaType.bodyStrong else KomparaType.body,
                    color = if (selected == state) KomparaTheme.colors.ink else KomparaTheme.colors.inkSecondary)
            }
        }
    }
}
