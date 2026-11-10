package mx.kompara.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import mx.kompara.ui.onboarding.*

/** Local onboarding and the three-tab shell do not depend on account/session availability. */
@Composable
fun KomparaRoot(modifier: Modifier = Modifier,
    notification: mx.kompara.ui.notifications.NoticeTarget? = null,
    onNotificationConsumed: () -> Unit = {},
    registerExtraDestinations: NavGraphBuilder.(NavController) -> Unit = {},
    rootViewModel: RootViewModel = hiltViewModel()) {
    val route by rootViewModel.route.collectAsStateWithLifecycle()
    Surface(modifier.fillMaxSize()) {
        when (route) {
            RootRoute.LOADING -> Box(Modifier.fillMaxSize())
            RootRoute.ONBOARDING -> OnboardingNavGraph(onComplete = {})
            // Auth is contextual in the navigation stack, never a root gate.
            RootRoute.MAIN, RootRoute.AUTH -> KomparaApp(notification = notification, onNotificationConsumed = onNotificationConsumed,
                registerExtraDestinations = registerExtraDestinations)
        }
    }
}
