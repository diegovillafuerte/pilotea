package mx.kompara.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import mx.kompara.metrics.imss.ImssMonth
import mx.kompara.ui.components.RootBottomBar
import mx.kompara.ui.components.SheetContent
import mx.kompara.ui.contract.v4.V4Contract
import mx.kompara.ui.contract.v4.V4Snapshot
import mx.kompara.ui.fixtures.ImssFixture
import mx.kompara.ui.fixtures.imssFixtureSheet
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
import java.io.File

/** The IMSS tab's kit frames M1–M4 under the shell's header and bar; M3 and M4 draw their sheet over M1 as the sheet does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImssScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val fixture get() = ImssFixture(ApplicationProvider.getApplicationContext())

    private class Calls { var previous = 0; var next = 0; var rule = 0; var official = 0; var retry = 0; var save = 0; var remove = 0
        val incentives = mutableListOf<String>(); val drafts = mutableListOf<String>() }

    @Composable private fun Host(month: ImssMonth, sheet: String? = null, draft: String = "1800", calls: Calls = Calls(), linkFailed: Boolean = false) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(topBar = { NavigationHeader("IMSS", null, {}) }, bottomBar = { RootBottomBar(2, {}) }) { padding ->
                ImssContent(ImssUiState(month.month, month.today, month), { calls.previous++ }, { calls.next++ }, { calls.incentives += it },
                    { calls.rule++ }, { calls.official++ }, { calls.retry++ }, Modifier.padding(padding).consumeWindowInsets(padding), linkFailed)
            }
            if (sheet != null) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)))
                Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 828.dp),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), color = KomparaTheme.colors.paper) {
                    Column {
                        Box(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp).size(32.dp, 4.dp)
                            .background(KomparaTheme.colors.rule, RoundedCornerShape(2.dp)))
                        if (sheet == "rule") SheetContent(RULE_TITLE) { ImssRuleContent({ calls.official++ }) }
                        else imssFixtureSheet(month, sheet, draft).let { state ->
                            SheetContent(incentivesTitle(state)) { IncentivesContent(state, { calls.drafts += it }, { calls.save++ }, { calls.remove++ }) }
                        }
                    }
                }
            }
        }
    }

    @Test fun `M1 to M4 match their reviewed v4 baselines day night and large type`() {
        var frame by mutableStateOf("M1")
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(393)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                KomparaTheme(darkTheme = dark) {
                    Box(Modifier.width(width.dp).height(852.dp)) {
                        Host(if (frame == "M2") fixture.m2() else fixture.m1(), when (frame) { "M3" -> "rule"; "M4" -> "UBER"; else -> null })
                    }
                }
            }
        }
        for (id in listOf("M1", "M2", "M3", "M4")) {
            for ((night, scale, size) in listOf(Triple(false, 1f, 393), Triple(true, 1f, 393), Triple(false, 1.3f, 393), Triple(true, 2f, 320))) {
                compose.runOnIdle { frame = id; dark = night; font = scale; width = size }
                compose.waitForIdle()
                V4Contract.assertFrame(compose, id) // also fails on a coverage claim (V4Contract.coverage)
                val name = V4Snapshot.name("imss", id, night, size, scale)
                V4Snapshot.assertNothingClipped(compose, name)
                V4Snapshot.assertMatchesBaseline(name, compose.runOnIdle { V4Snapshot.capture(compose.activity, size) })
            }
        }
    }

    @Test fun `M1 arrows rows and the parts row open what they name`() {
        val calls = Calls()
        compose.setContent { KomparaTheme { Host(fixture.m1(), calls = calls) } }
        compose.onNodeWithContentDescription("Mes siguiente").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Mes anterior").performClick()
        compose.onNodeWithText("Viajes \$7,942 · Incentivos: agregar").performScrollTo().performClick()
        compose.onNodeWithText("¿Qué cambia si lo alcanzas?").performScrollTo().performClick()
        compose.onNodeWithText("Ver mi vigencia en el IMSS").assertDoesNotExist()
        assertEquals(1, calls.previous); assertEquals(listOf("DIDI"), calls.incentives); assertEquals(1, calls.rule)
    }

    @Test fun `M2 adds the official row and a past month can step forward`() {
        val calls = Calls()
        val past = fixture.m1().let { ImssMonth.of(it.month.minusMonths(1), it.today, mapOf("UBER" to java.math.BigDecimal(12_287)), emptyMap()) }
        var month by mutableStateOf(fixture.m2())
        compose.setContent { KomparaTheme { Host(month, calls = calls, linkFailed = true) } }
        compose.onNodeWithText("Ver mi vigencia en el IMSS").performScrollTo().performClick()
        assertEquals(1, calls.official)
        compose.onNodeWithText("No pudimos abrir el sitio del IMSS. Inténtalo otra vez.").assertExists()
        compose.runOnIdle { month = past }
        compose.onNodeWithText("Septiembre").assertExists()
        compose.onNodeWithText("Te faltaron \$9,651.").assertExists()
        compose.onNodeWithContentDescription("Mes siguiente").assertIsEnabled().performClick()
        assertEquals(1, calls.next)
    }

    @Test fun `M3 sends the driver to the IMSS`() {
        val calls = Calls()
        compose.setContent { KomparaTheme { Host(fixture.m1(), "rule", calls = calls) } }
        compose.onNodeWithText("Ver mi vigencia en el IMSS ↗").performScrollTo().performClick()
        assertEquals(1, calls.official)
    }

    @Test fun `M4 stages whole pesos and offers Quitar only for a saved amount`() {
        val calls = Calls()
        var draft by mutableStateOf("1800")
        var platform by mutableStateOf("UBER")
        compose.setContent { KomparaTheme { Host(fixture.m1(), platform, draft, calls) } }
        compose.onNodeWithContentDescription("Incentivos y bonos por referidos").performTextReplacement("2500")
        assertEquals("2500", calls.drafts.last())
        compose.onNodeWithText("Guardar").performScrollTo().performClick()
        compose.onNodeWithText("Quitar").performScrollTo().performClick()
        assertEquals(1, calls.save); assertEquals(1, calls.remove)
        compose.runOnIdle { platform = "DIDI"; draft = "" }
        compose.onNodeWithText("Incentivos de DiDi · octubre").assertExists()
        compose.onNodeWithText("Lo que DiDi te ha pagado este mes además de tus viajes.").assertExists()
        compose.onNodeWithText("Guardar").assertIsNotEnabled()
        compose.onNodeWithText("Quitar").assertDoesNotExist()
    }

    @Test fun `loading and error keep the month and retry`() {
        val calls = Calls()
        var state by mutableStateOf(ImssUiState(fixture.month, fixture.today))
        compose.setContent { KomparaTheme { ImssContent(state, {}, {}, {}, {}, {}, { calls.retry++ }) } }
        compose.onNodeWithText("Octubre · quedan 12 días").assertExists()
        compose.onNodeWithContentDescription("Cargando").assertExists()
        compose.runOnIdle { state = state.copy(error = true) }
        compose.onNodeWithText("Reintentar").performClick()
        assertEquals(1, calls.retry)
    }

    @Test fun `the IMSS sources never certify coverage`() {
        val main = File("src/main/java/mx/kompara/ui")
        for (file in V4Contract.imssSources.map { File(main, it) }) {
            assertTrue("$file is gone", file.isFile)
            Regex("\"([^\"\\\\]|\\\\.)*\"").findAll(file.readText()).map { it.value }.forEach {
                assertFalse("${file.name} certifies coverage: $it", V4Contract.coverage.containsMatchIn(it))
            }
        }
    }
}
