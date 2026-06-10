package mx.kompara.ui.onboarding

/**
 * The in-app banner state surfaced by [ServiceWatchdog]. The Inicio screen collects this to show a
 * "el lector se desactivó — reactívalo" banner with a one-tap re-enable path when the accessibility
 * service has been killed by an OEM task killer (B-036).
 */
enum class WatchdogState {
    /** Watchdog idle — onboarding not complete yet, or no opinion formed. No banner. */
    IDLE,

    /** Service is connected and healthy. No banner. */
    HEALTHY,

    /** Service was healthy and then dropped — show the re-enable banner + (already) notified. */
    DROPPED,
}

/**
 * Pure transition logic for the service watchdog, split from the Android-bound [ServiceWatchdog] so
 * the "armed + connected history → state + should-notify" decisions are unit-tested without a real
 * service or NotificationManager.
 *
 * The watchdog only fires once onboarding is complete (we don't nag a driver who hasn't finished
 * setup), and only treats a drop as actionable if the service was previously seen connected this
 * session — that way a cold start (never-connected) doesn't immediately alarm.
 */
class WatchdogStateMachine {

    var state: WatchdogState = WatchdogState.IDLE
        private set

    private var everConnected: Boolean = false

    /**
     * Feed a new sample. Returns true when this transition should post a notification — i.e. the
     * service just dropped from a previously-healthy state while armed. The in-app banner state is
     * always reflected in [state].
     *
     * @param armed whether onboarding is complete (the watchdog is active).
     * @param connected the latest service-connected sample.
     */
    fun onSample(armed: Boolean, connected: Boolean): Boolean {
        if (!armed) {
            state = WatchdogState.IDLE
            return false
        }
        if (connected) {
            everConnected = true
            state = WatchdogState.HEALTHY
            return false
        }
        // Disconnected while armed.
        if (!everConnected) {
            // Never came up this session — don't alarm on a cold start; wait for first connect.
            state = WatchdogState.IDLE
            return false
        }
        val wasHealthy = state == WatchdogState.HEALTHY
        state = WatchdogState.DROPPED
        // Only notify on the edge (healthy -> dropped), not on every repeated disconnected sample.
        return wasHealthy
    }
}
