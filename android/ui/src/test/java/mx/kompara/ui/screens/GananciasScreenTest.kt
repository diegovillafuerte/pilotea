package mx.kompara.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import mx.kompara.ui.contract.v4.V4Contract
import mx.kompara.ui.contract.v4.V4Snapshot
import mx.kompara.ui.fixtures.GananciasFixture
import mx.kompara.ui.fixtures.GananciasFixtureHost
import mx.kompara.ui.stats.DayDetailUiState
import mx.kompara.ui.theme.KomparaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Ganancias (v4 L1–L8) in the real shell on the kit's fixture: its strings, its doors and its reviewed baselines. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GananciasScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val fixture get() = GananciasFixture(ApplicationProvider.getApplicationContext())

    @Test fun `L1 to L8 show the kit's strings and match their reviewed baselines with nothing clipped`() {
        var frame by mutableStateOf("L1")
        var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(393)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                KomparaTheme(darkTheme = dark) {
                    Box(Modifier.width(width.dp).height(852.dp)) { key(frame) { GananciasFixtureHost(frame, Modifier.fillMaxSize()) } }
                }
            }
        }
        val frames = listOf("L1", "L2", "L3", "L4", "L5", "L6", "L7", "L8")
        val cases = frames.flatMap { listOf(Case(it, false, 1f, 393), Case(it, true, 1f, 393), Case(it, false, 1.3f, 393), Case(it, true, 2f, 320)) }
        for (case in cases) {
            compose.runOnIdle { frame = case.frame; dark = case.dark; font = case.font; width = case.width }
            compose.waitForIdle()
            V4Contract.assertFrame(compose, case.frame)
            val name = V4Snapshot.name("ganancias", case.frame, case.dark, case.width, case.font)
            V4Snapshot.assertNothingClipped(compose, name)
            V4Snapshot.assertMatchesBaseline(name, compose.runOnIdle { V4Snapshot.capture(compose.activity, case.width) })
        }
    }
    private data class Case(val frame: String, val dark: Boolean, val font: Float, val width: Int)

    @Test fun `Hoy and Semana open the Semáforo screen the offer sheet the calculation and the days`() {
        val f = fixture
        val day = f.dayPresentation("L1")
        var period by mutableIntStateOf(0)
        var week by mutableStateOf(f.weekState("L4"))
        val opened = mutableListOf<String>()
        compose.setContent { KomparaTheme { GananciasContent(f.ganancias("L1"), period, DayDetailUiState.Loaded(day), week, GananciasActions(
            period = { period = it }, semaforo = { opened += "semaforo" }, offer = { opened += "offer:$it" }, calculation = { opened += "calculation" },
            day = { opened += "day:$it" }, previousWeek = { opened += "previous" }, nextWeek = { opened += "next" }, weekCalculation = { opened += "week" })) } }
        compose.onNodeWithText("Semáforo funcionando").performClick()
        compose.onNodeWithTag("calculation").performClick()
        val live = day.rows.first { day.clock(it) == "14:32" }.offerId
        compose.onNodeWithTag("offer-$live").performClick()
        compose.onNodeWithTag("offer-${day.rows.last().offerId}").performScrollTo().assertIsDisplayed()
        assertEquals(listOf("semaforo", "calculation", "offer:$live"), opened)
        compose.onNode(isSelectable() and hasText("Semana")).performClick()
        assertEquals(1, period)
        compose.onNodeWithText("Ganaste").assertExists()
        compose.onNodeWithContentDescription("Viernes 16 oct: $1,380").performClick()
        compose.onNodeWithContentDescription("Semana anterior").performClick()
        compose.onNodeWithContentDescription("Semana siguiente").assertIsEnabled().performClick()
        compose.onNodeWithTag("calculation").performClick()
        assertEquals(listOf("day:2026-10-16", "previous", "next", "week"), opened.drop(3))
        compose.runOnIdle { week = f.weekState("L5"); opened.clear() }
        compose.onNodeWithText("19 – 25 oct · en curso").assertExists()
        compose.onNodeWithContentDescription("Semana siguiente").assertIsNotEnabled()
        // A day that has not come yet opens nothing.
        compose.onNodeWithContentDescription("Martes 20 oct").performClick()
        assertTrue(opened.isEmpty())
    }

    @Test fun `an empty Hoy offers yesterday and no offer list`() {
        val f = fixture
        var opened: LocalDate? = null
        compose.setContent { KomparaTheme { GananciasContent(f.ganancias("empty"), 0, DayDetailUiState.Loaded(f.dayPresentation("empty")),
            f.weekState("L5"), GananciasActions(day = { opened = it })) } }
        compose.onNodeWithText("$0").assertExists()
        compose.onNodeWithText("Aún no hay viajes hoy").assertExists()
        compose.onNodeWithText("Ofertas de hoy").assertDoesNotExist()
        compose.onNodeWithText("Semáforo preparado", substring = true).assertExists()
        compose.onNodeWithText("Ayer, lunes 19 oct, ganaste $1,240.").performClick()
        assertEquals(f.date, opened)
    }

    @Test fun `the first day opens the limits the costs and the driver's ride app`() {
        val f = fixture
        val opened = mutableListOf<String>()
        compose.setContent { KomparaTheme { GananciasContent(f.ganancias("L2"), 0, DayDetailUiState.Loading(""), f.weekState("L5"),
            GananciasActions(limits = { opened += "limits" }, costs = { opened += "costs" }, openUber = { opened += "uber" })) } }
        compose.onNodeWithText("Hoy").assertDoesNotExist()
        compose.onNodeWithText("Tu meta $8.00 · tu mínimo $6.00").performClick()
        compose.onNodeWithText("Manejar te cuesta $2.84 por km").performClick()
        compose.onNodeWithText("Abrir Uber para probar").performClick()
        assertEquals(listOf("limits", "costs", "uber"), opened)
        compose.onNodeWithContentDescription("9.49 pesos por kilómetro, neto", substring = true).assertExists()
    }

    @Test fun `off the tab becomes the cause with the permission and battery buttons`() {
        val f = fixture
        val opened = mutableListOf<String>()
        compose.setContent { KomparaTheme { GananciasContent(f.ganancias("L3"), 0, DayDetailUiState.Loading(""), f.weekState("L5"),
            GananciasActions(semaforo = { opened += "semaforo" }, recovery = { opened += "recovery" }, battery = { opened += "battery" },
                day = { opened += "day:$it" })) } }
        compose.onNodeWithText("Semana").assertDoesNotExist()
        compose.onNodeWithText("Semáforo apagado", substring = true).assertHasNoClickAction()
        compose.onNodeWithText("Falta el permiso de Accesibilidad").assertExists()
        compose.onNodeWithText("Hoy no hemos leído ofertas.").assertExists()
        compose.onNodeWithText("Volver a dar el permiso").performClick()
        compose.onNodeWithText("Que no se vuelva a apagar").performClick()
        compose.onNodeWithText("Ayer, lunes 19 oct, ganaste $1,240.").performClick()
        assertEquals(listOf("recovery", "battery", "day:2026-10-19"), opened)
    }

    @Test fun `a report shows its text before sharing and removing a trip asks first`() {
        val day = fixture.dayPresentation("L1")
        val model = day.offer(day.rows.first { day.clock(it) == "14:32" }.offerId)!!
        var cancellations = 0
        compose.setContent { KomparaTheme { OfferDetailSheet(model, day.date.toString(), {}, { cancellations++ }, reportAvailable = true) } }
        compose.onNodeWithText("Avisar que se leyó mal").performScrollTo().performClick()
        compose.onNodeWithText("Se compartirán la fecha", substring = true).assertExists()
        compose.onNodeWithText("Cancelar").performClick()
        assertNull(org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivity)
        compose.onNodeWithText("Quitar de mi día").performScrollTo().performClick()
        compose.onNodeWithText("¿Se canceló el viaje?").assertExists()
        assertEquals(0, cancellations)
        compose.onAllNodesWithText("Quitar de mi día").onLast().performClick()
        assertEquals(1, cancellations)
        compose.onNodeWithText("Avisar que se leyó mal").performScrollTo().performClick()
        compose.onNodeWithText("Compartir").performClick()
        val launched = org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivity
        val draft = if (launched.action == android.content.Intent.ACTION_CHOOSER)
            launched.getParcelableExtra(android.content.Intent.EXTRA_INTENT, android.content.Intent::class.java)!! else launched
        assertEquals(android.content.Intent.ACTION_SEND, draft.action)
        assertEquals(offerReadingReport(model, day.date.toString()), draft.getStringExtra(android.content.Intent.EXTRA_TEXT))
        assertFalse(draft.hasExtra(android.content.Intent.EXTRA_STREAM))
    }

    @Test fun `without configured support the sheet keeps only the local action`() {
        val day = fixture.dayPresentation("L1")
        compose.setContent { KomparaTheme { OfferDetailSheet(day.offer(day.rows.first { day.clock(it) == "14:32" }.offerId)!!, day.date.toString(), {}, {},
            reportAvailable = false) } }
        compose.onNodeWithText("Avisar que se leyó mal").assertDoesNotExist()
        compose.onNodeWithText("Quitar de mi día").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("$9.49 por km neto · arriba de tu meta de $8.00").assertExists()
    }
}
