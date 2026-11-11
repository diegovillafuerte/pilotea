package mx.kompara.ui.referral

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import mx.kompara.ui.nav.KomparaDestination

/**
 * Registers the B-056 "Invita y gana" flow on the app nav graph at [KomparaDestination.REFERRAL_ROUTE].
 *
 * The screen lives in `:ui` and is wired straight into [mx.kompara.ui.nav.KomparaApp] (no `:app`
 * inversion needed): `:ui` depends on `:sync` (the [mx.kompara.sync.referral.Referrals] repository)
 * and `:billing` (the entitlement refresh), so the screen resolves its [ReferralViewModel] directly.
 * The Ajustes "Invita y gana" entry navigates here by route key.
 *
 * **No deep link after launch** (docs/v4-simple-launch-plan.md §3): 0.2.x declared
 * `kompara://referral?code=XXXXXXXX` here with a nav deep link. Removing the manifest filter alone was not enough:
 * the NavHost matches the launch intent's data against every nav deep link, and MainActivity is exported, so any
 * app could have opened this screen. To bring it back, restore the `navDeepLink { uriPattern = ... }` here and the
 * intent filter. The optional `code` argument is still read by [ReferralViewModel] (via SavedStateHandle).
 *
 * [onClose] pops back; the [navController] is threaded through so the destination can `popBackStack()`.
 */
fun NavGraphBuilder.referralDestination(navController: NavController) {
    composable(
        route = "${KomparaDestination.REFERRAL_ROUTE}?${ReferralViewModel.ARG_CODE}={${ReferralViewModel.ARG_CODE}}",
        arguments = listOf(
            navArgument(ReferralViewModel.ARG_CODE) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        ReferralScreen(onClose = { navController.popBackStack() })
    }
}
