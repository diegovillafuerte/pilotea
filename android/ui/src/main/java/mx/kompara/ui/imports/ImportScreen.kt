package mx.kompara.ui.imports

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.kompara.sync.api.ImportFile
import mx.kompara.sync.imports.*
import mx.kompara.ui.components.*
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.stats.appName
import mx.kompara.ui.stats.weekRange
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

const val IMPORT_CONSENT = "Tu archivo se sube a Kompara solo para leer tus números."

@Composable
fun ImportScreen(onClose: () -> Unit, onSignIn: () -> Unit, onSaved: (String) -> Unit,
    modifier: Modifier = Modifier, viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val files = withContext(Dispatchers.IO) { uris.take(3).map { readImportFile(context, it) } }
            if (files.size > 2 || files.any { it == null }) viewModel.fileError()
            else viewModel.selected(files.filterNotNull())
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.returnedFromSignIn() }
    LaunchedEffect(state) { (state as? ImportUiState.Saved)?.let {
        Toast.makeText(context, "Listo, tu semana ya está en Ganancias", Toast.LENGTH_LONG).show()
        onSaved(it.weekStart)
    } }
    BackHandler(state is ImportUiState.Correcting) { viewModel.cancelCorrection() }
    ImportContent(state, ImportActions(
        close = onClose, choose = { viewModel.preparePicker(adding = false); picker.launch(ShareTargetClassifier.ACCEPTED_MIME.toTypedArray()) },
        upload = viewModel::upload, save = viewModel::confirm, useStatement = viewModel::useStatement,
        correct = viewModel::edit, correction = viewModel::updateCorrection, saveCorrection = viewModel::saveCorrection,
        cancelCorrection = viewModel::cancelCorrection, retry = viewModel::retry, restart = viewModel::restart,
        add = { viewModel.preparePicker(adding = true); picker.launch(arrayOf("image/png", "image/jpeg", "image/webp")) },
        review = viewModel::reviewPartial, signIn = onSignIn, didi = viewModel::singleImageIsDidi), modifier, focusCorrection = true)
}

data class ImportActions(
    val close: () -> Unit = {}, val choose: () -> Unit = {}, val upload: () -> Unit = {}, val save: () -> Unit = {},
    val useStatement: () -> Unit = {}, val correct: () -> Unit = {}, val correction: (ImportCorrection) -> Unit = {},
    val saveCorrection: () -> Unit = {}, val cancelCorrection: () -> Unit = {}, val retry: () -> Unit = {},
    val restart: () -> Unit = {}, val add: () -> Unit = {}, val review: () -> Unit = {}, val signIn: () -> Unit = {}, val didi: () -> Unit = {},
)

@Composable
fun ImportContent(state: ImportUiState, actions: ImportActions, modifier: Modifier = Modifier, focusCorrection: Boolean = false) {
    ScreenScaffold("Importar", modifier, if (state is ImportUiState.Correcting) ScreenKind.DRILL else ScreenKind.TASK,
        onNavigateUp = if (state is ImportUiState.Correcting) actions.cancelCorrection else actions.close,
        footer = {
            when(state) {
                ImportUiState.Picking -> { Consent(); PrimaryButton("Elegir archivos", actions.choose) }
                is ImportUiState.Ready -> { Consent(); PrimaryButton("Leer mi resumen", actions.upload) }
                is ImportUiState.SignedOut -> PrimaryButton("Entrar con WhatsApp", actions.signIn)
                is ImportUiState.Review -> if (state.draft.stage == "SAVED") PrimaryButton("Listo", actions.close) else if (state.partial) PrimaryButton("Agregar la captura", actions.add) else PrimaryButton("Guardar", actions.save)
                is ImportUiState.Reconcile -> {
                    Text(reconciliationConsequence(state.review), style = KomparaType.small)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(if (state.review.draft.uploadType == "pdf") "Usar el PDF" else "Usar el resumen", actions.useStatement, enabled = state.review.overlap?.active != true)
                }
                is ImportUiState.Missing -> { PrimaryButton("Agregar la captura", actions.add); if (state.parsed) TextAction("Revisar lo leído", actions.review) }
                is ImportUiState.Correcting -> { PrimaryButton("Guardar corrección", actions.saveCorrection); TextAction("Cancelar", actions.cancelCorrection) }
                is ImportUiState.Error -> {
                    if (state.retryable) { PrimaryButton(if (state.saving) "Revisar lo leído" else "Reintentar", actions.retry); TextAction("Elegir otro archivo", actions.restart) }
                    else PrimaryButton("Elegir otro archivo", actions.restart)
                }
                else -> Unit
            }
        }) {
        Spacer(Modifier.height(20.dp))
        when(state) {
            ImportUiState.Loading -> Text("Abriendo tu resumen", style = KomparaType.body)
            ImportUiState.Picking, is ImportUiState.Ready -> {
                Headline("Trae tu resumen")
                Spacer(Modifier.height(8.dp)); Secondary("Para semanas que Kompara no vio.")
                Spacer(Modifier.height(32.dp))
                listOf("Uber · por correo" to "El PDF semanal", "Uber · desde la app" to "La captura de Ganancias", "DiDi" to "2 capturas: Ganancias y Tablero").forEach { (title, note) ->
                    Text(title, style = KomparaType.bodyStrong); Spacer(Modifier.height(2.dp)); Small(note); Spacer(Modifier.height(20.dp))
                }
                if (state is ImportUiState.Ready) {
                    Rule()
                    state.files.forEach { file -> CalculationLedger(listOf(CalculationRow(file.fileName, fileSize(file)))) }
                    Small("${appName(state.draft.platform)} · ${if (state.draft.uploadType == "pdf") "PDF" else "captura"}")
                    if (state.files.size == 1 && state.draft.uploadType != "pdf" && state.draft.platform != "didi") TextAction("Es una captura de DiDi", actions.didi)
                    if (state.draft.parentId != null) { Spacer(Modifier.height(12.dp)); Small("Se leerán las dos capturas juntas. El resultado anterior se conserva hasta que revises y guardes.") }
                }
            }
            is ImportUiState.SignedOut -> { Headline("Entra para leer tu resumen"); Spacer(Modifier.height(16.dp)); Secondary("Necesitas una cuenta de WhatsApp. Tus datos locales siguen en este teléfono.") }
            is ImportUiState.Reading -> { Headline(if (state.saving) "Guardando tu semana" else "Leyendo tu archivo"); Spacer(Modifier.height(16.dp)); Small(if (state.saving) "En este teléfono" else "Esperando la lectura de tu resumen") }
            is ImportUiState.Review -> ReviewContent(state, actions.correct)
            is ImportUiState.Reconcile -> ReconcileContent(state.review)
            is ImportUiState.Missing -> {
                Headline("Falta la captura del Tablero")
                Spacer(Modifier.height(8.dp)); Small("DiDi" + (state.draft.metrics()?.let { " · ${weekRange(LocalDate.parse(it.weekStart))}" } ?: ""))
                Spacer(Modifier.height(32.dp)); Rule()
                val metrics = state.draft.metrics()
                MissingRow("Ganancias", metrics?.let { "Te pagó la app ${it.netEarnings?.let(Formatters::formatMxnWhole) ?: "—"} · ${it.totalTrips ?: "—"} viajes" } ?: "Captura en este teléfono", if (state.parsed) "Leída" else "Guardada")
                MissingRow("Tablero", "Horas en línea y kilómetros", "Falta")
                Spacer(Modifier.height(16.dp)); Small("No sumamos la semana a tu historial hasta que revises y guardes.")
                if (!state.parsed) { Spacer(Modifier.height(16.dp)); Small("DiDi necesita las dos capturas para leerlas juntas.") }
            }
            is ImportUiState.Correcting -> CorrectionContent(state, actions.correction, focusCorrection)
            is ImportUiState.Error -> {
                Headline(if (!state.retryable) "No pudimos leer este archivo" else if (state.saving) "No se pudo guardar tu semana" else "No se pudo subir tu archivo")
                Spacer(Modifier.height(24.dp)); StatusLine(state.message, StatusGlyph.OFF)
                Spacer(Modifier.height(16.dp)); Small(if (state.saving) "Lo leído sigue en este teléfono." else "Tu archivo sigue en tu teléfono.")
                Spacer(Modifier.height(32.dp)); Rule()
                state.files.forEach { CalculationLedger(listOf(CalculationRow(it.fileName, fileSize(it)))) }
            }
            is ImportUiState.Saved -> Text("Listo, tu semana ya está en Ganancias", style = KomparaType.body)
        }
    }
}

@Composable private fun ReviewContent(state: ImportUiState.Review, correct: () -> Unit) {
    val m = state.draft.metrics()!!
    val start = LocalDate.parse(m.weekStart)
    Headline("Te pagó la app")
    Spacer(Modifier.height(8.dp)); Text(m.netEarnings?.let(Formatters::formatMxnWhole) ?: "—", style = KomparaType.number)
    Spacer(Modifier.height(8.dp)); Secondary("${weekRange(start)} · ${importSourceLabel(state.draft.platform, state.draft.uploadType)}")
    Spacer(Modifier.height(24.dp)); Rule()
    CalculationLedger(buildList {
        add(CalculationRow("Viajes", m.totalTrips?.toString() ?: "—"))
        add(CalculationRow("Horas en línea", m.hoursOnline?.let { "${decimal(it)} h" } ?: "—"))
        m.platformCommissionPct?.let { add(CalculationRow("Comisión", "${decimal(it)} %")) }
        m.totalKm?.let { add(CalculationRow("Kilómetros", decimal(it))) }
        add(CalculationRow("Te quedaron", "—", if (m.totalKm == null) "faltan los kilómetros" else "falta confirmar los costos de esa semana"))
    }); Rule()
    Spacer(Modifier.height(16.dp)); Small(importConsequence(start))
    if (state.draft.stage != "SAVED") {
        Spacer(Modifier.height(20.dp)); Rule(); ListRow("Corregir lo leído", onClick = correct); Rule()
    } else { Spacer(Modifier.height(20.dp)); Small(if (state.draft.correctionJson != null) "Guardado en este teléfono · corregiste lo leído" else "Guardado en este teléfono · según tu resumen") }
}

fun importSourceLabel(platform: String, type: String) = when {
    platform == "didi" -> "capturas de DiDi"
    type == "pdf" -> "PDF de Uber"
    else -> "según tu captura de Uber"
}
fun importConsequence(start: LocalDate): String = if (start.month != start.plusDays(6).month)
    "Se guarda como una semana, sin repartir entre meses ni por día."
else "Se guarda en ${start.format(DateTimeFormatter.ofPattern("MMMM", Locale.forLanguageTag("es-MX")))} como una semana, sin repartir por día."
fun reconciliationConsequence(review: ImportUiState.Review): String {
    val start = LocalDate.parse(review.draft.metrics()!!.weekStart)
    val overlap = review.overlap!!
    val source = if (review.draft.uploadType == "pdf") "El PDF" else "El resumen"
    return if (start.month != start.plusDays(6).month) "$source queda en la semana; ${overlap.end.format(DateTimeFormatter.ofPattern("MMMM", Locale.forLanguageTag("es-MX")))} conserva lo leído del ${overlap.start.dayOfMonth} al ${overlap.end.dayOfMonth}."
        else "$source se usa en esta semana. Tus registros originales se conservan."
}

@Composable private fun ReconcileContent(review: ImportUiState.Review) {
    val m = review.draft.metrics()!!
    val overlap = review.overlap!!
    Headline("Esa semana ya tiene datos")
    Spacer(Modifier.height(8.dp)); Small("${weekRange(LocalDate.parse(m.weekStart))} · ${appName(review.draft.platform)}")
    Spacer(Modifier.height(32.dp))
    val capturedLabel = "${overlap.sourceLabel}\n${shortRange(overlap.start, overlap.end)}"
    val source = if (review.draft.uploadType == "pdf") "PDF" else "Resumen"
    ComparisonRow("", capturedLabel, "$source\n7 días", header = true)
    Rule(); ComparisonRow("Viajes", overlap.trips.toString(), m.totalTrips?.toString() ?: "—")
    Rule(); ComparisonRow("Te pagó la app", overlap.payment?.let(Formatters::formatMxnWhole) ?: "—", m.netEarnings?.let(Formatters::formatMxnWhole) ?: "—")
    Rule(); ComparisonRow("Kilómetros", overlap.km?.stripTrailingZeros()?.toPlainString() ?: "—", m.totalKm?.let(::decimal) ?: "—"); Rule()
    Spacer(Modifier.height(16.dp)); Small("Así quedaría tu semana")
    val missing = m.totalTrips?.minus(overlap.trips)?.takeIf { it > 0 }
    CalculationLedger(listOf(
        CalculationRow("Gasolina y desgaste", overlap.runningExpense?.negate()?.let(Formatters::formatMxnWhole) ?: "—", "de ${overlap.trips} viajes"),
        CalculationRow("Te quedaron", "—", if (missing != null && m.totalKm == null) "faltan los km de $missing viajes" else if (m.totalKm == null) "faltan los kilómetros del resumen" else "falta confirmar los costos de esa semana", total = true)))
    Spacer(Modifier.height(8.dp)); Small("Lo del coche se resta de la semana, no de una app.")
    if (overlap.active) { Spacer(Modifier.height(12.dp)); Small("Hay un viaje en curso. Revisa la semana cuando termine.") }
}

@Composable private fun ComparisonRow(label: String, left: String, right: String, header: Boolean = false) {
    val large = LocalDensity.current.fontScale > 1.3f
    if (large && !header) Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(label, style = KomparaType.body); Text("Lector: $left", style = KomparaType.rate); Text("Resumen: $right", style = KomparaType.rate)
    } else Row(Modifier.fillMaxWidth().heightIn(min = if (header) 28.dp else 48.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1.5f), style = KomparaType.body)
        Text(left, Modifier.weight(1f), style = if (header) KomparaType.small else KomparaType.rate, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        Text(right, Modifier.weight(.9f).padding(start = 8.dp), style = if (header) KomparaType.small else KomparaType.rate, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable private fun CorrectionContent(state: ImportUiState.Correcting, changed: (ImportCorrection) -> Unit, requestFocus: Boolean) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.review.draft.id) { if (requestFocus && state.values.payment != null) focus.requestFocus() }
    Headline("Corregir lo leído")
    val values = state.values
    val original = state.review.draft.response()!!.metrics
    val defaults = ImportCorrection.from(original)
    Spacer(Modifier.height(16.dp))
    values.trips?.let { CorrectionField("Viajes", it, defaults.trips, { changed(values.copy(trips = it)) }, integer = true) }
    values.payment?.let { CorrectionField("Te pagó la app", it, defaults.payment, { changed(values.copy(payment = it)) }, modifier = Modifier.focusRequester(focus), prefix = "$",
        hint = if (state.review.draft.uploadType == "pdf") "Escribe “Tus ganancias” tal como aparece en el PDF." else "Escribe la cifra tal como aparece en tu captura.") }
    values.hours?.let { CorrectionField("Horas en línea", it, defaults.hours, { changed(values.copy(hours = it)) }, suffix = "h") }
    values.km?.let { CorrectionField("Kilómetros", it, defaults.km, { changed(values.copy(km = it)) }, suffix = "km") }
    // Legacy parsers may infer a Monday: allow the driver to correct the visible period as well.
    KomparaTextField(values.week, { changed(values.copy(week = it)) }, "Lunes de la semana", underlined = true, hint = "AAAA-MM-DD")
    state.error?.let { Spacer(Modifier.height(12.dp)); Text(it, style = KomparaType.body) }
}
@Composable private fun CorrectionField(label: String, value: String, original: String?, changed: (String) -> Unit,
    prefix: String? = null, suffix: String? = null, hint: String? = null, integer: Boolean = false, modifier: Modifier = Modifier) {
    KomparaTextField(value, changed, if (value != original) "$label · se leyó ${prefix.orEmpty()}$original" else label,
        modifier.fillMaxWidth(), prefix = prefix, suffix = suffix, hint = hint, underlined = true,
        visualTransformation = if (prefix == "$") mx.kompara.ui.format.DecimalGrouping else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal))
    Spacer(Modifier.height(16.dp))
}
@Composable private fun MissingRow(title: String, sub: String, status: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = KomparaType.body); Small(sub) }
        Row(Modifier.widthIn(min = 74.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(if (status == "Falta") StatusGlyph.OFF else StatusGlyph.DONE)
            Spacer(Modifier.width(8.dp)); Text(status, style = KomparaType.small)
        }
    }; Rule()
}
@Composable private fun Headline(text: String) = Text(text, style = KomparaType.headline)
@Composable private fun Secondary(text: String) = Text(text, style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
@Composable private fun Small(text: String) = Text(text, style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
@Composable private fun Consent() { Text(IMPORT_CONSENT, style = KomparaType.small); Spacer(Modifier.height(12.dp)) }
@Composable private fun TextAction(text: String, action: () -> Unit) = KomparaButton(text, action, variant = ButtonVariant.TEXT, fullWidth = true)
private fun decimal(value: Double) = value.toBigDecimal().stripTrailingZeros().toPlainString()
private fun shortRange(start: LocalDate, end: LocalDate) = if (start == end) "${start.dayOfMonth} ${start.format(DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("es-MX")))}"
    else "${start.dayOfMonth} – ${end.dayOfMonth} ${end.format(DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("es-MX")))}"
private fun fileSize(file: ImportFile) = String.format(Locale.US, "%.1f MB", file.bytes.size / (1024.0 * 1024.0))

/** Bounded resolver read, off the UI thread. Cancellation/picker denial leaves the retained draft. */
internal fun readImportFile(context: Context, uri: Uri): ImportFile? = runCatching {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri)?.replace("image/jpg", "image/jpeg") ?: return null
    if (mime !in ShareTargetClassifier.ACCEPTED_MIME) return null
    val bytes = resolver.openInputStream(uri)?.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            if (out.size() + size > 10 * 1024 * 1024) return null
            out.write(buffer, 0, size)
        }
        out.toByteArray()
    } ?: return null
    if (bytes.isEmpty()) return null
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
    } ?: "resumen.${if (mime == "application/pdf") "pdf" else "png"}"
    ImportFile(name, mime, bytes)
}.getOrNull()
