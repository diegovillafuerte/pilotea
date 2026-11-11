package mx.kompara.ui.onboarding

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.data.service.ScreenReaderState

/** Short first run and recovery share the same saved owner; returning always invokes the caller. */
@Composable
fun OnboardingNavGraph(onComplete: () -> Unit, modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(), entry: OnboardingEntry = OnboardingEntry.FIRST_RUN) {
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val page by viewModel.page.collectAsStateWithLifecycle()
    val permission by viewModel.permissionReturn.collectAsStateWithLifecycle()
    val readiness by viewModel.readiness.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val done by rememberUpdatedState(onComplete)
    LaunchedEffect(entry) { viewModel.start(entry) }
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_RESUME -> viewModel.onResume()
            Lifecycle.Event.ON_PAUSE -> viewModel.onPause()
            else -> Unit
        } }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) viewModel.onResume()
        onDispose { lifecycle.removeObserver(observer); viewModel.onPause() }
    }
    BackHandler(page != OnboardingPage.PITCH) { viewModel.back() }
    if (!loaded) return
    when (page) {
        OnboardingPage.PITCH, OnboardingPage.SIGNIN -> PitchScreen(viewModel::begin, modifier)
        OnboardingPage.DISCLOSURE -> DisclosureScreen({ viewModel.consent(true) }, { viewModel.consent(false) }, modifier, viewModel::back, error)
        OnboardingPage.ACCESSIBILITY -> AccessibilityGrantScreen(permission, viewModel::openSettings, viewModel::finish, modifier, viewModel::back, error)
        OnboardingPage.BATTERY -> OemSurvivalScreen(readiness.permission == true, viewModel::openSettings, viewModel::finish,
            modifier, viewModel::back, recovered = entry != OnboardingEntry.FIRST_RUN, error = error,
            working = readiness.phase in listOf(mx.kompara.data.service.ReaderPhase.WORKING, mx.kompara.data.service.ReaderPhase.ATTENTION))
        OnboardingPage.CAPTURE -> CaptureConsentScreen({
            viewModel.beforeCapture()
            runCatching { context.startActivity(Intent(ScreenReaderState.ACTION_START).setPackage(context.packageName)) }
                .onFailure { viewModel.captureLaunchFailed() }
        }, viewModel::finish, error)
        OnboardingPage.DONE -> LaunchedEffect(Unit) { done() }
    }
}
