package mx.kompara.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import mx.kompara.data.accounting.MONEY_CONTEXT
import mx.kompara.ui.components.*
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun CarCostSheet(state: CarCostState, amount: (String) -> Unit, date: (LocalDate) -> Unit,
    save: () -> Unit, dismiss: () -> Unit, confirmRemove: (Boolean) -> Unit, remove: () -> Unit, retry: () -> Unit) {
    SheetScaffold("Lo del coche", dismiss) { CarCostContent(state, amount, date, save, { confirmRemove(true) }, retry) }
    if (state.confirmRemove) AlertDialog(onDismissRequest = { confirmRemove(false) },
        title = { Text("¿Quitar lo del coche?", style = KomparaType.title) },
        text = { Text("Se deja de restar desde hoy, ${costDate(state.today)}. Lo anterior se conserva.", style = KomparaType.body) },
        confirmButton = { TextButton(remove, enabled = !state.saving) { Text("Quitar", color = KomparaTheme.colors.ink) } },
        dismissButton = { TextButton({ confirmRemove(false) }) { Text("Volver", color = KomparaTheme.colors.ink) } })
}

@Composable
fun CarCostContent(state: CarCostState, amount: (String) -> Unit, date: (LocalDate) -> Unit,
    save: () -> Unit, remove: () -> Unit, retry: () -> Unit = {}) {
    val context = LocalContext.current
    Text("Renta, crédito o seguro", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
    Spacer(Modifier.height(16.dp))
    if (state.legacy) Text("Tus costos anteriores. Confirma el monto y desde cuándo se resta.", Modifier.padding(bottom = 12.dp), style = KomparaType.small)
    CostNumberField("Cada semana", state.amount, amount, prefix = "$", grouped = true)
    Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth().background(KomparaTheme.colors.paperSecondary, RoundedCornerShape(8.dp))
        .heightIn(min = 56.dp).clickable(enabled = !state.saving) {
            DatePickerDialog(context, { _, y, m, d -> date(LocalDate.of(y, m + 1, d)) }, state.from.year, state.from.monthValue - 1, state.from.dayOfMonth).show()
        }.padding(horizontal = 16.dp, vertical = 5.dp)) {
        Text("Se resta desde", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
        Text(costDate(state.from, state.today), style = KomparaType.title)
    }
    Spacer(Modifier.height(16.dp))
    carCostDecimal(state.amount)?.let {
        Text("Se resta de cada día: ${Formatters.formatMxnWhole(it.divide(BigDecimal(7), MONEY_CONTEXT))}, también los días que no manejas.", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
    }
    Text("Nunca del semáforo.", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
    state.error?.let { InkNotice(it); if (it.contains("cargar")) TextButton(retry) { Text("Reintentar", color = KomparaTheme.colors.ink) } }
    Spacer(Modifier.height(16.dp))
    PrimaryButton(if (state.saving) "Guardando…" else "Guardar", save, enabled = !state.saving)
    if (state.canRemove) TextButton(remove, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !state.saving) {
        Text("Quitar", style = KomparaType.bodyStrong, color = KomparaTheme.colors.inkSecondary)
    }
}
