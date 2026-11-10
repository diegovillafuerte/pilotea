package mx.kompara.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.kompara.ui.components.*
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType
import java.time.LocalDate

/** Comparar's week picker (until WP-E replaces Comparar); Ganancias steps weeks with two arrows. */
data class WeekChoice(val date: LocalDate, val importedOnly: Boolean = false)

@Composable
fun WeekPickerContent(choices: List<WeekChoice>, selected: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit, onImport: () -> Unit) {
    choices.groupBy { it.date.plusDays(6).withDayOfMonth(1) }.forEach { (month, weeks) ->
        Spacer(Modifier.height(20.dp))
        val before = weeks.all { it.importedOnly }
        Text(Formatters.formatMonthLabel(month.toString()).removeSuffix(" ${today.year}") + if (before) " · antes de Kompara" else "", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
        weeks.forEach { choice ->
            Rule()
            Row(verticalAlignment = Alignment.CenterVertically) {
                ListRow(weekRange(choice.date, today) + if (choice.date == today.monday()) " · en curso" else "", Modifier.weight(1f),
                    detail = if (choice.importedOnly) "Importada · sin detalle por día" else if (!HistoryWeeks.isFree(choice.date, today)) "total siempre visible" else null,
                    onClick = { onSelect(choice.date) })
                if (choice.date == selected) StatusIcon(StatusGlyph.DONE)
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    ListRow("Traer un resumen", onClick = onImport)
}
