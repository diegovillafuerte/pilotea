package mx.kompara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.R
import mx.kompara.ui.components.*
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

data class AjustesActions(val limits: () -> Unit = {}, val costs: () -> Unit = {}, val reader: () -> Unit = {},
    val account: () -> Unit = {}, val data: () -> Unit = {}, val help: () -> Unit = {})

@Composable
fun AjustesScreen(onOpenReader: () -> Unit, onOpenPermission: () -> Unit, onOpenCostProfile: () -> Unit,
    onOpenThresholds: () -> Unit, onOpenHelp: () -> Unit, onOpenAccount: () -> Unit, onOpenData: () -> Unit,
    onSignIn: () -> Unit, modifier: Modifier = Modifier, viewModel: AjustesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    AjustesContent(state = state, modifier = modifier, error = failure, retry = viewModel::retry,
        actions = AjustesActions(
            limits = onOpenThresholds, costs = onOpenCostProfile,
            reader = if (state?.firstPermission == true) onOpenPermission else onOpenReader,
            account = if (state?.signedIn == true) onOpenAccount else onSignIn,
            data = onOpenData, help = onOpenHelp,
        ))
}

/** J1: six rows, each with its current value (v4 plan 4.8). The shell draws the header. */
@Composable
internal fun AjustesContent(state: AjustesState? = AjustesState(), actions: AjustesActions = AjustesActions(),
    modifier: Modifier = Modifier, error: String? = null, retry: () -> Unit = {}) {
    val snackbar = LocalLimitsSnackbar.current
    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            if (state == null) {
                if (error != null) { InkNotice(error); TextButton(retry) { Text("Reintentar", color = KomparaTheme.colors.ink) } }
                else StaticSkeleton(Modifier.fillMaxWidth().height(300.dp))
            } else {
                AjustesRow("Tu meta y tu mínimo", state.limits, onClick = actions.limits)
                AjustesRow("Tus costos", state.costs, onClick = actions.costs)
                AjustesRow("Semáforo", state.reader, glyph = state.glyph, onClick = actions.reader)
                Spacer(Modifier.height(20.dp))
                AjustesRow("Tu cuenta", state.account, onClick = actions.account)
                AjustesRow("Tus datos", onClick = actions.data)
                AjustesRow("Ayuda", onClick = actions.help)
                error?.let { InkNotice(it) }
            }
        }
        if (snackbar?.message != null) SaveSnackbar(snackbar, Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp))
    }
}

/** Settings labels are regular; identity alone uses the money headline. Kept for Tu perfil and Tu plan. */
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

/** The kit's 14 sp line under a row label. */
internal val RowDetail = KomparaType.small.copy(fontSize = 14.sp, lineHeight = 19.sp)

/**
 * The v4 kit's `.row`: 60 dp (52 for a one-line link), the label at 16 sp with its value under it,
 * or a [value] at the right, a 20 dp chevron when it opens something, and its rule.
 */
@Composable
internal fun AjustesRow(label: String, detail: String? = null, modifier: Modifier = Modifier, glyph: StatusGlyph? = null,
    value: String? = null, link: Boolean = false, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = if (link) 52.dp else 60.dp)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // A value sits at the right while it fits beside the label; at large text it moves under it.
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(label, style = KomparaType.body)
                if (detail != null) Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    glyph?.let { StatusIcon(it) }
                    Text(detail, style = RowDetail, color = KomparaTheme.colors.inkSecondary)
                }
            }
            if (value != null) Text(value, Modifier.padding(start = 12.dp), style = KomparaType.meaning, color = KomparaTheme.colors.inkSecondary)
        }
        if (trailing != null) trailing()
        else if (onClick != null) DrawnIcon(R.drawable.v3_chev, Modifier.size(20.dp), tint = KomparaTheme.colors.inkTertiary)
    }
    Rule()
}

/**
 * The kit's `.seg`: a label, then text choices with a green underline the width of the chosen word.
 * Each choice is a 48 dp target; the semibold width is reserved so the row never shifts.
 */
@Composable
internal fun SettingsChoice(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier, labelWidth: Dp = 86.dp, enabled: Boolean = true) {
    val c = KomparaTheme.colors
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.Center) {
        Text(label, Modifier.widthIn(min = labelWidth).heightIn(min = 48.dp).wrapContentHeight(), style = KomparaType.body, color = c.inkSecondary)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            options.forEachIndexed { i, option ->
                val chosen = i == selected
                Column(Modifier.width(IntrinsicSize.Max).heightIn(min = 48.dp)
                    .selectable(chosen, enabled = enabled, role = Role.Tab, onClick = { onSelect(i) }),
                    verticalArrangement = Arrangement.Center) {
                    Spacer(Modifier.height(8.dp)) // Puts the word on the label's line, as the kit's baseline row.
                    Box {
                        Text(option, Modifier.alpha(0f).clearAndSetSemantics {}, style = KomparaType.bodyStrong)
                        Text(option, style = if (chosen) KomparaType.bodyStrong else KomparaType.body,
                            color = if (!enabled) c.inkTertiary else if (chosen) c.ink else c.inkSecondary)
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (chosen) c.activeNavigation else c.paper))
                }
            }
        }
    }
}
