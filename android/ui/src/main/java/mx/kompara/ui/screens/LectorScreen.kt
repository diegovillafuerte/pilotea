package mx.kompara.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import mx.kompara.data.service.*
import mx.kompara.ui.R
import mx.kompara.ui.components.*
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LectorViewModel @Inject constructor(
    private val reader: ReaderReadinessRepository,
    private val weekClock: WeekClock,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    private val failure = MutableStateFlow<String?>(null)
    val error = failure.asStateFlow()
    fun retry() { failure.value = null; refresh.value++ }
    /** "Encender el semáforo": clears a stored pause (the screen-capture fallback's own stop). */
    fun resume() = reader.resume()
    val state = refresh.flatMapLatest { combine(reader.state, ScreenReaderState.running) { readiness, running ->
        semaforoState(readiness, weekClock.zone, !running && readiness.permission == true)
    }.map<SemaforoState, SemaforoState?> { it }.catch { e ->
        if (e is kotlinx.coroutines.CancellationException) throw e
        failure.value = "No pudimos cargar el estado del semáforo. Inténtalo otra vez."
        emit(null)
    } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun LectorScreen(onBack: () -> Unit, onHelp: () -> Unit, onPermission: () -> Unit, onBattery: () -> Unit,
    modifier: Modifier = Modifier, viewModel: LectorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var consent by rememberSaveable { mutableStateOf(false) }
    SemaforoContent(state, onBack, onHelp, { action -> when (action) {
        SemaforoAction.PERMISSION -> onPermission()
        SemaforoAction.BATTERY -> onBattery()
        SemaforoAction.CAPTURE -> consent = true
        SemaforoAction.SETTINGS -> mx.kompara.ui.onboarding.AccessibilitySettings.open(context)
        SemaforoAction.RESUME -> viewModel.resume()
    } }, modifier, error = error, retry = viewModel::retry)
    if (consent) AlertDialog(onDismissRequest = { consent = false },
        title = { Text(androidx.compose.ui.res.stringResource(R.string.lector_ocr_disclosure_title)) },
        text = { Text(androidx.compose.ui.res.stringResource(R.string.lector_ocr_disclosure_body)) },
        confirmButton = { TextButton({ consent = false
            context.startActivity(Intent(ScreenReaderState.ACTION_START).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text("Continuar") } }, dismissButton = { TextButton({ consent = false }) { Text("Ahora no") } })
}

/** J4: the state, a proof row per app, two links. Off, the state carries the cause and one button (as L3). */
@Composable
fun SemaforoContent(state: SemaforoState?, back: () -> Unit, help: () -> Unit, primary: (SemaforoAction) -> Unit,
    modifier: Modifier = Modifier, error: String? = null, retry: () -> Unit = {}) {
    var disclosure by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold("Semáforo", modifier, ScreenKind.DRILL, back, footer = state?.primary?.let { action -> {
        PrimaryButton(state.primaryLabel!!, { primary(action) })
    } }) {
        if (state == null) {
            if (error != null) { InkNotice(error); TextButton(retry) { Text("Reintentar") } }
            else StaticSkeleton(Modifier.fillMaxWidth().height(200.dp))
            return@ScreenScaffold
        }
        Row(Modifier.heightIn(min = 56.dp).padding(start = 3.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            StatusIcon(state.glyph)
            Text(buildAnnotatedString {
                append(state.title)
                state.since?.let { withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = KomparaTheme.colors.inkSecondary)) { append(" · $it") } }
            }, style = KomparaType.title)
        }
        // A cause with a button reads as L3's headline; a passing state stays a quiet line.
        state.detail?.let { Text(it, Modifier.padding(top = 4.dp), style = if (state.primary != null) KomparaType.headline else KomparaType.body,
            color = if (state.primary != null) KomparaTheme.colors.ink else KomparaTheme.colors.inkSecondary) }
        state.consequence?.let { Text(it, Modifier.padding(top = 8.dp), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary) }
        if (state.detail != null) Spacer(Modifier.height(12.dp))
        state.apps.forEach { app ->
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 9.dp).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) { StatusIcon(app.glyph) }
                Column(Modifier.weight(1f)) {
                    Text(app.name, style = KomparaType.body)
                    Text(app.detail, Modifier.padding(top = 2.dp), style = RowDetail, color = KomparaTheme.colors.inkSecondary)
                }
            }
            Rule()
        }
        Spacer(Modifier.height(20.dp))
        AjustesRow("¿No ves el semáforo?", link = true, onClick = help)
        AjustesRow("Qué lee Kompara", link = true, onClick = { disclosure = true })
    }
    if (disclosure) SheetScaffold("Lo que hace Kompara", { disclosure = false }) {
        Text("Para mostrarte lo que deja una oferta.", style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
        Spacer(Modifier.height(32.dp))
        listOf(
            "Lee cada oferta con el permiso de accesibilidad de Android: toma tarifa, distancia y tiempo de una captura de pantalla y la borra.",
            "Guarda tus cifras en este teléfono, nunca imágenes ni datos del pasajero.",
            "Manda conteos de lectura (apágalos en Tus datos) y, si tú eliges, totales, tu aporte al promedio, archivos o reportes.",
            "Uber y DiDi no son parte de Kompara y podrían no permitir apps como esta. Kompara nunca toca su app.",
        ).forEachIndexed { index, text ->
            if (index > 0) Rule()
            Text(text, Modifier.padding(vertical = 16.dp), style = KomparaType.body)
        }
    }
}
