package mx.kompara.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import mx.kompara.ui.fixtures.V3ComponentHost
import mx.kompara.ui.fixtures.V3Fixture
import mx.kompara.ui.format.Formatters
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
class V3ComponentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun primaryAndSteppersHaveMinimumTargetsAndCorrectActions() {
        var clicked = 0
        compose.setContent { KomparaTheme {
            Surface {
                Column {
                    PrimaryButton("Guardar", { clicked++ })
                    LimitControl("Tu mínimo", "6.50", "/km", {}, { clicked-- }, { clicked++ }, cue = OfferMarkState.BELOW)
                    KomparaSwitch(true, { clicked++ }, Modifier.testTag("switch"))
                }
            }
        } }
        compose.onNodeWithText("Guardar").assertHeightIsAtLeast(56.dp).performClick()
        compose.onNodeWithContentDescription("Subir tu mínimo $0.50").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("switch").assertHeightIsAtLeast(48.dp).assertIsOn()
        compose.runOnIdle { assertEquals(2, clicked) }
    }

    @Test fun longAmountsAndUnitsRemainEditableAndComplete() {
        var amount by mutableStateOf("99.99")
        compose.setContent { KomparaTheme {
            Column(Modifier.width(320.dp)) {
                LimitControl("Tu meta", amount, "/km", { amount = it }, {}, {})
                FigureRow("−$11,692") { PeriodBars(emptyList()) }
                OfferRow("La tomaste", "14:32 · Cumple tu mínimo", "$1,200/h", OfferMarkState.MEETS, {})
            }
        } }
        compose.onNode(hasSetTextAction()).performTextReplacement("6.50")
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("6.50")))
        compose.onNodeWithText("−$11,692").assertExists()
        compose.onNodeWithText("$1,200/h", substring = true).assertExists()
        compose.runOnIdle { assertEquals("6.50", amount) }
    }

    @Test fun unreadRateIsAbsentAndStatusGlyphsAreDecorative() {
        compose.setContent { KomparaTheme {
            Column {
                OfferRow("Sin confirmar", "11:48 · No pudimos leer la tarifa", "$0.00/km", OfferMarkState.UNKNOWN, {})
                StatusDoor("Semáforo preparado", "Esperando una oferta", StatusGlyph.WAITING, {})
            }
        } }
        compose.onNodeWithText("$0.00/km").assertDoesNotExist()
        compose.onNodeWithText("Semáforo preparado", substring = true).assertHasClickAction().assertHeightIsAtLeast(48.dp)
        compose.onAllNodes(hasContentDescription("WAITING")).assertCountEquals(0)
    }

    @Test fun periodPickerPrecedesStepsAndFutureIsDisabled() {
        compose.setContent { KomparaTheme {
            PeriodSelector("12 – 18 oct", PeriodUnit.WEEK, {}, {}, {}, true, false)
        } }
        compose.onNodeWithText("12 – 18 oct").assertHasClickAction().assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TraversalIndex, 0f))
        compose.onNodeWithText("Semana anterior")
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TraversalIndex, 1f))
        compose.onNodeWithText("Semana siguiente").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TraversalIndex, 2f))
    }

    @Test fun shortMonthTabHasFullTargetWithoutOverlappingItsNeighbor() {
        var selected by mutableIntStateOf(0)
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
            KomparaTheme {
                TextTabs(listOf("Semana", "Mes"), selected, { selected = it }, Modifier.width(320.dp))
            }
        } }
        for (scale in listOf(1f, 1.3f, 2f)) {
            compose.runOnIdle { fontScale = scale }
            val week = compose.onNodeWithText("Semana")
            val month = compose.onNodeWithText("Mes")
            week.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            month.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            val weekBounds = week.fetchSemanticsNode().boundsInRoot
            val monthBounds = month.fetchSemanticsNode().boundsInRoot
            assertFalse("Period tab targets overlap at $scale", weekBounds.overlaps(monthBounds))
            month.performClick().assertIsSelected()
            week.performClick().assertIsSelected()
        }
    }

    @Test fun saveNoticeExpiresAfterFourSecondsAndAnnouncesPolitely() {
        val state = SaveSnackbarState().apply { saved("Guardado: tu mínimo ahora es $6.50/km") }
        compose.mainClock.autoAdvance = false
        compose.setContent { KomparaTheme { SaveSnackbar(state) } }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Guardado: tu mínimo ahora es $6.50/km").assertExists()
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.LiveRegion,
                androidx.compose.ui.semantics.LiveRegionMode.Polite))
        compose.mainClock.advanceTimeBy(4_100)
        compose.onNodeWithText("Guardado: tu mínimo ahora es $6.50/km").assertDoesNotExist()
    }

    @Test fun fixtureIsComputedAndClockIsFixed() {
        val fixture = V3Fixture(ApplicationProvider.getApplicationContext<Context>())
        assertEquals("$548", Formatters.formatMxnWhole(fixture.today))
        assertEquals("$1,026", Formatters.formatMxnWhole(fixture.closedDay))
        assertEquals("$4,980", Formatters.formatMxnWhole(fixture.weekNet))
        assertEquals("$10.34/km", Formatters.formatPerKm(fixture.rate("didi")))
        assertEquals("$1.21", Formatters.formatMxn(fixture.rate("didi").subtract(fixture.rate("uber"))))
        assertEquals("2026-10-19T20:40:00Z", java.time.Instant.ofEpochMilli(fixture.clock.nowMs()).toString())
    }

    @Test fun actualStringGoldensDayNightAndLargeFonts() {
        var frame by mutableStateOf("B1")
        var dark by mutableStateOf(false)
        var fontScale by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(393)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                KomparaTheme(darkTheme = dark) { V3ComponentHost(frame, Modifier.width(width.dp).height(852.dp).testTag("fixture")) }
            }
        }
        val cases = buildList {
            for (id in listOf("B1", "D1", "E1", "F1", "G2", "G4", "H5")) {
                add(RenderCase(id, false, 1f, 393)); add(RenderCase(id, true, 1f, 393))
                add(RenderCase(id, false, 1.3f, 393)); add(RenderCase(id, true, 2f, 320))
            }
        }
        cases.forEach { case ->
            compose.runOnIdle { frame = case.frame; dark = case.dark; fontScale = case.fontScale; width = case.width }
            compose.waitForIdle()
            val bitmap = compose.runOnIdle {
                val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
                Bitmap.createBitmap(case.width, view.height, Bitmap.Config.ARGB_8888).also {
                    view.draw(android.graphics.Canvas(it))
                }
            }
            val output = File("build/outputs/v3-screenshots/${case.name}.png").apply { parentFile!!.mkdirs() }
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // All composed labels, including below the scroll viewport, must retain complete text.
            compose.onAllNodes(hasTextLayout(), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.forEach { result ->
                    assertFalse("${case.name}: clipped ${result.layoutInput.text}; size=${result.size}, width=${result.didOverflowWidth}, height=${result.didOverflowHeight}", result.didOverflowHeight || (0 until result.lineCount).any { result.getLineRight(it) > result.size.width + 1f })
                }
            }
            val golden = File("src/test/snapshots/v3/${case.name}.png")
            if (System.getProperty("v3.recordScreenshots") == "true") {
                golden.parentFile!!.mkdirs(); output.copyTo(golden, overwrite = true)
            } else {
                assertTrue("Missing reviewed baseline: $golden", golden.exists())
                val expected = BitmapFactory.decodeFile(golden.path)
                assertTrue("Screenshot changed: $output", expected.sameAs(bitmap))
            }
        }
    }

    private fun hasTextLayout() = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)
    private data class RenderCase(val frame: String, val dark: Boolean, val fontScale: Float, val width: Int) {
        val name = "${frame}_${if (dark) "night" else "day"}_${width}_${fontScale}"
    }
}
