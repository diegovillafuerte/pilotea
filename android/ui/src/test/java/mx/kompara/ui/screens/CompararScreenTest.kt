package mx.kompara.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.RateBasis
import mx.kompara.ui.components.RootBottomBar
import mx.kompara.ui.contract.FrameContract
import mx.kompara.ui.contract.v4.V4Contract
import mx.kompara.ui.contract.v4.V4Snapshot
import mx.kompara.ui.fixtures.TuFixture
import mx.kompara.ui.fixtures.compareFixtureState
import mx.kompara.ui.nav.NavigationHeader
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Comparar's kit frames K1 and K2 and Tu ciudad's one honest state, under the shell's header and bar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompararScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val fixture get() = TuFixture(ApplicationProvider.getApplicationContext())
    private val ciudad = listOf("Tu ciudad todavía no abre", "Cuando haya suficientes conductores de CDMX, aquí verás en qué lugar estás " +
        "entre ellos: en total, por app, por día y por hora.")

    @Composable private fun Host(state: CompararUiState, onTab: (CompararTab) -> Unit = {}, onRetry: () -> Unit = {}) {
        Scaffold(topBar = { NavigationHeader("Comparar", null, {}) }, bottomBar = { RootBottomBar(1, {}) }) { padding ->
            CompararContent(state, onTab, onRetry, Modifier.padding(padding).consumeWindowInsets(padding))
        }
    }

    @Test fun `K1 K2 and Tu ciudad match their reviewed v4 baselines day night and large type`() {
        var frame by mutableStateOf("K1")
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(393)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                KomparaTheme(darkTheme = dark) { Box(Modifier.width(width.dp).height(852.dp)) { Host(compareFixtureState(fixture, frame)) } }
            }
        }
        for (id in listOf("K1", "K2", "ciudad")) {
            for ((night, scale, size) in listOf(Triple(false, 1f, 393), Triple(true, 1f, 393), Triple(false, 1.3f, 393), Triple(true, 2f, 320))) {
                compose.runOnIdle { frame = id; dark = night; font = scale; width = size }
                compose.waitForIdle()
                if (id == "ciudad") ciudad.forEach { assertTrue("missing $it", FrameContract.normalize(it) in FrameContract.visibleText(compose)) }
                else V4Contract.assertFrame(compose, id)
                val name = V4Snapshot.name("comparar", id, night, size, scale)
                V4Snapshot.assertNothingClipped(compose, name)
                V4Snapshot.assertMatchesBaseline(name, compose.runOnIdle { V4Snapshot.capture(compose.activity, size) })
            }
        }
    }

    @Test fun `the two text tabs select Tú and Tu ciudad`() {
        var state by mutableStateOf(compareFixtureState(fixture, "K1"))
        val chosen = mutableListOf<CompararTab>()
        compose.setContent { KomparaTheme { Host(state, { chosen += it; state = state.copy(tab = it) }) } }
        compose.onNode(isSelectable() and hasText("Tú")).assertIsSelected()
        compose.onNode(isSelectable() and hasText("Tu ciudad")).assertIsNotSelected().performClick()
        assertEquals(listOf(CompararTab.CIUDAD), chosen)
        compose.onNodeWithText(ciudad[0]).assertExists()
        compose.onNodeWithText("¿Uber o DiDi?").assertDoesNotExist()
        compose.onNode(isSelectable() and hasText("Tú")).performClick()
        compose.onNodeWithText("¿Uber o DiDi?").assertExists()
    }

    @Test fun `an unknown city says de tu ciudad and nothing on Tu ciudad sends data`() {
        compose.setContent { KomparaTheme { Host(CompararUiState(CompararTab.CIUDAD)) } }
        compose.onNodeWithText("Cuando haya suficientes conductores de tu ciudad", substring = true).assertExists()
        compose.onAllNodes(hasClickAction() and !isSelectable()).assertCountEquals(1) // only the header's gear
    }

    @Test fun `the charts read their groups aloud and follow the chosen basis and unit`() {
        val bruto = TuPresentation.of(fixture.k1(), PreferredMetric.IPK, RateBasis.GROSS)
        compose.setContent { KomparaTheme { Host(CompararUiState(tu = bruto)) } }
        compose.onNodeWithText("Tus viajes · últimas 4 semanas · por km, bruto").assertExists()
        compose.onNodeWithText("DiDi te paga \$1.66 más por km.").assertExists()
        compose.onNodeWithContentDescription("DiDi \$13.46, Uber \$11.80").assertExists()
        compose.onNodeWithContentDescription("sábado \$15.50", substring = true).assertExists()
    }

    @Test fun `loading and error keep the tabs and retry`() {
        var state by mutableStateOf(CompararUiState())
        var retries = 0
        compose.setContent { KomparaTheme { Host(state, onRetry = { retries++ }) } }
        compose.onNodeWithContentDescription("Cargando").assertExists()
        compose.runOnIdle { state = CompararUiState(error = true) }
        compose.onNodeWithText("No pudimos abrir tus viajes").assertExists()
        compose.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retries)
        compose.onNode(isSelectable() and hasText("Tu ciudad")).assertExists()
    }
}
