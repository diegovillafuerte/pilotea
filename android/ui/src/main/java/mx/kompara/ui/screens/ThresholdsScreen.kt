package mx.kompara.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.ui.components.*
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

@Composable
fun ThresholdsScreen(onBack: () -> Unit, onSaved: (String) -> Unit, modifier: Modifier = Modifier,
    viewModel: ThresholdsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.result) { state.result?.let(onSaved) }
    ThresholdsContent(state, viewModel::edit, viewModel::step, viewModel::select, viewModel::reset,
        viewModel::save, viewModel::confirmExit, onBack, modifier, viewModel::load)
}

@Composable
fun ThresholdsContent(state: ThresholdsUiState, edit: (LimitField, String) -> Unit,
    step: (LimitField, Boolean) -> Unit, select: (PreferredMetric) -> Unit, reset: () -> Unit,
    save: () -> Unit, confirm: (Boolean) -> Unit, close: () -> Unit, modifier: Modifier = Modifier,
    retry: () -> Unit = {}) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val ime = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var focused by remember { mutableStateOf<LimitField?>(null) }
    val back = {
        if (!state.saving) when (limitsBack(ime, state.dirty)) {
            LimitsBack.HIDE_KEYBOARD -> { keyboard?.hide(); focus.clearFocus() }
            LimitsBack.CONFIRM -> confirm(true)
            LimitsBack.CLOSE -> close()
        }
    }
    BackHandler { back() }
    ScreenScaffold("Tu meta y tu mínimo", modifier, ScreenKind.DRILL, back, footer = {
        PrimaryButton("Guardar", save, Modifier.testTag("limits-save"), enabled = state.canSave)
        if (!ime) {
            val initial = state.initial?.copy(metric = state.draft?.metric ?: PreferredMetric.IPK)
            TextButton(reset, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("limits-reset"), enabled = state.canReset && !state.saving) {
                Text(initial?.let { "Volver a lo inicial (${mx.kompara.ui.format.Formatters.unbroken(ThresholdEditor.limit(it.pair.goal, it.unit))} y ${mx.kompara.ui.format.Formatters.unbroken(ThresholdEditor.limit(it.pair.minimum, it.unit))})" } ?: "Volver a lo inicial",
                    style = KomparaType.bodyStrong, color = if (state.canReset) KomparaTheme.colors.inkSecondary else KomparaTheme.colors.inkTertiary)
            }
        }
    }) {
        val draft = state.draft
        if (draft == null) {
            if (state.error != null) { InkNotice(state.error); TextButton(retry) { Text("Reintentar") } }
            else StaticSkeleton(Modifier.fillMaxWidth().height(180.dp))
            return@ScreenScaffold
        }
        FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.Center,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Decides por:", Modifier.heightIn(min = 48.dp).wrapContentHeight(), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
            TextTabs(listOf("Por km", "Por hora"), draft.metric.ordinal, { if (!state.saving) select(PreferredMetric.entries[it]) })
        }
        Column(Modifier.fillMaxWidth().background(KomparaTheme.colors.paperSecondary, RoundedCornerShape(12.dp))
            .padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (draft.metric == PreferredMetric.IPK) "Ejemplo · $148 · 12 km" else "Ejemplo · $148 · 12 km · 24 min",
                style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp)) {
                state.preview?.let { Regla(it) }
            }
        }
        Spacer(Modifier.height(16.dp))
        LimitField.entries.forEach { field ->
            val goal = field == LimitField.GOAL
            LimitControl(if (goal) "Tu meta ·" else "Tu mínimo ·", if (goal) draft.pair.goal else draft.pair.minimum,
                draft.unit.suffix, { edit(field, it) }, { step(field, false) }, { step(field, true) },
                cue = if (goal) OfferMarkState.ABOVE else OfferMarkState.BELOW,
                helper = if (goal) "Mayor que tu mínimo (${ThresholdEditor.limit(draft.pair.minimum, draft.unit)})" else "Menor que tu meta (${ThresholdEditor.limit(draft.pair.goal, draft.unit)})",
                error = state.error?.takeIf { field == state.touched },
                stepLabel = if (draft.metric == PreferredMetric.IPK) "de $0.50 en $0.50" else "de $10 en $10",
                stepAmount = if (draft.metric == PreferredMetric.IPK) "$0.50" else "$10", enabled = !state.saving, editorLayout = true,
                onFocus = { if (it) focused = field else if (focused == field) focused = null })
            Spacer(Modifier.height(8.dp))
        }
        if (focused == null) Text("Toca la cifra para escribirla.", Modifier.padding(horizontal = 16.dp), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
    }
    if (state.confirmExit && state.draft != null && state.saved != null) Dialog(
        onDismissRequest = { if (!state.saving) confirm(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("limits-exit-dialog"),
            shape = RoundedCornerShape(4.dp), color = KomparaTheme.colors.paper,
            border = BorderStroke(1.dp, KomparaTheme.colors.rule), shadowElevation = 4.dp) {
            Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)) {
                Text("¿Guardar el cambio?", style = KomparaType.title)
                Spacer(Modifier.height(8.dp))
                Text(ThresholdEditor.changes(state.draft, state.saved), style = KomparaType.body,
                    color = KomparaTheme.colors.inkSecondary)
                Spacer(Modifier.height(4.dp))
                FlowRow(Modifier.fillMaxWidth().offset(x = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(close, enabled = !state.saving, contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("No guardar", style = KomparaType.bodyStrong, color = KomparaTheme.colors.ink)
                    }
                    TextButton(save, enabled = state.canSave, contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Guardar", style = KomparaType.bodyStrong, color = KomparaTheme.colors.ink)
                    }
                }
            }
        }
    }
}
