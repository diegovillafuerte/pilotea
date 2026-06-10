package mx.kompara.overlay.simulator

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import mx.kompara.ui.nav.KomparaDestination

/**
 * Registers the offer simulator (B-037) on the shared app nav graph at
 * [KomparaDestination.SIMULATOR_ROUTE].
 *
 * `:ui` owns the nav shell and the route key but cannot depend on `:overlay` (the simulator embeds
 * the verdict chip, which lives here). So `:app` — the one module that depends on both — calls this
 * from `KomparaApp(registerExtraDestinations = { simulatorDestination() })`, injecting the screen
 * without inverting the module dependency. The onboarding done-screen can navigate to the same route.
 */
fun NavGraphBuilder.simulatorDestination() {
    composable(KomparaDestination.SIMULATOR_ROUTE) {
        SimulatorScreen()
    }
}
