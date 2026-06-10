package mx.kompara.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import mx.kompara.ui.screens.AjustesScreen
import mx.kompara.ui.screens.CompararScreen
import mx.kompara.ui.screens.FiscalScreen
import mx.kompara.ui.screens.InicioScreen
import mx.kompara.ui.screens.LectorScreen
import mx.kompara.ui.theme.KomparaTheme

/**
 * The whole app shell: a [Scaffold] with the [KomparaBottomBar] hosting a [NavHost] over the five
 * top-level destinations. Launches into [KomparaDestination.START] (Inicio). `:app`'s MainActivity
 * just wraps this in [KomparaTheme], keeping the app module thin.
 *
 * [registerExtraDestinations] lets a module that `:ui` cannot depend on (e.g. `:overlay`, which owns
 * the offer simulator screen) inject its own routes into the shared [NavHost]. `:app` is the only
 * place that depends on both `:ui` and those modules, so it supplies this. The lambda receives the
 * [NavController] so extra screens can navigate too.
 */
@Composable
fun KomparaApp(
    modifier: Modifier = Modifier,
    registerExtraDestinations: NavGraphBuilder.(NavController) -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = backStackEntry?.destination?.route
        ?.let { route -> KomparaDestination.entries.firstOrNull { it.route == route } }
        ?: KomparaDestination.START

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            KomparaBottomBar(
                current = current,
                onSelect = { destination ->
                    if (destination.route != current.route) {
                        navController.navigate(destination.route) {
                            // Single-top tabs: keep one copy of each, restore its state, and pop
                            // back to the start so the back button always exits from Inicio.
                            popUpTo(KomparaDestination.START.route) {
                                saveState = true
                                inclusive = false
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = KomparaDestination.START.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            tabScreens(onOpenSimulator = { navController.navigate(KomparaDestination.SIMULATOR_ROUTE) })
            registerExtraDestinations(navController)
        }
    }
}

/** Registers the placeholder screen for every top-level destination. */
private fun NavGraphBuilder.tabScreens(onOpenSimulator: () -> Unit) {
    composable(KomparaDestination.INICIO.route) { InicioScreen() }
    composable(KomparaDestination.COMPARAR.route) { CompararScreen() }
    composable(KomparaDestination.LECTOR.route) { LectorScreen() }
    composable(KomparaDestination.FISCAL.route) { FiscalScreen() }
    composable(KomparaDestination.AJUSTES.route) { AjustesScreen(onOpenSimulator = onOpenSimulator) }
}

@Preview(showBackground = true, name = "KomparaApp shell")
@Composable
private fun KomparaAppPreview() {
    KomparaTheme { KomparaApp() }
}
