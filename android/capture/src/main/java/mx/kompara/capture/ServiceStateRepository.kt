package mx.kompara.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mx.kompara.data.service.ServiceStatusProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether [KomparaAccessibilityService] is currently connected.
 *
 * Singleton-scoped so the service (which is constructed by the framework) and any watchdog UI share
 * one source of truth. The service flips this from `onServiceConnected`/`onDestroy`; observers (e.g.
 * an overlay health badge, the onboarding accessibility-grant detector, or the watchdog) collect
 * [connected] to detect a disabled or killed service.
 *
 * Implements the `:data` [ServiceStatusProvider] so the `:ui` layer can observe reader health
 * without depending on `:capture` (the binding lives in [mx.kompara.capture.di.ServiceStateModule]).
 */
@Singleton
class ServiceStateRepository @Inject constructor() : ServiceStatusProvider {

    private val _connected = MutableStateFlow(false)

    /** Emits true while the accessibility service is bound, false once it disconnects or dies. */
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun setConnected(connected: Boolean) {
        _connected.value = connected
    }
}
