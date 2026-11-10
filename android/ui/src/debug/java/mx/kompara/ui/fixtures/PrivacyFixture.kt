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
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.ui.components.*
import mx.kompara.ui.screens.*
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme

/** Tu perfil (G9 and its profile state); Tus datos and Ayuda are J5 and J6 in [SettingsFixture]. */
@Composable
fun PrivacyFixtureHost(frame: String) {
    val fixture = V3Fixture(LocalContext.current)
    val profile = AccountUiState(loaded = true, phone = "+525512345678", citySource = "por tu número",
        limits = ThresholdEditor.summary(LimitDraft(LimitPair(fixture.goal, fixture.minimum), LimitPair("200", "150"), PreferredMetric.IPK)))
    when (frame) {
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
    }
}
