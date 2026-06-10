package mx.kompara.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.kompara.data.service.ServiceStatusProvider
import mx.kompara.data.settings.SettingsRepository
import javax.inject.Inject

/**
 * Drives the onboarding funnel (B-036): records funnel step transitions, exposes the live
 * accessibility-service connected state (so the grant screen can auto-advance when the driver flips
 * the switch), and persists completion.
 *
 * Reads service health via the `:data` [ServiceStatusProvider] abstraction so `:ui` never touches
 * `:capture`.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val funnel: OnboardingFunnel,
    serviceStatus: ServiceStatusProvider,
) : ViewModel() {

    /** Live connected state of the reader service — true once the driver grants accessibility. */
    val serviceConnected: StateFlow<Boolean> = serviceStatus.connected

    /** The detected OEM profile for this device (drives the survival-kit steps). */
    val oemProfile: OemProfile = OemDetector.current()

    /** Record a funnel step transition (local counter + Logcat; no network, no PII). */
    fun record(step: OnboardingStep) {
        viewModelScope.launch { funnel.record(step) }
    }

    /** Persist onboarding completion; the root composable then switches to the main shell. */
    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
            funnel.record(OnboardingStep.DONE)
        }
    }
}

/**
 * Root-level state holder: observes [SettingsRepository] for the onboarding-completed flag and maps
 * it to a [RootRoute] for the app root composable. Kept separate from [OnboardingViewModel] so the
 * root can decide onboarding-vs-shell without constructing the funnel machinery.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /** LOADING until settings emit, then ONBOARDING or MAIN. */
    val route: StateFlow<RootRoute> =
        settingsRepository.settings.map { settings ->
            RootRouter.route(settings.onboardingCompleted)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RootRoute.LOADING,
        )
}
