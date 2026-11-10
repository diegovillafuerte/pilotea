package mx.kompara.ui.onboarding

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import mx.kompara.ui.auth.*
import mx.kompara.ui.components.*
import mx.kompara.ui.fixtures.*
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
class OnboardingScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun `auth and capture frames remain readable at larger text and narrow width`() {
        var frame by mutableStateOf("A6"); var scale by mutableFloatStateOf(1f); var width by mutableIntStateOf(393)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
            KomparaTheme(darkTheme = width == 320) { Box(Modifier.width(width.dp).height(852.dp)) { key(frame, scale, width) { OnboardingFixtureHost(frame) } } }
        } }
        // The first run itself is P1–P4 (SettingsFramesTest); sign-in and the capture consent keep their v3 frames.
        val cases = listOf("A6", "A7", "auth-error", "capture").map { Triple(it, 1f, 393) } + Triple("A7", 1.3f, 393) +
            listOf("A6", "A7").map { Triple(it, 2f, 320) }
        for ((id, font, size) in cases) {
            compose.runOnIdle { frame = id; scale = font; width = size }; compose.waitForIdle()
            mx.kompara.ui.contract.FrameContract.assertFrame(compose, id)
            val name = "onboarding_${id}_${size}_$font"
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                val results = mutableListOf<TextLayoutResult>(); node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.forEach { r -> assertFalse("$name clipped ${r.layoutInput.text}", r.didOverflowHeight || (0 until r.lineCount).any { r.getLineRight(it) > r.size.width + 1f }) }
            }
            val bitmap = compose.runOnIdle {
                val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
                Bitmap.createBitmap(size, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
            }
            val output = File("build/outputs/v3-screenshots/$name.png").apply { parentFile!!.mkdirs() }
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val baseline = File("src/test/snapshots/v3/$name.png")
            if (System.getProperty("v3.recordScreenshots") == "true") output.copyTo(baseline, overwrite = true)
            else { assertTrue("Missing $name", baseline.exists()); assertTrue("Changed $name", BitmapFactory.decodeFile(baseline.path).sameAs(bitmap)) }
        }
    }
    @Test fun `demo steps once through the three colours and rests on green`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { KomparaTheme { PitchScreen({}, autoplay = true) } }
        compose.onNodeWithText("$148.00").assertExists()
        compose.mainClock.advanceTimeBy(1600)
        compose.onNodeWithText("$120.00").assertExists()
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithText("$62.00").assertExists()
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithText("$148.00").assertExists()
        compose.mainClock.advanceTimeBy(8_000)
        compose.onNodeWithText("$148.00").assertExists()
        compose.onNodeWithText("Entrar con WhatsApp").assertDoesNotExist()
        var playback = DemoPlayback()
        val states = mutableListOf(playback.selected)
        repeat(3) { playback = playback.tick(); states += playback.selected }
        assertEquals(listOf(DemoOffer.GREEN, DemoOffer.YELLOW, DemoOffer.RED, DemoOffer.GREEN), states)
        assertEquals(playback, playback.tick())
    }
    @Test fun `reduced motion has no autoplay and each teaching offer matches the kit chips`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { KomparaTheme { PitchScreen({}, autoplay = false) } }
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithText("$148.00").assertExists()
        val fixture = V3Fixture(compose.activity)
        listOf(DemoOffer.GREEN to "verde", DemoOffer.YELLOW to "amarillo", DemoOffer.RED to "rojo").forEach { (offer, key) ->
            val rate = (offer.model().band as ReglaBand.Rated).rate
            assertEquals(0, fixture.chip(key).ratePerKm!!.compareTo(rate))
        }
    }
    @Test fun `Android illustration is one inert image and denial retry skip remain reachable`() {
        var opens = 0; var skips = 0
        compose.setContent { KomparaTheme { AccessibilityGrantScreen(PermissionReturnState(missing = true), { opens++ }, { skips++ }) } }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image)).assertHasNoClickAction()
        compose.onNodeWithText("Permitir", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Semáforo apagado · aún falta el permiso").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Abrir ajustes").performClick(); compose.onNodeWithText("Ahora no").performClick()
        assertEquals(1, opens); assertEquals(1, skips)
    }
    @Test fun `large text disclosure risk remains reachable and choices are full targets`() {
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
            KomparaTheme { Box(Modifier.width(320.dp).height(600.dp)) { DisclosureScreen({}, {}) } }
        } }
        compose.onNodeWithText("Uber y DiDi no son parte de Kompara y podrían no permitir apps como esta. Kompara nunca toca su app.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Acepto").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("No acepto").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }
    @Test fun `auth keyboard sized viewport preserves retry cancel and editable code`() {
        var input = ""; var cancelled = 0; var retry = 0
        compose.setContent { KomparaTheme { Box(Modifier.height(420.dp)) {
            SignupContent(SignupUiState(SignupStep.CODE, "5512345678", "123456", error = SignupError.VERIFY_FAILED),
                codeChange = { input = it }, verify = { retry++ }, cancel = { cancelled++ }, requestFocus = false)
        } } }
        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("654321")
        assertEquals("654321", input)
        compose.onNodeWithText("Reintentar").assertIsDisplayed().performClick()
        compose.onNodeWithText("Seguir sin cuenta por ahora").assertIsDisplayed().performClick()
        assertEquals(1, retry); assertEquals(1, cancelled)
    }
}
