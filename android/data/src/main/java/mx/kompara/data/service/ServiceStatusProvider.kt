package mx.kompara.data.service

import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only view of whether the accessibility reader service is currently connected.
 *
 * Lives in `:data` (not `:capture`) so the UI layer can observe reader health without taking a
 * dependency on `:capture` internals — the architecture rule is that `:ui` may depend on `:data`
 * and `:metrics` only. `:capture`'s `ServiceStateRepository` implements this and binds it via Hilt,
 * so onboarding (accessibility-grant detection) and the watchdog read one shared source of truth.
 */
interface ServiceStatusProvider {
    /** Emits true while the accessibility service is bound, false once it disconnects or dies. */
    val connected: StateFlow<Boolean>
}
