package mx.kompara.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.RateBasis
import mx.kompara.ui.components.*
import mx.kompara.ui.fixtures.SettingsFixture
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
class LimitsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val fixture get() = SettingsFixture(ApplicationProvider.getApplicationContext())
    @Test fun `changed cents reach every opener while only the named historical limit gets a suffix`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val day = mx.kompara.ui.fixtures.DayFixture(context).day()
        val saved = LimitDraft.from(day.settings.effectiveThreshold, day.settings.preferredMetric)
        var draft by mutableStateOf(saved.edit(LimitField.MINIMUM, "6.50"))
        var opener by mutableIntStateOf(0)
        compose.setContent { KomparaTheme(darkTheme = true) {
            key(opener, draft) {
                val settings = day.settings.copy(threshold = draft.threshold(), preferredMetric = draft.metric)
                when (opener) {
                    2 -> AjustesContent(state = AjustesState(limits = ThresholdEditor.summary(draft)))
                    else -> {
                        val changed = DayPresentation(day.snapshot, settings)
                        val model = changed.offer(day.rows.first { day.clock(it) == "11:20" }.offerId)!!
                        SheetContent(model.title) { OfferDetailContent(model, {}, {}, {}, {}) }
                    }
                }
            }
        } }
        for (newDraft in listOf(saved.edit(LimitField.MINIMUM, "6.50"), saved.edit(LimitField.GOAL, "9.50"),
            saved.copy(metric = PreferredMetric.IPH).edit(LimitField.GOAL, "315.25"))) {
            compose.runOnIdle { draft = newDraft }
            for (i in listOf(2, 3)) {
                compose.runOnIdle { opener = i }
                val expected = when (i) {
                    2 -> ThresholdEditor.summary(newDraft)
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
    @Test fun `typing selects only number and stepping names the chosen unit`() {
        var state by mutableStateOf(fixture.limits("saved"))
        var clicked: Pair<LimitField, Boolean>? = null
        compose.setContent { KomparaTheme { ThresholdsContent(state,
            { field, text -> state = state.copy(draft = state.draft!!.edit(field, text)) }, { f, up -> clicked = f to up },
            { state = state.copy(draft = state.draft!!.copy(metric = it)) }, {}, {}, {}, {}) } }
        compose.onNodeWithTag("limits-save").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Tu mínimo").performScrollTo().performClick().assert(hasText("6.00"))
        val selection = compose.onNodeWithContentDescription("Tu mínimo").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange]
        assertEquals(androidx.compose.ui.text.TextRange(0, 4), selection)
        compose.onNodeWithContentDescription("Tu mínimo").performTextInput("6,50")
        compose.onNodeWithContentDescription("Tu mínimo").assert(hasText("6,50"))
        compose.onNodeWithTag("limits-save").assertIsEnabled()
        compose.onNodeWithContentDescription("Subir tu mínimo $0.50").performScrollTo().performClick()
        assertEquals(LimitField.MINIMUM to true, clicked)
        compose.onNodeWithText("Por hora").performClick()
        compose.onNodeWithContentDescription("Subir tu meta $10").performScrollTo().performClick()
        assertEquals(LimitField.GOAL to true, clicked)
    }
    @Test fun `neto and bruto are chosen beside the unit and a staged switch offers Cancelar`() {
        var state by mutableStateOf(fixture.limits("saved"))
        val chosen = mutableListOf<RateBasis>(); var resets = 0
        compose.setContent { KomparaTheme { ThresholdsContent(state, { _, _ -> }, { _, _ -> }, {}, { resets++ }, {}, {}, {},
            choose = { chosen += it }) } }
        compose.onNodeWithText("Neto: ya restamos tu costo de $2.84 por km.").assertExists()
        compose.onNodeWithText("Volver a $8.00 y $6.00").assertExists()
        compose.onNodeWithText("Bruto").performClick()
        assertEquals(listOf(RateBasis.GROSS), chosen)
        compose.runOnIdle { state = fixture.limits("J7") }
        compose.onNodeWithText("Bruto: la tarifa entre los km, sin restar tu costo.").assertExists()
        compose.onNodeWithText("Son tus $8.00 y $6.00 neto más tu costo de $2.84, redondeados.").performScrollTo().assertExists()
        compose.onNodeWithTag("limits-save").assertIsEnabled()
        compose.onNodeWithText("Cancelar").performClick(); assertEquals(1, resets)
        // An edit after the switch drops the explanation: the numbers are no longer the converted ones.
        compose.runOnIdle { state = state.copy(draft = state.draft!!.edit(LimitField.GOAL, "12.00")) }
        compose.onNodeWithText("Son tus", substring = true).assertDoesNotExist()
        compose.onNodeWithText("De $0.50 en $0.50. Toca la cifra para escribirla.").performScrollTo().assertExists()
        // Without a known cost there is nothing to convert with: the choice is shown but cannot change.
        compose.runOnIdle { state = fixture.limits("saved").copy(cost = null) }
        compose.onNode(hasText("Bruto") and isSelectable()).assertIsNotEnabled()
    }
    @Test fun `back asks and discard closes without invoking save while scrim keeps draft`() {
        var state by mutableStateOf(fixture.limits("J2"))
        var saves = 0; var closes = 0
        compose.setContent { KomparaTheme { ThresholdsContent(state, { _, _ -> }, { _, _ -> }, {}, {}, { saves++ },
            { state = state.copy(confirmExit = it) }, { closes++ }) } }
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("¿Guardar el cambio?").assertExists()
        compose.onNodeWithText("Tu mínimo: $6.50/km").assertExists()
        compose.onNodeWithText("No guardar").performClick()
        assertEquals(1, closes); assertEquals(0, saves)
    }
    @Test fun `semaforo links and recovery dispatch correct routes and nothing pauses`() {
        val f = fixture
        var state by mutableStateOf(semaforoState(f.readiness(), f.zone)); var help = 0
        var action: SemaforoAction? = null
        compose.setContent { KomparaTheme { SemaforoContent(state, {}, { help++ }, { action = it }) } }
        compose.onNodeWithText("¿No ves el semáforo?").performScrollTo().performClick(); assertEquals(1, help)
        compose.onNodeWithText("Qué lee Kompara").performScrollTo().performClick()
        compose.onNodeWithText("Uber y DiDi no son parte de Kompara", substring = true).assertExists()
        compose.onNodeWithText("Pausar", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Reanudar", substring = true).assertDoesNotExist()
        compose.runOnIdle { state = semaforoState(f.readiness("off"), f.zone) }
        compose.onNodeWithText("Falta el permiso de Accesibilidad").assertExists()
        compose.onNodeWithText("Volver a dar el permiso").performClick(); assertEquals(SemaforoAction.PERMISSION, action)
    }
    @Test fun `footer slots do not jump and the secondary action follows the draft`() {
        var state by mutableStateOf(fixture.limits("saved"))
        compose.setContent { KomparaTheme { ThresholdsContent(state, { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}) } }
        val before = compose.onNodeWithTag("limits-save").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("limits-reset").assertIsNotEnabled()
        compose.runOnIdle { state = fixture.limits("J2") }
        assertEquals(before, compose.onNodeWithTag("limits-save").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("limits-reset").assertIsEnabled()
    }
    @Test fun `held step repeats and releases without a trailing change`() {
        var steps = 0
        compose.setContent { KomparaTheme { ThresholdsContent(fixture.limits("saved"), { _, _ -> }, { _, _ -> steps++ }, {}, {}, {}, {}, {}) } }
        compose.onNodeWithContentDescription("Subir tu mínimo $0.50").performScrollTo()
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
    @Test fun `every opener reserves snackbar space without covering rows or footer actions`() {
        var opener by mutableIntStateOf(2)
        val message = "Guardado: tu mínimo ahora es $6.50/km"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent { KomparaTheme {
            key(opener) {
                val snackbar = remember { SaveSnackbarState().apply { saved(message) } }
                CompositionLocalProvider(LocalLimitsSnackbar provides snackbar) {
                    when (opener) {
                        2 -> AjustesContent(state = fixture.ajustes())
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
        for (i in listOf(2, 3)) {
            compose.runOnIdle { opener = i }
            compose.onNodeWithText(message).assertIsDisplayed()
            val toast = compose.onNodeWithText(message).fetchSemanticsNode().boundsInRoot
            if (i == 3) {
                val bounds = compose.onNodeWithText("Cambiar tu meta y tu mínimo").fetchSemanticsNode().boundsInRoot
                assertTrue("opener $i has clearance", toast.bottom + 8 <= bounds.top)
            }
            if (i == 2) assertTrue("settings bottom margin", 852 - toast.bottom >= 16)
        }
    }
}
