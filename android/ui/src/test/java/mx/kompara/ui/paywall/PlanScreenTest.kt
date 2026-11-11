package mx.kompara.ui.paywall

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
import mx.kompara.testing.BaselineComparison
import mx.kompara.ui.fixtures.PlanFixture
import mx.kompara.ui.fixtures.PlanFixtureHost
import mx.kompara.ui.referral.*
import mx.kompara.billing.*
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
class PlanScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val fixture get() = PlanFixture(ApplicationProvider.getApplicationContext())
    @Test fun `trial terms paid terms and pending states have distinct explicit actions`() {
        var frame by mutableStateOf("H1")
        var starts = 0
        compose.setContent { KomparaTheme {
            if (frame == "H1") PaywallContent(fixture.offer, onPurchase = { starts++ }, legalAvailable = true)
            else TuPlanContent(fixture.state(frame))
        } }
        compose.onNodeWithText("Empezar mi prueba").assertIsDisplayed().performClick(); assertEquals(1, starts)
        compose.onNodeWithText("Se renueva cada mes", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Restaurar compra").assertDoesNotExist()
        compose.runOnIdle { frame = "H2d" }
        compose.onNodeWithText("Pago en proceso").assertIsDisplayed()
        compose.onNodeWithText("9 de noviembre").assertIsDisplayed()
        compose.onNodeWithText("Arreglarlo en Google Play").assertDoesNotExist()
        compose.runOnIdle { frame = "pending-empty" }
        compose.onNodeWithText("Premium se activa cuando se confirme").assertIsDisplayed()
        compose.onNodeWithText("Tu acceso sigue hasta").assertDoesNotExist()
        compose.runOnIdle { frame = "H2c" }
        compose.onNodeWithText("Arreglarlo en Google Play").assertIsDisplayed()
        compose.onNodeWithText("17 de diciembre").assertIsDisplayed()
    }
    @Test fun `absent data and unpublished legal links cannot sell a subscription`() {
        var state by mutableStateOf<PaywallUiState>(fixture.offer)
        compose.setContent { KomparaTheme { PaywallContent(state) } }
        compose.onNodeWithText("Empezar mi prueba").assertDoesNotExist()
        compose.onNodeWithText("Términos").assertDoesNotExist()
        compose.onNodeWithText("Privacidad").assertDoesNotExist()
        compose.runOnIdle { state = PaywallUiState.NoResult }
        compose.onNodeWithText("Tus datos siguen gratis").assertIsDisplayed()
        compose.onNodeWithText("$149").assertDoesNotExist()
    }
    @Test fun `referral redemption starts collapsed and actual bonus comes from the service`() {
        var state by mutableStateOf(fixture.referral)
        var redeemed = 0
        compose.setContent { KomparaTheme { ReferralContent(state, toggle = { state = state.copy(expanded = !state.expanded) }, input = { state = state.copy(redeemInput = it) }, redeem = { redeemed++ }) } }
        compose.onNodeWithText("Código de invitación").assertDoesNotExist()
        compose.onNodeWithText("¿Te invitaron? Escribe su código").performClick()
        compose.onNodeWithContentDescription("Código de invitación").performTextInput("ABC123")
        compose.onNodeWithText("Canjear código").performScrollTo().performClick(); assertEquals(1, redeemed)
        assertNull(referralRewardText(null, null))
        assertFalse(referralShareText(state.copy(rewardDaysReferrer = null, rewardDaysRedeemer = null)).contains("gratis"))
        assertTrue(referralRewardText(14, 14)!!.contains("14 días"))
    }
    @Test fun `large text preserves readable dates and reachable actions`() {
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) { KomparaTheme {
            Box(Modifier.width(320.dp).height(600.dp)) { TuPlanContent(fixture.state("H2c")) }
        } } }
        compose.onNodeWithText("Arreglarlo en Google Play").assertIsDisplayed()
        compose.onNodeWithText("Restaurar compra").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("17 de diciembre").performScrollTo().assertIsDisplayed()
    }
    @Test fun `all plan states day night and larger fonts have reviewed screenshot comparisons`() {
        var frame by mutableStateOf("H1"); var width by mutableIntStateOf(393); var scale by mutableFloatStateOf(1f); var night by mutableStateOf(false)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
            KomparaTheme(darkTheme = night) { Box(Modifier.width(width.dp).height(852.dp)) { PlanFixtureHost(frame) } }
        } }
        val cases = listOf("H1", "H1b", "H2", "H2b", "H2c", "H2d", "pending-empty", "canceled", "expired", "unavailable", "no-result", "referral", "redeem").map { listOf(it, "393", "1.0", "false") } +
            listOf("H1", "H1b", "H2", "H2b", "H2c", "H2d").map { listOf(it, "393", "1.3", "false") } +
            listOf("H1", "H2b", "H2c", "H2d", "referral").map { listOf(it, "320", "2.0", "true") }
        for ((id, size, font, dark) in cases) {
            compose.runOnIdle { frame = id; width = size.toInt(); scale = font.toFloat(); night = dark.toBoolean() }
            compose.waitForIdle()
            mx.kompara.ui.contract.FrameContract.assertFrame(compose, id)
            val name = "plan_${id}_${size}_${font}_${if (night) "night" else "day"}"
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                val results = mutableListOf<TextLayoutResult>(); node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.forEach { result -> assertFalse("$name clipped ${result.layoutInput.text}", result.didOverflowHeight || (0 until result.lineCount).any { result.getLineRight(it) > result.size.width + 1f }) }
            }
            val bitmap = compose.runOnIdle {
                val view = compose.activity.findViewById<android.view.View>(android.R.id.content)
                Bitmap.createBitmap(width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
            }
            val output = File("build/outputs/v3-screenshots/$name.png").apply { parentFile!!.mkdirs() }
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val baseline = File("src/test/snapshots/v3/$name.png")
            if (System.getProperty("v3.recordScreenshots") == "true") output.copyTo(baseline, overwrite = true)
            else { assertTrue("Missing $name", baseline.exists()); BaselineComparison.assertMatches(BitmapFactory.decodeFile(baseline.path), bitmap, output) }
        }
    }
}
