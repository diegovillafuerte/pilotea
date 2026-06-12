package mx.kompara.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.data.model.City
import mx.kompara.ui.R
import mx.kompara.ui.components.PrimaryButton
import mx.kompara.ui.stats.AccountError
import mx.kompara.ui.stats.AccountUiState
import mx.kompara.ui.stats.AccountViewModel
import mx.kompara.ui.theme.KomparaTheme

/**
 * "Tu cuenta" (B-069): the mirror surface for the required signup. Shows the read-only WhatsApp
 * phone, lets the driver edit name + benchmark city (PATCH /v1/me), and exposes cerrar-sesión and
 * permanent account deletion (Play data-safety). Logout/delete clear local auth, which flips the
 * app root back to the signup gate — so no navigation is wired here for those.
 */
@Composable
fun AccountScreen(
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AccountContent(
        state = state,
        modifier = modifier,
        onNameChange = viewModel::onNameChange,
        onCityChange = viewModel::onCityChange,
        onSave = viewModel::save,
        onLogout = viewModel::logout,
        onDelete = viewModel::deleteAccount,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountContent(
    state: AccountUiState,
    modifier: Modifier = Modifier,
    onNameChange: (String) -> Unit = {},
    onCityChange: (City) -> Unit = {},
    onSave: () -> Unit = {},
    onLogout: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.account_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        OutlinedTextField(
            value = state.phone,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(stringResource(R.string.account_phone_label)) },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.nameInput,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.account_name_label)) },
            singleLine = true,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )

        var cityMenuOpen by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = cityMenuOpen,
            onExpandedChange = { if (!state.busy) cityMenuOpen = it },
        ) {
            OutlinedTextField(
                value = state.city.displayName,
                onValueChange = {},
                readOnly = true,
                enabled = !state.busy,
                label = { Text(stringResource(R.string.account_city_label)) },
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
                            onCityChange(city)
                            cityMenuOpen = false
                        },
                    )
                }
            }
        }

        if (state.saved) {
            Text(
                text = stringResource(R.string.account_saved),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        FeedbackText(state.error)

        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            text = stringResource(R.string.account_save_cta),
            onClick = onSave,
            enabled = !state.busy,
        )
        TextButton(
            onClick = onLogout,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.account_logout_cta))
        }
        TextButton(
            onClick = { confirmDelete = true },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.account_delete_cta),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.account_delete_dialog_title)) },
            text = { Text(stringResource(R.string.account_delete_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text(
                        text = stringResource(R.string.account_delete_dialog_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.account_delete_dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun FeedbackText(error: AccountError?) {
    val message = when (error) {
        null -> return
        AccountError.SAVE_FAILED -> stringResource(R.string.account_error_save)
        AccountError.DELETE_FAILED -> stringResource(R.string.account_error_delete)
    }
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

@Preview(showBackground = true, name = "Tu cuenta")
@Composable
private fun AccountScreenPreview() {
    KomparaTheme {
        AccountContent(
            state = AccountUiState(phone = "+5215512345678", nameInput = "Ana", city = City.CDMX),
        )
    }
}
