package mx.kompara.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mx.kompara.ui.components.*
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType
import java.time.LocalDate

@Composable
fun DayBreakdownContent(week: WeekPresentation, onDay: (LocalDate) -> Unit) {
    Rule()
    week.days.forEachIndexed { index, day ->
        if (day != null) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("week-day-$index")
                .clickable { onDay(week.start.plusDays(index.toLong())) }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(week.dayLabel(index), style = KomparaType.body)
                    week.dayNote(index)?.let { Text(it, style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
                }
                Text(day.periodResult?.let(Formatters::formatMxnWhole) ?: "—", style = KomparaType.rate, softWrap = false)
            }
            if (index < week.days.indexOfLast { it != null }) Rule()
        }
    }
    CalculationLedger(listOf(CalculationRow(if (week.open) "Llevas" else "Te quedaron", week.amount ?: "—", week.roundingNote, total = true)))
}

@Composable
fun PerAppContent(week: WeekPresentation) {
    week.finding?.let { Text(it, style = KomparaType.body) }
    Spacer(Modifier.height(32.dp))
    Text("te dejó por km, sin el coche", Modifier.fillMaxWidth().wrapContentWidth(Alignment.End), style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
    Spacer(Modifier.height(8.dp))
    Rule()
    CalculationLedger(week.apps.map { (app, values) ->
        CalculationRow("${appName(app)} · ${values.trips} viajes", values.ratePerKm?.let { Formatters.unbroken(Formatters.formatPerKm(it)) } ?: "—",
            if (values.ratePerKm == null) "Faltan datos para calcular por km" else null)
    })
    Rule()
    if (week.apps.size > 1) {
        Text("Manejaste cada app a distintas horas", Modifier.padding(top = 16.dp), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
        Spacer(Modifier.height(32.dp))
        Text("Lo del coche cuesta igual en las dos apps.", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
    } else Text("Lo del coche se resta de la semana, no de una app.", Modifier.padding(top = 24.dp), style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
}

@Composable
fun StatementDifferenceContent(week: WeekPresentation, difference: StatementDifference, onImport: () -> Unit) {
    Text("Solo ${appName(difference.app)}, antes de gastos", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
    Spacer(Modifier.height(16.dp))
    Rule()
    CalculationLedger(listOf(
        CalculationRow("Lo que leyó Kompara", Formatters.formatMxnWhole(difference.captured)),
        CalculationRow("Lo que dice tu resumen", Formatters.formatMxnWhole(difference.statement)),
        CalculationRow("Diferencia", difference.display, total = true)))
    Spacer(Modifier.height(24.dp))
    val selected = week.snapshot.statements.any { it.app == difference.app && it.id in week.snapshot.selectedStatements }
    Text(if (selected) "Esta semana usa el pago de tu resumen de ${appName(difference.app)}. Los registros originales se conservan." else "El total de la semana (${week.amount ?: "—"}) usa lo que leyó Kompara. El pago final es el de ${appName(difference.app)}.",
        style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
    Spacer(Modifier.height(24.dp))
    ListRow("Traer otro resumen", onClick = onImport)
}

data class WeekChoice(val date: LocalDate, val importedOnly: Boolean = false)
fun weekChoices(week: WeekPresentation): List<WeekChoice> {
    val history = week.snapshot.importedWeeks.associateWith { true } + HistoryWeeks.build(week.snapshot.history).associate { LocalDate.parse(it.weekStart) to (it.source == WeekSourceBadge.IMPORTADO) }
    return (history.keys + week.start + week.today.monday()).filter { it <= week.today.monday() }.distinct().sortedDescending()
        .map { WeekChoice(it, history[it] == true) }
}

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

@Composable
fun WeekSheetContent(sheet: String, week: WeekPresentation, onDay: (LocalDate) -> Unit, onSelect: (LocalDate) -> Unit, onImport: () -> Unit) {
    when {
        sheet == "days" -> DayBreakdownContent(week, onDay)
        sheet == "apps" -> PerAppContent(week)
        sheet == "calculation" -> DayCalculationContent(weekCalculation(week))
        sheet == "picker" -> WeekPickerContent(weekChoices(week), week.start, week.today, onSelect, onImport)
        sheet.startsWith("statement:") -> week.differences.firstOrNull { it.app == sheet.substringAfter(':') }?.let { StatementDifferenceContent(week, it, onImport) }
    }
}
fun weekSheetTitle(sheet: String, week: WeekPresentation): String = when {
    sheet == "days" -> "Por día · ${weekRange(week.start, week.today)}"
    sheet == "apps" -> "Por app · ${weekRange(week.start, week.today)}"
    sheet == "calculation" -> "Cómo se calculó · semana"
    sheet == "picker" -> "Elegir semana"
    else -> "Tu resumen de ${appName(sheet.substringAfter(':'))} · ${weekRange(week.start, week.today)}"
}
