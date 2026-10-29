package mx.kompara.ui.onboarding

import mx.kompara.data.service.ReaderReadiness
import mx.kompara.data.service.ReaderPhase

/** Compatibility banner for the existing home until S7; all decisions use shared readiness. */
enum class WatchdogState { IDLE, PREPARED, HEALTHY, DROPPED }

class WatchdogStateMachine {
    var state = WatchdogState.IDLE
        private set

    fun onSample(armed: Boolean, readiness: ReaderReadiness): Boolean {
        state = when {
            !armed || readiness.paused -> WatchdogState.IDLE
            readiness.alertIncident != null -> WatchdogState.DROPPED
            readiness.phase in setOf(ReaderPhase.WORKING, ReaderPhase.ATTENTION) -> WatchdogState.HEALTHY
            else -> WatchdogState.PREPARED
        }
        // The delivery watermark is durable and owned by the incident repository, not this machine.
        return armed && readiness.alertIncident?.notified == false
    }
}
