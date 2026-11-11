package mx.kompara.ui.paywall

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import mx.kompara.ui.nav.KomparaDestination

/**
 * Registers premium (B-050 paywall, Tu plan, subscribe) on the app nav graph. After launch
 * (docs/v4-simple-launch-plan.md §3) nothing in the launch set links here; the screens link only to each other.
 */
fun NavGraphBuilder.paywallDestinations(navController: NavController) {
    // The surface path arg buckets analytics.
    composable(
        route = "${KomparaDestination.PAYWALL_ROUTE}/{${KomparaDestination.ARG_PAYWALL_SURFACE}}",
        arguments = listOf(
            navArgument(KomparaDestination.ARG_PAYWALL_SURFACE) { type = NavType.StringType },
        ),
    ) { entry ->
        val surfaceName = entry.arguments?.getString(KomparaDestination.ARG_PAYWALL_SURFACE)
        val surface = runCatching { GateSurface.valueOf(surfaceName ?: "") }
            .getOrDefault(GateSurface.GENERIC)
        PaywallScreen(surface = surface, onClose = { navController.popBackStack() }, onPlan = {
            navController.navigate(KomparaDestination.PLAN_ROUTE) { popUpTo(entry.destination.id) { inclusive = true }; launchSingleTop = true }
        })
    }
    composable(KomparaDestination.PLAN_ROUTE) {
        TuPlanScreen(onBack = { navController.popBackStack() }, onSubscribe = { navController.navigate("plan-subscribe") })
    }
    composable("plan-subscribe") {
        PaywallScreen(onClose = { navController.popBackStack() }, purchaseExplanation = true,
            onPlan = { navController.popBackStack(KomparaDestination.PLAN_ROUTE, false) })
    }
}
