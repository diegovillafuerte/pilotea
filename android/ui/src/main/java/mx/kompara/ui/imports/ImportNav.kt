package mx.kompara.ui.imports

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import mx.kompara.ui.nav.KomparaDestination

/**
 * Registers the B-045 import flow on the app nav graph at [KomparaDestination.IMPORT_ROUTE].
 *
 * The import flow screen lives in `:ui` and is wired straight into [mx.kompara.ui.nav.KomparaApp]'s
 * stats sub-graph (no `:app` inversion needed): `:ui` depends on `:sync`, so the screen can resolve
 * its [ImportViewModel] (which uses the `:sync` [ImportRepository]) directly. The History "Importar
 * semana" CTA navigates here by route key.
 *
 * [onClose] pops back to History; the [navController] is threaded through so the destination can
 * `popBackStack()` on success / cancel.
 */
fun NavGraphBuilder.importDestination(navController: NavController) {
    composable(KomparaDestination.IMPORT_ROUTE) {
        ImportScreen(onClose = { navController.popBackStack() })
    }
}
