package mx.kompara.ocr

import java.util.concurrent.atomic.AtomicReference

/** Intentional shutdown rejects new failures without discarding an already accepted failure. */
internal class ProjectionLifecycle {
    private enum class State { RUNNING, STOPPING, FAILED }
    private val state = AtomicReference(State.RUNNING)

    val isRunning: Boolean get() = state.get() == State.RUNNING
    val isIntentionalStop: Boolean get() = state.get() == State.STOPPING

    fun stopIntentionally() {
        state.compareAndSet(State.RUNNING, State.STOPPING)
    }

    fun fail(): Boolean = state.compareAndSet(State.RUNNING, State.FAILED)
}
