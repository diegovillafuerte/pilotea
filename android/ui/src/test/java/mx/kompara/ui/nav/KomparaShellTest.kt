package mx.kompara.ui.nav

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import mx.kompara.ui.contract.v4.V4Snapshot
import mx.kompara.ui.theme.KomparaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Drives the real shell — header, gear, bottom bar, root selection — over stand-in screens on its real routes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KomparaShellTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var nav: NavHostController

    @Composable private fun Shell(bodies: Boolean = true) {
        nav = rememberNavController()
        KomparaShell(nav) {
            composable("ganancias") {
                var week by rememberSaveable { mutableStateOf("2026-10-12") }
                if (bodies) Column(Modifier.padding(20.dp)) {
                    TextField(week, { week = it }, Modifier.testTag("period"))
                    Text("Abrir el lunes", Modifier.clickable { nav.navigate(KomparaDestination.dayRoute("2026-10-19", 1)) })
                }
            }
            composable("comparar") { if (bodies) Text("Pantalla Comparar") }
            composable("imss") { if (bodies) Text("Pantalla IMSS") }
            composable("ajustes") { Text("Semáforo", Modifier.padding(20.dp).clickable { nav.navigate(KomparaDestination.SEMAFORO.route) }) }
            composable("semaforo") { Text("Pantalla Semáforo") }
            composable("day/{day}?offerId={offerId}&ledger={ledger}", arguments = listOf(navArgument("day") { type = NavType.StringType },
                navArgument("offerId") { type = NavType.LongType; defaultValue = -1L }, navArgument("ledger") { type = NavType.BoolType; defaultValue = false })) {
                Text("Pantalla del día")
            }
        }
    }
    private fun tab(label: String) = compose.onNode(isSelectable() and hasText(label))
    private fun route() = compose.runOnIdle { nav.currentDestination?.route }

    @Test fun `the three tabs open their roots with title gear and the active tab`() {
        compose.setContent { KomparaTheme { Shell() } }
        assertEquals("ganancias", route())
        for ((label, body) in listOf("Comparar" to "Pantalla Comparar", "IMSS" to "Pantalla IMSS", "Ganancias" to null)) {
            tab(label).performClick().assertIsSelected()
            body?.let { compose.onNodeWithText(it).assertExists() }
            compose.onAllNodesWithText(label).assertCountEquals(2) // The root title and the tab.
            compose.onNodeWithContentDescription("Ajustes").assertHeightIsAtLeast(48.dp)
            compose.onNodeWithContentDescription("Volver").assertDoesNotExist()
            compose.onAllNodes(isSelectable()).assertCountEquals(3)
        }
        assertEquals("ganancias", route())
    }

    @Test fun `the gear opens Ajustes over the root it came from and back returns there`() {
        compose.setContent { KomparaTheme { Shell() } }
        tab("IMSS").performClick()
        compose.onNodeWithContentDescription("Ajustes").performClick()
        assertEquals("ajustes", route())
        compose.onAllNodes(isSelectable()).assertCountEquals(0)
        compose.onNodeWithContentDescription("Volver").performClick()
        assertEquals("imss", route())
        tab("IMSS").assertIsSelected()
        compose.onNodeWithContentDescription("Ajustes").performClick()
        compose.onNodeWithText("Semáforo").performClick()
        assertEquals("semaforo", route())
        compose.onNodeWithContentDescription("Volver").assertDoesNotExist() // The Semáforo screen draws its own header.
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals("ajustes", route())
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals("imss", route())
        tab("IMSS").assertIsSelected()
    }

    @Test fun `a past day keeps the bar with Ganancias lit and a retap returns to the root`() {
        compose.setContent { KomparaTheme { Shell() } }
        compose.onNodeWithText("Abrir el lunes").performClick()
        assertEquals("day/{day}?offerId={offerId}&ledger={ledger}", route())
        compose.runOnIdle { assertEquals("2026-10-19", nav.currentBackStackEntry!!.arguments!!.getString("day")) }
        compose.onNodeWithContentDescription("Ajustes").assertDoesNotExist()
        tab("Ganancias").assertIsSelected().performClick()
        assertEquals("ganancias", route())
    }

    @Test fun `tab switch and process restore retain the selected period`() {
        val restore = StateRestorationTester(compose)
        restore.setContent { KomparaTheme { Shell() } }
        compose.onNodeWithTag("period").performTextReplacement("2026-10-05")
        tab("Comparar").performClick()
        restore.emulateSavedInstanceStateRestore()
        tab("Comparar").assertIsSelected()
        tab("Ganancias").performClick()
        compose.onNodeWithTag("period").assertTextEquals("2026-10-05")
    }

    @Test fun `the three tab roots match their reviewed v4 baselines`() {
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(393)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                KomparaTheme(darkTheme = dark) { Box(Modifier.width(width.dp).height(852.dp)) { Shell(bodies = false) } }
            }
        }
        val tabs = listOf("ganancias" to "Ganancias", "comparar" to "Comparar", "imss" to "IMSS")
        val cases = tabs.flatMap { listOf(Triple(it, false, 1f), Triple(it, true, 1f)) } + Triple(tabs.first(), true, 2f)
        for ((destination, night, scale) in cases) {
            val (route, label) = destination
            val size = if (scale == 2f) 320 else 393
            compose.runOnIdle { dark = night; font = scale; width = size }
            tab(label).performClick()
            compose.waitForIdle()
            val name = V4Snapshot.name("shell", route, night, size, scale)
            V4Snapshot.assertNothingClipped(compose, name)
            V4Snapshot.assertMatchesBaseline(name, compose.runOnIdle { V4Snapshot.capture(compose.activity, size) })
        }
    }
}
