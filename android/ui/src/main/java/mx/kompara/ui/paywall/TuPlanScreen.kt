package mx.kompara.ui.paywall

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import mx.kompara.ui.R
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.billing.PaymentStatus
import mx.kompara.billing.BillingProducts
import mx.kompara.ui.components.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

@Composable
fun TuPlanScreen(onBack: () -> Unit, onSubscribe: () -> Unit, viewModel: TuPlanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    TuPlanContent(state, onBack, onSubscribe, viewModel::restore, {
        openPlanLink(context, "https://play.google.com/store/account/subscriptions?sku=${BillingProducts.PREMIUM_SUBSCRIPTION}&package=${context.packageName}")
    })
}
@Composable
fun TuPlanContent(state: TuPlanState, onBack: () -> Unit = {}, subscribe: () -> Unit = {}, restore: () -> Unit = {}, manage: () -> Unit = {}) {
    val attention = state.payment == PaymentStatus.GRACE || state.payment == PaymentStatus.HOLD
    ScreenScaffold("Tu plan", kind = ScreenKind.DRILL, onNavigateUp = onBack, footer = if (attention) ({ PrimaryButton("Arreglarlo en Google Play", manage) }) else null) {
        Spacer(Modifier.height(24.dp))
        Text(state.title, style = KomparaType.headline)
        state.detail?.let { detail ->
            Spacer(Modifier.height(8.dp))
            if (attention || state.payment == PaymentStatus.PENDING) StatusLine(detail, if (attention) StatusGlyph.OFF else StatusGlyph.WAITING)
            else Text(detail, style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
        }
        Spacer(Modifier.height(32.dp))
        if (state.dates.isNotEmpty()) {
            Text("Tu acceso sigue hasta", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
            Rule(Modifier.padding(top = 8.dp))
            state.dates.forEach { (label, date) ->
                FlowRow(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = KomparaType.body, modifier = Modifier.padding(end = 12.dp))
                    Text(date, style = KomparaType.bodyStrong)
                }
                Rule()
            }
        }
        if (state.manage && !attention) { mx.kompara.ui.screens.SettingsValueRow("Administrar suscripción", onClick = manage); Rule() }
        else if (state.subscribe) { PlanSubscribeRow(requireNotNull(state.price), subscribe); Rule() }
        PlanRestoreRow(state.busy, restore)
        Rule()
        if (!attention) Text(if (state.payment == PaymentStatus.PENDING) {
            if (state.dates.isEmpty()) "Premium se activa cuando se confirme" else "Si no se confirma, no se cobra nada y conservas lo que ya tenías."
        } else "Si no haces nada, tu semáforo, tu día, tu semana y tu IMSS siguen gratis.", Modifier.padding(top = 16.dp), style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
        state.message?.let { Text(it, Modifier.padding(top = 16.dp), style = KomparaType.body) }
    }
}

@Composable private fun PlanRestoreRow(busy: Boolean, restore: () -> Unit) {
    if (busy) Text("Consultando compra…", Modifier.padding(vertical = 16.dp), style = KomparaType.body)
    else mx.kompara.ui.screens.SettingsValueRow("Restaurar compra", onClick = restore)
}

@Composable private fun PlanSubscribeRow(price: String, subscribe: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = subscribe).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Suscribirme ahora", style = KomparaType.body)
            Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(price) }; append(" al mes") },
                style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
        }
        DrawnIcon(R.drawable.v3_chev)
    }
}
