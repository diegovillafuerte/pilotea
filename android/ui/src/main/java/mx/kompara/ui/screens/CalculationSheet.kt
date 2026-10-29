package mx.kompara.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.kompara.ui.components.*
import mx.kompara.ui.stats.DayCalculation
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

@Composable
fun DayCalculationSheet(calculation: DayCalculation, onDismiss: () -> Unit) {
    SheetScaffold(calculation.title, onDismiss) { DayCalculationContent(calculation) }
}

@Composable
fun DayCalculationContent(calculation: DayCalculation) {
    Rule()
    CalculationLedger(calculation.rows)
    Spacer(Modifier.height(24.dp))
    calculation.notes.forEach { Text(it, style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
}
