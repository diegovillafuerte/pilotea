package mx.kompara.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import mx.kompara.ui.imports.importDestination
import mx.kompara.ui.paywall.GateSurface
import mx.kompara.ui.paywall.PaywallScreen
import mx.kompara.ui.referral.referralDestination
import mx.kompara.ui.screens.AccountScreen
import mx.kompara.ui.screens.AjustesScreen
import mx.kompara.ui.screens.CompararScreen
import mx.kompara.ui.screens.CostProfileScreen
import mx.kompara.ui.screens.DayDetailScreen
import mx.kompara.ui.screens.FiscalScreen
import mx.kompara.ui.screens.HelpScreen
import mx.kompara.ui.screens.HistoryScreen
import mx.kompara.ui.screens.InicioDashboardScreen
import mx.kompara.ui.screens.LectorScreen
import mx.kompara.ui.screens.ThresholdsScreen
import mx.kompara.ui.screens.WeekSummaryScreen
import mx.kompara.ui.share.ShareCardScreen
import mx.kompara.ui.stats.DayDetailViewModel
import mx.kompara.ui.stats.WeekSummaryViewModel
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
 *
 * @param navigateToReaderTrial when true (set right after onboarding's "Probar el lector" CTA), the
 *   shell navigates on first composition to the offer simulator if its route is present in the graph
 *   (registered via [registerExtraDestinations]), otherwise to the Lector tab. Checked at runtime so
 *   this file does not have to know whether the simulator has been registered.
 */
@Composable
fun KomparaApp(
    modifier: Modifier = Modifier,
    navigateToReaderTrial: Boolean = false,
    navigateToShareCard: Boolean = false,
    registerExtraDestinations: NavGraphBuilder.(NavController) -> Unit = {},
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStack.collectAsStateWithLifecycle()
    // The highlighted tab is the topmost tab in the back stack: on a detail screen (Ayuda, Historial,
    // Tu semáforo…) this lights the tab it was opened from rather than falling back to Inicio, so a
    // re-tap can pop that detail stack back to the tab root (B-074 F5).
    val current = KomparaDestination.activeTab(backStack.map { it.destination.route })

    if (navigateToReaderTrial) {
        LaunchedEffect(Unit) { navController.navigateToReaderTrial() }
    }

    // Week-close notification deep link (B-055): jump straight to the share-card preview.
    if (navigateToShareCard) {
        LaunchedEffect(Unit) { navController.navigate(KomparaDestination.SHARE_CARD_ROUTE) }
    }

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
                    } else {
                        // Re-tapping the active tab pops its detail stack back to the tab root (B-074
                        // F5) — standard bottom-nav behaviour. No-op when already at the root.
                        navController.popBackStack(destination.route, inclusive = false)
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
            tabScreens(navController)
            statsScreens(navController)
            registerExtraDestinations(navController)
        }
    }
}

/**
 * Navigate to the offer simulator if its route was registered into the graph (via
 * [KomparaApp]'s `registerExtraDestinations`), otherwise fall back to the Lector tab. Backs the
 * onboarding "Probar el lector" CTA — see [KomparaApp]'s `navigateToReaderTrial`. The runtime graph
 * check keeps `:ui` from hard-depending on whether `:overlay` registered the simulator.
 */
private fun NavHostController.navigateToReaderTrial() {
    val hasSimulator = runCatching {
        graph.any { node -> node.route == KomparaDestination.SIMULATOR_ROUTE }
    }.getOrDefault(false)
    val target = if (hasSimulator) {
        KomparaDestination.SIMULATOR_ROUTE
    } else {
        KomparaDestination.LECTOR.route
    }
    navigate(target) {
        popUpTo(KomparaDestination.START.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Navigate to the paywall screen, carrying the originating [GateSurface] for analytics bucketing. */
private fun NavController.navigateToPaywall(surface: GateSurface) {
    navigate(KomparaDestination.paywallRoute(surface.name))
}

/** Registers the screen for every top-level destination. */
private fun NavGraphBuilder.tabScreens(navController: NavController) {
    composable(KomparaDestination.INICIO.route) {
        InicioDashboardScreen(
            onOpenCostProfile = { navController.navigate(KomparaDestination.COST_PROFILE_ROUTE) },
            onOpenToday = { navController.navigate(KomparaDestination.DAY_DETAIL_ROUTE) },
            onOpenReaderTrial = { navController.navigate(KomparaDestination.SIMULATOR_ROUTE) },
            onUpgrade = { surface -> navController.navigateToPaywall(surface) },
            onOpenShareCard = { navController.navigate(KomparaDestination.SHARE_CARD_ROUTE) },
        )
    }
    composable(KomparaDestination.COMPARAR.route) {
        CompararScreen(
            onUpgrade = { surface -> navController.navigateToPaywall(surface) },
            onOpenReader = {
                navController.navigate(KomparaDestination.LECTOR.route) {
                    popUpTo(KomparaDestination.START.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )
    }
    composable(KomparaDestination.LECTOR.route) {
        LectorScreen(
            onOpenSimulator = { navController.navigate(KomparaDestination.SIMULATOR_ROUTE) },
            onOpenThresholds = { navController.navigate(KomparaDestination.THRESHOLDS_ROUTE) },
        )
    }
    composable(KomparaDestination.FISCAL.route) {
        FiscalScreen(onUpgrade = { surface -> navController.navigateToPaywall(surface) })
    }
    composable(KomparaDestination.AJUSTES.route) {
        AjustesScreen(
            onOpenSimulator = { navController.navigate(KomparaDestination.SIMULATOR_ROUTE) },
            onOpenCostProfile = { navController.navigate(KomparaDestination.COST_PROFILE_ROUTE) },
            onOpenHistory = { navController.navigate(KomparaDestination.HISTORY_ROUTE) },
            onOpenReferral = { navController.navigate(KomparaDestination.REFERRAL_ROUTE) },
            onOpenThresholds = { navController.navigate(KomparaDestination.THRESHOLDS_ROUTE) },
            onOpenHelp = { navController.navigate(KomparaDestination.HELP_ROUTE) },
            onOpenAccount = { navController.navigate(KomparaDestination.ACCOUNT_ROUTE) },
        )
    }
}

/**
 * The B-040 stats detail screens (not bottom-bar tabs): cost-profile editor, history weeks list,
 * day detail (today by default, or a specific ISO day), and week summary.
 */
private fun NavGraphBuilder.statsScreens(navController: NavController) {
    composable(KomparaDestination.COST_PROFILE_ROUTE) {
        CostProfileScreen(onSaved = { navController.popBackStack() })
    }
    // B-070 threshold editor and B-071 help center, both reached from Ajustes.
    composable(KomparaDestination.THRESHOLDS_ROUTE) { ThresholdsScreen() }
    composable(KomparaDestination.HELP_ROUTE) { HelpScreen() }
    // B-069 account management ("Tu cuenta"), reached from Ajustes.
    composable(KomparaDestination.ACCOUNT_ROUTE) { AccountScreen() }
    composable(KomparaDestination.HISTORY_ROUTE) {
        HistoryScreen(
            onOpenWeek = { weekStart ->
                navController.navigate("${KomparaDestination.WEEK_SUMMARY_ROUTE}/$weekStart")
            },
            // B-045 import flow (registered below via importDestination).
            onImportWeek = { navController.navigate(KomparaDestination.IMPORT_ROUTE) },
            // B-050 paywall: older history rows are gated behind the free window.
            onUpgrade = { surface -> navController.navigateToPaywall(surface) },
        )
    }
    // B-050 paywall screen, reached from any PaywallGate CTA. The surface path arg buckets analytics.
    composable(
        route = "${KomparaDestination.PAYWALL_ROUTE}/{${KomparaDestination.ARG_PAYWALL_SURFACE}}",
        arguments = listOf(
            navArgument(KomparaDestination.ARG_PAYWALL_SURFACE) { type = NavType.StringType },
        ),
    ) { entry ->
        val surfaceName = entry.arguments?.getString(KomparaDestination.ARG_PAYWALL_SURFACE)
        val surface = runCatching { GateSurface.valueOf(surfaceName ?: "") }
            .getOrDefault(GateSurface.GENERIC)
        PaywallScreen(surface = surface, onClose = { navController.popBackStack() })
    }
    importDestination(navController)
    // B-056 referral / "Invita y gana" flow.
    referralDestination(navController)
    // Day detail with no arg → today.
    composable(KomparaDestination.DAY_DETAIL_ROUTE) { DayDetailScreen() }
    composable(
        route = "${KomparaDestination.DAY_DETAIL_ROUTE}/{${DayDetailViewModel.ARG_DAY}}",
        arguments = listOf(navArgument(DayDetailViewModel.ARG_DAY) { type = NavType.StringType }),
    ) { DayDetailScreen() }
    composable(
        route = "${KomparaDestination.WEEK_SUMMARY_ROUTE}/{${WeekSummaryViewModel.ARG_WEEK_START}}",
        arguments = listOf(
            navArgument(WeekSummaryViewModel.ARG_WEEK_START) { type = NavType.StringType },
        ),
    ) {
        WeekSummaryScreen(
            onOpenShareCard = { navController.navigate(KomparaDestination.SHARE_CARD_ROUTE) },
        )
    }
    // B-055 share-card preview, reachable from the Inicio header icon, the week-summary CTA, and the
    // Monday week-close notification.
    composable(KomparaDestination.SHARE_CARD_ROUTE) { ShareCardScreen() }
}

@Preview(showBackground = true, name = "KomparaApp shell")
@Composable
private fun KomparaAppPreview() {
    KomparaTheme { KomparaApp() }
}
