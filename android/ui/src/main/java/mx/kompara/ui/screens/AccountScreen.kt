package mx.kompara.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.data.model.City
import mx.kompara.ui.auth.MxPhone
import mx.kompara.ui.components.*
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

@Composable
fun AccountScreen(onBack: () -> Unit = {}, onSignIn: () -> Unit = {}, onHelp: () -> Unit = {},
    viewModel: AccountViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AccountContent(state, onBack, viewModel::open, viewModel::close, viewModel::chooseCity,
        viewModel::confirmCity, viewModel::name, viewModel::saveName, viewModel::logout, onSignIn, onHelp, viewModel::retry, supportAvailable = PrivacyLinks.support != null)
}

@Composable
internal fun AccountContent(state: AccountUiState, onBack: () -> Unit = {}, open: (String) -> Unit = {},
    close: () -> Unit = {}, chooseCity: (City?) -> Unit = {}, confirmCity: () -> Unit = {},
    changeName: (String) -> Unit = {}, saveName: () -> Unit = {}, logout: () -> Unit = {},
    signIn: () -> Unit = {}, help: () -> Unit = {}, retry: () -> Unit = {}, supportAvailable: Boolean = false) {
    ScreenScaffold("Tu perfil", kind = ScreenKind.DRILL, onNavigateUp = onBack,
        footer = { TextButton(if (state.phone.isNotEmpty()) ({ open("logout") }) else signIn,
            Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = state.loaded && !state.busy) {
            Text(if (state.phone.isNotEmpty()) "Cerrar sesión" else "Entrar con WhatsApp", style = KomparaType.bodyStrong, color = KomparaTheme.colors.inkSecondary)
        } }) {
        Spacer(Modifier.height(24.dp))
        if (!state.loaded) SettingsLoading(state.error, retry) else {
            FlowRow(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    Text("Tu WhatsApp", style = KomparaType.body)
                    if (supportAvailable) Text("Para cambiarlo, escríbenos", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
                }
                Text(if (state.phone.isEmpty()) "Sin agregar" else MxPhone.display(state.phone),
                    Modifier.padding(start = 12.dp), style = KomparaType.bodyStrong)
            }
            Rule()
            SettingsValueRow("Tu nombre", state.name.ifEmpty { "Opcional" }, onClick = { if (state.phone.isEmpty()) signIn() else open("name") })
            SettingsValueRow("Tu ciudad", state.city.displayName + (state.citySource?.let { " · $it" } ?: ""), onClick = { open("city") })
            state.message?.let { Text(it, Modifier.padding(top = 20.dp), style = KomparaType.body) }
            state.error?.let { InkNotice(it) }
            if (state.busy) Text("Un momento…", Modifier.padding(top = 16.dp), style = KomparaType.body)
        }
    }
    if (state.sheet == "city") SheetScaffold("Tu ciudad", close) {
        ProfileCityChoices(state.city, state.busy, chooseCity)
        state.error?.let { InkNotice(it) }
    }
    if (state.sheet == "name") SheetScaffold("Tu nombre", close) {
        KomparaTextField(state.nameDraft, changeName, label = "Opcional", enabled = !state.busy, singleLine = true)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Guardar", saveName, enabled = !state.busy && state.nameDraft.trim().length in 1..100 && state.nameDraft.trim() != state.name)
        state.error?.let { InkNotice(it) }
    }
    state.pendingCity?.let { city -> SettingsConfirmation("¿Cambiar a ${city.displayName}?", cityConfirmation(state, city),
        "Cambiar", { chooseCity(null) }, confirmCity, !state.busy) }
    if (state.sheet == "logout") SettingsConfirmation("¿Cerrar sesión?",
        "Tu configuración y tus totales quedan en este teléfono.", "Cerrar sesión", close, logout, !state.busy)
}

@Composable
internal fun ProfileCityChoices(selected: City, busy: Boolean = false, chooseCity: (City) -> Unit = {}) {
    val cities = listOf(City.CDMX, City.GUADALAJARA, City.MONTERREY, City.PUEBLA) + City.entries.filter { it !in listOf(City.CDMX, City.GUADALAJARA, City.MONTERREY, City.PUEBLA) }
    cities.forEach { city ->
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = !busy) { if (city != selected) chooseCity(city) },
            verticalAlignment = Alignment.CenterVertically) {
            Text(city.displayName, Modifier.weight(1f).padding(vertical = 12.dp), style = KomparaType.body)
            if (city == selected) StatusIcon(StatusGlyph.DONE)
        }
        Rule()
    }
}
