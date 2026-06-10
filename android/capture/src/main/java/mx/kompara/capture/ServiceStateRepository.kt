package mx.kompara.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether [KomparaAccessibilityService] is currently connected.
 *
 * Singleton-scoped so the service (which is constructed by the framework) and any watchdog UI share
 * one source of truth. The service flips this from `onServiceConnected`/`onDestroy`; observers (e.g.
 * an overlay health badge) collect [connected] to detect a disabled or killed service.
 */
@Singleton
class ServiceStateRepository @Inject constructor() {

    private val _connected = MutableStateFlow(false)

    /** Emits true while the accessibility service is bound, false once it disconnects or dies. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun setConnected(connected: Boolean) {
        _connected.value = connected
    }
}
