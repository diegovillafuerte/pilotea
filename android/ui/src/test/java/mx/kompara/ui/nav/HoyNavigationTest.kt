package mx.kompara.ui.nav

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.navigation.*
import androidx.navigation.compose.*
import androidx.test.core.app.ApplicationProvider
import mx.kompara.ui.fixtures.HoyFixture
import mx.kompara.ui.screens.HoyActions
import mx.kompara.ui.screens.HoyContent
import mx.kompara.ui.theme.KomparaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
class HoyNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var nav: NavHostController
    private lateinit var actions: HoyActions
    @Suppress("RestrictedApi")
    @Composable private fun TestGraph() {
        nav = rememberNavController()
        actions = hoyActions(nav, LocalContext.current)
        val entries by nav.currentBackStack.collectAsState()
        val current = KomparaDestination.activeTab(entries.map { it.destination.route })
        val route = entries.lastOrNull()?.destination?.route
        KomparaTheme {
            Scaffold(bottomBar = {
                if (KomparaDestination.showsBottomBar(route)) KomparaBottomBar(current, { nav.selectRoot(it, current) })
            }) { padding ->
                NavHost(nav, "inicio", Modifier.padding(padding).consumeWindowInsets(padding)) {
                    composable("inicio") { HoyContent(HoyFixture(ApplicationProvider.getApplicationContext()).state("B1"), actions) }
                    composable("ganancias") {
                        var week by rememberSaveable { mutableStateOf("2026-10-12") }
                        TextField(week, { week = it }, Modifier.testTag("period"))
                    }
                    composable("comparar") { Text("Comparación") }
                    composable("${KomparaDestination.WEEK_SUMMARY_ROUTE}/{weekStart}") { Text("Semana elegida") }
                    composable("day/{day}?offerId={offerId}&ledger={ledger}", arguments = listOf(navArgument("day") { type = NavType.StringType }, navArgument("offerId") { type = NavType.LongType; defaultValue = -1L }, navArgument("ledger") { type = NavType.BoolType; defaultValue = false })) { Text("Día elegido") }
                    listOf("ajustes", "lector", "cost-profile", "cost-efficiency", "thresholds", "account", "reader-battery", "reader-recovery").forEach { destination ->
                        composable(destination) { Text("Destino $destination") }
                    }
                }
            }
        }
    }
    @Test fun `all Hoy graph doors open existing destinations without bottom bar`() {
        compose.setContent { TestGraph() }
        val doors: List<Pair<String, () -> Unit>> = listOf(
            "ajustes" to { actions.settings() }, "lector" to { actions.reader() },
            "cost-efficiency" to { actions.costs() }, "thresholds" to { actions.limits() },
            "account" to { actions.profile() }, "reader-battery" to { actions.battery() }, "reader-recovery" to { actions.permission() })
        doors.forEach { (destination, open) ->
            compose.runOnIdle { open() }
            compose.onNodeWithText("Destino $destination").assertExists()
            compose.onAllNodes(isSelectable()).assertCountEquals(0)
            compose.runOnIdle { assertEquals(destination, nav.currentDestination?.route); nav.popBackStack() }
        }
    }
    @Test fun `offer keeps day identity after restore and active tab retap returns to root`() {
        val restore = StateRestorationTester(compose)
        restore.setContent { TestGraph() }
        compose.onNodeWithText("En curso").performClick()
        compose.runOnIdle {
            assertEquals("2026-10-19", nav.currentBackStackEntry!!.arguments!!.getString("day"))
            assertEquals(1L, nav.currentBackStackEntry!!.arguments!!.getLong("offerId"))
        }
        restore.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals("2026-10-19", nav.currentBackStackEntry!!.arguments!!.getString("day"))
            assertEquals(1L, nav.currentBackStackEntry!!.arguments!!.getLong("offerId"))
        }
        compose.onNode(isSelectable() and hasText("Hoy")).performClick()
        compose.onNodeWithText("Semáforo funcionando").assertExists()
    }
    @Test fun `Hoy money opens the same dated ledger directly and limits back retains offer route`() {
        compose.setContent { TestGraph() }
        val state = HoyFixture(ApplicationProvider.getApplicationContext()).state("B1")
        compose.onNodeWithText(state.quietLine).performClick()
        compose.runOnIdle {
            assertEquals("2026-10-19", nav.currentBackStackEntry!!.arguments!!.getString("day"))
            assertTrue(nav.currentBackStackEntry!!.arguments!!.getBoolean("ledger"))
            nav.popBackStack()
        }
        compose.onNodeWithText("En curso").performClick()
        compose.runOnIdle { nav.navigate("thresholds") }
        compose.onNodeWithText("Destino thresholds").assertExists()
        compose.runOnIdle {
            nav.popBackStack()
            assertEquals("2026-10-19", nav.currentBackStackEntry!!.arguments!!.getString("day"))
            assertEquals(1L, nav.currentBackStackEntry!!.arguments!!.getLong("offerId"))
        }
    }
    @Test fun `tab switch and process restore retain selected period`() {
        val restore = StateRestorationTester(compose)
        restore.setContent { TestGraph() }
        compose.onNode(isSelectable() and hasText("Ganancias")).performClick()
        compose.onNodeWithTag("period").performTextReplacement("2026-10-05")
        compose.onNode(isSelectable() and hasText("Comparar")).performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNode(isSelectable() and hasText("Ganancias")).performClick()
        compose.onNodeWithTag("period").assertTextEquals("2026-10-05")
    }
    @Test fun `legacy week alias selects Ganancias and root retap works without an existing earnings entry`() {
        compose.setContent { TestGraph() }
        compose.runOnIdle { nav.navigate("${KomparaDestination.WEEK_SUMMARY_ROUTE}/2026-10-12") }
        compose.onNode(isSelectable() and hasText("Ganancias")).assertIsSelected().performClick()
        compose.onNodeWithTag("period").assertExists()
    }

}
