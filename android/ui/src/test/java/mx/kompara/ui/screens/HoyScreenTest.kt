package mx.kompara.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
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
import mx.kompara.ui.components.RootBottomBar
import mx.kompara.ui.fixtures.HoyFixture
import mx.kompara.ui.stats.InicioUiState
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
class HoyScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val fixture get() = HoyFixture(ApplicationProvider.getApplicationContext())
    private fun actions(hit: (String) -> Unit = {}) = HoyActions(
        { hit("settings") }, { hit("reader") }, { hit("day:$it") }, { d, id -> hit("offer:$d:$id") },
        { hit("costs") }, { hit("confirmCosts") }, { hit("limits") }, { hit("profile") }, { hit("battery") },
        { hit("permission") }, { hit("uber") }, { hit("notifications") }, { hit("resume") }, { hit("retry") })
    @Composable private fun Host(state: InicioUiState, actions: HoyActions, width: Int = 393) {
        Scaffold(Modifier.width(width.dp).height(852.dp), bottomBar = { RootBottomBar(0, {}) }) { padding ->
            HoyContent(state, actions, Modifier.padding(padding).consumeWindowInsets(padding))
        }
    }
    @Test fun `healthy routes preserve day and offer identity and have no retired dashboard`() {
        val calls = mutableListOf<String>()
        val state = fixture.state("B1")
        compose.setContent { KomparaTheme { Host(state, actions(calls::add)) } }
        compose.onNodeWithContentDescription("Ajustes").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Semáforo funcionando").performClick()
        compose.onNodeWithText(state.quietLine).performClick()
        state.offers.forEach { compose.onNodeWithText(it.outcome).performClick() }
        compose.onNodeWithText("Ver todas las ofertas de hoy").performScrollTo().performClick()
        assertEquals(listOf("settings", "reader", "day:2026-10-19") + state.offers.map { "offer:2026-10-19:${it.id}" } + "day:2026-10-19", calls)
        listOf("Premium", "Consejos", "Racha", "Todas", "Tu meta semanal", "Kompara").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }
    @Test fun `all permission prepared pause and cost doors invoke real actions`() {
        var state by mutableStateOf(fixture.state("B2"))
        val calls = mutableListOf<String>()
        compose.setContent { KomparaTheme { Host(state, actions(calls::add)) } }
        compose.onNodeWithText("Volver a dar el permiso").performClick()
        compose.onNodeWithText("Que no se vuelva a apagar").performClick()
        compose.onNodeWithText("Semáforo apagado", substring = true).assertHasNoClickAction()
        compose.runOnIdle { state = fixture.state("B2r") }
        compose.onNodeWithText("Semáforo preparado").performClick()
        compose.onNodeWithText("Abrir Uber para probar").performClick()
        compose.runOnIdle { state = fixture.state("B3n") }
        compose.onNodeWithText("Activar el semáforo").performClick()
        compose.runOnIdle { state = fixture.state("B5") }
        compose.onNodeWithText("Reanudar el semáforo").performClick()
        compose.runOnIdle { state = fixture.state("B4") }
        compose.onNodeWithText("Ajustar mis costos").performClick()
        compose.onNodeWithText("Sí, son mis costos").performClick()
        compose.runOnIdle { state = fixture.state("B3") }
        compose.onNodeWithText("Elegir mi ciudad").performScrollTo().performClick()
        compose.onNodeWithText("Avisarme si se apaga").performClick()
        assertEquals(listOf("permission", "battery", "reader", "uber", "permission", "resume", "costs", "confirmCosts", "profile", "notifications"), calls)
    }
    @Test fun `loading and read failure never substitute zero`() {
        var state by mutableStateOf(InicioUiState())
        val calls = mutableListOf<String>()
        compose.setContent { KomparaTheme { Host(state, actions(calls::add)) } }
        compose.onNodeWithText("$0").assertDoesNotExist()
        compose.runOnIdle { state = InicioUiState(loading = false, error = true) }
        compose.onNodeWithText("Reintentar").performClick()
        assertEquals(listOf("retry"), calls)
    }
    @Test fun `all B frames night twins and large fonts render without text overflow`() {
        var frame by mutableStateOf("B1")
        var scale by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(393)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                KomparaTheme(darkTheme = frame.startsWith("N")) { Host(fixture.state(frame), actions(), width) }
            }
        }
        val cases = listOf("B1", "B2", "B2r", "B3", "B3n", "B4", "B5", "B6", "B7", "B8", "B9", "N1", "N2").map { Triple(it, 1f, 393) } +
            listOf(Triple("N1", 1.3f, 393), Triple("N2", 1.3f, 393), Triple("B1", 1.3f, 393), Triple("B3", 1.3f, 393), Triple("N1", 2f, 320), Triple("N2", 2f, 320), Triple("B8", 2f, 320))
        cases.forEach { (id, font, dpWidth) ->
            compose.runOnIdle { frame = id; scale = font; width = dpWidth }
            compose.waitForIdle()
            mx.kompara.ui.contract.FrameContract.assertFrame(compose, id)
            val name = "hoy_${id}_${dpWidth}_$font"
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.forEach { result -> assertFalse("$name clipped ${result.layoutInput.text}", result.didOverflowHeight || (0 until result.lineCount).any { result.getLineRight(it) > result.size.width + 1f }) }
            }
            val bitmap = compose.runOnIdle {
                val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
                Bitmap.createBitmap(dpWidth, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
            }
            val output = File("build/outputs/v3-screenshots/$name.png").apply { parentFile!!.mkdirs() }
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val baseline = File("src/test/snapshots/v3/$name.png")
            if (System.getProperty("v3.recordScreenshots") == "true") output.copyTo(baseline, overwrite = true)
            else { assertTrue("Missing baseline $name", baseline.exists()); assertTrue("Changed screenshot $name", BitmapFactory.decodeFile(baseline.path).sameAs(bitmap)) }
        }
        compose.onNodeWithText("Últimas ofertas").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("En curso").performScrollTo().assertIsDisplayed()
    }
}
