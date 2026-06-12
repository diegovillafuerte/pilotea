package mx.kompara.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import mx.kompara.ui.auth.SignupFlowScreen

/**
 * The onboarding funnel navigation graph (B-036), kept entirely in its own file so the shared nav
 * host ([mx.kompara.ui.nav.KomparaApp]) needs only a one-line root routing decision and merges are
 * minimal. Routes are private to this graph.
 *
 * Flow: pitch → signup → disclosure → (accept) accessibility → oem → done → [onComplete];
 *                                     (decline) limitedInfo → [onComplete] (skips the grant entirely).
 */
object OnboardingRoutes {
    const val PITCH = "onb_pitch"
    const val SIGNUP = "onb_signup"
    const val DISCLOSURE = "onb_disclosure"
    const val LIMITED_INFO = "onb_limited_info"
    const val ACCESSIBILITY = "onb_accessibility"
    const val OEM = "onb_oem"
    const val DONE = "onb_done"
}

/**
 * Hosts the onboarding graph. [onComplete] fires once the driver finishes (or opts into the limited
 * experience); the caller persists completion and switches to the main shell.
 */
@Composable
fun OnboardingNavGraph(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = OnboardingRoutes.PITCH,
        modifier = modifier.fillMaxSize(),
    ) {
        onboardingDestinations(navController, viewModel, onComplete)
    }
}

private fun NavGraphBuilder.onboardingDestinations(
    navController: NavHostController,
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
) {
    composable(OnboardingRoutes.PITCH) {
        LaunchedEffect(Unit) { viewModel.record(OnboardingStep.PITCH) }
        PitchScreen(
            onFinished = { navController.navigate(OnboardingRoutes.SIGNUP) },
        )
    }

    composable(OnboardingRoutes.SIGNUP) {
        LaunchedEffect(Unit) { viewModel.record(OnboardingStep.SIGNUP) }
        SignupFlowScreen(
            onComplete = {
                viewModel.record(OnboardingStep.SIGNUP_DONE)
                navController.navigate(OnboardingRoutes.DISCLOSURE)
            },
        )
    }

    composable(OnboardingRoutes.DISCLOSURE) {
        LaunchedEffect(Unit) { viewModel.record(OnboardingStep.DISCLOSURE) }
        DisclosureScreen(
            onAccept = {
                viewModel.record(OnboardingStep.DISCLOSURE_ACCEPTED)
                navController.navigate(OnboardingRoutes.ACCESSIBILITY)
            },
            onDecline = {
                viewModel.record(OnboardingStep.DISCLOSURE_DECLINED)
                navController.navigate(OnboardingRoutes.LIMITED_INFO)
            },
        )
    }

    composable(OnboardingRoutes.LIMITED_INFO) {
        LimitedInfoScreen(
            onReview = { navController.popBackStack() },
            onContinue = { viewModel.completeOnboarding(); onComplete() },
        )
    }

    composable(OnboardingRoutes.ACCESSIBILITY) {
        val context = LocalContext.current
        val connected by viewModel.serviceConnected.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { viewModel.record(OnboardingStep.ACCESSIBILITY) }
        // Auto-advance celebration: when the service flips connected we record the grant; the
        // screen swaps to its celebratory state, and the driver taps Continuar from there.
        LaunchedEffect(connected) {
            if (connected) viewModel.record(OnboardingStep.ACCESSIBILITY_GRANTED)
        }
        AccessibilityGrantScreen(
            connected = connected,
            onOpenSettings = { AccessibilitySettings.open(context) },
            onContinue = { navController.navigate(OnboardingRoutes.OEM) },
        )
    }

    composable(OnboardingRoutes.OEM) {
        val context = LocalContext.current
        LaunchedEffect(Unit) { viewModel.record(OnboardingStep.OEM) }
        OemSurvivalScreen(
            profile = viewModel.oemProfile,
            onOpenBattery = { BatterySettings.open(context) },
            onContinue = { navController.navigate(OnboardingRoutes.DONE) },
        )
    }

    composable(OnboardingRoutes.DONE) {
        // On 33+ notifications need a runtime permission; request it contextually here so the
        // watchdog can later alert the driver if the reader gets killed.
        val needsNotifPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* result handled implicitly: the watchdog re-checks the grant before posting */ }
        OnboardingDoneScreen(
            showNotificationPrompt = needsNotifPermission,
            onRequestNotifications = {
                if (needsNotifPermission) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onFinish = { viewModel.completeOnboarding(); onComplete() },
        )
    }
}
