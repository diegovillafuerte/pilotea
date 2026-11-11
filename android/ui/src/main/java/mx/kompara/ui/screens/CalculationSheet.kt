package mx.kompara.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.kompara.ui.components.SheetScaffold
import mx.kompara.ui.stats.PeriodCalculation
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

/** Cómo se calculó (v4 L8), for today, a past day or a week: two lines, a total and the one sentence about estimates. */
@Composable
fun PeriodCalculationSheet(calculation: PeriodCalculation, onDismiss: () -> Unit) {
    SheetScaffold(calculation.title, onDismiss) { PeriodCalculationContent(calculation) }
}

@Composable
fun PeriodCalculationContent(calculation: PeriodCalculation) {
    Spacer(Modifier.height(6.dp))
    calculation.rows.forEachIndexed { index, row -> LedgerLine(row, first = index == 0) }
    calculation.notes.forEach { Text(it, Modifier.padding(top = 6.dp), style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
}
