package mx.kompara.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.data.service.ReaderPhase
import mx.kompara.ui.R
import mx.kompara.ui.components.*
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.service.statusGlyph
import mx.kompara.ui.service.statusLabel
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

/** All doors are supplied by the live graph; fixtures exercise these same callbacks. */
data class HoyActions(
    val settings: () -> Unit,
    val reader: () -> Unit,
    val day: (String) -> Unit,
    val offer: (String, Long) -> Unit,
    val costs: () -> Unit,
    val confirmCosts: () -> Unit,
    val limits: () -> Unit,
    val profile: () -> Unit,
    val battery: () -> Unit,
    val permission: () -> Unit,
    val openUber: () -> Unit,
    val notifications: () -> Unit,
    val resume: () -> Unit,
    val retry: () -> Unit,
    val calculation: (String) -> Unit = day,
)

@Composable
fun InicioDashboardScreen(actions: HoyActions, modifier: Modifier = Modifier,
    viewModel: InicioDashboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    HoyContent(state, actions.copy(resume = viewModel::resume, retry = viewModel::retry, confirmCosts = viewModel::confirmCosts,
        notifications = {
            if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else actions.notifications()
        }), modifier)
}

@Composable
fun HoyContent(state: InicioUiState, actions: HoyActions, modifier: Modifier = Modifier) {
    val reader = state.reader
    val footer: (@Composable ColumnScope.() -> Unit)? = when {
        reader?.paused == true -> ({ PrimaryButton("Reanudar el semáforo", actions.resume) })
        state.mode == HoyMode.FIRST_OFF -> ({ PrimaryButton("Activar el semáforo", actions.permission) })
        state.mode == HoyMode.RECOVERY -> ({
            PrimaryButton(if (reader?.permission == false) "Volver a dar el permiso" else "Revisar el semáforo",
                if (reader?.permission == false) actions.permission else actions.reader)
            if (reader?.batteryRestricted == true) HoyTextAction("Que no se vuelva a apagar", actions.battery)
        })
        state.mode == HoyMode.FIRST_PREPARED || state.mode == HoyMode.PREPARED -> ({
            PrimaryButton("Abrir Uber para probar", actions.openUber)
            if (state.mode == HoyMode.FIRST_PREPARED) HoyTextAction("Avisarme si se apaga", actions.notifications)
            else if (reader?.batteryRestricted == true) HoyTextAction("Que no se vuelva a apagar", actions.battery)
        })
        // Explicit confirmation appends a version from now; it never rewrites older costs.
        state.mode == HoyMode.COSTS -> ({ PrimaryButton("Sí, son mis costos", actions.confirmCosts) })
        else -> null
    }
    val limitsSnackbar = LocalLimitsSnackbar.current
    ScreenScaffold(stringResource(R.string.v3_today), modifier, actions = {
        IconButton(onClick = actions.settings, modifier = Modifier.size(48.dp)) {
            DrawnIcon(R.drawable.v3_gear, description = "Ajustes")
        }
    }, footer = if (limitsSnackbar?.message == null) footer else ({
        SaveSnackbar(limitsSnackbar)
        Spacer(Modifier.height(if (footer == null) 16.dp else 8.dp))
        footer?.invoke(this)
        Unit
    })) {
        when {
            state.loading -> { StaticSkeleton(Modifier.fillMaxWidth().height(160.dp)); return@ScreenScaffold }
            state.error -> {
                Text("No pudimos cargar tus viajes.", style = KomparaType.body)
                GreyButton("Reintentar", actions.retry)
                return@ScreenScaffold
            }
        }
        state.actionError?.let { QuietText(it) }
        if (reader != null) {
            if (reader.phase == ReaderPhase.OFF) {
                Row(Modifier.heightIn(min = 56.dp).semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusIcon(reader.statusGlyph())
                    Text(buildAnnotatedString {
                        withStyle(KomparaType.bodyStrong.toSpanStyle()) { append(reader.statusLabel()) }
                        withStyle(KomparaType.body.copy(color = KomparaTheme.colors.inkSecondary).toSpanStyle()) {
                            append(if (state.statusDetail.startsWith("·")) " " else "\n"); append(state.statusDetail)
                        }
                    }, style = KomparaType.body)
                }
            } else {
                HoyStatusDoor(if (reader.paused) "Semáforo en pausa · se reanuda mañana" else reader.statusLabel(),
                    state.statusDetail, reader.statusGlyph(), actions.reader)
            }
        }
        when (state.mode) {
            HoyMode.FIRST_OFF -> {
                Spacer(Modifier.height(20.dp))
                Text("Activa el semáforo", style = KomparaType.headline)
                Spacer(Modifier.height(16.dp))
                QuietText("Sin el permiso de Accesibilidad,\nKompara no ve tus ofertas.")
            }
            HoyMode.RECOVERY -> {
                Spacer(Modifier.height(20.dp))
                Text(if (reader?.permission == false) "Falta el permiso de Accesibilidad" else "Dejamos de leer tus ofertas", style = KomparaType.headline)
                Spacer(Modifier.height(16.dp))
                QuietText(if (reader?.permission == false) "Android a veces lo apaga\n(por ejemplo, al ahorrar batería).\nSin él, Kompara no ve tus ofertas."
                    else "Revisa el estado del semáforo para volver a leer tus ofertas.")
                Yesterday(state)
                if (state.snapshot?.today?.money?.trips != 0) TodayHero(state, actions)
            }
            HoyMode.FIRST_PREPARED, HoyMode.PREPARED -> {
                Spacer(Modifier.height(20.dp))
                Text("Listo. Abre tu app de viajes", style = KomparaType.headline)
                Spacer(Modifier.height(12.dp))
                QuietText(if (state.mode == HoyMode.FIRST_PREPARED) "En tu primera oferta verás el semáforo." else "En tu próxima oferta verás el semáforo.")
                if (state.mode == HoyMode.FIRST_PREPARED) {
                    Spacer(Modifier.height(24.dp))
                    val example = state.example
                    if (example != null) {
                        FlowRow(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Ejemplo", Modifier.padding(end = 12.dp, top = 18.dp), style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
                            Box(Modifier.horizontalScroll(rememberScrollState())) { Regla(example) }
                        }
                    }
                    Rule()
                    state.limits?.let { limits ->
                        val perHour = limits.unit == ReglaUnit.HOUR
                        HoySetupRow("Tu meta ${Formatters.formatLimit(limits.goal, perHour)} · tu mínimo ${Formatters.formatLimit(limits.minimum, perHour)}",
                            "Valores iniciales", onClick = actions.limits)
                    }
                    Rule()
                    state.typicalCost?.inputs?.costPerKm?.let {
                        HoySetupRow("Usamos costos típicos: ${Formatters.formatMxn(it)} por km", action = "Ajustar", onClick = actions.costs)
                        Rule()
                    }
                    HoySetupRow("Elegir mi ciudad", onClick = actions.profile)
                    Rule()
                } else Yesterday(state)
            }
            HoyMode.COSTS -> {
                Spacer(Modifier.height(20.dp))
                val cost = state.typicalCost?.inputs
                Text("¿Manejar te cuesta\nunos ${cost?.costPerKm?.let(Formatters::formatMxn)} por km?", style = KomparaType.headline)
                Spacer(Modifier.height(12.dp))
                if (cost?.kmPerLitre != null && cost.fuelPricePerLitre != null) Text(
                    "${cost.kmPerLitre?.stripTrailingZeros()?.toPlainString()} km/L · gasolina ${cost.fuelPricePerLitre?.let(Formatters::formatMxn)}/L · desgaste ${Formatters.formatMxn(cost.wearPerKm)}/km", style = KomparaType.meaning, color = KomparaTheme.colors.inkSecondary)
                Spacer(Modifier.height(20.dp)); Rule()
                HoySetupRow("Ajustar mis costos", onClick = actions.costs)
                Rule()
                Yesterday(state, withRule = false)
            }
            HoyMode.MONEY -> TodayHero(state, actions)
            HoyMode.EMPTY -> Yesterday(state)
        }
        if (state.partialDidi && state.mode == HoyMode.MONEY) {
            Spacer(Modifier.height(12.dp)); Rule()
            Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatusIcon(StatusGlyph.OFF)
                Text("Por ahora no leemos DiDi", style = KomparaType.body)
            }
            Rule()
        }
        if (state.offers.isNotEmpty() && state.mode !in listOf(HoyMode.FIRST_OFF, HoyMode.RECOVERY)) {
            Spacer(Modifier.height(32.dp))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Últimas ofertas", style = KomparaType.section)
                Text(if (state.limits?.unit == ReglaUnit.HOUR) "te deja por hora, sin el coche" else "te deja por km, sin el coche",
                    Modifier.align(Alignment.Bottom), style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
            }
            Spacer(Modifier.height(8.dp))
            state.offers.forEach { offer ->
                OfferRow(offer.outcome, offer.detail, offer.rate, offer.mark,
                    { state.dayIso?.let { actions.offer(it, offer.id) } })
                Rule()
            }
            if (reader?.paused != true && state.snapshot?.today?.offers?.size != 1) {
                Spacer(Modifier.height(20.dp))
                GreyButton("Ver todas las ofertas de hoy", { state.dayIso?.let(actions.day) })
            }
        }
    }
}

@Composable private fun TodayHero(state: InicioUiState, actions: HoyActions) {
    Spacer(Modifier.height(12.dp))
    PeriodHero(state.label, state.amount, if (state.amount == null) "Faltan datos para calcular lo que llevas." else state.quietLine,
        { state.dayIso?.let(actions.calculation) })
}
@Composable private fun Yesterday(state: InicioUiState, withRule: Boolean = true) {
    if (withRule) { Spacer(Modifier.height(32.dp)); Rule() }
    Spacer(Modifier.height(32.dp))
    state.yesterday?.let { line ->
        Text(buildAnnotatedString {
            val amount = Regex("−?\\$[0-9,]+").find(line)
            if (amount == null) append(line) else {
                append(line.substring(0, amount.range.first))
                withStyle(KomparaType.rate.toSpanStyle()) { append(amount.value) }
                append(line.substring(amount.range.last + 1))
            }
        }, style = KomparaType.body)
        Spacer(Modifier.height(4.dp))
    }
    if (state.snapshot?.today?.money?.trips == 0) QuietText("Aún no registramos viajes hoy.")
}
@Composable private fun QuietText(text: String) = Text(text, style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
@Composable private fun HoyTextAction(text: String, onClick: () -> Unit) {
    TextButton(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(text, style = KomparaType.bodyStrong, color = KomparaTheme.colors.ink) }
}
@Composable private fun HoyStatusDoor(text: String, detail: String, glyph: StatusGlyph, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StatusIcon(glyph)
        Column(Modifier.weight(1f)) {
            Text(text, style = KomparaType.bodyStrong)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (detail == "permiso listo") StatusIcon(StatusGlyph.DONE)
                QuietText(detail)
            }
        }
        DrawnIcon(R.drawable.v3_chev)
    }
}

@Composable private fun HoySetupRow(label: String, detail: String? = null, action: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(buildAnnotatedString {
                var index = 0
                Regex("−?\\$[0-9,.]+(?:/km|/h| por km)?").findAll(label).forEach { amount ->
                    append(label.substring(index, amount.range.first))
                    withStyle(KomparaType.rate.toSpanStyle()) { append(Formatters.unbroken(amount.value)) }
                    index = amount.range.last + 1
                }
                append(label.substring(index))
            }, style = KomparaType.body)
            if (detail != null) Text(detail, style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
        }
        if (action != null) QuietText(action)
        DrawnIcon(R.drawable.v3_chev)
    }
}
