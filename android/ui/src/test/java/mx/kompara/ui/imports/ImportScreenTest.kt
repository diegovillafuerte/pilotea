package mx.kompara.ui.imports

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
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
import mx.kompara.testing.BaselineComparison
import mx.kompara.ui.fixtures.ImportFixture
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
class ImportScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val fixture get() = ImportFixture(ApplicationProvider.getApplicationContext())
    @Test fun `consent review correction and partial states expose one explicit action and missing km`() {
        var state by mutableStateOf<ImportUiState>(fixture.frame("H4"))
        var picked = 0; var saved = 0; var edited = 0; var added = 0
        compose.setContent { KomparaTheme { ImportContent(state, ImportActions(choose = { picked++ }, save = { saved++ }, correct = { edited++ }, add = { added++ })) } }
        compose.onNodeWithText(IMPORT_CONSENT).assertIsDisplayed()
        compose.onNodeWithText("Elegir archivos").performClick(); assertEquals(1, picked)
        compose.runOnIdle { state = fixture.frame("H5") }
        compose.onNodeWithText("$5,340").assertIsDisplayed(); compose.onNodeWithText("faltan los kilómetros").assertExists()
        compose.onNodeWithText("26 h").assertExists(); compose.onNodeWithText("29").assertExists()
        compose.onNodeWithText("Corregir lo leído").performScrollTo().performClick(); assertEquals(1, edited)
        compose.onNodeWithText("Guardar").performClick(); assertEquals(1, saved)
        compose.runOnIdle { state = fixture.frame("H5c") }
        compose.onNodeWithText("Leída").assertExists(); compose.onNodeWithText("Guardar").assertDoesNotExist()
        compose.onNodeWithText("Agregar la captura").performClick(); assertEquals(1, added)
    }
    @Test fun `correction uses numeric fields retains missing distance and typed original hint`() {
        var state by mutableStateOf(fixture.frame("H5d") as ImportUiState.Correcting)
        compose.setContent { KomparaTheme { ImportContent(state, ImportActions(correction = { state = state.copy(values = it) })) } }
        compose.onNodeWithText("Kilómetros").assertDoesNotExist()
        compose.onNodeWithText("Lunes de la semana").assertDoesNotExist()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(3)
        compose.onNodeWithContentDescription("Te pagó la app").performTextReplacement("5400")
        compose.onNodeWithText("Te pagó la app · se leyó $5,340").assertExists()
        compose.onNodeWithText("Guardar corrección").assertIsDisplayed()
    }
    @Test fun `failed upload describes retained source and known period rather than raw filename`() {
        compose.setContent { KomparaTheme { ImportContent(fixture.frame("H5e"), ImportActions()) } }
        compose.onNodeWithText("Uber · PDF semanal · 14 – 20 sep").assertExists()
        compose.onNodeWithText("resumen-uber.pdf").assertDoesNotExist()
        compose.onNodeWithText("1.2 MB").assertExists()
        compose.onNodeWithText("Reintentar").assertIsDisplayed()
    }
    @Test fun `shared fields retain selection through external text and saved state without emitting edits`() {
        var value by mutableStateOf("1234")
        var edits = 0
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent { KomparaTheme {
            mx.kompara.ui.components.KomparaTextField(value, { value = it; edits++ }, "Número")
        } }
        val field = compose.onNodeWithContentDescription("Número")
        field.performTextInputSelection(androidx.compose.ui.text.TextRange(1, 3))
        compose.runOnIdle { value = "12345" }
        assertEquals(androidx.compose.ui.text.TextRange(1, 3), field.fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange])
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(androidx.compose.ui.text.TextRange(1, 3), field.fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange])
        assertEquals(0, edits)
    }
    @Test fun `live correction focuses payment for native numeric entry`() {
        compose.setContent { KomparaTheme { ImportContent(fixture.frame("H5d"), ImportActions(), focusCorrection = true) } }
        compose.onNodeWithContentDescription("Te pagó la app").assertIsFocused()
        assertEquals(androidx.compose.ui.text.TextRange(4), compose.onNodeWithContentDescription("Te pagó la app")
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange])
        val hours = compose.onNodeWithContentDescription("Horas en línea").fetchSemanticsNode().boundsInRoot
        val suffix = compose.onNodeWithText("h").fetchSemanticsNode().boundsInRoot
        assertTrue("fixed h stays beside its number", suffix.left - hours.left < 50f)
    }
    @Test fun `partial statement gives consequence before Use PDF and no fabricated net`() {
        var used = 0
        compose.setContent { KomparaTheme { ImportContent(fixture.frame("H5b"), ImportActions(useStatement = { used++ })) } }
        compose.onNodeWithText("faltan los km de 6 viajes").assertExists()
        compose.onNodeWithText("−$428").assertExists()
        compose.onNodeWithText("El PDF queda en la semana; octubre conserva lo leído del 1 al 4.").assertIsDisplayed()
        compose.onNodeWithText("Usar el PDF").performClick(); assertEquals(1, used)
    }

    @Test fun `saved statement stays readable without a paid window or save action`() {
        val state = fixture.frame("H5") as ImportUiState.Review
        compose.setContent { KomparaTheme { ImportContent(state.copy(draft = state.draft.copy(stage = "SAVED")), ImportActions()) } }
        compose.onNodeWithText("$5,340").assertExists()
        compose.onNodeWithText("Listo").assertIsDisplayed()
        compose.onNodeWithText("Guardar").assertDoesNotExist()
        compose.onNodeWithText("Corregir lo leído").assertDoesNotExist()
    }
    @Test fun `large text and keyboard sized viewport keep correction controls reachable`() {
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) { KomparaTheme {
            Box(Modifier.width(320.dp).height(520.dp)) { ImportContent(fixture.frame("H5d"), ImportActions()) }
        } } }
        compose.onNodeWithText("Guardar corrección").assertIsDisplayed()
        compose.onNodeWithText("Horas en línea").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancelar").assertIsDisplayed()
    }
    @Test fun `all import frames day night and larger fonts have reviewed screenshot comparisons`() {
        var frame by mutableStateOf("H4"); var width by mutableIntStateOf(393); var scale by mutableFloatStateOf(1f); var night by mutableStateOf(false)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
            KomparaTheme(darkTheme = night) { Box(Modifier.width(width.dp).height(852.dp)) { ImportContent(fixture.frame(frame), ImportActions()) } }
        } }
        val cases = listOf("H4", "H5", "H5b", "H5c", "H5d", "H5e", "missing", "unreadable", "auth", "reading").map { listOf(it, "393", "1.0", "false") } +
            listOf("H4", "H5", "H5b", "H5c", "H5d", "H5e").map { listOf(it, "393", "1.3", "false") } +
            listOf("H4", "H5b", "H5d", "H5e").map { listOf(it, "320", "2.0", "true") }
        for ((id, size, font, dark) in cases) {
            compose.runOnIdle { frame = id; width = size.toInt(); scale = font.toFloat(); night = dark.toBoolean() }
            compose.waitForIdle()
            mx.kompara.ui.contract.FrameContract.assertFrame(compose, id)
            val name = "import_${id}_${size}_${font}_${if (night) "night" else "day"}"
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                val results = mutableListOf<TextLayoutResult>(); node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.forEach { result -> assertFalse("$name clipped ${result.layoutInput.text}", result.didOverflowHeight || (0 until result.lineCount).any { result.getLineRight(it) > result.size.width + 1f }) }
            }
            val bitmap = compose.runOnIdle {
                val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
                Bitmap.createBitmap(width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
            }
            val output = File("build/outputs/v3-screenshots/$name.png").apply { parentFile!!.mkdirs() }
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val baseline = File("src/test/snapshots/v3/$name.png")
            if (System.getProperty("v3.recordScreenshots") == "true") output.copyTo(baseline, overwrite = true)
            else { assertTrue("Missing $name", baseline.exists()); BaselineComparison.assertMatches(BitmapFactory.decodeFile(baseline.path), bitmap, output) }
        }
    }
}
