package mx.kompara.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import mx.kompara.metrics.compare.ComparisonMetric
import mx.kompara.ui.components.*
import mx.kompara.ui.fixtures.CompareFixture
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompareScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val fixture get() = CompareFixture(ApplicationProvider.getApplicationContext())
    @Composable private fun Host(state: CompareUiState, sheet: (String) -> Unit = {}, scope: (CompareScope) -> Unit = {},
        imports: () -> Unit = {}, upgrade: () -> Unit = {}, retry: () -> Unit = {}) {
        Scaffold(bottomBar = { RootBottomBar(1, {}) }) { padding ->
            Column(Modifier.padding(padding).consumeWindowInsets(padding)) {
                mx.kompara.ui.nav.NavigationHeader("Comparar", null, {})
                CompararContent(state, scope, sheet, {}, {}, imports, upgrade, retry, Modifier.weight(1f))
            }
        }
    }
    @Test fun `all frames render computed values night and large text without clipped labels`() {
        var frame by mutableStateOf("F1")
        var font by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(393)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                KomparaTheme(darkTheme = frame.startsWith("N")) { Box(Modifier.width(width.dp).height(852.dp)) { Host(fixture.state(frame)) } }
            }
        }
        val cases = listOf("F1", "F2", "F3", "F4", "F5", "N6", "N7").map { Triple(it, 1f, 393) } +
            listOf("F1", "F2", "F3", "F4", "F5", "N6", "N7").map { Triple(it, 1.3f, 393) } +
            listOf("F1", "F3", "F5", "N6", "N7").map { Triple(it, 2f, 320) }
        cases.forEach { (id, scale, size) ->
            compose.runOnIdle { frame = id; font = scale; width = size }
            compose.waitForIdle()
            mx.kompara.ui.contract.FrameContract.assertFrame(compose, id)
            val name = "compare_${id}_${size}_$scale"
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.forEach { result -> assertFalse("$name clipped ${result.layoutInput.text}", result.didOverflowHeight || (0 until result.lineCount).any { result.getLineRight(it) > result.size.width + 1f }) }
            }
            val bitmap = compose.runOnIdle {
                val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
                Bitmap.createBitmap(size, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
            }
            val output = File("build/outputs/v3-screenshots/$name.png").apply { parentFile!!.mkdirs() }
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val baseline = File("src/test/snapshots/v3/$name.png")
            if (System.getProperty("v3.recordScreenshots") == "true") output.copyTo(baseline, overwrite = true)
            else { assertTrue("Missing baseline $name", baseline.exists()); assertTrue("Changed screenshot $name", BitmapFactory.decodeFile(baseline.path).sameAs(bitmap)) }
        }
    }
    @Test fun `free rank masks only average including semantics and opens Premium`() {
        var upgrade = 0
        compose.setContent { KomparaTheme { Host(fixture.state("F3"), upgrade = { upgrade++ }) } }
        compose.onNodeWithText("Te pagan más por viaje que al 78 % de los de Uber en CDMX").assertExists()
        compose.onNodeWithTag("compare-chart").assertContentDescriptionEquals("Por viaje: Tú $149/viaje, Promedio Con Premium")
        compose.onAllNodes(hasText("$131/viaje", substring = true), useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("Ver Premium").assertIsDisplayed().performClick()
        assertEquals(1, upgrade)
    }
    @Test fun `missing cohort renders its explanation and own data while unsupported contribution stays hidden`() {
        compose.setContent { KomparaTheme { Host(fixture.state("F4").copy(contributionAvailable = false)) } }
        compose.onNodeWithText("Aún juntamos conductores de DiDi para compararte").assertExists()
        compose.onNodeWithTag("compare-chart").assertContentDescriptionEquals("Por viaje: Tú $120/viaje, Tú · 5 – 11 oct $107/viaje, diferencia +12 %")
        compose.onNodeWithText("Aportar al promedio").assertDoesNotExist()
        compose.onNodeWithText("Ver Premium").assertDoesNotExist()
    }
    @Test fun `context provenance and opt in work and restore independently of rank`() {
        val restored = StateRestorationTester(compose)
        var contributed = false
        var imports = 0
        var metric: ComparisonMetric? = null
        restored.setContent {
            var frame by rememberSaveable { mutableStateOf("F5") }
            var sheet by rememberSaveable { mutableStateOf<String?>(null) }
            var contributing by rememberSaveable { mutableStateOf(false) }
            val state = fixture.state(frame).copy(contributing = contributing)
            KomparaTheme {
                Host(state, { sheet = it }, { frame = if (it == CompareScope.APPS) "F1" else "F5" }, { imports++ })
                sheet?.let { id -> SheetScaffold(compareSheetTitle(id), { sheet = null }) {
                    CompareSheetContent(id, state, { sheet = it }, { metric = it; sheet = null },
                        { frame = if (it == "DIDI") "F4" else "F5" }, {}, {}, { contributing = it; contributed = it }, { imports++ })
                } }
            }
        }
        fun dismiss() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss) { it() }
        compose.onNodeWithText("Confirmar con mi resumen").assertIsDisplayed().performClick(); assertEquals(1, imports)
        compose.onNodeWithTag("compare-data").performScrollTo().performClick()
        compose.onNodeWithText("De dónde salen estos datos").assertExists()
        compose.onNodeWithText("31 viajes · pago de la app antes de gastos.").assertExists()
        restored.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("De dónde salen estos datos").assertExists(); dismiss()
        compose.onNodeWithText("Aportar al promedio").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Aportar al promedio").assertIsOff().performClick()
        assertTrue(contributed); dismiss()
        compose.onNodeWithTag("compare-context").performScrollTo().performClick()
        compose.onNodeWithText("DiDi").performClick()
        compose.onNodeWithText("Por hora").performScrollTo().assertExists()
        compose.onNodeWithText("Por viaje").performScrollTo().performClick()
        assertEquals(ComparisonMetric.PER_TRIP, metric)
        compose.onNodeWithText("Aún juntamos conductores de DiDi para compararte").assertExists()
        compose.onNodeWithText("Ver Premium").assertDoesNotExist()
    }
    @Test fun `narrow large type scrolls to consent and preserves bottom inset`() {
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) { KomparaTheme {
            Box(Modifier.width(320.dp).height(380.dp).testTag("bounded")) {
                SheetContent("Aportar al promedio") { CompareSheetContent("contribution", fixture.state("F4"), {}, {}, {}, {}, {}, {}, {}) }
            }
        } } }
        val text = "Solo cuentan tus semanas confirmadas con resumen; las estimaciones del lector no se suman"
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10000f) }
        assertTrue(compose.onNodeWithTag("bounded").fetchSemanticsNode().boundsInRoot.bottom - compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.bottom >= 24f)
    }
    @Test fun `large text root scrolls all comparison rows above the pinned confirmation`() {
        var imports = 0
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) { KomparaTheme {
            Box(Modifier.width(320.dp).height(852.dp)) { Host(fixture.state("F5"), imports = { imports++ }) }
        } } }
        compose.onNodeWithText("Aportar al promedio").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Confirmar con mi resumen").assertIsDisplayed().performClick()
        assertEquals(1, imports)
        val row = compose.onNodeWithText("Aportar al promedio").fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithText("Confirmar con mi resumen").fetchSemanticsNode().boundsInRoot
        assertTrue(row.bottom <= button.top)
    }

    @Test fun `loading empty and error retain context actions and current week cannot step forward`() {
        var state by mutableStateOf(fixture.state().copy(loading = true, comparison = null))
        var opened: String? = null
        var retry = 0
        compose.setContent { KomparaTheme { Host(state, { opened = it }, retry = { retry++ }) } }
        compose.onNodeWithTag("compare-context").performClick(); assertEquals("context", opened)
        compose.runOnIdle { state = state.copy(loading = false, error = true, weekStart = state.today.monday()) }
        compose.onNodeWithText("Semana siguiente").assertIsNotEnabled()
        compose.onNodeWithText("Reintentar").performClick(); assertEquals(1, retry)
    }
}
