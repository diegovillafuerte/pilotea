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
    @Test fun `privacy profile notices and help render at normal large and narrow sizes`() {
        var frame by mutableStateOf("G6")
        var scale by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(393)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
            KomparaTheme(darkTheme = width == 320) {
                Box(Modifier.width(width.dp).height(852.dp)) { key(frame, scale, width) { PrivacyFixtureHost(frame) } }
            }
        } }
        val cases = listOf("G6", "G6b", "G8", "G9", "profile", "deleted", "delete-error", "support-gated").map { Triple(it, 1f, 393) } +
            listOf("G6", "G8", "G9").map { Triple(it, 1.3f, 393) } +
            listOf("G6", "G8", "profile").map { Triple(it, 2f, 320) }
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
                    if (id in listOf("G6b", "G9")) org.robolectric.shadows.ShadowDialog.getLatestDialog()?.window?.decorView?.let { dialog ->
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
    @Test fun `help has five questions and entry opens only its requested answer`() {
        compose.setContent { KomparaTheme { HelpContent(initialAnswer = "semaforo", supportAvailable = true) } }
        assertEquals(5, helpAnswers.size)
        compose.onNodeWithText("Lo pausaste: Reanudar el semáforo en Hoy.").assertExists()
        compose.onNodeWithText("¿Qué significan los colores?").performScrollTo().performClick()
        compose.onNodeWithText("Lo pausaste: Reanudar el semáforo en Hoy.").assertDoesNotExist()
        compose.onNodeWithText("Amarillo: cumple tu mínimo.").assertExists()
        compose.onNodeWithText("¿Qué significan los colores?").performClick()
        compose.onNodeWithText("Amarillo: cumple tu mínimo.").assertDoesNotExist()
    }
    @Test fun `data deletion actions require distinct confirmations and errors stay outside them`() {
        var state by mutableStateOf(TusDatosState(loaded = true, signedIn = true))
        var remote = 0; var local = 0
        compose.setContent { KomparaTheme { TusDatosContent(state, delete = { remote++ }, erase = { local++ }) } }
        compose.onNodeWithText("Eliminar cuenta").performClick()
        compose.onNodeWithText("Se borran tu cuenta, totales y resúmenes. Tu\u00a0historial queda en este teléfono.").assertExists()
        compose.onNodeWithText("Cancelar").performClick(); assertEquals(0, remote)
        compose.onNodeWithText("Borrar datos del teléfono").performClick()
        compose.onNodeWithText("Borrar").performClick(); assertEquals(1, local); assertEquals(0, remote)
        compose.onNodeWithText("Eliminar cuenta").performClick(); compose.onNodeWithText("Eliminar").performClick()
        assertEquals(1, remote)
        compose.runOnIdle { state = state.copy(error = "No pudimos eliminarla. Inténtalo otra vez.") }
        compose.onNodeWithText("¿Eliminar tu cuenta?").assertDoesNotExist()
        compose.onNodeWithText("No pudimos eliminarla. Inténtalo otra vez.").assertExists()
    }
    @Test fun `contribution opens explanation before opt in and disabling is immediate`() {
        var state by mutableStateOf(TusDatosState(loaded = true, contributionAvailable = true))
        val changes = mutableListOf<Boolean>()
        compose.setContent { KomparaTheme { TusDatosContent(state, contribution = { changes += it }) } }
        compose.onNodeWithContentDescription("Aportar al promedio").performClick()
        assertTrue(changes.isEmpty())
        compose.onNodeWithText("Puedes ver comparaciones sin aportar.").assertExists()
        compose.onAllNodesWithContentDescription("Aportar al promedio").onLast().performClick()
        assertEquals(listOf(true), changes)
    }
    @Test fun `unsupported contribution and external deletion stay hidden without hiding local data controls`() {
        var deletion by mutableStateOf<String?>(null)
        var opened: String? = null
        compose.setContent { KomparaTheme {
            TusDatosContent(TusDatosState(loaded = true, signedIn = true, contribution = true),
                externalDeletion = deletion, openExternal = { opened = it })
        } }
        compose.onNodeWithText("Aportar al promedio").assertDoesNotExist()
        compose.onNodeWithText("También en kompara.mx/borrar").assertDoesNotExist()
        compose.onNodeWithText("Borrar datos del teléfono").assertExists()
        compose.onNodeWithText("Eliminar cuenta").assertExists()
        compose.onNodeWithText("Sale del teléfono").performClick()
        compose.onNodeWithText("Eliminar cuenta fuera de la app").assertDoesNotExist()
        compose.onNodeWithText("Si eliges Aportar al promedio", substring = true).assertDoesNotExist()
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
