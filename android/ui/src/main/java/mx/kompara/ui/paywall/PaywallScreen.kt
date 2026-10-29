package mx.kompara.ui.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.billing.*
import mx.kompara.ui.BuildConfig
import mx.kompara.ui.auth.SignupFlowScreen
import mx.kompara.ui.components.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

@Composable
fun PaywallScreen(surface: GateSurface = GateSurface.GENERIC, onClose: () -> Unit = {},
    onPlan: () -> Unit = {}, purchaseExplanation: Boolean = false, viewModel: PaywallViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val entitlement by viewModel.entitlement.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var auth by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(surface) { viewModel.onOpened(surface) }
    LaunchedEffect(message) {
        if (message == PaywallMessage.SignIn) { auth = true; viewModel.consumeMessage() }
        if (message == PaywallMessage.OfferChanged) viewModel.loadOffer()
    }
    LaunchedEffect(entitlement, plan) {
        if ((!purchaseExplanation && entitlement is Entitlement.Premium) || plan.payment == PaymentStatus.PENDING || plan.hasSubscription) onPlan()
    }
    if (auth) {
        SignupFlowScreen(onComplete = { auth = false; viewModel.loadOffer() }, onCancel = { auth = false }, premium = true)
    } else PaywallContent(state, onClose = onClose,
        onPurchase = { context.findActivity()?.let { viewModel.purchase(it, allowTrial = !purchaseExplanation) } },
        onRetry = viewModel::loadOffer, onRestore = viewModel::restore,
        onTerms = { openPlanLink(context, BuildConfig.PREMIUM_TERMS_URL) },
        onPrivacy = { openPlanLink(context, BuildConfig.PREMIUM_PRIVACY_URL) },
        legalAvailable = BuildConfig.PREMIUM_TERMS_URL.isNotEmpty() && BuildConfig.PREMIUM_PRIVACY_URL.isNotEmpty(),
        busy = busy, message = message?.text(), purchaseExplanation = purchaseExplanation)
}

@Composable
fun PaywallContent(state: PaywallUiState, onClose: () -> Unit = {}, onPurchase: () -> Unit = {},
    onRetry: () -> Unit = {}, onRestore: () -> Unit = {}, onTerms: () -> Unit = {}, onPrivacy: () -> Unit = {},
    legalAvailable: Boolean = false, busy: Boolean = false, message: String? = null,
    purchaseExplanation: Boolean = false, bankedDays: Int? = null, reminderDate: String? = null, onReminder: () -> Unit = {}) {
    val ready = state as? PaywallUiState.Ready
    ScreenScaffold(if (purchaseExplanation) "Tu plan" else "Premium", kind = if (purchaseExplanation) ScreenKind.DRILL else ScreenKind.TASK,
        onNavigateUp = onClose, footer = {
            if (message != null) Text(message, Modifier.padding(bottom = 12.dp), style = KomparaType.small)
            if (ready != null && legalAvailable) {
                if (!purchaseExplanation) Text(if (ready.promotional) buildAnnotatedString {
                    append("14 días gratis, sin tarjeta. Después, ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(ready.formattedPrice) }
                    append(" al mes.")
                } else AnnotatedString("Se renueva cada mes hasta que canceles en Google Play"), Modifier.padding(bottom = 16.dp), style = KomparaType.small)
                PrimaryButton(if (!purchaseExplanation && ready.promotional) "Empezar mi prueba" else "Suscribirme por ${ready.formattedPrice} al mes", onPurchase, enabled = !busy)
                if (purchaseExplanation && reminderDate != null) PlanTextAction("Mejor avísame el $reminderDate", onReminder)
            }
            if (legalAvailable) LegalLinks(onTerms, onPrivacy, if (purchaseExplanation) onRestore else null)
        }) {
        Spacer(Modifier.height(24.dp))
        when (state) {
            PaywallUiState.Loading -> Text("Consultando tu plan…", style = KomparaType.body)
            PaywallUiState.NoResult -> {
                Text("Tus datos siguen gratis", style = KomparaType.headline)
                Text("Por ahora no hay un resultado de Premium para mostrarte.", Modifier.padding(top = 8.dp), style = KomparaType.body)
            }
            PaywallUiState.PlayUnavailable -> {
                Text("Google Play no está disponible", style = KomparaType.headline)
                Text("Puedes seguir usando tu semáforo y tus registros.", Modifier.padding(top = 8.dp), style = KomparaType.body)
                PlanTextAction("Reintentar", onRetry)
            }
            PaywallUiState.Error -> { Text("No pudimos consultar tu plan.", style = KomparaType.headline); PlanTextAction("Reintentar", onRetry) }
            is PaywallUiState.Ready -> {
                Text(if (purchaseExplanation) "Google Play cobra hoy" else state.evidence.headline, style = KomparaType.headline)
                if (purchaseExplanation || state.evidence.ownAmount != null) {
                    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (purchaseExplanation) state.formattedPrice else state.evidence.ownAmount!!, style = KomparaType.number)
                        Text(if (purchaseExplanation) "al mes" else "antes de gastos", Modifier.padding(top = 10.dp), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
                    }
                }
                if (purchaseExplanation) {
                    Text("Se renueva cada mes. Cancelas en Google Play cuando quieras; sigues hasta el fin del mes pagado.", Modifier.padding(top = 16.dp), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
                    bankedDays?.let { Text(buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = KomparaTheme.colors.ink)) { append("Tus $it días gratis quedan guardados.") }
                        append(" Se usan al terminar tu acceso pagado y, si aplica, el periodo de gracia.")
                    }, Modifier.padding(top = 16.dp), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary) }
                } else {
                    if (state.evidence.ownAmount != null) Text("¿Y a los demás?", Modifier.padding(top = 32.dp, bottom = 4.dp), style = KomparaType.section)
                    state.evidence.benefits.forEach { Text(it, Modifier.padding(top = 12.dp), style = KomparaType.body) }
                }
                if (!legalAvailable) Text("La suscripción aún no está disponible aquí. Puedes seguir usando Kompara gratis.", Modifier.padding(top = 32.dp), style = KomparaType.body)
            }
        }
    }
}
@Composable internal fun PlanTextAction(text: String, action: () -> Unit, enabled: Boolean = true) {
    TextButton(action, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled) {
        Text(text, style = KomparaType.bodyStrong, color = if (enabled) KomparaTheme.colors.ink else KomparaTheme.colors.inkTertiary)
    }
}
@Composable internal fun LegalLinks(terms: () -> Unit, privacy: () -> Unit, restore: (() -> Unit)? = null) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        if (restore != null) TextButton(restore, Modifier.heightIn(min = 48.dp)) { Text("Restaurar compra", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
        TextButton(terms, Modifier.heightIn(min = 48.dp)) { Text("Términos", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
        Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) { Text("·", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
        TextButton(privacy, Modifier.heightIn(min = 48.dp)) { Text("Privacidad", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
    }
}
internal fun openPlanLink(context: Context, url: String) {
    if (url.isBlank()) return
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    catch (_: Exception) { Toast.makeText(context, "No pudimos abrir el enlace.", Toast.LENGTH_LONG).show() }
}
internal tailrec fun Context.findActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.findActivity(); else -> null }
private fun PaywallMessage.text() = when (this) {
    PaywallMessage.Restored -> "Compra restaurada"
    PaywallMessage.NothingToRestore -> "No encontramos una compra activa."
    PaywallMessage.AlreadyPremium -> "Revisa tu acceso en Tu plan."
    PaywallMessage.LaunchFailed -> "No pudimos completar la solicitud. Inténtalo otra vez."
    PaywallMessage.RestoreFailed -> "No pudimos consultar tu compra. Conservas el acceso guardado."
    PaywallMessage.OfferChanged -> "La oferta cambió. Revísala antes de continuar."
    PaywallMessage.SignIn -> "Confirma tu WhatsApp"
}
