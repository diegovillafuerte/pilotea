package mx.kompara.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.data.model.City
import mx.kompara.ui.R
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
    ) {
        OutlinedTextField(
            value = state.phoneInput,
            onValueChange = viewModel::onPhoneChange,
            label = { Text(stringResource(R.string.auth_phone_hint)) },
            prefix = { Text(stringResource(R.string.auth_phone_prefijo)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = state.error == SignupError.INVALID_PHONE,
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorText(state.error)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.auth_phone_privacidad),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = stringResource(R.string.auth_phone_cta),
            onClick = viewModel::submitPhone,
            enabled = state.canSubmitPhone,
        )
    }
}

@Composable
private fun CodeStep(state: SignupUiState, viewModel: SignupViewModel, modifier: Modifier = Modifier) {
    val phoneShown = state.phoneE164?.let(MxPhone::display) ?: state.phoneInput
    StepScaffold(
        title = stringResource(R.string.auth_code_titulo),
        body = stringResource(R.string.auth_code_cuerpo, phoneShown),
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = state.codeInput,
            onValueChange = viewModel::onCodeChange,
            label = { Text(stringResource(R.string.auth_code_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = state.error == SignupError.WRONG_CODE,
            modifier = Modifier.fillMaxWidth(),
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
        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = stringResource(R.string.auth_code_cta),
            onClick = viewModel::submitCode,
            enabled = state.canSubmitCode,
        )
    }
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
    ) {
        OutlinedTextField(
            value = state.nameInput,
            onValueChange = viewModel::onNameChange,
            label = { Text(stringResource(R.string.auth_profile_nombre_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

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
        Spacer(Modifier.height(24.dp))
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
    }
}

/** Shared step layout: icon, centered title + body, then the step's own content. */
@Composable
private fun StepScaffold(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        content()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ErrorText(error: SignupError?) {
    val message = when (error) {
        null -> return
        SignupError.INVALID_PHONE -> stringResource(R.string.auth_phone_error_invalido)
        SignupError.REQUEST_FAILED -> stringResource(R.string.auth_phone_error_red)
        SignupError.WRONG_CODE -> stringResource(R.string.auth_code_error_invalido)
        SignupError.VERIFY_FAILED -> stringResource(R.string.auth_code_error_red)
        SignupError.PROFILE_SAVE_FAILED -> stringResource(R.string.auth_profile_error)
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
