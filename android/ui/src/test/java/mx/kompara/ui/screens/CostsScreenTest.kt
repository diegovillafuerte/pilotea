package mx.kompara.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import mx.kompara.ui.components.*
import mx.kompara.ui.fixtures.*
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
class CostsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val fixture get() = CostsFixture(ApplicationProvider.getApplicationContext())
    @Test fun `settings costs car and efficiency render without clipping at supported sizes`() {
        var frame by mutableStateOf("G1")
        var scale by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(393)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                KomparaTheme(darkTheme = width == 320) {
                    Box(Modifier.width(width.dp).height(852.dp)) { key(frame, scale, width) { CostsFixtureHost(frame) } }
                }
            }
        }
        val cases = listOf("G1", "G1b", "G1e", "G1n", "G3", "G5", "A5", "electric", "direct").map { Triple(it, 1f, 393) } +
            listOf("G1", "G3", "G5", "A5").map { Triple(it, 1.3f, 393) } +
            listOf("G1n", "G3", "G5", "A5").map { Triple(it, 2f, 320) }
        for ((id, font, size) in cases) {
            compose.runOnIdle { frame = id; scale = font; width = size }; compose.waitForIdle()
            mx.kompara.ui.contract.FrameContract.assertFrame(compose, id)
            val name = "costs_${id}_${size}_$font"
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
    @Test fun `all settings doors have independent actions and theme is scrollable`() {
        val clicks = mutableListOf<String>()
        compose.setContent { KomparaTheme { AjustesContent(state = fixture.settings("G1e"), actions = AjustesActions(
            account = { clicks += "account" }, plan = { clicks += "plan" }, limits = { clicks += "limits" }, costs = { clicks += "costs" },
            car = { clicks += "car" }, reader = { clicks += "reader" }, notices = { clicks += "notices" }, referral = { clicks += "referral" },
            help = { clicks += "help" }, data = { clicks += "data" }, theme = { clicks += "theme" })) } }
        listOf("Entrar con WhatsApp", "Plan gratis", "Tu meta y tu mínimo", "Tus costos", "Lo del coche", "Semáforo", "Avisos", "Invita y gana", "Ayuda", "Tus datos", "Tema").forEach {
            compose.onNodeWithText(it).performScrollTo().performClick()
        }
        assertEquals(listOf("account", "plan", "limits", "costs", "car", "reader", "notices", "referral", "help", "data", "theme"), clicks)
        compose.onNodeWithText("Sin agregar").assertExists()
        compose.onNodeWithText("Prueba Premium 14 días").assertDoesNotExist()
    }
    @Test fun `formula rows open their own edits and no idle save or car appears`() {
        var selected: CostField? = null
        compose.setContent { KomparaTheme { CostProfileContent(fixture.editor("G3"), {}, { selected = it }) } }
        compose.onNodeWithText("Guardar").assertDoesNotExist(); compose.onNodeWithText("Lo del coche").assertDoesNotExist()
        for (field in listOf(CostField.FUEL, CostField.EFFICIENCY, CostField.PRICE, CostField.WEAR, CostField.DIRECT)) {
            compose.onNodeWithText(field.title).performScrollTo().performClick(); assertEquals(field, selected)
        }
    }
    @Test fun `input sheets switch fuel retain values and accept direct numeric entry`() {
        var state by mutableStateOf(fixture.editor("G3").let { it.copy(field = CostField.FUEL) })
        var saves = 0
        compose.setContent { KomparaTheme { SheetContent("Combustible") { CostInputContent(state, { state = state.copy(draft = it) }, { saves++ }) } } }
        compose.onNodeWithText("Eléctrico").performClick()
        compose.onNodeWithContentDescription("kWh por 100 km").performTextInput("18")
        compose.onNodeWithContentDescription("Precio por kWh").performTextInput("3,50")
        compose.onNodeWithText("Gasolina").performClick()
        compose.onNodeWithContentDescription("km por litro").assert(hasText("12"))
        compose.onNodeWithText("Eléctrico").performClick()
        compose.onNodeWithContentDescription("kWh por 100 km").assert(hasText("18"))
        compose.onNodeWithTag("cost-save").performScrollTo().performClick(); assertEquals(1, saves)
        compose.runOnIdle { state = state.copy(field = CostField.DIRECT, draft = state.draft!!.copy(direct = true)) }
        compose.onNodeWithContentDescription("Costo por km").performTextInput("0")
        assertEquals(java.math.BigDecimal.ZERO, CostPreview.compute(state.draft!!)!!.perKm)
    }
    @Test fun `efficiency steps update preview and large type keeps footer reachable`() {
        var state by mutableStateOf(fixture.editor("A5"))
        var saves = 0; var direct = 0
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) { KomparaTheme {
            Box(Modifier.width(320.dp).height(640.dp)) { EfficiencyContent(state, {}, { state = state.copy(draft = it) }, { saves++ }, { direct++ }) }
        } } }
        compose.onNodeWithText("Usar 12 km/L").assertIsDisplayed()
        compose.onNodeWithContentDescription("Subir rendimiento").performScrollTo().performClick()
        assertEquals("13", state.draft!!.rendimientoKmPerLitre)
        compose.onNodeWithText("Continuar").performClick(); assertEquals(1, saves)
        compose.onNodeWithText("Ya sé mi costo por km").performClick(); assertEquals(1, direct)
    }
    @Test fun `weekly grouping preserves every cursor boundary and decimal digit`() {
        val rendered = mx.kompara.ui.format.DecimalGrouping.filter(androidx.compose.ui.text.AnnotatedString("1500.25"))
        assertEquals("1,500.25", rendered.text.text)
        for (offset in 0..7) assertEquals(offset, rendered.offsetMapping.transformedToOriginal(rendered.offsetMapping.originalToTransformed(offset)))
    }

    @Test fun `car sheet keeps daily calendar explanation and actual numeric amount editable`() {
        var state by mutableStateOf(fixture.car())
        var saves = 0; var removes = 0
        compose.setContent { KomparaTheme { SheetContent("Lo del coche") { CarCostContent(state, { state = state.copy(amount = it) }, {}, { saves++ }, { removes++ }) } } }
        compose.onNodeWithText("Se resta de cada día: $214, también los días que no manejas.").assertExists()
        compose.onNodeWithText("Nunca del semáforo.").assertExists()
        compose.onNodeWithContentDescription("Cada semana").performTextReplacement("700")
        compose.onNodeWithText("Se resta de cada día: $100, también los días que no manejas.").assertExists()
        compose.onNodeWithText("Guardar").performScrollTo().performClick(); assertEquals(1, saves)
        compose.onNodeWithText("Quitar").performScrollTo().performClick(); assertEquals(1, removes)
    }
}
