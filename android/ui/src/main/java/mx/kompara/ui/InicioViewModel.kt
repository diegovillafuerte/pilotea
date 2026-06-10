package mx.kompara.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import mx.kompara.ui.onboarding.AccessibilitySettings
import mx.kompara.ui.onboarding.ServiceWatchdog
import mx.kompara.ui.onboarding.WatchdogState
import javax.inject.Inject

/**
 * Backs the Inicio screen's reader-health banner (B-036). Exposes the [ServiceWatchdog] in-app
 * banner state and a one-tap re-enable path that deep-links to Accessibility settings.
 */
@HiltViewModel
class InicioViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    watchdog: ServiceWatchdog,
) : ViewModel() {

    /** DROPPED while the reader is down (after a healthy session); the screen shows a banner then. */
    val watchdogState: StateFlow<WatchdogState> = watchdog.bannerState

    /** One-tap re-enable: open Accessibility settings so the driver can flip the reader back on. */
    fun reEnableReader() {
        AccessibilitySettings.open(context)
    }
}
