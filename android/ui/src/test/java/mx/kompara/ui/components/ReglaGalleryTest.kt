package mx.kompara.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReglaGalleryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun `generic router renders every C state and its actual host scene`() {
        var frame by mutableStateOf("C1")
        compose.setContent { KomparaTheme(darkTheme = false) { V3ComponentHost(frame, Modifier.width(393.dp)) } }
        for (id in (1..13).map { "C$it" } + listOf("N8", "N9", "N10")) {
            compose.runOnIdle { frame = id }
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
            if (id == "C9") {
                compose.onNodeWithContentDescription("Cumple tu mínimo: 7.16 pesos netos por km").assertExists()
                val bounds = compose.onNodeWithTag("regla").fetchSemanticsNode().boundsInRoot
                assertEquals(141f, bounds.left, .1f); assertEquals(243f, bounds.top, .1f)
            }
            if (id == "C10") compose.onNodeWithTag("regla").assertWidthIsEqualTo(312.dp)
            if (id == "C12") compose.onNodeWithTag("regla").assertHeightIsEqualTo(80.dp)
            FrameContract.assertFrame(compose, id)
            val bitmap = compose.runOnIdle {
                val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
                Bitmap.createBitmap(393, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
            val output = File("build/outputs/v3-screenshots/gallery_$id.png").apply { parentFile!!.mkdirs() }
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val baseline = File("src/test/snapshots/v3/${output.name}")
            if (System.getProperty("v3.recordScreenshots") == "true") output.copyTo(baseline, overwrite = true)
            else {
                assertTrue("Missing gallery baseline: $baseline", baseline.exists())
                assertTrue("Changed gallery: $output", BitmapFactory.decodeFile(baseline.path).sameAs(bitmap))
            }
        }
    }
}
