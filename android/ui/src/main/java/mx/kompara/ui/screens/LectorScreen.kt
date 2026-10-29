package mx.kompara.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import mx.kompara.data.db.dao.OfferPresentationDao
import mx.kompara.data.service.*
import mx.kompara.data.settings.SettingsRepository
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
    settings: SettingsRepository,
    presentations: OfferPresentationDao,
    weekClock: WeekClock,
) : ViewModel() {
    val readiness = reader.state
    private val records = reader.state.map { it.date }.distinctUntilChanged().flatMapLatest { date ->
        presentations.observeSuppressedOffers(weekClock.dayStartMs(date.toString()), weekClock.dayEndMs(date.toString()))
    }
    private val refresh = MutableStateFlow(0)
    private val failure = MutableStateFlow<String?>(null)
    val error = failure.asStateFlow()
    fun retry() { failure.value = null; refresh.value++ }
    val state = refresh.flatMapLatest { combine(reader.state, settings.settings, records, ScreenReaderState.running) { readiness, prefs, offers, running ->
        semaforoState(readiness, prefs, offers, weekClock.zone, !running && readiness.permission == true)
    }.map<SemaforoState, SemaforoState?> { it }.catch { e ->
        if (e is kotlinx.coroutines.CancellationException) throw e
        failure.value = "No pudimos cargar el estado del semáforo. Inténtalo otra vez."
        emit(null)
    } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun pause() = reader.pauseUntilTomorrow()
    fun resume() = reader.resume()
}

@Composable
fun LectorScreen(onBack: () -> Unit, onOpenThresholds: () -> Unit, onHelp: () -> Unit,
    onPermission: () -> Unit, onBattery: () -> Unit, onOffer: (String, Long) -> Unit,
    modifier: Modifier = Modifier, viewModel: LectorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var consent by rememberSaveable { mutableStateOf(false) }
    SemaforoContent(state, onBack, onOpenThresholds, onHelp, { action -> when (action) {
        SemaforoAction.RESUME -> viewModel.resume()
        SemaforoAction.PERMISSION -> onPermission()
        SemaforoAction.BATTERY -> onBattery()
        SemaforoAction.CAPTURE -> consent = true
        SemaforoAction.SETTINGS -> mx.kompara.ui.onboarding.AccessibilitySettings.open(context)
    } }, viewModel::pause, onOffer, modifier, error = error, retry = viewModel::retry)
    if (consent) AlertDialog(onDismissRequest = { consent = false },
        title = { Text(androidx.compose.ui.res.stringResource(R.string.lector_ocr_disclosure_title)) },
        text = { Text(androidx.compose.ui.res.stringResource(R.string.lector_ocr_disclosure_body)) },
        confirmButton = { TextButton({ consent = false
            context.startActivity(Intent(ScreenReaderState.ACTION_START).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text("Continuar") } }, dismissButton = { TextButton({ consent = false }) { Text("Ahora no") } })
}

@Composable
fun SemaforoContent(state: SemaforoState?, back: () -> Unit, limits: () -> Unit, help: () -> Unit,
    primary: (SemaforoAction) -> Unit, pause: () -> Unit, offer: (String, Long) -> Unit, modifier: Modifier = Modifier,
    snackbar: SaveSnackbarState? = LocalLimitsSnackbar.current, error: String? = null, retry: () -> Unit = {}) {
    var disclosure by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold("Semáforo", modifier, ScreenKind.DRILL, back, footer = {
        if (snackbar?.message != null) { SaveSnackbar(snackbar); Spacer(Modifier.height(if (state?.primary != null || state?.canPause == true) 8.dp else 16.dp)) }
        if (state?.primary != null) PrimaryButton(state.primaryLabel!!, { primary(state.primary) })
        if (state?.canPause == true) TextButton(pause, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Pausar el semáforo hasta mañana", style = KomparaType.bodyStrong, color = KomparaTheme.colors.inkSecondary)
        }
    }) {
        if (state == null) {
            if (error != null) { InkNotice(error); TextButton(retry) { Text("Reintentar") } }
            else StaticSkeleton(Modifier.fillMaxWidth().height(200.dp))
            return@ScreenScaffold
        }
        Spacer(Modifier.height(31.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusIcon(state.glyph)
            Text(state.title, style = KomparaType.headline)
        }
        state.since?.let { Text(it, style = KomparaType.body, color = KomparaTheme.colors.inkSecondary) }
        state.detail?.let { Text(it, Modifier.padding(top = 8.dp), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary) }
        state.consequence?.let { Text(it, style = KomparaType.body, color = KomparaTheme.colors.inkSecondary) }
        Spacer(Modifier.height(40.dp))
        ListRow("Tu meta y tu mínimo", detail = state.limits, onClick = limits)
        Rule()
        Spacer(Modifier.height(32.dp))
        state.apps.forEach { app ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatusIcon(app.glyph)
                Column {
                    Text(app.name, style = KomparaType.body, color = if (app.unavailable) KomparaTheme.colors.inkSecondary else KomparaTheme.colors.ink)
                    Text(app.detail, style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
                }
            }
            Rule()
        }
        Spacer(Modifier.height(32.dp))
        if (state.suppressed.isNotEmpty()) {
            val count = state.suppressed.size
            ListRow("No cupo el semáforo en $count ${if (count == 1) "oferta" else "ofertas"} hoy",
                detail = "${state.suppressionDetail}\nNo tienes que hacer nada; quedó en Tu día",
                onClick = { offer(state.date.toString(), state.suppressed.first().offerId) })
            Rule()
        }
        ListRow("¿No ves el semáforo?", onClick = help)
        Rule()
        ListRow("Qué lee Kompara", onClick = { disclosure = true })
        Rule()
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
