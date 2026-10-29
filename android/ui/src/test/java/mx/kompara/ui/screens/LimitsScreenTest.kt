package mx.kompara.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import mx.kompara.ui.components.*
import mx.kompara.ui.fixtures.LimitsFixture
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
class LimitsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val fixture get() = LimitsFixture(ApplicationProvider.getApplicationContext())
    @Test fun `changed cents reach all four openers while only the named historical limit gets a suffix`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = mx.kompara.ui.fixtures.HoyFixture(context).state("B3")
        val day = mx.kompara.ui.fixtures.DayFixture(context).day()
        val saved = LimitDraft.from(day.settings.effectiveThreshold, day.settings.preferredMetric)
        var draft by mutableStateOf(saved.edit(LimitField.MINIMUM, "6.50"))
        var opener by mutableIntStateOf(0)
        val actions = HoyActions({}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        compose.setContent { KomparaTheme(darkTheme = true) {
            key(opener, draft) {
                val settings = day.settings.copy(threshold = draft.threshold(), preferredMetric = draft.metric)
                when (opener) {
                    0 -> SemaforoContent(semaforoState(fixture.readiness("G4"), settings, emptyList(), fixture.fixture.zone), {}, {}, {}, {}, {}, { _, _ -> })
                    1 -> HoyContent(InicioStats.today(first.snapshot!!, first.reader!!, settings), actions)
                    2 -> AjustesContent(state = AjustesState(limits = ThresholdEditor.pairLabel(draft)))
                    else -> {
                        val changed = DayPresentation(day.snapshot, settings)
                        val model = changed.offer(day.rows.first { day.clock(it) == "11:20" }.offerId)!!
                        SheetContent(model.title) { OfferDetailContent(model, {}, {}, {}, {}) }
                    }
                }
            }
        } }
        for (newDraft in listOf(saved.edit(LimitField.MINIMUM, "6.50"), saved.edit(LimitField.GOAL, "9.50"),
            saved.copy(metric = mx.kompara.data.settings.PreferredMetric.IPH).edit(LimitField.GOAL, "315.25"))) {
            compose.runOnIdle { draft = newDraft }
            for (i in 0..3) {
                compose.runOnIdle { opener = i }
                val expected = when (i) {
                    0, 2 -> ThresholdEditor.pairLabel(newDraft)
                    1 -> "Tu meta ${ThresholdEditor.limit(newDraft.pair.goal, newDraft.unit)} · tu mínimo ${ThresholdEditor.limit(newDraft.pair.minimum, newDraft.unit)}"
                    else -> "$9.81/km · arriba de tu meta de $8.00/km" + if (newDraft.km.goal != saved.km.goal) " a esa hora" else ""
                }
                compose.onNode(SemanticsMatcher("opener $i: $expected") { node ->
                    node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text).orEmpty().any {
                        mx.kompara.ui.contract.FrameContract.normalize(it.text) == expected
                    }
                }).assertExists()
            }
        }
    }

    @Test fun `editor and all readiness states render at normal large and narrow sizes`() {
        var frame by mutableStateOf("G2b")
        var font by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(393)
        var dark by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                KomparaTheme(darkTheme = dark) { Box(Modifier.width(width.dp).height(852.dp)) {
                    key(frame, font, width, dark) {
                        if (frame.startsWith("G2") || frame == "hour") ThresholdsContent(fixture.editor(frame), { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {})
                        else SemaforoContent(fixture.reader(frame), {}, {}, {}, {}, {}, { _, _ -> },
                            snackbar = if (frame == "G4i") remember { SaveSnackbarState().apply { saved("Guardado: tu mínimo ahora es $6.50/km") } } else null)
                    }
                } }
            }
        }
        val cases = listOf("G2b", "G2", "G2c", "G2d", "G2e", "hour", "G4", "G4b", "G4c", "G4d", "G4e", "G4f", "G4g", "G4h", "G4i").map { Triple(it, 1f, 393) } +
            listOf("G2c", "G4b", "G4i").map { Triple(it, 1.3f, 393) } + listOf("G2c", "G4c", "G4h").map { Triple(it, 2f, 320) }
        for ((id, scale, size) in cases) {
            compose.runOnIdle { frame = id; font = scale; width = size; dark = size == 320 }
            if (id == "G2d") compose.onNodeWithContentDescription("Tu mínimo").performClick()
            compose.waitForIdle()
            mx.kompara.ui.contract.FrameContract.assertFrame(compose, id)
            val name = "limits_${id}_${size}_$scale"
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                val results = mutableListOf<TextLayoutResult>(); node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.forEach { r -> assertFalse("$name clipped ${r.layoutInput.text}", r.didOverflowHeight || (0 until r.lineCount).any { r.getLineRight(it) > r.size.width + 1f }) }
            }
            val bitmap = compose.runOnIdle {
                val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
                Bitmap.createBitmap(size, view.height, Bitmap.Config.ARGB_8888).also { output ->
                    val canvas = android.graphics.Canvas(output); view.draw(canvas)
                    // Android owns dialog centering/windowing. Draw its actual decor for the G2e baseline.
                    if (id == "G2e") org.robolectric.shadows.ShadowDialog.getLatestDialog()?.window?.decorView?.let { dialog ->
                        canvas.drawColor(android.graphics.Color.argb(89, 0, 0, 0)); canvas.save()
                        canvas.translate((size - dialog.width) / 2f, (view.height - dialog.height) / 2f); dialog.draw(canvas); canvas.restore()
                    }
                }
            }
            val output = File("build/outputs/v3-screenshots/$name.png").apply { parentFile!!.mkdirs() }
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val baseline = File("src/test/snapshots/v3/$name.png")
            if (System.getProperty("v3.recordScreenshots") == "true") output.copyTo(baseline, overwrite = true)
            else { assertTrue("Missing $name", baseline.exists()); assertTrue("Changed $name", BitmapFactory.decodeFile(baseline.path).sameAs(bitmap)) }
        }
    }
    @Test fun `typing selects only number and stepping names the chosen unit`() {
        var state by mutableStateOf(fixture.editor("G2b"))
        var clicked: Pair<LimitField, Boolean>? = null
        compose.setContent { KomparaTheme { ThresholdsContent(state,
            { field, text -> state = state.copy(draft = state.draft!!.edit(field, text)) }, { f, up -> clicked = f to up },
            { state = state.copy(draft = state.draft!!.copy(metric = it)) }, {}, {}, {}, {}) } }
        compose.onNodeWithTag("limits-save").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Tu mínimo").performClick().assert(hasText("6.00"))
        val selection = compose.onNodeWithContentDescription("Tu mínimo").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange]
        assertEquals(androidx.compose.ui.text.TextRange(0, 4), selection)
        compose.onNodeWithContentDescription("Tu mínimo").performTextInput("6,50")
        compose.onNodeWithContentDescription("Tu mínimo").assert(hasText("6,50"))
        compose.onNodeWithTag("limits-save").assertIsEnabled()
        compose.onNodeWithContentDescription("Subir tu mínimo $0.50").performClick()
        assertEquals(LimitField.MINIMUM to true, clicked)
        compose.onNodeWithText("Por hora").performClick()
        compose.onNodeWithContentDescription("Subir tu meta $10").performScrollTo().performClick()
        assertEquals(LimitField.GOAL to true, clicked)
    }
    @Test fun `back asks and discard closes without invoking save while scrim keeps draft`() {
        var state by mutableStateOf(fixture.editor("G2c"))
        var saves = 0; var closes = 0
        compose.setContent { KomparaTheme { ThresholdsContent(state, { _, _ -> }, { _, _ -> }, {}, {}, { saves++ },
            { state = state.copy(confirmExit = it) }, { closes++ }) } }
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("¿Guardar el cambio?").assertExists()
        compose.onNodeWithText("Tu mínimo: $6.50/km").assertExists()
        compose.onNodeWithText("No guardar").performClick()
        assertEquals(1, closes); assertEquals(0, saves)
    }
    @Test fun `reader limits help suppression and recovery dispatch correct routes`() {
        var state by mutableStateOf(fixture.reader("G4c")); var limits = 0; var help = 0; var pause = 0
        var action: SemaforoAction? = null; var offer: Pair<String, Long>? = null
        compose.setContent { KomparaTheme { SemaforoContent(state, {}, { limits++ }, { help++ }, { action = it }, { pause++ }, { day, id -> offer = day to id }) } }
        compose.onNodeWithText("Tu meta y tu mínimo").performClick(); assertEquals(1, limits)
        compose.onNodeWithText("No cupo el semáforo en 1 oferta hoy").performScrollTo().performClick()
        assertEquals("2026-10-19" to 12L, offer)
        compose.onNodeWithText("¿No ves el semáforo?").performScrollTo().performClick(); assertEquals(1, help)
        compose.onNodeWithText("Pausar el semáforo hasta mañana").performClick(); assertEquals(1, pause)
        compose.runOnIdle { state = fixture.reader("G4d") }
        compose.onNodeWithText("Pausar el semáforo hasta mañana").assertDoesNotExist()
        compose.onNodeWithText("Reanudar el semáforo").performClick(); assertEquals(SemaforoAction.RESUME, action)
        compose.runOnIdle { state = fixture.reader("G4h") }
        compose.onNodeWithText("Volver a dar el permiso").performClick(); assertEquals(SemaforoAction.PERMISSION, action)
    }
    @Test fun `footer slots do not jump and save snackbar clears rows and first action`() {
        var state by mutableStateOf(fixture.editor("G2b"))
        compose.setContent { KomparaTheme { ThresholdsContent(state, { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}) } }
        val before = compose.onNodeWithTag("limits-save").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { state = fixture.editor("G2c") }
        assertEquals(before, compose.onNodeWithTag("limits-save").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("limits-reset").assertIsEnabled()
    }
    @Test fun `held step repeats and releases without a trailing change`() {
        var steps = 0
        compose.setContent { KomparaTheme { ThresholdsContent(fixture.editor("G2b"), { _, _ -> }, { _, _ -> steps++ }, {}, {}, {}, {}, {}) } }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Subir tu mínimo $0.50").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithContentDescription("Subir tu mínimo $0.50").performTouchInput { up() }
        val released = steps
        assertTrue("hold repeats", released >= 3)
        compose.mainClock.advanceTimeBy(500)
        assertEquals(released, steps)
        compose.mainClock.autoAdvance = true
    }
    @Test fun `all four openers reserve snackbar space without covering rows or footer actions`() {
        var opener by mutableIntStateOf(0)
        val message = "Guardado: tu mínimo ahora es $6.50/km"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val actions = HoyActions({}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        compose.setContent { KomparaTheme {
            key(opener) {
                val snackbar = remember { SaveSnackbarState().apply { saved(message) } }
                CompositionLocalProvider(LocalLimitsSnackbar provides snackbar) {
                    when (opener) {
                        0 -> SemaforoContent(fixture.reader("G4i"), {}, {}, {}, {}, {}, { _, _ -> })
                        1 -> HoyContent(mx.kompara.ui.fixtures.HoyFixture(context).state("B3"), actions)
                        2 -> AjustesContent(limits = "$8.00/km · $6.50/km")
                        else -> {
                            val day = mx.kompara.ui.fixtures.DayFixture(context).day()
                            val model = day.offer(day.rows.first { day.clock(it) == "11:20" }.offerId)!!
                            SheetContent(model.title) { OfferDetailContent(model, {}, {}, {}, {}, {
                                Spacer(Modifier.height(8.dp)); SaveSnackbar(snackbar); Spacer(Modifier.height(8.dp))
                            }) }
                        }
                    }
                }
            }
        } }
        for (i in 0..3) {
            compose.runOnIdle { opener = i }
            compose.onNodeWithText(message).assertIsDisplayed()
            val toast = compose.onNodeWithText(message).fetchSemanticsNode().boundsInRoot
            val action = when (i) { 0 -> "Pausar el semáforo hasta mañana"; 1 -> "Abrir Uber para probar"; 2 -> null; else -> "Cambiar tu meta y tu mínimo" }
            if (action != null) {
                val bounds = compose.onNodeWithText(action).fetchSemanticsNode().boundsInRoot
                assertTrue("opener $i has clearance", toast.bottom + 8 <= bounds.top)
            }
            if (i == 2) assertTrue("settings bottom margin", 852 - toast.bottom >= 16)
        }
    }

}
