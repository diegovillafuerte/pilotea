package mx.kompara.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import mx.kompara.ui.contract.FrameContract
import mx.kompara.ui.fixtures.V3ComponentHost
import mx.kompara.ui.fixtures.reglaScene
import mx.kompara.ui.theme.KomparaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The v3 host scenes with the v4 pill in them, until the v3 chip frames are retired. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReglaGalleryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun `generic router renders every C state and its actual host scene`() {
        var frame by mutableStateOf("C1")
        var scale by mutableFloatStateOf(1f)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
            KomparaTheme(darkTheme = false) { V3ComponentHost(frame, Modifier.width(393.dp)) }
        } }
        // C5 is retired (contract/v4/WpB.kt): v4 draws a missing time as S4's "Sin lectura".
        val cases = ((1..13).map { "C$it" }.filter { it != "C5" } + listOf("N8", "N9", "N10")).map { it to 1f } +
            listOf("N8", "N9", "N10").flatMap { listOf(it to 1.3f, it to 2f) }
        for ((id, fontScale) in cases) {
            compose.runOnIdle { frame = id; scale = fontScale }
            compose.waitForIdle()
            val scene = reglaScene(id)
            compose.onNodeWithTag("host-$id").assertExists()
            if (scene.hasVerdict) compose.onNodeWithTag("regla").assertExists()
            else compose.onNodeWithTag("regla").assertDoesNotExist()
            if (scene.stopped) {
                compose.onNodeWithTag("stopped-tab").assertExists().assertHasNoClickAction()
                compose.onNodeWithText("Dejamos de leer tus ofertas", useUnmergedTree = true).assertExists()
                compose.onNodeWithText("tu meta $8").assertDoesNotExist()
            } else compose.onNodeWithTag("stopped-tab").assertDoesNotExist()
            if (scene.cardTop == null) compose.onNodeWithTag("host-offer").assertDoesNotExist()
            else compose.onNodeWithTag("host-offer").assertExists()
            if (scene.navigation) compose.onNodeWithTag("navigation-banner").assertExists()
            if (scene.hasVerdict) compose.onNodeWithTag("regla").assertHeightIsEqualTo((56 * maxOf(fontScale, scene.fontScale)).dp)
            if (id == "C9") {
                compose.onNodeWithContentDescription("Entre tu mínimo y tu meta: 7.16 pesos por kilómetro, neto").assertExists()
                val bounds = compose.onNodeWithTag("regla").fetchSemanticsNode().boundsInRoot
                assertEquals(141f, bounds.left, .1f); assertEquals(243f, bounds.top, .1f)
            }
            if (id == "C10") compose.onNodeWithTag("regla").assertWidthIsEqualTo(C10_WIDTH.dp)
            if (fontScale == 1f) FrameContract.assertFrame(compose, id)
            val name = if (fontScale == 1f) id else "${id}_$fontScale"
            val bitmap = compose.runOnIdle {
                val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
                Bitmap.createBitmap(393, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
            val output = File("build/outputs/v3-screenshots/gallery_$name.png").apply { parentFile!!.mkdirs() }
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val baseline = File("src/test/snapshots/v3/${output.name}")
            if (System.getProperty("v3.recordScreenshots") == "true") output.copyTo(baseline, overwrite = true)
            else {
                assertTrue("Missing gallery baseline: $baseline", baseline.exists())
                assertTrue("Changed gallery: $output", BitmapFactory.decodeFile(baseline.path).sameAs(bitmap))
            }
        }
    }

    private companion object { const val C10_WIDTH = 243 }
}
