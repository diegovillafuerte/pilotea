package mx.kompara.ui.components

import android.content.Context
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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import mx.kompara.ui.fixtures.V3Fixture
import mx.kompara.ui.fixtures.regla
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
    private val fixture get() = V3Fixture(ApplicationProvider.getApplicationContext<Context>())

    @Test fun `full and compact geometry scales once with the system font`() {
        var scale by mutableFloatStateOf(1f)
        var compact by mutableStateOf(false)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
            KomparaTheme { Regla(fixture.regla("C1"), Modifier.testTag("plate"), compact) }
        } }
        for (s in listOf(1f, 1.3f, 2f)) for (c in listOf(false, true)) {
            compose.runOnIdle { scale = s; compact = c }
            compose.onNodeWithTag("plate").assertWidthIsEqualTo((240 * s).dp)
                .assertHeightIsEqualTo((if (c) 80 * s else 112 * s).dp)
            compose.onNodeWithText("tu mínimo $6").assertExists()
        }
    }

    @Test fun `band is phrase first and both real text limits follow in full and compact`() {
        var compact by mutableStateOf(false)
        compose.setContent { KomparaTheme { Regla(fixture.regla("C1"), compact = compact) } }
        repeat(2) {
            compose.onNodeWithContentDescription("Arriba de tu meta: 9.49 pesos netos por km")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 0f))
                .assertHasNoClickAction()
                .assertHeightIsEqualTo((if (compact) 38 else 56).dp)
            compose.onNodeWithText("tu meta $8").assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 1f))
            compose.onNodeWithText("tu mínimo $6").assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 2f))
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).assertCountEquals(1)
            compose.runOnIdle { compact = true }
        }
    }

    @Test fun `all grey inputs omit trip money cue and marker and compact retains reason`() {
        var input by mutableStateOf(ReglaUnavailable.FARE)
        var compact by mutableStateOf(false)
        compose.setContent { KomparaTheme { Regla(fixture.regla("C5").copy(band = ReglaBand.Unavailable(input)), compact = compact) } }
        for (c in listOf(false, true)) for (reason in ReglaUnavailable.entries) {
            compose.runOnIdle { input = reason; compact = c }
            compose.onNodeWithContentDescription("${reason.headline}: ${reason.reason}").assertExists()
            compose.onNodeWithText("tu meta $200/h").assertExists()
            compose.onNodeWithText("tu mínimo $150/h").assertExists()
            compose.onAllNodes(hasText("9.49", substring = true), useUnmergedTree = true).assertCountEquals(0)
            compose.onAllNodes(hasContentDescription("pesos", substring = true)).assertCountEquals(0)
        }
    }

    @Test fun `long signed rate and hour limits grow instead of truncating or shrinking`() {
        val model = ReglaModel(ReglaBand.Rated(ReglaPosition.BELOW, BigDecimal("-12345.56")),
            BigDecimal("12345.50"), BigDecimal("12000.25"), ReglaUnit.HOUR)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
            KomparaTheme { Row(Modifier.width(320.dp).horizontalScroll(rememberScrollState())) {
                Regla(model, Modifier.testTag("plate"))
            } }
        } }
        compose.onNodeWithTag("plate").assertWidthIsAtLeast(312.dp)
        compose.onNodeWithContentDescription("Abajo de tu mínimo: menos 12,346 pesos netos por hora").assertExists()
        compose.onNodeWithText("tu meta $12,345.50/h").assertExists()
        assertLabelsFit()
    }

    @Test fun `new band changes atomically with no outgoing rate or phrase`() {
        var model by mutableStateOf(fixture.regla("C1"))
        compose.mainClock.autoAdvance = false
        compose.setContent { KomparaTheme { ReglaPresence(true, false) { Regla(model) } } }
        compose.mainClock.advanceTimeBy(150)
        compose.onNodeWithContentDescription("Arriba de tu meta: 9.49 pesos netos por km").assertExists()
        compose.runOnIdle { model = fixture.regla("C7") }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Abajo de tu mínimo: menos 1.05 pesos netos por km").assertExists()
        compose.onNodeWithContentDescription("Arriba de tu meta: 9.49 pesos netos por km").assertDoesNotExist()
        compose.runOnIdle { model = fixture.regla("C4") }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Sin lectura: falta la tarifa").assertExists()
        compose.onAllNodes(hasContentDescription("pesos", substring = true)).assertCountEquals(0)
    }

    @Test fun `reduced motion settles within 150ms and fades out without a retained band`() {
        var visible by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { KomparaTheme { ReglaPresence(visible, true) { Regla(fixture.regla("C1")) } } }
        compose.mainClock.advanceTimeBy(150)
        compose.onNodeWithContentDescription("Arriba de tu meta: 9.49 pesos netos por km").assertIsDisplayed()
        compose.runOnIdle { visible = false }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithContentDescription("Arriba de tu meta: 9.49 pesos netos por km").assertDoesNotExist()
    }

    @Test fun `reviewed frame state screenshots and greyscale silhouettes`() {
        var model by mutableStateOf(fixture.regla("C1"))
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(393)
        var compact by mutableStateOf(false)
        var status by mutableStateOf(false)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
            KomparaTheme(darkTheme = dark) {
                Box(Modifier.width(width.dp).height(852.dp).background(if (dark) Color(0xFF262C33) else Color(0xFFEBEEF4)),
                    contentAlignment = Alignment.Center) {
                    if (status) Column {
                        listOf(StatusGlyph.HEALTHY to "Semáforo funcionando", StatusGlyph.WAITING to "Semáforo preparado",
                            StatusGlyph.OFF to "Semáforo apagado", StatusGlyph.PAUSED to "Semáforo en pausa",
                            StatusGlyph.DONE to "Lectura comprobada", StatusGlyph.ATTENTION to "Batería restringida").forEach { (glyph, text) ->
                            StatusLine(text, glyph)
                        }
                    } else Regla(model, Modifier.testTag("plate"), compact, dark)
                }
            }
        } }
        data class Case(val id: String, val frame: String = id, val scale: Float = 1f, val width: Int = 393,
            val compact: Boolean = frame == "C12", val grey: Boolean = false, val model: ReglaModel? = null)
        val cases = buildList {
            listOf("C1", "C2", "C3", "C4", "C5", "C7", "C12", "N8", "N9", "N10").forEach { add(Case(it)) }
            for (id in listOf("N8", "N9", "N10")) { add(Case("${id}_1.3", id, 1.3f, 320)); add(Case("${id}_2.0", id, 2f, 560)) }
            add(Case("C10", "C1", 1.3f)); add(Case("C12_1.3", "C12", 1.3f))
            add(Case("C1_narrow", "C1", width = 320)); add(Case("C7_2.0", "C7", 2f, 560))
            // G4's Semáforo screen is retired (WP-F, J4); its grey chip went with it.
            for (id in listOf("C1", "C2", "C3", "C4", "C5", "C7", "C12")) add(Case("${id}_grey", id, grey = true))
            for (id in listOf("C2", "C3", "C4", "C5")) add(Case("${id}_compact_grey", id, compact = true, grey = true))
            add(Case("cents", model = fixture.regla("C1").copy(goal = BigDecimal("9.50"), minimum = BigDecimal("6.00"),
                band = ReglaBand.Rated(ReglaPosition.MEETS, (fixture.regla("C1").band as ReglaBand.Rated).rate))))
            add(Case("hour", model = fixture.regla("C1").copy(unit = ReglaUnit.HOUR, goal = BigDecimal(200), minimum = BigDecimal(150),
                band = ReglaBand.Rated(ReglaPosition.ABOVE, BigDecimal("284.75")))))
        }
        cases.forEach { case ->
            compose.runOnIdle {
                model = case.model ?: fixture.regla(case.frame); dark = case.frame.startsWith("N"); scale = case.scale
                width = case.width; compact = case.compact; status = case.frame == "G4"
            }
            compose.waitForIdle(); assertLabelsFit()
            if (case.id == case.frame && case.frame != "G4") mx.kompara.ui.contract.FrameContract.assertFrame(compose, case.frame)
            val bitmap = compose.runOnIdle {
                val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
                Bitmap.createBitmap(case.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
            val rendered = if (!case.grey) bitmap else Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also {
                Canvas(it).drawBitmap(bitmap, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) })
            }
            val name = "regla_${case.id}"
            val output = File("build/outputs/v3-screenshots/$name.png").apply { parentFile!!.mkdirs() }
            output.outputStream().use { rendered.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val expected = File("src/test/snapshots/v3/$name.png")
            if (System.getProperty("v3.recordScreenshots") == "true") output.copyTo(expected, overwrite = true)
            else {
                assertTrue("Missing reviewed baseline: $expected", expected.exists())
                assertTrue("Screenshot changed: $output", BitmapFactory.decodeFile(expected.path).sameAs(rendered))
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
}
