package mx.kompara.ui.nav

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import mx.kompara.ui.theme.KomparaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
class NavigationHeaderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun `root gear and drill back clear side cutouts at large text and the root has no share`() {
        var settings = 0; var back = 0
        lateinit var view: android.view.View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                KomparaTheme { Box(Modifier.width(393.dp)) { Column {
                    NavigationHeader("IMSS", null, { settings++ })
                    NavigationHeader("Ajustes", { back++ }, null)
                } } }
            }
        }
        compose.runOnIdle {
            ViewCompat.dispatchApplyWindowInsets(view, WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 24, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 28, 0))
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(44, 0, 0, 0))
                .build())
        }
        compose.onNodeWithContentDescription("Compartir", substring = true).assertDoesNotExist()
        for (label in listOf("Volver", "Ajustes")) {
            val node = compose.onNodeWithContentDescription(label)
            node.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$label clears left cutout", bounds.left >= 44f)
            assertTrue("$label clears right cutout", bounds.right <= 393f - 28f)
            assertTrue("$label clears status bar", bounds.top >= 24f)
        }
        compose.onNodeWithContentDescription("Ajustes").performClick()
        compose.onNodeWithContentDescription("Volver").performClick()
        assertEquals(1, settings); assertEquals(1, back)
    }
}
