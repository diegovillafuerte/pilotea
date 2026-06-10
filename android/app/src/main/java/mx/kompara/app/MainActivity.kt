package mx.kompara.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import mx.kompara.overlay.simulator.simulatorDestination
import mx.kompara.ui.nav.KomparaApp
import mx.kompara.ui.theme.KomparaTheme

/**
 * Single-activity host. [@AndroidEntryPoint][AndroidEntryPoint] makes this a Hilt injection target
 * so screens deeper in `:ui` can resolve their ViewModels (which pull injected dependencies from
 * `:data`) end-to-end.
 *
 * The activity stays deliberately thin: the design system and the whole navigable shell live in
 * `:ui` ([KomparaApp] + [KomparaTheme]); `:app` only wires modules together and launches the shell.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KomparaTheme {
                // `:app` is the only module that depends on both `:ui` (nav shell) and `:overlay`
                // (the verdict chip the simulator embeds), so it injects the simulator route here.
                KomparaApp(registerExtraDestinations = { simulatorDestination() })
            }
        }
    }
}
