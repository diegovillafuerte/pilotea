package mx.kompara.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mx.kompara.ui.R
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

/** Native modal over the caller's scaffold; keeps its underlying navigation and Android insets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetScaffold(title: String, onDismiss: () -> Unit, modifier: Modifier = Modifier,
    heading: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = KomparaTheme.colors.paper, contentColor = KomparaTheme.colors.ink,
        tonalElevation = 0.dp, scrimColor = Color.Black.copy(alpha = .35f),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 12.dp).size(32.dp, 4.dp)
            .background(KomparaTheme.colors.rule, RoundedCornerShape(2.dp))) },
    ) {
        SheetContent(title, heading = heading, content = content)
    }
}

/** Also used by deterministic debug renders without substituting OS chrome. */
@Composable
fun SheetContent(title: String, modifier: Modifier = Modifier, heading: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
        .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
            if (heading == null) Text(title, style = KomparaType.title) else heading()
        }
        content()
    }
}

data class CalculationRow(val label: String, val value: String, val detail: String? = null, val total: Boolean = false)

@Composable
fun CalculationLedger(rows: List<CalculationRow>, modifier: Modifier = Modifier) {
    Column(modifier) {
        rows.forEachIndexed { index, row ->
            if (index > 0 || row.total) Rule(total = row.total)
            FlowRow(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(row.label, style = if (row.total) KomparaType.bodyStrong else KomparaType.body)
                    row.detail?.let { Text(it, style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
                }
                Text(row.value, Modifier.padding(start = 12.dp), style = KomparaType.rate, softWrap = false)
            }
        }
    }
}

@Composable
fun CalculationSheet(rows: List<CalculationRow>, onDismiss: () -> Unit, modifier: Modifier = Modifier,
    title: String = stringResource(R.string.v3_calculation)) {
    SheetScaffold(title, onDismiss, modifier) { CalculationLedger(rows) }
}
