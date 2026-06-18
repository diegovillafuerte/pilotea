package mx.kompara.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.data.model.City
import mx.kompara.ui.R
import mx.kompara.ui.components.KomparaOtpInput
import mx.kompara.ui.components.KomparaTextField
import mx.kompara.ui.components.PrimaryButton

/**
 * The required signup flow (phone → WhatsApp code → profile), one composable per step with the
 * step switch driven by [SignupViewModel]. Lives both inside the onboarding funnel (new installs)
 * and standalone at the app root (installs that completed onboarding before accounts existed).
 */
@Composable
fun SignupFlowScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (state.step) {
        SignupStep.PHONE -> PhoneStep(state, viewModel, modifier)
        SignupStep.CODE -> CodeStep(state, viewModel, modifier)
        SignupStep.PROFILE -> ProfileStep(state, viewModel, onComplete, modifier)
    }
}

@Composable
private fun PhoneStep(state: SignupUiState, viewModel: SignupViewModel, modifier: Modifier = Modifier) {
    StepScaffold(
        title = stringResource(R.string.auth_phone_titulo),
        body = stringResource(R.string.auth_phone_cuerpo),
        modifier = modifier,
        content = {
            KomparaTextField(
                value = state.phoneInput,
                onValueChange = viewModel::onPhoneChange,
                label = stringResource(R.string.auth_phone_hint),
                prefix = stringResource(R.string.auth_phone_prefijo),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                error = if (state.error == SignupError.INVALID_PHONE) errorMessage(state.error) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            ErrorText(state.error, ignore = SignupError.INVALID_PHONE)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.auth_phone_privacidad),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        footer = {
            PrimaryButton(
                text = stringResource(R.string.auth_phone_cta),
                onClick = viewModel::submitPhone,
                enabled = state.canSubmitPhone,
            )
        },
    )
}

@Composable
private fun CodeStep(state: SignupUiState, viewModel: SignupViewModel, modifier: Modifier = Modifier) {
    val phoneShown = state.phoneE164?.let(MxPhone::display) ?: state.phoneInput
    StepScaffold(
        title = stringResource(R.string.auth_code_titulo),
        body = stringResource(R.string.auth_code_cuerpo, phoneShown),
        modifier = modifier,
        content = {
            KomparaOtpInput(
                value = state.codeInput,
                onValueChange = viewModel::onCodeChange,
                error = state.error == SignupError.WRONG_CODE,
            )
            ErrorText(state.error)
            state.devCodeHint?.let { devCode ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.auth_code_dev_hint, devCode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = viewModel::changePhone) {
                    Text(stringResource(R.string.auth_code_cambiar))
                }
                TextButton(
                    onClick = viewModel::resendCode,
                    enabled = state.resendSecondsLeft == 0 && !state.busy,
                ) {
                    Text(
                        text = if (state.resendSecondsLeft > 0) {
                            stringResource(R.string.auth_code_reenviar_en, state.resendSecondsLeft)
                        } else {
                            stringResource(R.string.auth_code_reenviar)
                        },
                    )
                }
            }
        },
        footer = {
            PrimaryButton(
                text = stringResource(R.string.auth_code_cta),
                onClick = viewModel::submitCode,
                enabled = state.canSubmitCode,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileStep(
    state: SignupUiState,
    viewModel: SignupViewModel,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StepScaffold(
        title = stringResource(R.string.auth_profile_titulo),
        body = stringResource(R.string.auth_profile_cuerpo),
        modifier = modifier,
        content = {
            KomparaTextField(
                value = state.nameInput,
                onValueChange = viewModel::onNameChange,
                label = stringResource(R.string.auth_profile_nombre_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            var cityMenuOpen by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = cityMenuOpen,
                onExpandedChange = { cityMenuOpen = it },
            ) {
                OutlinedTextField(
                    value = state.city.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.auth_profile_ciudad_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityMenuOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = cityMenuOpen,
                    onDismissRequest = { cityMenuOpen = false },
                ) {
                    City.entries.forEach { city ->
                        DropdownMenuItem(
                            text = { Text(city.displayName) },
                            onClick = {
                                viewModel.onCityChange(city)
                                cityMenuOpen = false
                            },
                        )
                    }
                }
            }
            ErrorText(state.error)
        },
        footer = {
            PrimaryButton(
                text = stringResource(R.string.auth_profile_cta),
                onClick = { viewModel.saveProfile(onComplete) },
                enabled = !state.busy,
            )
            TextButton(
                onClick = { viewModel.skipProfile(onComplete) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.auth_profile_saltar))
            }
        },
    )
}

/**
 * Shared step layout: left-aligned title + body and the step's own fields scroll in the upper
 * region; the primary CTA (and any secondary action) is pinned to a fixed [footer] at the bottom so
 * it never scrolls away under the keyboard.
 */
@Composable
private fun StepScaffold(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
    footer: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            content()
        }
        Spacer(Modifier.height(16.dp))
        footer()
    }
}

/** The user-facing copy for each [SignupError], or null when there is no error. */
@Composable
private fun errorMessage(error: SignupError?): String? = when (error) {
    null -> null
    SignupError.INVALID_PHONE -> stringResource(R.string.auth_phone_error_invalido)
    SignupError.REQUEST_FAILED -> stringResource(R.string.auth_phone_error_red)
    SignupError.WRONG_CODE -> stringResource(R.string.auth_code_error_invalido)
    SignupError.VERIFY_FAILED -> stringResource(R.string.auth_code_error_red)
    SignupError.PROFILE_SAVE_FAILED -> stringResource(R.string.auth_profile_error)
}

/**
 * Renders the error message below a step's fields. [ignore] is the one error already surfaced inline
 * by a field (its red border + helper text), so it isn't repeated here.
 */
@Composable
private fun ErrorText(error: SignupError?, ignore: SignupError? = null) {
    if (error == null || error == ignore) return
    Spacer(Modifier.height(8.dp))
    Text(
        text = errorMessage(error)!!,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
