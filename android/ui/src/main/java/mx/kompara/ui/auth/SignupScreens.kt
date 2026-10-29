package mx.kompara.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.components.*
import mx.kompara.ui.onboarding.OnboardingFrame
import mx.kompara.ui.onboarding.OnboardingTextAction
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

@Composable
fun SignupFlowScreen(onComplete: () -> Unit, modifier: Modifier = Modifier, onCancel: () -> Unit = onComplete,
    viewModel: SignupViewModel = hiltViewModel(), premium: Boolean = false) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    // The destination's ViewModel survives rotation, including an OTP request already sent.
    // Explicit navigation cancels below; removing the destination clears its ViewModel scope.
    LaunchedEffect(viewModel) { viewModel.resume() }
    val cancel = { viewModel.cancel(); keyboard?.hide(); onCancel() }
    val back = { if (state.step == SignupStep.CODE) viewModel.changePhone() else cancel() }
    BackHandler { back() }
    if (state.step == SignupStep.DONE) {
        LaunchedEffect(Unit) { keyboard?.hide(); onComplete() }
    } else SignupContent(state, viewModel::onPhoneChange, viewModel::onCodeChange, viewModel::submitPhone,
        viewModel::submitCode, viewModel::resendCode, viewModel::changePhone, back, cancel, modifier, premium = premium)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignupContent(state: SignupUiState, phoneChange: (String) -> Unit = {}, codeChange: (String) -> Unit = {},
    send: () -> Unit = {}, verify: () -> Unit = {}, resend: () -> Unit = {}, changePhone: () -> Unit = {},
    back: () -> Unit = {}, cancel: () -> Unit = {}, modifier: Modifier = Modifier, requestFocus: Boolean = true, premium: Boolean = false) {
    val code = state.step == SignupStep.CODE
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(code, requestFocus) { if (requestFocus) { focus.requestFocus(); keyboard?.show() } }
    OnboardingFrame(back, modifier = modifier, title = if (premium) "Premium" else null, close = premium, footer = {
        if (!code) PrimaryButton(if (state.error == SignupError.REQUEST_FAILED) "Reintentar" else "Mandar código", send, enabled = state.canSubmitPhone)
        else if (state.error != null) PrimaryButton("Reintentar", if (state.error == SignupError.REQUEST_FAILED) resend else verify,
            enabled = if (state.error == SignupError.REQUEST_FAILED) !state.busy && state.resendSecondsLeft == 0 else state.canSubmitCode)
        if (premium && !code) OnboardingTextAction("Ahora no", cancel)
        if (state.error == SignupError.REQUEST_FAILED || state.error == SignupError.VERIFY_FAILED) OnboardingTextAction("Seguir sin cuenta por ahora", cancel)
    }) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(if (code) "Escribe el código" else if (premium) "Confirma tu WhatsApp" else "Entra con tu WhatsApp", style = KomparaType.headline)
            Text(if (code) "Te lo mandamos por WhatsApp al ${state.phoneE164?.let(MxPhone::display) ?: state.phoneInput}."
                else if (premium) "Una prueba por conductor.\nDespués eliges cuándo empieza." else "El semáforo y tus registros funcionan sin cuenta.", Modifier.padding(top = 8.dp), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
            Spacer(Modifier.height(32.dp))
            if (code) {
                KomparaOtpInput(state.codeInput, codeChange, Modifier.focusRequester(focus), error = state.error == SignupError.WRONG_CODE)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(changePhone, Modifier.heightIn(min = 48.dp), enabled = !state.busy) { Text("Cambiar número", style = KomparaType.bodyStrong, color = KomparaTheme.colors.ink) }
                    TextButton(resend, Modifier.heightIn(min = 48.dp), enabled = !state.busy && state.resendSecondsLeft == 0) {
                        Text(if (state.resendSecondsLeft > 0) "Reenviar en ${state.resendSecondsLeft} s" else "Reenviar código", style = KomparaType.bodyStrong, color = if (state.resendSecondsLeft > 0 || state.busy) KomparaTheme.colors.inkTertiary else KomparaTheme.colors.ink)
                    }
                }
                state.devCodeHint?.let { Text("Prueba local: $it", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
            } else {
                KomparaTextField(state.phoneInput, phoneChange, if (premium) "Número de WhatsApp" else "Tu número", Modifier.focusRequester(focus), prefix = "+52",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), enabled = !state.busy, underlined = true)
                if (!premium) Text("No lo mostramos a otros conductores.", Modifier.padding(top = 12.dp), style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
            }
            val error = when (state.error) {
                SignupError.INVALID_PHONE -> "Revisa tu número: debe tener 10 dígitos."
                SignupError.REQUEST_FAILED -> "No pudimos mandar el código. Inténtalo otra vez."
                SignupError.WRONG_CODE -> "El código no es válido o ya venció."
                SignupError.VERIFY_FAILED -> "No pudimos verificar el código. Inténtalo otra vez."
                null -> null
            }
            if (error != null) Text(error, Modifier.padding(top = 12.dp), style = KomparaType.body)
            if (state.busy) Text(if (code) "Verificando…" else "Mandando código…", Modifier.padding(top = 12.dp), style = KomparaType.body)
        }
    }
}
