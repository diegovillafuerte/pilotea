package mx.kompara.ui.fixtures

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import mx.kompara.data.model.City
import mx.kompara.ui.components.*
import mx.kompara.ui.screens.*
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme

/** Reference identities, configured destinations and city suggestions live only in debug fixtures. */
@Composable
fun PrivacyFixtureHost(frame: String) {
    val fixture = V3Fixture(LocalContext.current)
    val profile = AccountUiState(loaded = true, phone = "+525512345678", citySource = "por tu número",
        goal = "$${fixture.goal}/km", minimum = "$${fixture.minimum}/km", suggestedGoal = "$7.70/km")
    when (frame) {
        "G8" -> HelpContent(initialAnswer = "semaforo", supportAvailable = true)
        "support-gated" -> HelpContent(initialAnswer = "colores")
        "profile" -> AccountContent(profile, supportAvailable = true)
        "G9" -> Box(Modifier.fillMaxSize()) {
            AccountContent(profile, supportAvailable = true)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)))
            Surface(Modifier.align(Alignment.BottomCenter).heightIn(max = 360.dp), color = KomparaTheme.colors.paper,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
                SheetContent("Tu ciudad") { ProfileCityChoices(City.CDMX) }
            }
            SettingsConfirmation("¿Cambiar a Monterrey?", cityConfirmation(profile, City.MONTERREY), "Cambiar", {}, {})
        }
        else -> TusDatosContent(TusDatosState(loaded = true, signedIn = true, contributionAvailable = true,
            result = if (frame == "deleted") "Cuenta eliminada" else null,
            error = if (frame == "delete-error") "No pudimos eliminarla. Inténtalo otra vez." else null),
            externalDeletion = "https://kompara.mx/borrar", initialDialog = if (frame == "G6b") "account" else null)
    }
}
