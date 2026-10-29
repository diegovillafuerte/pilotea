package mx.kompara.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import mx.kompara.ui.components.*
import mx.kompara.ui.fixtures.MonthFixture
import mx.kompara.ui.fixtures.ImssRuleExample
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
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonthScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val fixture get() = MonthFixture(ApplicationProvider.getApplicationContext())
    @Composable private fun Host(state: FiscalUiState, sheet: (String) -> Unit = {}, imss: () -> Unit = {},
        previous: () -> Unit = {}, next: () -> Unit = {}, onAnchor: (String, Float) -> Unit = { _, _ -> }) {
        Scaffold(bottomBar = { RootBottomBar(1, {}) }) { padding ->
            Column(Modifier.padding(padding).consumeWindowInsets(padding)) {
                mx.kompara.ui.nav.NavigationHeader("Ganancias", null, {}, showShare = true, onShare = {})
                MonthSummaryContent(state, sheet, previous, next, {}, imss, {}, {}, {}, Modifier.weight(1f), onAnchor)
            }
        }
    }
    @Test fun `month bars ledger picker and week selection restore without switching roots`() {
        val month = fixture.month()
        val restore = StateRestorationTester(compose)
        var selectedWeek: LocalDate? = null
        var selectedMonth: YearMonth? = null
        var imss = 0
        restore.setContent {
            var sheet by rememberSaveable { mutableStateOf<String?>(null) }
            KomparaTheme {
                Host(FiscalUiState(month.month, false, month), { sheet = it }, { imss++ })
                sheet?.let { id -> SheetScaffold(monthSheetTitle(id, month), { sheet = null }) {
                    MonthSheetContent(id, month, { selectedWeek = it }, { selectedMonth = it }, {})
                } }
            }
        }
        fun dismiss() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss) { it() }
        compose.onNodeWithText("Mes siguiente").assertIsNotEnabled()
        compose.onNodeWithText("Por semana").performScrollTo().performClick()
        compose.onNodeWithText("4 de 7 días del coche").assertExists()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Por semana · octubre").assertExists()
        compose.onNodeWithTag("month-week-0").performScrollTo().performClick()
        assertEquals(LocalDate.parse("2026-09-28"), selectedWeek)
        dismiss()
        compose.onNodeWithTag("month-calculation").performScrollTo().performClick()
        compose.onNodeWithText("$1,500 ÷ 7 × 19 días").assertExists()
        dismiss()
        compose.onNodeWithText("Octubre · en curso").performScrollTo().performClick()
        compose.onNodeWithText("Elegir mes").assertExists()
        compose.onAllNodesWithText("Octubre · en curso").onLast().performClick()
        assertEquals(month.month, selectedMonth)
        dismiss()
        compose.onNodeWithText("IMSS del mes").performScrollTo().performClick()
        assertEquals(1, imss)
    }
    @Test fun `IMSS factual figures and official action remain free and never render a regulatory claim`() {
        val month = fixture.month()
        var official = 0
        var pdf = 0
        var explanation = 0
        compose.setContent { KomparaTheme { FiscalContent(FiscalUiState(month.month, false, month), { explanation++ }, {}, {}, { official++ }, { pdf++ }, {}) } }
        compose.onNodeWithText("$12,487").assertExists()
        compose.onNodeWithText("$7,942").assertExists()
        compose.onNodeWithText("¿Cómo funciona?").performScrollTo().performClick()
        compose.onNodeWithText("Resumen en PDF").performScrollTo().performClick()
        compose.onNodeWithText("Consultar mi vigencia en el IMSS").performScrollTo().performClick()
        assertEquals(1, official); assertEquals(1, pdf); assertEquals(1, explanation)
        compose.onNodeWithText(IMSS_NON_REPRESENTATION).assertExists()
        listOf("Para el IMSS cuentan", "Cubierto", "Retenciones", "Salario mínimo").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }
    @Test fun `large text official action scrolls fully into view with bottom inset`() {
        val month = fixture.month()
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
            KomparaTheme { Box(Modifier.width(320.dp).height(600.dp)) {
                FiscalContent(FiscalUiState(month.month, false, month), {}, {}, {}, {}, {}, {})
            } }
        } }
        val action = compose.onNodeWithText("Consultar mi vigencia en el IMSS")
        action.performScrollTo().assertIsDisplayed()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        val bounds = action.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.top >= 0f)
        assertTrue(bounds.bottom <= 600f - 24f)
    }
    @Test fun `month navigation alias keeps Ganancias selected and IMSS hides bottom bar`() {
        assertTrue(mx.kompara.ui.nav.KomparaDestination.showsBottomBar(mx.kompara.ui.nav.KomparaDestination.MONTH_ROUTE))
        assertEquals(mx.kompara.ui.nav.KomparaDestination.GANANCIAS,
            mx.kompara.ui.nav.KomparaDestination.activeTab(listOf("inicio", "ganancias-mes", "fiscal")))
        assertFalse(mx.kompara.ui.nav.KomparaDestination.showsBottomBar("fiscal"))
    }
    @Test fun `all month and factual IMSS frames have unclipped computed screenshots`() {
        var frame by mutableStateOf("E3")
        var font by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(393)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                KomparaTheme(darkTheme = frame == "N5") {
                    val month = fixture.month(frame)
                    val state = FiscalUiState(month.month, false, month)
                    var anchors by remember(frame) { mutableStateOf(WeekSheetAnchors()) }
                    val sheet = when(frame) { "E3d" -> "weeks"; "D3b" -> "calculation"; "picker" -> "picker"; else -> null }
                    Box(Modifier.width(width.dp).height(852.dp).background(KomparaTheme.colors.paper)) {
                        if (frame in listOf("E4", "E5")) Column {
                            mx.kompara.ui.nav.NavigationHeader("IMSS del mes", {}, null)
                            FiscalContent(state, {}, {}, {}, {}, {}, {}, example = if (frame == "E5") ({ ImssRuleExample(fixture) }) else null)
                        } else Host(state, onAnchor = { name, bottom -> anchors = anchors.updated(name, bottom) })
                        if (sheet != null) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)))
                            Surface(Modifier.align(Alignment.BottomCenter).then(Modifier.testTag("month-sheet")).fillMaxWidth().then(monthSheetSize(sheet, 852f, anchors)).heightIn(max = 828.dp),
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), color = KomparaTheme.colors.paper) {
                                Column {
                                    Box(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp).size(32.dp, 4.dp).background(KomparaTheme.colors.rule))
                                    SheetContent(monthSheetTitle(sheet, month)) { MonthSheetContent(sheet, month, {}, {}, {}) }
                                }
                            }
                        }
                    }
                }
            }
        }
        val cases = listOf("E3", "E3b", "E3c", "E3d", "D3b", "E4", "E5", "N5", "picker").map { Triple(it, 1f, 393) } +
            listOf(Triple("N5", 1.3f, 393), Triple("E3", 1.3f, 393), Triple("E3d", 1.3f, 393), Triple("E4", 1.3f, 393), Triple("D3b", 2f, 320), Triple("E4", 2f, 320), Triple("N5", 2f, 320))
        cases.forEach { (id, scale, size) ->
            compose.runOnIdle { frame = id; font = scale; width = size }
            compose.waitForIdle()
            mx.kompara.ui.contract.FrameContract.assertFrame(compose, id)
            val name = "month_${id}_${size}_$scale"
            if (id == "E3d") {
                val quiet = compose.onNodeWithTag("month-calculation").fetchSemanticsNode().boundsInRoot
                val sheetBounds = compose.onNodeWithTag("month-sheet").fetchSemanticsNode().boundsInRoot
                assertTrue("$name sliced the basis sentence", sheetBounds.top >= quiet.bottom - 1f)
            }
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
}
