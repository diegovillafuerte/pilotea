package mx.kompara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.data.model.City
import mx.kompara.ui.R
import mx.kompara.ui.auth.MxPhone
import mx.kompara.ui.components.ButtonVariant
import mx.kompara.ui.components.KomparaButton
import mx.kompara.ui.components.KomparaTextField
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.account_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Fields block — design-system label-above filled inputs, 14dp gap (mock .fields).
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Read-only WhatsApp number: non-editable by design (PATCH /v1/me never updates it).
            // "+52" affordance via the prefix slot, with the local digits formatted for legibility
            // ("55 1234 5678"), mirroring the signup phone field.
            KomparaTextField(
                value = localPhoneDisplay(state.phone),
                onValueChange = {},
                label = stringResource(R.string.account_phone_label),
                prefix = stringResource(R.string.auth_phone_prefijo).trim(),
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
            )

            KomparaTextField(
                value = state.nameInput,
                onValueChange = onNameChange,
                label = stringResource(R.string.account_name_label),
                singleLine = true,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )

            CityPickerField(
                city = state.city,
                enabled = !state.busy,
                onCityChange = onCityChange,
            )
        }

        if (state.saved) {
            Text(
                text = stringResource(R.string.account_saved),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        FeedbackText(state.error)

        // Actions block — 10dp gap (mock .actions).
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                text = stringResource(R.string.account_save_cta),
                onClick = onSave,
                enabled = !state.busy,
            )
            KomparaButton(
                text = stringResource(R.string.account_logout_cta),
                onClick = onLogout,
                variant = ButtonVariant.SECONDARY,
                fullWidth = true,
                enabled = !state.busy,
            )
            // Delete: the unified TEXT button, full-width, in destructive red (UI-destructive,
            // distinct from the verdict semáforo) per the mock.
            KomparaButton(
                text = stringResource(R.string.account_delete_cta),
                onClick = { confirmDelete = true },
                variant = ButtonVariant.TEXT,
                fullWidth = true,
                enabled = !state.busy,
                contentColor = MaterialTheme.colorScheme.error,
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

/**
 * The local-formatted WhatsApp number for display under the "+52" prefix, e.g. "55 1234 5678".
 * [MxPhone.display] already includes the "+52 " country code, so we drop it here and let the field's
 * prefix slot carry it.
 */
private fun localPhoneDisplay(e164: String): String =
    MxPhone.display(e164).removePrefix("+52").trim()

/**
 * The benchmark-city control: a constrained 10-city picker (its key is a backend benchmark slug, so
 * it can NOT be free text) styled to read as a [KomparaTextField] — a 12sp/Medium muted label above
 * a 52dp surfaceContainer field with a 1.5dp outline and a trailing dropdown chevron.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CityPickerField(
    city: City,
    enabled: Boolean,
    onCityChange: (City) -> Unit,
) {
    var cityMenuOpen by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = cityMenuOpen,
        onExpandedChange = { if (enabled) cityMenuOpen = it },
        modifier = Modifier.alpha(if (enabled) 1f else 0.45f),
    ) {
        Column {
            val cityLabel = stringResource(R.string.account_city_label)
            Text(
                text = cityLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = enabled)
                    .semantics { contentDescription = "$cityLabel: ${city.displayName}" }
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = city.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ExposedDropdownMenu(
            expanded = cityMenuOpen,
            onDismissRequest = { cityMenuOpen = false },
        ) {
            City.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.displayName) },
                    onClick = {
                        onCityChange(entry)
                        cityMenuOpen = false
                    },
                )
            }
        }
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
