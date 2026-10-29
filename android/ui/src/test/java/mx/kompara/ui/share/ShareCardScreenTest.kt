package mx.kompara.ui.share

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
import androidx.compose.ui.unit.*
import androidx.test.core.app.ApplicationProvider
import mx.kompara.ui.fixtures.ShareFixture
import mx.kompara.ui.fixtures.ShareFixtureHost
import mx.kompara.ui.theme.KomparaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareCardScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val fixture get() = ShareFixture(ApplicationProvider.getApplicationContext())
    @Test fun `H3 accessible preview follows privacy state and buttons launch only their own target`() {
        var state by mutableStateOf(fixture.state())
        var whatsApp = 0; var more = 0; var closed = 0
        compose.setContent { KomparaTheme { ShareCardContent(state, { closed++ }, { state = fixture.state(hide = it) }, { whatsApp++ }, { more++ }) } }
        compose.onNodeWithText("Así se verá").assertIsDisplayed()
        compose.onNodeWithContentDescription(state.data!!.description).assertIsDisplayed()
        assertFalse(state.data!!.description.contains('$'))
        compose.onNodeWithContentDescription("Ocultar montos y horas").assertIsOn().performClick()
        compose.onNodeWithContentDescription(state.data!!.description).assertIsDisplayed()
        assertTrue(state.data!!.description.contains("$149/viaje"))
        assertTrue(state.data!!.description.contains("24\u00a0h"))
        compose.onNodeWithContentDescription("Ocultar montos y horas").performClick()
        assertFalse(state.data!!.description.contains('$'))
        compose.onNodeWithText("Compartir en WhatsApp").performClick(); assertEquals(1, whatsApp)
        compose.onNodeWithText("Más opciones").performClick(); assertEquals(1, more)
        compose.onNodeWithContentDescription("Cerrar").performClick(); assertEquals(1, closed)
    }
    @Test fun `narrow large text keeps privacy and both target actions reachable`() {
        val state = fixture.state()
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) { KomparaTheme {
            Box(Modifier.width(320.dp).height(600.dp)) { ShareCardContent(state) }
        } } }
        compose.onNodeWithContentDescription("Ocultar montos y horas").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Compartir en WhatsApp").assertIsDisplayed()
        compose.onNodeWithText("Más opciones").assertIsDisplayed()
    }
    @Test fun `all share period privacy theme and larger font states have reviewed image comparisons`() {
        var frame by mutableStateOf("H3"); var hide by mutableStateOf(true)
        var width by mutableIntStateOf(393); var scale by mutableFloatStateOf(1f); var night by mutableStateOf(false)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, scale)) { KomparaTheme(darkTheme = night) {
            Box(Modifier.width(width.dp).height(852.dp)) { ShareFixtureHost(frame, hide) }
        } } }
        val cases = listOf("H3", "week", "month", "historical").flatMap { id -> listOf(true, false).map { listOf(id, it.toString(), "393", "1.0", "false") } } +
            listOf("H3", "month").map { listOf(it, "true", "393", "1.3", "false") } +
            listOf("H3", "month").map { listOf(it, "true", "320", "2.0", "true") }
        for ((id, redacted, size, font, dark) in cases) {
            compose.runOnIdle { frame = id; hide = redacted.toBoolean(); width = size.toInt(); scale = font.toFloat(); night = dark.toBoolean() }
            compose.waitForIdle()
            mx.kompara.ui.contract.FrameContract.assertFrame(compose, id)
            val name = "share_${id}_${if (hide) "hidden" else "shown"}_${size}_${font}_${if (night) "night" else "day"}"
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                val results = mutableListOf<TextLayoutResult>(); node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.forEach { r -> assertFalse("$name clipped ${r.layoutInput.text}", r.didOverflowHeight || (0 until r.lineCount).any { r.getLineRight(it) > r.size.width + 1f }) }
            }
            val bitmap = compose.runOnIdle {
                val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
                Bitmap.createBitmap(width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
            }
            val output = File("build/outputs/v3-screenshots/$name.png").apply { parentFile!!.mkdirs() }
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val baseline = File("src/test/snapshots/v3/$name.png")
            if (System.getProperty("v3.recordScreenshots") == "true") output.copyTo(baseline, overwrite = true)
            else { assertTrue("Missing $name", baseline.exists()); assertTrue("Changed $name", BitmapFactory.decodeFile(baseline.path).sameAs(bitmap)) }
        }
    }
}
