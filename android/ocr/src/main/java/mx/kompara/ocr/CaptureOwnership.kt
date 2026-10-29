package mx.kompara.ocr

internal enum class CaptureLane { SILENT, PROJECTION }
internal data class CaptureSession(val lane: CaptureLane, val generation: Long)
internal data class CapturePermit(val session: CaptureSession, val generation: Long)
internal data class CaptureOwnerState(val owner: CapturePermit?, val silentFailed: Boolean)

/**
 * One process-wide arbiter for BOTH pipelines. Every publish/close and ownership transition runs
 * under this monitor; recognition runs outside it and must present its immutable permit afterward.
 * Silent wins. Projection keeps its one-shot consent and drains/discards images while parked, then
 * resumes with a NEW permit if silent stands down. Stopping the platform projection would destroy
 * the driver's grant, so only its OCR pipeline is stopped during this handoff.
 */
internal class CaptureOwnership(private val publish: (CaptureOwnerState, Boolean, Boolean) -> Unit) {
    private data class Registration(val session: CaptureSession, val close: () -> Unit)
    private val registrations = mutableMapOf<CaptureLane, Registration>()
    private var nextGeneration = 0L
    private var current: CapturePermit? = null
    private var silentFailed = false

    @Synchronized fun <T> serialized(block: () -> T): T = block()

    @Synchronized fun start(lane: CaptureLane, close: () -> Unit): CaptureSession {
        val session = CaptureSession(lane, ++nextGeneration)
        // Close before replacing the registration: it belongs to the old generation.
        if (current?.session?.lane == lane) relinquish()
        registrations[lane] = Registration(session, close)
        if (lane == CaptureLane.SILENT) silentFailed = false
        selectOwner()
        publishState()
        return session
    }

    @Synchronized fun stop(session: CaptureSession, failed: Boolean = false, intentional: Boolean = false): Boolean {
        if (registrations[session.lane]?.session != session) return false
        if (current?.session == session) relinquish()
        registrations.remove(session.lane)
        if (session.lane == CaptureLane.SILENT && failed) silentFailed = true
        selectOwner()
        publishState(intentional, failed)
        return true
    }

    /** Startup refusal has no loop token yet; never change a live session's failure state. */
    @Synchronized fun silentUnavailable() {
        if (CaptureLane.SILENT in registrations) return
        silentFailed = true
        publishState(failed = true)
    }

    /** Rebinding resets readiness evidence even when only a retained projection is available. */
    @Synchronized fun reaffirm() {
        publishState()
    }

    /** Setup can fail before a session exists; a surviving owner still determines readiness. */
    @Synchronized fun startupFailed() = publishState(failed = true)

    @Synchronized fun permit(session: CaptureSession): CapturePermit? = current?.takeIf { it.session == session }

    @Synchronized fun withOwner(permit: CapturePermit, block: () -> Unit): Boolean {
        if (current != permit) return false
        block()
        return true
    }

    private fun selectOwner() {
        val selected = registrations[CaptureLane.SILENT] ?: registrations[CaptureLane.PROJECTION]
        if (current?.session == selected?.session) return
        relinquish()
        current = selected?.let { CapturePermit(it.session, ++nextGeneration) }
    }

    private fun relinquish() {
        val previous = current ?: return
        current = null
        // Callers contain cleanup errors; a close must finish before a replacement may publish.
        registrations[previous.session.lane]?.close?.invoke()
    }

    private fun publishState(intentional: Boolean = false, failed: Boolean = false) =
        publish(CaptureOwnerState(current, silentFailed), intentional, failed && current == null)
}
