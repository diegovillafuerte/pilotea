package mx.kompara.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.components.*
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

@Composable
fun TusDatosScreen(onBack: () -> Unit, onSignIn: () -> Unit, viewModel: TusDatosViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var linkError by remember { mutableStateOf<String?>(null) }
    TusDatosContent(state, onBack, viewModel::telemetry, viewModel::contribution, viewModel::deleteAccount,
        viewModel::eraseLocal, viewModel::clearResult, onSignIn, externalDeletion = PrivacyLinks.deletion,
        openExternal = { if (!PrivacyLinks.open(context, it)) linkError = "No pudimos abrir el enlace. Inténtalo otra vez." },
        linkError = linkError, retry = viewModel::retry)
}

@Composable
internal fun TusDatosContent(state: TusDatosState, onBack: () -> Unit = {}, telemetry: (Boolean) -> Unit = {},
    contribution: (Boolean) -> Unit = {}, delete: () -> Unit = {}, erase: () -> Unit = {}, clearResult: () -> Unit = {},
    signIn: () -> Unit = {}, externalDeletion: String? = null, openExternal: (String) -> Unit = {},
    linkError: String? = null, initialDialog: String? = null, retry: () -> Unit = {}) {
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var dialog by rememberSaveable { mutableStateOf(initialDialog) }
    ScreenScaffold("Tus datos", kind = ScreenKind.DRILL, onNavigateUp = onBack) {
        Spacer(Modifier.height(24.dp))
        if (state.result != null) {
            Text(state.result, style = KomparaType.headline)
            Text(if (state.result == "Cuenta eliminada") "Tu historial queda en este teléfono."
                else "Tu cuenta, tus costos y tus límites se conservan. El semáforo queda en pausa hasta mañana; puedes reanudarlo en Hoy.",
                Modifier.padding(top = 12.dp), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
            TextButton(clearResult) { Text("Continuar", color = KomparaTheme.colors.ink) }
        } else if (!state.loaded) {
            SettingsLoading(state.error, retry)
        } else {
            Text("Tus viajes se calculan en este teléfono", style = KomparaType.headline.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading))
            Text("Tu pantalla se lee sin guardarse", Modifier.padding(top = 8.dp), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
            Spacer(Modifier.height(32.dp))
            SettingsValueRow("Sale del teléfono", "Cuenta y lo encendido", onClick = { sheet = "Sale del teléfono" })
            PrivacyPreferenceRow("Enviar conteos de lectura", "Sin cifras de tus viajes", state.telemetry, !state.busy, telemetry)
            if (state.contributionAvailable) PrivacyPreferenceRow("Aportar al promedio", checked = state.contribution, enabled = !state.busy,
                change = { if (it) sheet = "Aportar al promedio" else contribution(false) })
            Spacer(Modifier.height(32.dp))
            SettingsValueRow("Datos guardados", onClick = { sheet = "Datos guardados" })
            SettingsValueRow("Borrar datos del teléfono", onClick = { if (!state.busy) dialog = "local" })
            if (state.signedIn) SettingsValueRow("Eliminar cuenta", externalDeletion?.let { "También en kompara.mx/borrar" },
                onClick = { if (!state.busy) dialog = "account" })
            else SettingsValueRow("Entrar con WhatsApp", "No hay una cuenta abierta", onClick = signIn)
            if (state.busy) Text("Un momento…", Modifier.padding(top = 16.dp), style = KomparaType.body)
            (state.error ?: linkError)?.let { InkNotice(it) }
        }
    }
    sheet?.let { title -> SheetScaffold(title, { sheet = null }) {
        when (title) {
            "Sale del teléfono" -> {
                listOfNotNull(
                    "Tu WhatsApp y los datos de tu cuenta se envían al entrar con un código.",
                    "Enviar conteos de lectura manda conteos anónimos, sin cifras de tus viajes. Puedes apagarlo en Tus datos.",
                    "Si eliges Aportar al promedio, se comparten tus totales sin tu nombre. Solo cuentan semanas confirmadas con resumen.".takeIf { state.contributionAvailable },
                    "Cuando traes un resumen, tu archivo se sube a Kompara solo para leer tus números. Un reporte se comparte solo cuando lo confirmas.",
                    "Google Play procesa las compras. Kompara recibe el estado de tu plan.",
                    "Uber y DiDi no son parte de Kompara y podrían no permitir apps como esta. Kompara nunca toca su app.",
                ).forEach { Text(it, Modifier.padding(vertical = 12.dp), style = KomparaType.body); Rule() }
                externalDeletion?.let { ListRow("Eliminar cuenta fuera de la app", onClick = { openExternal(it) }) }
            }
            "Datos guardados" -> {
                Text("En este teléfono quedan tus ofertas, viajes, turnos, totales, resúmenes guardados, costos y límites.", style = KomparaType.body)
                Text("Borrar datos del teléfono quita el historial y las copias para compartir. Conserva tu cuenta, tus costos y tus límites. No borra lo que ya enviaste.", Modifier.padding(top = 16.dp), style = KomparaType.body)
                Text("Eliminar cuenta borra la cuenta, totales y resúmenes del servidor. Tu historial queda en este teléfono.", Modifier.padding(top = 16.dp), style = KomparaType.body)
            }
            "Aportar al promedio" -> if (state.contributionAvailable) {
                Text("Sumar mis totales, sin mi nombre, al promedio de mi ciudad", style = KomparaType.body)
                PrivacyPreferenceRow("Aportar al promedio", checked = state.contribution, enabled = !state.busy, change = contribution)
                Text("Solo cuentan tus semanas confirmadas con resumen; las estimaciones del lector no se suman", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
                Text("Puedes ver comparaciones sin aportar.", Modifier.padding(top = 12.dp), style = KomparaType.body)
                state.error?.let { InkNotice(it) }
            }
        }
    } }
    if (dialog != null) SettingsConfirmation(
        title = if (dialog == "account") "¿Eliminar tu cuenta?" else "¿Borrar datos del teléfono?",
        body = if (dialog == "account") deletionBody(state.playSubscription)
            else "Se borran las ofertas, viajes, turnos y resúmenes de este teléfono y las copias para compartir. Tu cuenta, tus costos y tus límites se conservan. El semáforo queda en pausa hasta mañana.",
        confirm = if (dialog == "account") "Eliminar" else "Borrar", dismiss = { dialog = null },
        accept = { val action = dialog; dialog = null; if (action == "account") delete() else erase() })
}

@Composable
internal fun PrivacyPreferenceRow(label: String, detail: String? = null, checked: Boolean,
    enabled: Boolean = true, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = KomparaType.body, color = if (enabled) KomparaTheme.colors.ink else KomparaTheme.colors.inkSecondary)
            detail?.let { Text(it, style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
        }
        KomparaSwitch(checked, change, Modifier.alpha(if (enabled) 1f else .45f).semantics { contentDescription = label }, enabled)
    }
    Rule()
}

@Composable
internal fun SettingsConfirmation(title: String, body: String, confirm: String, dismiss: () -> Unit,
    accept: () -> Unit, enabled: Boolean = true) {
    AlertDialog(onDismissRequest = dismiss, shape = RoundedCornerShape(4.dp),
        containerColor = KomparaTheme.colors.paper, tonalElevation = 0.dp,
        title = { Text(title, style = KomparaType.title, color = KomparaTheme.colors.ink) },
        text = { Text(body, Modifier.verticalScroll(rememberScrollState()), style = KomparaType.body, color = KomparaTheme.colors.ink) },
        confirmButton = { TextButton(accept, enabled = enabled) { Text(confirm, style = KomparaType.bodyStrong, color = KomparaTheme.colors.ink) } },
        dismissButton = { TextButton(dismiss, enabled = enabled) { Text("Cancelar", style = KomparaType.bodyStrong, color = KomparaTheme.colors.ink) } })
}

@Composable
internal fun SettingsLoading(error: String?, retry: () -> Unit) {
    if (error == null) StaticSkeleton(Modifier.fillMaxWidth().height(280.dp)) else {
        InkNotice(error)
        TextButton(retry) { Text("Reintentar", style = KomparaType.bodyStrong, color = KomparaTheme.colors.ink) }
    }
}
