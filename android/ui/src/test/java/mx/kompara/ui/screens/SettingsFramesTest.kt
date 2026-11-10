package mx.kompara.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import mx.kompara.ui.contract.v4.V4Snapshot
import mx.kompara.ui.contract.v4.WpF
import mx.kompara.ui.fixtures.SettingsFixtureHost
import mx.kompara.ui.theme.KomparaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** J1–J7 and P1–P4 on the kit's fixture: their contract strings, nothing clipped, reviewed v4 baselines. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsFramesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val screens = mapOf("J1" to "ajustes", "J2" to "limits", "J7" to "limits", "J3" to "costs", "J4" to "semaforo",
        "J5" to "datos", "J6" to "ayuda", "P1" to "firstrun", "P2" to "firstrun", "P3" to "firstrun", "P4" to "firstrun")
    /** States the kit does not draw, kept from 0.2.20 and restyled: one day baseline each, named without a frame id. */
    private val states = mapOf("hour" to "limits", "electric" to "costs", "direct" to "costs", "off" to "semaforo",
        "prepared" to "semaforo", "signed-out" to "datos", "erased" to "datos", "missing" to "firstrun",
        "granted" to "firstrun", "battery-first" to "firstrun")

    private data class Case(val frame: String, val screen: String, val dark: Boolean, val scale: Float, val width: Int)

    private fun render(cases: List<Case>, contract: Boolean) {
        var case by mutableStateOf(cases.first())
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, case.scale)) {
                KomparaTheme(darkTheme = case.dark) {
                    Box(Modifier.width(case.width.dp).height(852.dp)) { key(case) { SettingsFixtureHost(case.frame) } }
                }
            }
        }
        for (next in cases) {
            compose.runOnIdle { case = next }
            compose.waitForIdle()
            val name = V4Snapshot.name(next.screen, next.frame, next.dark, next.width, next.scale)
            if (contract && !next.dark && next.scale == 1f) WpF.assertFrame(compose, next.frame)
            V4Snapshot.assertNothingClipped(compose, name)
            V4Snapshot.assertMatchesBaseline(name, compose.runOnIdle { V4Snapshot.capture(compose.activity, next.width) })
        }
    }

    @Test fun `each frame shows its kit strings day and night at the v3 font scales`() = render(screens.flatMap { (frame, screen) ->
        listOf(Case(frame, screen, false, 1f, 393), Case(frame, screen, true, 1f, 393),
            Case(frame, screen, false, 1.3f, 393), Case(frame, screen, true, 2f, 320))
    }, contract = true)

    @Test fun `undrawn states keep their reviewed look`() = render(states.map { (state, screen) -> Case(state, screen, false, 1f, 393) } +
        Case("off", "semaforo", true, 2f, 320), contract = false)
}
