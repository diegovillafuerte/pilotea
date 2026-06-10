package mx.kompara.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import mx.kompara.ui.onboarding.OnboardingNavGraph
import mx.kompara.ui.onboarding.RootRoute
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
    registerExtraDestinations: NavGraphBuilder.(NavController) -> Unit = {},
    rootViewModel: RootViewModel = hiltViewModel(),
) {
    val route by rootViewModel.route.collectAsStateWithLifecycle()
    // One-shot: did onboarding just complete this session? Drives the reader-trial deep link.
    var justCompletedOnboarding by remember { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize()) {
        when (route) {
            RootRoute.LOADING -> Box(Modifier.fillMaxSize())
            RootRoute.ONBOARDING -> OnboardingNavGraph(
                onComplete = { justCompletedOnboarding = true },
            )
            RootRoute.MAIN -> KomparaApp(
                navigateToReaderTrial = justCompletedOnboarding,
                registerExtraDestinations = registerExtraDestinations,
            )
        }
    }
}
