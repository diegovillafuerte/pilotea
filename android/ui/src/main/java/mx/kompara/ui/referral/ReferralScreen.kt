package mx.kompara.ui.referral

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.auth.SignupFlowScreen
import mx.kompara.ui.components.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

/** Reward promises require the service's current terms, never a client constant or earned-days average. */
fun referralRewardText(referrer: Int?, redeemer: Int?): String? = when {
    referrer == null || redeemer == null || referrer <= 0 || redeemer <= 0 -> null
    referrer == redeemer -> "Por cada conductor que entre con tu código, los dos ganan $referrer días de Premium"
    else -> "Por cada conductor que entre con tu código, ganas $referrer días de Premium y esa persona gana $redeemer días."
}
fun referralShareText(state: ReferralUiState.Ready): String =
    "Te invito a Kompara. Mi código es ${state.code}." +
        (referralRewardText(state.rewardDaysReferrer, state.rewardDaysRedeemer)?.let { " $it." } ?: "")

@Composable
fun ReferralScreen(onClose: () -> Unit, modifier: Modifier = Modifier, viewModel: ReferralViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var signingIn by rememberSaveable { mutableStateOf(false) }
    if (signingIn) SignupFlowScreen(onComplete = { signingIn = false; viewModel.load() }, onCancel = { signingIn = false })
    else ReferralContent(state, onClose, modifier, signIn = { signingIn = true }, retry = viewModel::load,
        toggle = viewModel::toggleRedemption, input = viewModel::onRedeemInputChange, redeem = viewModel::redeem,
        copy = { (state as? ReferralUiState.Ready)?.let {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("Tu código", it.code))
            Toast.makeText(context, "Código copiado", Toast.LENGTH_SHORT).show()
        } }, share = { (state as? ReferralUiState.Ready)?.let { shareInvite(context, referralShareText(it)) } })
}
@Composable
fun ReferralContent(state: ReferralUiState, back: () -> Unit = {}, modifier: Modifier = Modifier,
    signIn: () -> Unit = {}, retry: () -> Unit = {}, toggle: () -> Unit = {}, input: (String) -> Unit = {},
    redeem: () -> Unit = {}, copy: () -> Unit = {}, share: () -> Unit = {}) {
    ScreenScaffold("Invita y gana", modifier, ScreenKind.DRILL, back, footer = {
        if (state is ReferralUiState.Ready) {
            PrimaryButton("Compartir por WhatsApp", share)
            ReferralTextAction("Copiar código", copy)
        } else if (state == ReferralUiState.SignedOut) PrimaryButton("Entrar con WhatsApp", signIn)
    }) {
        Spacer(Modifier.height(24.dp))
        when (state) {
            ReferralUiState.Loading -> Text("Consultando tu código…", style = KomparaType.body)
            ReferralUiState.SignedOut -> {
                Text("Entra para ver tu código", style = KomparaType.headline)
                Text("Tu semáforo y tus registros siguen disponibles sin cuenta.", Modifier.padding(top = 8.dp), style = KomparaType.body)
            }
            is ReferralUiState.LoadError -> { Text(state.message, style = KomparaType.body); ReferralTextAction("Reintentar", retry) }
            is ReferralUiState.Ready -> {
                Text("Tu código", style = KomparaType.headline)
                Text(state.code, Modifier.padding(top = 8.dp), style = KomparaType.number)
                val reward = referralRewardText(state.rewardDaysReferrer, state.rewardDaysRedeemer) ?: "Tu código para invitar a otros conductores."
                Text(buildAnnotatedString {
                    val emphasis = reward.indexOf("los dos ganan")
                    if (emphasis < 0) append(reward) else {
                        append(reward.substring(0, emphasis))
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(reward.substring(emphasis)) }
                    }
                }, Modifier.padding(top = 16.dp), style = KomparaType.body)
                Text("${state.redemptionsCount} invitaciones · ${state.premiumDaysEarned} días de Premium ganados", Modifier.padding(top = 32.dp, bottom = 24.dp), style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
                Rule()
                ListRow("¿Te invitaron? Escribe su código", onClick = toggle)
                Rule()
                if (state.expanded) {
                    Spacer(Modifier.height(16.dp))
                    KomparaTextField(state.redeemInput, input, "Código de invitación", enabled = state.redeem !is RedeemPhase.Submitting && state.redeem !is RedeemPhase.Success)
                    when (val phase = state.redeem) {
                        is RedeemPhase.Error -> Text(phase.message, Modifier.padding(top = 12.dp), style = KomparaType.body)
                        is RedeemPhase.Success -> Text("Se agregaron ${phase.grantedDays} días de Premium a tu cuenta.", Modifier.padding(top = 12.dp), style = KomparaType.body)
                        else -> Unit
                    }
                    if (state.redeem !is RedeemPhase.Success) ReferralTextAction(if (state.redeem is RedeemPhase.Submitting) "Canjeando…" else "Canjear código", redeem,
                        state.redeemInput.isNotBlank() && state.redeem !is RedeemPhase.Submitting)
                }
            }
        }
    }
}
@Composable private fun ReferralTextAction(text: String, action: () -> Unit, enabled: Boolean = true) {
    TextButton(action, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled) {
        Text(text, style = KomparaType.bodyStrong, color = if (enabled) KomparaTheme.colors.ink else KomparaTheme.colors.inkTertiary)
    }
}
private fun shareInvite(context: Context, message: String) {
    val base = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message) }
    try { context.startActivity(Intent(base).setPackage("com.whatsapp")) }
    catch (_: Exception) {
        try { context.startActivity(Intent.createChooser(base, "Compartir invitación")) }
        catch (_: Exception) { Toast.makeText(context, "No pudimos abrir una app para compartir.", Toast.LENGTH_LONG).show() }
    }
}
