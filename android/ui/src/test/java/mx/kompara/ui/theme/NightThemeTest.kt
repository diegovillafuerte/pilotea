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
import androidx.compose.ui.platform.testTag
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

    @Test fun `the system's night mode recomposes existing content without changing state`() {
        var systemNight by mutableStateOf(false)
        var observed: KomparaColors? = null
        var material: ColorScheme? = null
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (systemNight) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                // The theme follows the system (docs/v4-simple-launch-plan.md §3); there is no stored choice.
                KomparaTheme {
                    KomparaSystemBars(compose.activity.window)
                    var on by remember { mutableStateOf(false) }
                    val colors = KomparaTheme.colors
                    val scheme = MaterialTheme.colorScheme
                    SideEffect { observed = colors; material = scheme }
                    Surface { Column {
                        KomparaSwitch(on, { on = it }, Modifier.testTag("switch"))
                        PrimaryButton("Guardar", {})
                        RootBottomBar(0, {})
                    } }
                }
            }
        }
        compose.onNodeWithTag("switch").performClick()
        for ((system, expected) in listOf(false to DayColors, true to NightColors, false to DayColors, true to NightColors)) {
            compose.runOnIdle { systemNight = system }
            compose.onNodeWithTag("switch").assertIsOn()
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
            assertEquals(Color(0xFF58C47A), colors.kOnDark)
        }
        assertEquals(Color(0xFF3FB565), NightColors.activeNavigation)
        assertNotEquals(NightColors.brand, NightColors.activeNavigation)
    }
}
