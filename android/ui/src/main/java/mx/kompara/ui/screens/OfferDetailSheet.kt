package mx.kompara.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import mx.kompara.ui.R
import mx.kompara.ui.components.*
import mx.kompara.ui.stats.OfferDetailModel
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

/** No raw screen text, passenger, coordinates, screenshot or device identifiers enter a report. */
fun offerReadingReport(model: OfferDetailModel, dayIso: String): String = buildString {
    append("Kompara · Avisar que se leyó mal\n$dayIso · ${model.title}\n${model.why}")
    model.amount?.let { append("\n${model.label}: $it sin el coche") }
    model.timeRate?.let { append("\n$it") }
}

/** Opens a draft only; without a configured recipient, the driver chooses one in the other app. */
internal fun openReadingReport(context: Context, report: String): Boolean {
    if (PrivacyLinks.supportDraft(report)?.let { PrivacyLinks.open(context, it) } == true) return true
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, report) }
    return try { context.startActivity(Intent(send).setPackage("com.whatsapp")); true }
    catch (_: ActivityNotFoundException) {
        try { context.startActivity(Intent.createChooser(send, "Avisar que se leyó mal")); true }
        catch (_: ActivityNotFoundException) { false }
    }
}

@Composable
fun OfferDetailSheet(model: OfferDetailModel, dayIso: String, onDismiss: () -> Unit,
    onCalculation: () -> Unit, onLimits: () -> Unit, onCancel: () -> Unit,
    message: (@Composable () -> Unit)? = null) {
    var confirmCancel by rememberSaveable(model.offerId) { mutableStateOf(false) }
    var consent by rememberSaveable(model.offerId) { mutableStateOf(false) }
    var reportError by rememberSaveable(model.offerId) { mutableStateOf(false) }
    val context = LocalContext.current
    SheetScaffold(model.title, onDismiss, heading = { OfferDetailHeading(model) }) {
        OfferDetailContent(model, onCalculation, onLimits, { consent = true }, { confirmCancel = true }, message)
        if (reportError) Text("No pudimos abrir una app para compartir. Inténtalo otra vez.", style = KomparaType.body)
    }
    if (confirmCancel) AlertDialog(onDismissRequest = { confirmCancel = false },
        title = { Text("¿Se canceló el viaje?") },
        text = { Text("Se quitará de tu día. La oferta queda en tu historial.") },
        confirmButton = { TextButton({ confirmCancel = false; onCancel() }) { Text("Quitar de mi día") } },
        dismissButton = { TextButton({ confirmCancel = false }) { Text("Volver") } })
    if (consent) {
        val report = offerReadingReport(model, dayIso)
        AlertDialog(onDismissRequest = { consent = false }, title = { Text("Avisar que se leyó mal") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Se compartirán la fecha, la hora y estas cifras. Elige a soporte como destinatario y revisa el mensaje antes de enviarlo.")
                Spacer(Modifier.height(16.dp))
                Text(report, style = KomparaType.small)
            } },
            confirmButton = { TextButton({ consent = false; reportError = !openReadingReport(context, report) }) { Text("Compartir") } },
            dismissButton = { TextButton({ consent = false }) { Text("Cancelar") } })
    }
}

@Composable
fun OfferDetailHeading(model: OfferDetailModel) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OfferMark(model.mark)
        Text(model.title, style = KomparaType.title)
    }
}

/** Equal padding on all three ledger rows absorbs slack; content grows/scrolls with wrapped text. */
@Composable
fun OfferDetailContent(model: OfferDetailModel, onCalculation: () -> Unit, onLimits: () -> Unit,
    onReport: () -> Unit, onCancel: () -> Unit, message: (@Composable () -> Unit)? = null) {
    Spacer(Modifier.height(12.dp))
    model.label?.let { Text(it, style = KomparaType.body, color = KomparaTheme.colors.inkSecondary) }
    model.amount?.let { amount ->
        Text(buildAnnotatedString {
            withStyle(KomparaType.number.toSpanStyle()) { append(amount) }
            withStyle(KomparaType.carSuffix.copy(color = KomparaTheme.colors.inkSecondary).toSpanStyle()) { append(" sin el coche") }
        }, Modifier.padding(top = 8.dp, bottom = 8.dp), style = KomparaType.number)
    }
    Text(moneyInProse(model.why), style = KomparaType.body)
    model.timeRate?.let { Text(it, style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
    model.suppression?.let { Text(it, style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
    if (model.typical) Text("Con costos típicos", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
    Spacer(Modifier.height(8.dp))
    // Snackbar is in a dedicated gap above the rows, never on top of a reading or action.
    message?.invoke()
    val padding = if (model.suppression == null) 18.1.dp else 15.1.dp
    listOf(model.fare, model.running).forEach { row ->
        Rule()
        Row(Modifier.fillMaxWidth().padding(vertical = padding), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(row.label, style = KomparaType.body)
                row.detail?.let { Text(it, style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
            }
            Text(row.value, style = KomparaType.rate)
        }
    }
    Rule()
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onCalculation).padding(vertical = padding)
        .testTag("offer-day-ledger"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(moneyInProse(model.dayLine), style = KomparaType.body)
            Text(model.carLine, style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
        }
        DrawnIcon(R.drawable.v3_chev)
    }
    Rule()
    OfferAction("Cambiar tu meta y tu mínimo", onLimits, chevron = true)
    OfferAction("Avisar que se leyó mal", onReport)
    if (model.cancellableTripId != null) OfferAction("¿Se canceló? Quitar de mi día", onCancel, quiet = true)
}

@Composable
private fun OfferAction(text: String, onClick: () -> Unit, chevron: Boolean = false, quiet: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = KomparaType.body,
            color = if (quiet) KomparaTheme.colors.inkSecondary else KomparaTheme.colors.ink)
        if (chevron) DrawnIcon(R.drawable.v3_chev)
    }
    Rule()
}

/** Keep financial runs on the shared money face without making the explanation heavier. */
private fun moneyInProse(text: String) = buildAnnotatedString {
    var end = 0
    Regex("[−]?\\$[0-9,.]+(?:/km|/h)?").findAll(text).forEach { match ->
        append(text.substring(end, match.range.first))
        withStyle(KomparaType.rate.toSpanStyle()) { append(mx.kompara.ui.format.Formatters.unbroken(match.value)) }
        end = match.range.last + 1
    }
    append(text.substring(end))
}
