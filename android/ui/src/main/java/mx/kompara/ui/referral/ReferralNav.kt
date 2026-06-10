package mx.kompara.ui.referral

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import mx.kompara.ui.nav.KomparaDestination

/**
 * Registers the B-056 "Invita y gana" flow on the app nav graph at [KomparaDestination.REFERRAL_ROUTE].
 *
 * The screen lives in `:ui` and is wired straight into [mx.kompara.ui.nav.KomparaApp] (no `:app`
 * inversion needed): `:ui` depends on `:sync` (the [mx.kompara.sync.referral.Referrals] repository)
 * and `:billing` (the entitlement refresh), so the screen resolves its [ReferralViewModel] directly.
 * The Ajustes "Invita y gana" entry navigates here by route key.
 *
 * **Deep link:** `kompara://referral?code=XXXXXXXX` is declared here via [navDeepLink], so the system
 * routes the manifest intent (registered in the app manifest) straight to this composable. The
 * optional `code` argument is read by [ReferralViewModel] (via SavedStateHandle) and prefilled into
 * the redeem field once the driver is signed in.
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
        deepLinks = listOf(
            navDeepLink {
                uriPattern = "kompara://referral?code={${ReferralViewModel.ARG_CODE}}"
            },
        ),
    ) {
        ReferralScreen(onClose = { navController.popBackStack() })
    }
}
