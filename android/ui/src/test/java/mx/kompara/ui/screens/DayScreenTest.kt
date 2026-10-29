package mx.kompara.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import mx.kompara.ui.components.*
import mx.kompara.ui.fixtures.DayFixture
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
class DayScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val fixture get() = DayFixture(ApplicationProvider.getApplicationContext())
    private fun DayPresentation.at(time: String) = rows.first { clock(it) == time }.offerId

    @Composable private fun Host(day: DayPresentation, filtered: Boolean = false, filter: () -> Unit = {},
        offer: (Long) -> Unit = {}, ledger: () -> Unit = {}) {
        Scaffold(bottomBar = { RootBottomBar(0, {}) }) { padding ->
            Column(Modifier.padding(padding).consumeWindowInsets(padding)) {
                DayHeader(day.title, {}, {}, {}, true, false, {})
                DayDetailContent(day, filtered, filter, offer, ledger, Modifier.weight(1f))
            }
        }
    }

    @Test fun `complete list filters both missed offers and reaches its oldest record`() {
        val day = fixture.day()
        var filtered by mutableStateOf(false)
        var selected: Long? = null
        compose.setContent { KomparaTheme { Host(day, filtered, { filtered = !filtered }, { selected = it }) } }
        compose.onNodeWithText("2 ofertas arriba de tu meta", substring = true).performClick()
        compose.onNodeWithText("Arriba de tu meta · 2").assertExists()
        compose.onNodeWithTag("offer-${day.at("9:51")}").assertExists()
        compose.onNodeWithTag("offer-${day.at("14:32")}").assertDoesNotExist()
        compose.onNodeWithText("Ver todas las ofertas del día").performScrollTo().performClick()
        compose.onNodeWithTag("day-offers").performScrollToNode(hasTestTag("offer-${day.at("8:15")}"))
        compose.onNodeWithTag("offer-${day.at("8:15")}").performClick()
        assertEquals(day.at("8:15"), selected)
        listOf("Mejores horas", "Turnos", "Vistas", "$648").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }

    @Test fun `past no trip car day exposes its negative hero and calculation door`() {
        val source = fixture.day()
        val window = source.money.window.copy(asOf = source.money.window.asOf.plusSeconds(86_400))
        val money = mx.kompara.data.accounting.PeriodAccounting.calculate(window, emptyList(), fixture.fixture.schedule)
        val day = source.copy(snapshot = source.snapshot.copy(money = money, offers = emptyList(), trips = emptyList()))
        var opened = false
        compose.setContent { KomparaTheme { Host(day, ledger = { opened = true }) } }
        compose.onNodeWithText("Te quedaron el lunes 19 oct · aprox.").assertIsDisplayed()
        compose.onNodeWithText("−$214").assertIsDisplayed()
        compose.onNodeWithText("Sin viajes; lo del coche corre igual").performClick()
        assertTrue(opened)
    }

    @Test fun `own record sheet survives restore ledger dismissal and limits return with frozen judgement`() {
        val day = fixture.day()
        val restore = StateRestorationTester(compose)
        var limits = 0
        restore.setContent {
            var selected by rememberSaveable { mutableStateOf<Long?>(null) }
            var ledger by rememberSaveable { mutableStateOf(false) }
            KomparaTheme {
                Host(day, offer = { selected = it })
                if (ledger) DayCalculationSheet(dayCalculation(day.money)) { ledger = false }
                else selected?.let { day.offer(it) }?.let { model ->
                    OfferDetailSheet(model, day.date.toString(), { selected = null }, { ledger = true }, { limits++ }, {})
                }
            }
        }
        compose.onNodeWithTag("day-offers").performScrollToNode(hasTestTag("offer-${day.at("11:20")}"))
        compose.onNodeWithTag("offer-${day.at("11:20")}").performClick()
        compose.onNodeWithText("$133 sin el coche").assertExists()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Oferta de las 11:20 · La tomaste").assertExists()
        compose.onNodeWithTag("offer-day-ledger").performScrollTo().performClick()
        compose.onNodeWithText("Cómo se calculó · hoy").assertExists()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss) { it() }
        compose.onNodeWithText("$133 sin el coche").assertExists()
        compose.onNodeWithText("Cambiar tu meta y tu mínimo").performScrollTo().performClick()
        assertEquals(1, limits)
        compose.onNodeWithText("$9.81\u2060/\u2060km · arriba de tu meta de $8.00\u2060/\u2060km").assertExists()
    }

    @Test fun `report requires explicit preview consent and cancellation requires its own confirmation`() {
        val day = fixture.day()
        val model = day.offer(day.at("14:32"))!!
        var cancellations = 0
        compose.setContent { KomparaTheme { OfferDetailSheet(model, day.date.toString(), {}, {}, {}, { cancellations++ }) } }
        compose.onNodeWithText("Avisar que se leyó mal").performScrollTo().performClick()
        compose.onNodeWithText("Compartir").assertExists()
        compose.onNodeWithText("Se compartirán la fecha", substring = true).assertExists()
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(0, cancellations)
        assertNull(org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivity)
        compose.onNodeWithText("¿Se canceló? Quitar de mi día").performScrollTo().performClick()
        compose.onNodeWithText("¿Se canceló el viaje?").assertExists()
        assertEquals(0, cancellations)
        compose.onNodeWithText("Quitar de mi día").performClick()
        assertEquals(1, cancellations)
        compose.onNodeWithText("Avisar que se leyó mal").performScrollTo().performClick()
        compose.onNodeWithText("Compartir").performClick()
        val launched = org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivity
        val draft = if (launched.action == android.content.Intent.ACTION_CHOOSER)
            launched.getParcelableExtra(android.content.Intent.EXTRA_INTENT, android.content.Intent::class.java)!! else launched
        assertEquals(android.content.Intent.ACTION_SEND, draft.action)
        assertEquals("text/plain", draft.type)
        assertEquals(offerReadingReport(model, day.date.toString()), draft.getStringExtra(android.content.Intent.EXTRA_TEXT))
        assertFalse(draft.hasExtra(android.content.Intent.EXTRA_STREAM))
    }

    @Test fun `large text and keyboard sized viewport keep snackbar separate and actions above bottom inset`() {
        val day = fixture.day()
        val model = day.offer(day.at("11:20"))!!
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                KomparaTheme {
                    Box(Modifier.width(320.dp).height(420.dp).testTag("sheet-viewport")) {
                        SheetContent(model.title, heading = { OfferDetailHeading(model) }) {
                            OfferDetailContent(model, {}, {}, {}, {}, { SaveSnackbarContent("Guardado: tu mínimo ahora es $6.50/km") })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Guardado: tu mínimo ahora es $6.50/km").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("¿Se canceló? Quitar de mi día").performScrollTo().assertIsDisplayed()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        val action = compose.onNodeWithText("¿Se canceló? Quitar de mi día").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("sheet-viewport").fetchSemanticsNode().boundsInRoot
        assertTrue("24dp bottom inset stays clear", viewport.bottom - action.bottom >= 24f)
    }

    @Test fun `D frames and night have reviewed computed screenshots and no clipped text`() {
        var frame by mutableStateOf("D1a")
        var scale by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(393)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                KomparaTheme(darkTheme = frame == "N3") {
                    val day = fixture.day(late = frame in listOf("D1", "N3", "D2", "D2b", "D2c", "D3"), suppressed = frame in listOf("D1b", "D2d"))
                    Box(Modifier.width(width.dp).height(852.dp)) {
                        Host(day)
                        val time = when (frame) { "D2" -> "14:32"; "D2b", "D2d" -> "12:10"; "D2c" -> "14:50"; "D2e" -> "11:20"; "D2_live" -> "14:32"; "D2_grey" -> "11:48"; else -> null }
                        if (time != null || frame in listOf("D3", "D3a")) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)))
                            // The actual sheet contents, in a deterministic native View host (no OS dialog replica).
                            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 828.dp),
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), color = KomparaTheme.colors.paper) {
                                Column {
                                    Box(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp).size(32.dp, 4.dp).background(KomparaTheme.colors.rule))
                                    if (time != null) {
                                        val model = day.offer(day.at(time))!!
                                        SheetContent(model.title, heading = { OfferDetailHeading(model) }) { OfferDetailContent(model, {}, {}, {}, {}) }
                                    } else {
                                        val calculation = dayCalculation(day.money)
                                        SheetContent(calculation.title) { DayCalculationContent(calculation) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val cases = listOf("D1a", "D1b", "D1", "N3", "D2", "D2_live", "D2b", "D2c", "D2d", "D2e", "D2_grey", "D3", "D3a").map { Triple(it, 1f, 393) } +
            listOf(Triple("N3", 1.3f, 393), Triple("D1a", 1.3f, 393), Triple("D2e", 1.3f, 393), Triple("D2d", 1.3f, 393), Triple("D3a", 1.3f, 393),
                Triple("N3", 2f, 320), Triple("D2e", 2f, 320), Triple("D3a", 2f, 320))
        cases.forEach { (id, font, dpWidth) ->
            compose.runOnIdle { frame = id; scale = font; width = dpWidth }
            compose.waitForIdle()
            mx.kompara.ui.contract.FrameContract.assertFrame(compose, id)
            val name = "day_${id}_${dpWidth}_$font"
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.forEach { result ->
                    assertFalse("$name clipped ${result.layoutInput.text}", result.didOverflowHeight || (0 until result.lineCount).any { result.getLineRight(it) > result.size.width + 1f })
                    Regex("[−]?\\$[0-9,.]+\u2060/\u2060(?:km|h)").findAll(result.layoutInput.text.text).forEach { rate ->
                        assertEquals("$name split rate ${rate.value}", result.getLineForOffset(rate.range.first), result.getLineForOffset(rate.range.last))
                    }
                }
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
    }
}
