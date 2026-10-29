package mx.kompara.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.data.settings.ThemePreference
import mx.kompara.ui.R
import mx.kompara.ui.components.*
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

data class AjustesActions(val account: () -> Unit = {}, val plan: () -> Unit = {}, val limits: () -> Unit = {},
    val costs: () -> Unit = {}, val car: () -> Unit = {}, val reader: () -> Unit = {}, val notices: () -> Unit = {},
    val referral: () -> Unit = {}, val help: () -> Unit = {}, val data: () -> Unit = {}, val theme: () -> Unit = {})

@Composable
fun AjustesScreen(onOpenReader: () -> Unit, onOpenPermission: () -> Unit, onOpenCostProfile: () -> Unit,
    onOpenReferral: () -> Unit, onOpenThresholds: () -> Unit, onOpenHelp: () -> Unit,
    onOpenAccount: () -> Unit, onOpenData: () -> Unit, onOpenNotices: () -> Unit, onSignIn: () -> Unit, onPlan: () -> Unit,
    modifier: Modifier = Modifier, viewModel: AjustesViewModel = hiltViewModel(), carModel: CarCostViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    val car by carModel.state.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    AjustesContent(state = state, modifier = modifier, error = failure, retry = viewModel::retry,
        actions = AjustesActions(
            account = if (state?.signedIn == true) onOpenAccount else onSignIn, plan = onPlan,
            limits = onOpenThresholds, costs = onOpenCostProfile, car = carModel::open,
            reader = if (state?.firstPermission == true) onOpenPermission else onOpenReader,
            notices = onOpenNotices, referral = onOpenReferral, help = onOpenHelp,
            data = onOpenData, theme = { sheet = "Tema" },
        ))
    if (car.open) CarCostSheet(car, carModel::amount, carModel::date, { carModel.save() }, carModel::dismiss,
        carModel::confirmRemove, { carModel.save(remove = true) }, carModel::open)
    sheet?.let { title -> SheetScaffold(title, { sheet = null }) {
        ThemePreference.entries.forEach { theme ->
            SettingsValueRow(themeLabel(theme), glyph = if (theme == state?.theme) StatusGlyph.DONE else null,
                onClick = { viewModel.setThemePreference(theme); sheet = null })
        }
    } }
}

fun themeLabel(theme: ThemePreference) = when (theme) {
    ThemePreference.AUTOMATIC -> "Automático"; ThemePreference.LIGHT -> "Claro"; ThemePreference.DARK -> "Oscuro"
}

@Composable
internal fun AjustesContent(limits: String = "", state: AjustesState? = AjustesState(limits = limits),
    actions: AjustesActions = AjustesActions(), modifier: Modifier = Modifier, error: String? = null, retry: () -> Unit = {}) {
    val snackbar = LocalLimitsSnackbar.current
    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            if (state == null) {
                if (error != null) { InkNotice(error); TextButton(retry) { Text("Reintentar", color = KomparaTheme.colors.ink) } }
                else StaticSkeleton(Modifier.fillMaxWidth().height(300.dp))
            } else {
                Spacer(Modifier.height(8.dp))
                SettingsValueRow(state.account, state.accountDetail, onClick = actions.account, identity = state.signedIn)
                SettingsValueRow(state.plan, state.planDetail, onClick = actions.plan)
                Spacer(Modifier.height(32.dp))
                SettingsValueRow("Tu meta y tu mínimo", state.limits, onClick = actions.limits)
                SettingsValueRow("Tus costos", state.costs, onClick = actions.costs)
                SettingsValueRow("Lo del coche", state.car, onClick = actions.car)
                Spacer(Modifier.height(32.dp))
                SettingsValueRow("Semáforo", state.reader, state.glyph, actions.reader)
                SettingsValueRow("Avisos", onClick = actions.notices)
                SettingsValueRow("Invita y gana", onClick = actions.referral)
                SettingsValueRow("Ayuda", onClick = actions.help)
                SettingsValueRow("Tus datos", onClick = actions.data)
                SettingsValueRow("Tema", themeLabel(state.theme), onClick = actions.theme)
                error?.let { InkNotice(it) }
            }
        }
        if (snackbar?.message != null) SaveSnackbar(snackbar, Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp))
    }
}

/** Settings labels are regular; identity alone uses the money headline. */
@Composable
internal fun SettingsValueRow(label: String, detail: String? = null, glyph: StatusGlyph? = null,
    onClick: () -> Unit, identity: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = if (identity) KomparaType.headline else KomparaType.body)
            if (detail != null || glyph != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                glyph?.let { StatusIcon(it) }
                detail?.let { Text(it, style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
            }
        }
        DrawnIcon(R.drawable.v3_chev)
    }
    Rule()
}
