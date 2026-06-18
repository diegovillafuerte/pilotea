package mx.kompara.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import mx.kompara.ui.auth.SignupFlowScreen
import mx.kompara.ui.onboarding.OnboardingNavGraph
import mx.kompara.ui.onboarding.RootRoute
import mx.kompara.ui.onboarding.RootRouter
import mx.kompara.ui.onboarding.RootViewModel

/**
 * The true app root (B-036): observes the persisted onboarding-completed flag and renders either the
 * onboarding funnel or the main 5-tab [KomparaApp] shell. `:app`'s MainActivity now hosts this
 * instead of [KomparaApp] directly.
 *
 * When onboarding finishes via "Probar el lector", we flip a one-shot flag so the freshly-shown
 * shell jumps straight to the reader trial (simulator if present, else Lector).
 *
 * [registerExtraDestinations] is forwarded to [KomparaApp] so `:app` can inject routes from modules
 * `:ui` cannot depend on (e.g. the `:overlay` offer simulator).
 */
@Composable
fun KomparaRoot(
    modifier: Modifier = Modifier,
    navigateToShareCard: Boolean = false,
    navigateToImport: Boolean = false,
    registerExtraDestinations: NavGraphBuilder.(NavController) -> Unit = {},
    rootViewModel: RootViewModel = hiltViewModel(),
) {
    val route by rootViewModel.route.collectAsStateWithLifecycle()
    // One-shot: did onboarding just complete this session? Drives the reader-trial deep link.
    var justCompletedOnboarding by remember { mutableStateOf(false) }
    // B-069 item 4: hold the standalone signup gate until the WHOLE flow finishes. Once we enter the
    // AUTH route we stay on the signup flow until its onComplete fires (profile saved or skipped),
    // even though the session — and thus the route — flips to MAIN the moment the OTP verifies. Without
    // this latch the root would swap to MAIN on verify and the profile (name/ciudad) step would never
    // show, which is exactly the bug this task fixes. name/ciudad feed the benchmarks, so we want them.
    var authFlowActive by remember { mutableStateOf(false) }
    if (route == RootRoute.AUTH) authFlowActive = true
    // The latch only matters while we'd otherwise be in MAIN with a session; ONBOARDING/LOADING clear
    // it so a later logout re-enters AUTH cleanly.
    if (route == RootRoute.ONBOARDING || route == RootRoute.LOADING) authFlowActive = false
    val effectiveRoute = RootRouter.effectiveRoute(route, authFlowActive)

    Surface(modifier = modifier.fillMaxSize()) {
        when (effectiveRoute) {
            RootRoute.LOADING -> Box(Modifier.fillMaxSize())
            // B-073: the funnel branches draw edge-to-edge with no system-bar handling of their own
            // (unlike the MAIN shell's Scaffold), so their bottom CTAs landed under the gesture-nav
            // inset. Inset them at the root with safeDrawingPadding() — one shared fix covering every
            // onboarding/signup screen. MAIN is excluded: KomparaApp's Scaffold already insets.
            RootRoute.ONBOARDING -> OnboardingNavGraph(
                onComplete = { justCompletedOnboarding = true },
                modifier = Modifier.safeDrawingPadding(),
            )
            // Standalone signup for installs that completed onboarding before accounts became
            // required (or after logout). Held open until onComplete (profile saved/skipped) so the
            // profile step shows even after the session persists on OTP verify (see authFlowActive).
            RootRoute.AUTH -> SignupFlowScreen(
                onComplete = { authFlowActive = false },
                modifier = Modifier.safeDrawingPadding(),
            )
            RootRoute.MAIN -> KomparaApp(
                navigateToReaderTrial = justCompletedOnboarding,
                // Week-close notification deep link (B-055): only honoured once onboarding is done.
                navigateToShareCard = navigateToShareCard,
                // Share-target deep link (PR-D3): jump to the import flow for a shared earnings file.
                navigateToImport = navigateToImport,
                registerExtraDestinations = registerExtraDestinations,
            )
        }
    }
}
