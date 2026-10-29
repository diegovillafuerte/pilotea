package mx.kompara.ui.theme

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import mx.kompara.data.settings.ThemePreference
import mx.kompara.ui.components.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
class NightThemeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun `system and persisted overrides recompose existing content without changing state`() {
        var preference by mutableStateOf(ThemePreference.AUTOMATIC)
        var systemNight by mutableStateOf(false)
        var observed: KomparaColors? = null
        var material: ColorScheme? = null
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (systemNight) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                KomparaTheme(preference = preference) {
                    KomparaSystemBars(compose.activity.window)
                    var selected by remember { mutableIntStateOf(0) }
                    val colors = KomparaTheme.colors
                    val scheme = MaterialTheme.colorScheme
                    SideEffect { observed = colors; material = scheme }
                    Surface { Column {
                        TextTabs(listOf("Semana", "Mes"), selected, { selected = it })
                        PrimaryButton("Guardar", {})
                        RootBottomBar(1, {})
                    } }
                }
            }
        }
        compose.onNodeWithText("Mes").performClick()
        for ((pref, system, expected) in listOf(
            Triple(ThemePreference.AUTOMATIC, false, DayColors), Triple(ThemePreference.AUTOMATIC, true, NightColors),
            Triple(ThemePreference.LIGHT, true, DayColors), Triple(ThemePreference.DARK, false, NightColors),
            Triple(ThemePreference.AUTOMATIC, false, DayColors), Triple(ThemePreference.DARK, true, NightColors))) {
            compose.runOnIdle { preference = pref; systemNight = system }
            compose.onNodeWithText("Mes").assertIsSelected()
            compose.onNodeWithText("Guardar").assertHeightIsAtLeast(56.dp)
            compose.onNodeWithText("Ganancias").assertIsSelected()
            compose.runOnIdle {
                assertEquals(expected, observed)
                val scheme = requireNotNull(material)
                assertEquals(Color(0xFF187A36), scheme.primary)
                assertEquals(Color.White, scheme.onPrimary)
                assertEquals(expected.ink, scheme.error)
                assertEquals(expected.paperSecondary, scheme.errorContainer)
                assertEquals(expected.paper, scheme.surface)
                assertEquals(expected.paperSecondary, scheme.surfaceContainer)
                assertEquals(expected.ink, scheme.onSurface)
                val window = compose.activity.window
                val bars = WindowCompat.getInsetsController(window, window.decorView)
                assertEquals(expected == DayColors, bars.isAppearanceLightStatusBars)
                assertEquals(expected == DayColors, bars.isAppearanceLightNavigationBars)
            }
        }
    }

    @Test fun `all semantic verdict and host edge colors are independent of app theme`() {
        for (colors in listOf(DayColors, NightColors)) {
            assertEquals(Color(0xFF187A36), colors.verde)
            assertEquals(Color(0xFFF2B705), colors.amarillo)
            assertEquals(Color(0xFFD32F2F), colors.rojo)
            assertEquals(Color(0xFF0E0F10), colors.chipInk)
            assertEquals(Color(0xFFEDEDEA), colors.onChip)
            assertEquals(Color(0xFF2A2C2F), colors.chipGrey)
            assertEquals(Color(0xFF2FB35A), colors.trayGreen)
            assertEquals(Color(0xFFF2B705), colors.trayYellow)
            assertEquals(Color(0xFFFF5C5C), colors.trayRed)
            assertEquals(Color.White.copy(alpha = .12f), colors.chipEdge)
            assertEquals(Color.White.copy(alpha = .20f), colors.chipGreyEdge)
        }
        assertEquals(Color(0xFF3FB565), NightColors.activeNavigation)
        assertNotEquals(NightColors.brand, NightColors.activeNavigation)
    }
}
