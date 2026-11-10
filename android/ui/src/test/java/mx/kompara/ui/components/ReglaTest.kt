package mx.kompara.ui.components

import android.graphics.*
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import mx.kompara.metrics.CounterOffers
import mx.kompara.ui.fixtures.reglaFrame
import mx.kompara.ui.theme.KomparaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w560dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReglaTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun `pill and tray heights scale once with the system font and widths hug the content`() {
        var scale by mutableFloatStateOf(1f)
        var model by mutableStateOf(reglaFrame("S1"))
        var compact by mutableStateOf(false)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
            KomparaTheme { Regla(model, Modifier.testTag("plate"), compact) }
        } }
        // Widths in dp at font scale 1, measured: S1 plain, S5 with both amounts, S9 with one, S4 unread.
        val widths = mapOf("S1" to 205f, "S5" to 207f, "S9" to 205f, "S4" to 158f)
        for (frame in listOf("S1", "S5", "S9", "S4")) {
            var previous = 0f
            for (s in listOf(1f, 1.3f, 2f)) for (c in listOf(false, true)) {
                compose.runOnIdle { model = reglaFrame(frame); scale = s; compact = c }
                val tray = !c && frame in setOf("S5", "S9")
                val plate = compose.onNodeWithTag("plate").assertHeightIsEqualTo(((if (tray) 90 else 56) * s).dp)
                val width = plate.fetchSemanticsNode().size.width.toFloat()
                if (s == 1f && !c) assertEquals("$frame width", widths.getValue(frame), width, .5f)
                if (!c) { assertTrue("$frame grows at $s", width >= previous); previous = width }
            }
        }
    }

    @Test fun `the rated pill draws the K, the shape's rate and the two-line unit, and nothing else`() {
        var model by mutableStateOf(reglaFrame("S1"))
        compose.setContent { KomparaTheme { Regla(model) } }
        fun texts() = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes().map { node -> node.config[SemanticsProperties.Text].joinToString { it.text } }
        assertEquals(listOf("K", "$9.49", "/km", "neto"), texts())
        compose.runOnIdle { model = reglaFrame("S10") }
        assertEquals(listOf("K", "$12.33", "/km", "bruto"), texts())
        compose.runOnIdle { model = reglaFrame("S2") }
        assertEquals(listOf("K", "$7.16", "/km", "neto"), texts())
        compose.runOnIdle { model = ReglaModel.rated(ReglaPosition.BELOW, BigDecimal("-1.0530"), ReglaUnit.KM, mx.kompara.data.settings.RateBasis.NET) }
        assertEquals(listOf("K", "−$1.05", "/km", "neto"), texts())
        compose.runOnIdle { model = ReglaModel.rated(ReglaPosition.ABOVE, BigDecimal("284.75"), ReglaUnit.HOUR, mx.kompara.data.settings.RateBasis.GROSS) }
        assertEquals(listOf("K", "$285", "/h", "bruto"), texts())
        compose.runOnIdle { model = reglaFrame("S5") }
        assertEquals(listOf("K", "$1.09", "/km", "neto", "Desde", "$203", "$249"), texts())
        compose.runOnIdle { model = reglaFrame("S9") }
        assertEquals(listOf("K", "$6.39", "/km", "neto", "Desde", "$113"), texts())
        // Above the meta, counter-offers from a rounding edge are never drawn.
        compose.runOnIdle { model = reglaFrame("S1").copy(counterOffers = CounterOffers(140, 150)) }
        assertEquals(listOf("K", "$9.49", "/km", "neto"), texts())
        for (input in ReglaUnavailable.entries) {
            compose.runOnIdle { model = ReglaModel(ReglaBand.Unavailable(input), counterOffers = CounterOffers(203, 249)) }
            assertEquals(listOf("K", input.headline), texts())
        }
        assertEquals("Sin lectura", ReglaUnavailable.TIME.headline)
    }

    @Test fun `one accessible image names the chip and the drawn words stay out of the reading order`() {
        var model by mutableStateOf(reglaFrame("S5"))
        var compact by mutableStateOf(false)
        compose.setContent { KomparaTheme { Regla(model, Modifier.testTag("plate"), compact) } }
        val names = mapOf(
            "S1" to "Arriba de tu meta: 9.49 pesos por kilómetro, neto",
            "S2" to "Entre tu mínimo y tu meta: 7.16 pesos por kilómetro, neto",
            "S3" to "Abajo de tu mínimo: 2.20 pesos por kilómetro, neto",
            "S4" to "Sin lectura: no pudimos leer esta oferta",
            "S5" to "Abajo de tu mínimo: 1.09 pesos por kilómetro, neto. Desde 203 pesos alcanza tu mínimo; desde 249, tu meta.",
            "S9" to "Entre tu mínimo y tu meta: 6.39 pesos por kilómetro, neto. Desde 113 pesos alcanza tu meta.",
            "S10" to "Arriba de tu meta: 12.33 pesos por kilómetro, bruto",
        )
        names.forEach { (frame, name) ->
            compose.runOnIdle { model = reglaFrame(frame); compact = false }
            val bounds = compose.onNodeWithTag("plate").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithContentDescription(name).assertHasNoClickAction()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image))
                .assert(SemanticsMatcher("spans the chip") { it.boundsInRoot == bounds })
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).assertCountEquals(1)
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .assertAll(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        }
        // The plain pill of a bid says only what it draws.
        compose.runOnIdle { model = reglaFrame("S5"); compact = true }
        compose.onNodeWithContentDescription("Abajo de tu mínimo: 1.09 pesos por kilómetro, neto").assertExists()
        compose.onAllNodes(hasText("Desde"), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun `a long signed rate grows the pill instead of truncating or shrinking`() {
        val model = ReglaModel.rated(ReglaPosition.BELOW, BigDecimal("-12345.56"), ReglaUnit.HOUR, mx.kompara.data.settings.RateBasis.NET)
            .copy(counterOffers = CounterOffers(12_345, 13_000))
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
            KomparaTheme { Row(Modifier.width(200.dp).horizontalScroll(rememberScrollState())) {
                Regla(model, Modifier.testTag("plate"))
            } }
        } }
        compose.onNodeWithTag("plate").assertWidthIsAtLeast(260.dp)
        compose.onNodeWithText("−$12,346", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("$13,000", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Abajo de tu mínimo: menos 12,346 pesos por hora, neto. " +
            "Desde 12,345 pesos alcanza tu mínimo; desde 13,000, tu meta.").assertExists()
        assertLabelsFit()
    }

    @Test fun `new band changes atomically with no outgoing rate`() {
        var model by mutableStateOf(reglaFrame("S1"))
        compose.mainClock.autoAdvance = false
        compose.setContent { KomparaTheme { ReglaPresence(true, false) { Regla(model) } } }
        compose.mainClock.advanceTimeBy(150)
        compose.onNodeWithContentDescription("Arriba de tu meta: 9.49 pesos por kilómetro, neto").assertExists()
        compose.runOnIdle { model = reglaFrame("S3") }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Abajo de tu mínimo: 2.20 pesos por kilómetro, neto").assertExists()
        compose.onNodeWithContentDescription("Arriba de tu meta: 9.49 pesos por kilómetro, neto").assertDoesNotExist()
        compose.onAllNodes(hasText("$9.49"), useUnmergedTree = true).assertCountEquals(0)
        compose.runOnIdle { model = reglaFrame("S4") }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Sin lectura: no pudimos leer esta oferta").assertExists()
        compose.onAllNodes(hasContentDescription("pesos", substring = true)).assertCountEquals(0)
    }

    @Test fun `reduced motion settles within 150ms and fades out without a retained band`() {
        var visible by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { KomparaTheme { ReglaPresence(visible, true) { Regla(reglaFrame("S1")) } } }
        compose.mainClock.advanceTimeBy(150)
        compose.onNodeWithContentDescription("Arriba de tu meta: 9.49 pesos por kilómetro, neto").assertIsDisplayed()
        compose.runOnIdle { visible = false }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithContentDescription("Arriba de tu meta: 9.49 pesos por kilómetro, neto").assertDoesNotExist()
    }

    @Test fun `reviewed v4 chip frames on a day host and a dark host`() {
        var model by mutableStateOf(reglaFrame("S1"))
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
            KomparaTheme(darkTheme = dark) {
                Box(Modifier.width(WIDTH.dp).height(240.dp).background(if (dark) Color(0xFF262C33) else Color(0xFFEBEEF4)),
                    contentAlignment = Alignment.Center) { Regla(model, Modifier.testTag("plate"), darkHost = dark) }
            }
        } }
        data class Case(val id: String, val night: Boolean, val fontScale: Float = if (id == "S7") 1.3f else 1f, val suffix: String = "") {
            val name = "regla_${id}_${if (night) "dark" else "day"}$suffix"
        }
        val cases = listOf("S1", "S2", "S3", "S4", "S5", "S7", "S9", "S10").flatMap { listOf(Case(it, false), Case(it, true)) } +
            Case("S5", false, 2f, "_2.0")
        cases.forEach { case ->
            compose.runOnIdle { model = reglaFrame(case.id); dark = case.night; scale = case.fontScale }
            compose.waitForIdle(); assertLabelsFit()
            val bitmap = compose.runOnIdle {
                val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
                Bitmap.createBitmap(WIDTH, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
            val output = File("build/outputs/v3-screenshots/${case.name}.png").apply { parentFile!!.mkdirs() }
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val expected = File("src/test/snapshots/v3/${case.name}.png")
            if (System.getProperty("v3.recordScreenshots") == "true") output.copyTo(expected, overwrite = true)
            else {
                assertTrue("Missing reviewed baseline: $expected", expected.exists())
                assertTrue("Screenshot changed: $output", BitmapFactory.decodeFile(expected.path).sameAs(bitmap))
            }
        }
    }

    private fun assertLabelsFit() {
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes().forEach { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.forEach { result -> assertFalse("Clipped ${result.layoutInput.text}", result.didOverflowHeight || (0 until result.lineCount).any { result.getLineRight(it) > result.size.width + 1f }) }
            }
    }

    private companion object { const val WIDTH = 393 }
}
