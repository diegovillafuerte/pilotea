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
import mx.kompara.data.model.City
import mx.kompara.ui.fixtures.PrivacyFixtureHost
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
class PrivacyScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun `profile renders at normal large and narrow sizes`() {
        var frame by mutableStateOf("G9")
        var scale by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(393)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
            KomparaTheme(darkTheme = width == 320) {
                Box(Modifier.width(width.dp).height(852.dp)) { key(frame, scale, width) { PrivacyFixtureHost(frame) } }
            }
        } }
        val cases = listOf("G9", "profile").map { Triple(it, 1f, 393) } + Triple("G9", 1.3f, 393) + Triple("profile", 2f, 320)
        for ((id, font, size) in cases) {
            compose.runOnIdle { frame = id; scale = font; width = size }; compose.waitForIdle()
            mx.kompara.ui.contract.FrameContract.assertFrame(compose, id)
            val name = "privacy_${id}_${size}_$font"
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                val results = mutableListOf<TextLayoutResult>(); node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.forEach { r -> assertFalse("$name clipped ${r.layoutInput.text}", r.didOverflowHeight || (0 until r.lineCount).any { r.getLineRight(it) > r.size.width + 1f }) }
            }
            val bitmap = compose.runOnIdle {
                val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
                Bitmap.createBitmap(size, view.height, Bitmap.Config.ARGB_8888).also { output ->
                    val canvas = android.graphics.Canvas(output); view.draw(canvas)
                    if (id == "G9") org.robolectric.shadows.ShadowDialog.getLatestDialog()?.window?.decorView?.let { dialog ->
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
    @Test fun `help has five questions the first open and entry opens only its requested answer`() {
        var entry by mutableStateOf<String?>(null)
        compose.setContent { KomparaTheme { key(entry) { HelpContent(initialAnswer = entry, supportAvailable = true) } } }
        assertEquals(5, helpAnswers.size)
        compose.onNodeWithText("La batería lo apagó: quita la restricción.").assertExists()
        assertTrue(helpAnswers.flatMap { it.lines }.none { "paus" in it.lowercase() || "no cupo" in it.lowercase() })
        compose.onNodeWithText("¿Cuánto cuesta?").performScrollTo().performClick()
        compose.onNodeWithText("Hoy todo lo que ves en Kompara es gratis.").assertExists()
        compose.onNodeWithText("La batería lo apagó: quita la restricción.").assertDoesNotExist()
        compose.runOnIdle { entry = "colores" }
        compose.onNodeWithText("Amarillo: entre tu mínimo y tu meta.").assertExists()
        compose.onNodeWithText("La batería lo apagó: quita la restricción.").assertDoesNotExist()
        compose.onNodeWithText("¿Qué significan los colores?").performClick()
        compose.onNodeWithText("Amarillo: entre tu mínimo y tu meta.").assertDoesNotExist()
    }
    @Test fun `data deletion actions require distinct confirmations and errors stay outside them`() {
        var state by mutableStateOf(TusDatosState(loaded = true, signedIn = true))
        var remote = 0; var local = 0
        compose.setContent { KomparaTheme { TusDatosContent(state, delete = { remote++ }, erase = { local++ }) } }
        compose.onNodeWithText("Eliminar mi cuenta").performClick()
        compose.onNodeWithText("Se borran tu cuenta, totales y resúmenes. Tu\u00a0historial queda en este teléfono.").assertExists()
        compose.onNodeWithText("Cancelar").performClick(); assertEquals(0, remote)
        compose.onNodeWithText("Borrar datos del teléfono").performClick()
        compose.onNodeWithText("pausa", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Borrar").performClick(); assertEquals(1, local); assertEquals(0, remote)
        compose.onNodeWithText("Eliminar mi cuenta").performClick(); compose.onNodeWithText("Eliminar").performClick()
        assertEquals(1, remote)
        compose.runOnIdle { state = state.copy(error = "No pudimos eliminarla. Inténtalo otra vez.") }
        compose.onNodeWithText("¿Eliminar tu cuenta?").assertDoesNotExist()
        compose.onNodeWithText("No pudimos eliminarla. Inténtalo otra vez.").assertExists()
        compose.runOnIdle { state = TusDatosState(loaded = true, signedIn = true, result = "Datos del teléfono borrados") }
        compose.onNodeWithText("Tu cuenta, tus costos y tus límites se conservan.").assertExists()
    }
    @Test fun `the city averages switch is not built and external deletion stays hidden until configured`() {
        var deletion by mutableStateOf<String?>(null)
        var opened: String? = null
        compose.setContent { KomparaTheme {
            TusDatosContent(TusDatosState(loaded = true, signedIn = true, contribution = true, contributionAvailable = true),
                externalDeletion = deletion, openExternal = { opened = it })
        } }
        compose.onNodeWithText("Sumar mis promedios a mi ciudad").assertDoesNotExist()
        compose.onNodeWithText("Aportar al promedio").assertDoesNotExist()
        compose.onNodeWithText("También en kompara.mx/borrar").assertDoesNotExist()
        compose.onNodeWithText("Borrar datos del teléfono").assertExists()
        compose.onNodeWithText("Eliminar mi cuenta").assertExists()
        compose.onNodeWithText("Qué sale del teléfono").performClick()
        compose.onNodeWithText("Eliminar cuenta fuera de la app").assertDoesNotExist()
        compose.onNodeWithText("Aportar al promedio", substring = true).assertDoesNotExist()
        compose.runOnIdle { deletion = "https://kompara.mx/borrar" }
        compose.onNodeWithText("Eliminar cuenta fuera de la app").performScrollTo().performClick()
        assertEquals(deletion, opened)
    }

    @Test fun `profile has no deletion or idle save and city keeps saved goal in confirmation`() {
        var sheet: String? = null
        compose.setContent { KomparaTheme { AccountContent(AccountUiState(loaded = true, phone = "+525512345678"), open = { sheet = it }) } }
        compose.onNodeWithText("Eliminar cuenta").assertDoesNotExist(); compose.onNodeWithText("Guardar").assertDoesNotExist()
        compose.onNodeWithText("Para cambiarlo, escríbenos").assertDoesNotExist()
        compose.onNodeWithText("Tu ciudad").performClick(); assertEquals("city", sheet)
        assertEquals("Te comparamos con Monterrey desde esta semana. Tu meta sigue en $8.00/km; allá se sugiere $7.70/km.",
            cityConfirmation(AccountUiState(suggestedGoal = "$7.70/km"), City.MONTERREY))
    }
    @Test fun `unconfigured support is hidden and cannot open arbitrary WhatsApp contacts`() {
        var clicked = false
        compose.setContent { KomparaTheme { HelpContent(support = { clicked = true }) } }
        compose.onNodeWithText("Escríbenos por WhatsApp").assertDoesNotExist()
        compose.onNodeWithText("El soporte por WhatsApp aún no está disponible.").assertDoesNotExist()
        assertFalse(clicked)
        assertNull(PrivacyLinks.supportUrl("")); assertNull(PrivacyLinks.supportUrl("javascript:alert(1)"))
        assertEquals("https://wa.me/525512345678", PrivacyLinks.supportUrl("525512345678"))
        val report = "19 oct · 14:32\nTe dejó: $42.00 sin el coche & tiempo"
        assertNull(PrivacyLinks.supportDraft(report, ""))
        val draft = android.net.Uri.parse(PrivacyLinks.supportDraft(report, "525512345678"))
        assertEquals("/525512345678", draft.path)
        assertEquals(report, draft.getQueryParameter("text"))
        assertNull(PrivacyLinks.deletionUrl("https://kompara.mx.evil.test/borrar"))
    }
}
