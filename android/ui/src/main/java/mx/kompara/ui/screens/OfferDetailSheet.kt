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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import mx.kompara.ui.components.*
import mx.kompara.ui.stats.OfferDetailModel
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

/** No raw screen text, passenger, coordinates, screenshot or device identifiers enter a report. */
fun offerReadingReport(model: OfferDetailModel, dayIso: String): String = buildString {
    append("Kompara · Avisar que se leyó mal\n$dayIso · ${model.title}\n${model.why}")
    model.amount?.let { append("\n${model.label} $it") }
    model.other?.let { append("\n${it.label}: ${it.value}") }
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

/** One offer (v4 L7). Cancelling asks first; a report shows its exact text before sharing (0.2.20's behaviour). */
@Composable
fun OfferDetailSheet(model: OfferDetailModel, dayIso: String, onDismiss: () -> Unit, onCancel: () -> Unit,
    message: (@Composable () -> Unit)? = null, reportAvailable: Boolean = PrivacyLinks.support != null) {
    var confirmCancel by rememberSaveable(model.offerId) { mutableStateOf(false) }
    var consent by rememberSaveable(model.offerId) { mutableStateOf(false) }
    var reportError by rememberSaveable(model.offerId) { mutableStateOf(false) }
    val context = LocalContext.current
    SheetScaffold(model.title, onDismiss, heading = { OfferDetailHeading(model) }) {
        OfferDetailContent(model, { consent = true }, { confirmCancel = true }, message, reportAvailable)
        if (reportError) Text("No pudimos abrir una app para compartir. Inténtalo otra vez.", style = KomparaType.body)
    }
    if (confirmCancel) AlertDialog(onDismissRequest = { confirmCancel = false },
        title = { Text("¿Se canceló el viaje?") },
        text = { Text("Se quitará de tu día. La oferta queda en tu historial.") },
        confirmButton = { TextButton({ confirmCancel = false; onCancel() }) { Text("Quitar de mi día") } },
        dismissButton = { TextButton({ confirmCancel = false }) { Text("Volver") } })
    if (consent && reportAvailable) {
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

/** "▲ 14:32 · Uber · en curso" (`.sheet h5`). */
@Composable
fun OfferDetailHeading(model: OfferDetailModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (model.mark != OfferMarkState.UNKNOWN) VerdictShape(model.mark, width = 15.dp, height = 14.dp)
        Text(model.title, style = KomparaType.title)
    }
}

@Composable
fun OfferDetailContent(model: OfferDetailModel, onReport: () -> Unit, onCancel: () -> Unit,
    message: (@Composable () -> Unit)? = null, reportAvailable: Boolean = true) {
    val c = KomparaTheme.colors
    if (model.amount != null) Text(buildAnnotatedString {
        withStyle(GananciasType.heroLabel.toSpanStyle().copy(color = c.inkSecondary)) { append("${model.label}  ") }
        append(model.amount)
    }, Modifier.padding(top = 6.dp), style = GananciasType.sheetFigure, softWrap = false)
    Text(buildAnnotatedString {
        val rate = model.rate
        if (rate != null) {
            withStyle(KomparaType.bodyStrong.toSpanStyle().copy(color = c.ink, fontSize = GananciasType.status.fontSize)) { append(rate) }
            append(model.why.removePrefix(rate))
        } else append(model.why)
    }, Modifier.padding(top = 6.dp, bottom = 10.dp), style = GananciasType.status, color = c.inkSecondary)
    model.suppression?.let { Text(it, Modifier.padding(bottom = 10.dp), style = KomparaType.small, color = c.inkSecondary) }
    // Snackbar is in a dedicated gap above the rows, never on top of a reading or action.
    message?.invoke()
    listOfNotNull(model.fare, model.running, model.other).forEach { LedgerLine(it) }
    val links = listOfNotNull(("Quitar de mi día" to onCancel).takeIf { model.cancellableTripId != null },
        ("Avisar que se leyó mal" to onReport).takeIf { reportAvailable })
    if (links.isNotEmpty()) {
        Rule()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            links.forEach { (text, action) ->
                Text(text, Modifier.heightIn(min = 48.dp).clickable(onClick = action).wrapContentHeight(Alignment.CenterVertically),
                    style = GananciasType.status, color = c.inkSecondary)
            }
        }
    }
}

/** One `.led` row: label and its small arithmetic on the left, the amount on the right. */
@Composable
internal fun LedgerLine(row: CalculationRow, first: Boolean = false) {
    if (row.total) Rule(total = true) else if (!first) Rule()
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).alignByBaseline()) {
            Text(row.label, style = if (row.total) KomparaType.bodyStrong else KomparaType.body)
            row.detail?.let { Text(it, Modifier.padding(top = 2.dp), style = GananciasType.ledgerDetail, color = KomparaTheme.colors.inkSecondary) }
        }
        Text(row.value, Modifier.alignByBaseline(), style = if (row.total) GananciasType.ledgerTotal else GananciasType.ledgerValue,
            softWrap = false)
    }
}
