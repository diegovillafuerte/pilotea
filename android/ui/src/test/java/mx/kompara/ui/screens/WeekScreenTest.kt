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
import mx.kompara.ui.components.*
import mx.kompara.ui.fixtures.WeekFixture
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeekScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val fixture get() = WeekFixture(ApplicationProvider.getApplicationContext())
    @Composable private fun Host(state: WeekSummaryUiState, sheet: (String) -> Unit = {}, next: () -> Unit = {}, previous: () -> Unit = {}, upgrade: () -> Unit = {}, onAnchor: (String, Float) -> Unit = { _, _ -> }) {
        Scaffold(bottomBar = { RootBottomBar(1, {}) }) { padding ->
            Column(Modifier.padding(padding).consumeWindowInsets(padding)) {
                mx.kompara.ui.nav.NavigationHeader("Ganancias", null, {}, showShare = true, onShare = if (state.loading) null else ({}))
                WeekSummaryContent(state, sheet, previous, next, {}, {}, upgrade, {}, Modifier.weight(1f), onAnchor)
            }
        }
    }
    @Test fun `every sheet opens from its own door and restores over Ganancias on the same week`() {
        val week = fixture.week()
        val restore = StateRestorationTester(compose)
        var day: LocalDate? = null
        var imports = 0
        restore.setContent {
            var sheet by rememberSaveable { mutableStateOf<String?>(null) }
            KomparaTheme {
                Host(WeekSummaryUiState(week.start, false, week), { sheet = it })
                sheet?.let { id -> SheetScaffold(weekSheetTitle(id, week), { sheet = null }) {
                    WeekSheetContent(id, week, { day = it }, { sheet = null }, { imports++ })
                } }
            }
        }
        fun dismiss() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss) { it() }
        compose.onNodeWithText("Por app").performScrollTo().performClick()
        compose.onNodeWithText("Por app · 12 – 18 oct").assertExists()
        compose.onNodeWithText("te dejó por km, sin el coche").assertExists()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Por app · 12 – 18 oct").assertExists()
        dismiss()
        compose.onNodeWithText("Cuadrar con tu resumen de Uber").performScrollTo().performClick()
        compose.onNodeWithText("Solo Uber, antes de gastos").assertExists()
        compose.onNodeWithText("+$34").assertExists()
        compose.onNodeWithText("Traer otro resumen").performScrollTo().performClick()
        assertEquals(1, imports)
        dismiss()
        compose.onNodeWithTag("week-calculation").performScrollTo().performClick()
        compose.onNodeWithText("Uber $5,028 · DiDi $3,372").assertExists()
        dismiss()
        compose.onNodeWithTag("week-bars").performScrollTo().performClick()
        compose.onNodeWithTag("week-day-6").performScrollTo().performClick()
        assertEquals(LocalDate.parse("2026-10-18"), day)
        dismiss()
        compose.onNodeWithText("12 – 18 oct · semana pasada").performScrollTo().performClick()
        compose.onNodeWithText("Elegir semana").assertExists()
        compose.onNodeWithText("Importada · sin detalle por día").performScrollTo().assertIsDisplayed()
    }
    @Test fun `open week future step disabled older week own ledger free and local offline amounts unchanged`() {
        var week by mutableStateOf(fixture.week("E8"))
        var offline by mutableStateOf(false)
        var next = 0
        var upgrade = 0
        compose.setContent { KomparaTheme { Host(WeekSummaryUiState(week.start, false, week, offline = offline), next = { next++ }, upgrade = { upgrade++ }) } }
        compose.onNodeWithText("Semana siguiente").assertIsNotEnabled()
        assertEquals(0, next)
        compose.onNodeWithText("$548").assertExists()
        compose.runOnIdle { week = fixture.week("E2") }
        compose.onNodeWithText("$4,747").assertExists()
        compose.onNodeWithText("Por día · Premium").assertExists()
        compose.onNodeWithTag("week-bars").assertDoesNotExist()
        val baseline = compose.onNodeWithTag("week-locked-zero").fetchSemanticsNode().boundsInRoot
        assertEquals(168f, baseline.width, .1f)
        assertEquals(1f, baseline.height, .1f)
        listOf("L", "J", "V", "S", "D").forEach {
            val day = compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot
            assertTrue(day.top >= baseline.bottom)
        }
        compose.onAllNodesWithText("M").assertCountEquals(2)
        compose.onNodeWithTag("week-calculation").assertExists()
        compose.onNodeWithText("Ver Premium").performScrollTo().performClick()
        assertEquals(1, upgrade)
        compose.runOnIdle { week = fixture.week(); offline = true }
        compose.onNodeWithText("$4,980").assertExists()
        compose.onNodeWithText("Sin conexión").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Reintentar").assertDoesNotExist()
    }
    @Test fun `narrow large text day sheet scrolls to the total and preserves its bottom inset`() {
        val week = fixture.week()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                KomparaTheme {
                    Box(Modifier.width(320.dp).height(238.dp).then(Modifier.testTag("bounded-sheet"))) {
                        SheetContent("Por día · 12 – 18 oct") { DayBreakdownContent(week) {} }
                    }
                }
            }
        }
        compose.onNodeWithText("$4,980").performScrollTo().assertIsDisplayed()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        val note = compose.onNodeWithText("Redondeado una vez · los días suman $4,979").fetchSemanticsNode().boundsInRoot
        val bounds = compose.onNodeWithTag("bounded-sheet").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.bottom - note.bottom >= 24f)
    }

    @Test fun `all week frames and sheet contents have computed reviewed screenshots with no clipped text`() {
        var frame by mutableStateOf("E1")
        var font by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(393)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                KomparaTheme(darkTheme = frame == "N4") {
                    val week = fixture.week(frame)
                    var anchors by remember(frame) { mutableStateOf(WeekSheetAnchors()) }
                    val sheet = when(frame) { "E1b", "E1c" -> "days"; "E1d" -> "calculation"; "E1e" -> "statement:UBER"; "E1f" -> "apps"; "E6" -> "picker"; else -> null }
                    Box(Modifier.width(width.dp).height(852.dp)) {
                        Host(WeekSummaryUiState(week.start, frame == "E7", week, offline = frame == "E7b"), onAnchor = { name, bottom -> anchors = anchors.updated(name, bottom) })
                        if (sheet != null) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)))
                            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().then(weekSheetSize(sheet, 852f, anchors)).heightIn(max = 828.dp),
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), color = KomparaTheme.colors.paper) {
                                Column {
                                    Box(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp).size(32.dp, 4.dp).background(KomparaTheme.colors.rule))
                                    SheetContent(weekSheetTitle(sheet, week)) { WeekSheetContent(sheet, week, {}, {}, {}) }
                                }
                            }
                        }
                    }
                }
            }
        }
        val cases = listOf("E1", "E1b", "E1c", "E1d", "E1e", "E1f", "E2", "E6", "E7", "E7b", "E8", "N4").map { Triple(it, 1f, 393) } +
            listOf(Triple("N4", 1.3f, 393), Triple("E1", 1.3f, 393), Triple("E2", 1.3f, 393), Triple("E2", 2f, 320), Triple("E1e", 1.3f, 393), Triple("E1f", 1.3f, 393), Triple("E1b", 2f, 320), Triple("E1f", 2f, 320), Triple("N4", 2f, 320))
        cases.forEach { (id, scale, size) ->
            compose.runOnIdle { frame = id; font = scale; width = size }
            compose.waitForIdle()
            mx.kompara.ui.contract.FrameContract.assertFrame(compose, id)
            val name = "week_${id}_${size}_$scale"
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
