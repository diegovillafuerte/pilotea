package mx.kompara.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonthScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val fixture get() = MonthFixture(ApplicationProvider.getApplicationContext())
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
    @Test fun `factual IMSS frames have unclipped computed screenshots`() {
        var frame by mutableStateOf("E4")
        var font by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(393)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                KomparaTheme {
                    val month = fixture.month(frame)
                    val state = FiscalUiState(month.month, false, month)
                    Box(Modifier.width(width.dp).height(852.dp).background(KomparaTheme.colors.paper)) {
                        Column {
                            mx.kompara.ui.nav.NavigationHeader("IMSS del mes", {}, null)
                            FiscalContent(state, {}, {}, {}, {}, {}, {}, example = if (frame == "E5") ({ ImssRuleExample(fixture) }) else null)
                        }
                    }
                }
            }
        }
        val cases = listOf("E4", "E5").map { Triple(it, 1f, 393) } + listOf(Triple("E4", 1.3f, 393), Triple("E4", 2f, 320))
        cases.forEach { (id, scale, size) ->
            compose.runOnIdle { frame = id; font = scale; width = size }
            compose.waitForIdle()
            mx.kompara.ui.contract.FrameContract.assertFrame(compose, id)
            val name = "month_${id}_${size}_$scale"
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
