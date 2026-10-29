package mx.kompara.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.components.*
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

@Composable
fun AvisosScreen(onBack: () -> Unit, viewModel: AvisosViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    DisposableEffect(lifecycle) {
        viewModel.refreshSystem()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshSystem() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    AvisosContent(state, onBack, { HoyHostLinks.notifications(context) }, viewModel::set, viewModel::retry)
}

@Composable
internal fun AvisosContent(state: AvisosState, onBack: () -> Unit = {}, openSystem: () -> Unit = {},
    change: (NoticePreference, Boolean) -> Unit = { _, _ -> }, retry: () -> Unit = {}) {
    ScreenScaffold("Avisos", kind = ScreenKind.DRILL, onNavigateUp = onBack,
        footer = if (state.loaded && (!state.allowed || state.blocked.isNotEmpty())) ({ PrimaryButton("Activar avisos en el teléfono", openSystem) }) else null) {
        Spacer(Modifier.height(24.dp))
        if (!state.loaded) SettingsLoading(state.error, retry)
        else {
            if (!state.allowed) {
                Text("Tu teléfono los tiene apagados", style = KomparaType.headline)
                Text("Sin ellos, Kompara no puede avisarte", Modifier.padding(top = 8.dp), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
                Spacer(Modifier.height(32.dp))
            }
            NoticePreference.entries.forEach { option ->
                PrivacyPreferenceRow(option.title, listOfNotNull(if (option == NoticePreference.DAY) "Sin montos" else null,
                        if (state.allowed && option in state.blocked) "Apagado en Android" else null).joinToString(" · ").ifEmpty { null },
                    option.checked(state.settings), state.allowed && option !in state.blocked) { change(option, it) }
            }
            state.error?.let { InkNotice(it) }
        }
    }
}
