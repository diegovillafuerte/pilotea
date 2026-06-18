package mx.kompara.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import mx.kompara.ui.R
import mx.kompara.ui.auth.SignupFlowScreen
import mx.kompara.ui.components.KomparaProgressBar

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
        // First step of the funnel: no back chevron, no progress chrome (mirrors the design where
        // the topbar is empty until the driver moves past the value pitch).
        OnboardingScaffold(stepIndex = 0, showBack = false, showProgress = false, onBack = {}) {
            PitchScreen(
                onFinished = { navController.navigate(OnboardingRoutes.SIGNUP) },
            )
        }
    }

    composable(OnboardingRoutes.SIGNUP) {
        LaunchedEffect(Unit) { viewModel.record(OnboardingStep.SIGNUP) }
        OnboardingScaffold(stepIndex = 1, onBack = { navController.popBackStack() }) {
            SignupFlowScreen(
                onComplete = {
                    viewModel.record(OnboardingStep.SIGNUP_DONE)
                    navController.navigate(OnboardingRoutes.DISCLOSURE)
                },
            )
        }
    }

    composable(OnboardingRoutes.DISCLOSURE) {
        LaunchedEffect(Unit) { viewModel.record(OnboardingStep.DISCLOSURE) }
        OnboardingScaffold(stepIndex = 2, onBack = { navController.popBackStack() }) {
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
    }

    composable(OnboardingRoutes.LIMITED_INFO) {
        // Off-funnel decline branch: keep the back chevron (it mirrors the screen's own "review"
        // affordance) but no progress, since this leaf isn't part of the linear funnel.
        OnboardingScaffold(stepIndex = 0, showProgress = false, onBack = { navController.popBackStack() }) {
            LimitedInfoScreen(
                onReview = { navController.popBackStack() },
                onContinue = { viewModel.completeOnboarding(); onComplete() },
            )
        }
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
        OnboardingScaffold(stepIndex = 3, onBack = { navController.popBackStack() }) {
            AccessibilityGrantScreen(
                connected = connected,
                onOpenSettings = { AccessibilitySettings.open(context) },
                onContinue = { navController.navigate(OnboardingRoutes.OEM) },
            )
        }
    }

    composable(OnboardingRoutes.OEM) {
        val context = LocalContext.current
        LaunchedEffect(Unit) { viewModel.record(OnboardingStep.OEM) }
        OnboardingScaffold(stepIndex = 4, onBack = { navController.popBackStack() }) {
            OemSurvivalScreen(
                profile = viewModel.oemProfile,
                onOpenBattery = { BatterySettings.open(context) },
                onContinue = { navController.navigate(OnboardingRoutes.DONE) },
            )
        }
    }

    composable(OnboardingRoutes.DONE) {
        // On 33+ notifications need a runtime permission; request it contextually here so the
        // watchdog can later alert the driver if the reader gets killed.
        val needsNotifPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* result handled implicitly: the watchdog re-checks the grant before posting */ }
        // Final celebratory step: design hides the progress chrome here (the funnel is complete).
        OnboardingScaffold(stepIndex = ONBOARDING_TOTAL_STEPS, showProgress = false, onBack = { navController.popBackStack() }) {
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
}

/**
 * Total number of progress-bearing funnel steps. The visible nav destinations that carry progress
 * are SIGNUP, DISCLOSURE, ACCESSIBILITY, OEM and DONE (PITCH is the value-pitch intro and carries
 * no chrome; LIMITED_INFO is the off-funnel decline branch). The design's own counter splits SIGNUP
 * into phone/code/profile sub-pages, which this graph hosts inside a single SignupFlowScreen
 * destination, so the on-device denominator is 5 rather than the prototype's 7. See task notes.
 */
private const val ONBOARDING_TOTAL_STEPS = 5

/**
 * Persistent onboarding chrome (design: Onboarding.dc.html `.topbar`) wrapped around each funnel
 * destination so the screens themselves stay untouched. Renders an optional back chevron, a thin
 * progress bar (filled to [stepIndex] / [ONBOARDING_TOTAL_STEPS]) and a "n/total" step label, then
 * the screen content fills the remaining space.
 */
@Composable
private fun OnboardingScaffold(
    stepIndex: Int,
    onBack: () -> Unit,
    showBack: Boolean = true,
    showProgress: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OnboardingTopBar(
            stepIndex = stepIndex,
            total = ONBOARDING_TOTAL_STEPS,
            showBack = showBack,
            showProgress = showProgress,
            onBack = onBack,
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            content()
        }
    }
}

/**
 * The topbar row: back chevron (left), progress bar (centre, weighted), step label (right). The
 * back chevron and the progress/label are independently toggled so the pitch intro can hide the
 * whole chrome, the decline branch can keep only the chevron, and the celebratory final step can
 * keep the chevron but drop the progress.
 */
@Composable
private fun OnboardingTopBar(
    stepIndex: Int,
    total: Int,
    showBack: Boolean,
    showProgress: Boolean,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showBack) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.onb_atras),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showProgress) {
            KomparaProgressBar(
                progress = if (total > 0) stepIndex.toFloat() / total.toFloat() else 0f,
                modifier = Modifier.weight(1f),
                height = 4.dp,
            )
            Text(
                text = "$stepIndex/$total",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
