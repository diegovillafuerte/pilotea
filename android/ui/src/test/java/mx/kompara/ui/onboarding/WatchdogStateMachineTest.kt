package mx.kompara.ui.onboarding

import mx.kompara.data.service.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock

class WatchdogStateMachineTest {
    private val repo = ReaderReadinessRepository(object : ReaderMemoryStore {
        override fun load() = ReaderMemory()
        override fun save(memory: ReaderMemory) = Unit
    }, Clock.systemUTC())
    private val sm = WatchdogStateMachine()
    private fun drop() { repo.bound(); repo.disconnected(false) }
    @Test fun `idle and silent until onboarding is complete`() {
        drop(); assertFalse(sm.onSample(false, repo.state.value)); assertEquals(WatchdogState.IDLE, sm.state)
    }
    @Test fun `connected is prepared not proof of working`() {
        repo.bound(); assertFalse(sm.onSample(true, repo.state.value)); assertEquals(WatchdogState.PREPARED, sm.state)
    }
    @Test fun `cold start disconnected does not alarm before first connect`() {
        assertFalse(sm.onSample(true, repo.state.value)); assertEquals(WatchdogState.PREPARED, sm.state)
    }
    @Test fun `drop is deliverable once and remains a banner after delivery`() {
        drop(); assertTrue(sm.onSample(true, repo.state.value)); assertEquals(WatchdogState.DROPPED, sm.state)
        repo.markNotified(repo.state.value.incident!!)
        assertFalse(sm.onSample(true, repo.state.value)); assertEquals(WatchdogState.DROPPED, sm.state)
    }
    @Test fun `recreating watchdog does not re-notify unresolved incident`() {
        drop(); repo.markNotified(repo.state.value.incident!!)
        assertFalse(WatchdogStateMachine().onSample(true, repo.state.value))
    }
    @Test fun `recovery clears and new outage re-arms`() {
        drop(); repo.bound(); assertFalse(sm.onSample(true, repo.state.value))
        repo.disconnected(true); assertTrue(sm.onSample(true, repo.state.value))
    }
    @Test fun `pause or disarm after drop suppresses notice and banner`() {
        drop(); assertFalse(sm.onSample(false, repo.state.value)); assertEquals(WatchdogState.IDLE, sm.state)
        repo.pauseUntilTomorrow(); assertFalse(sm.onSample(true, repo.state.value)); assertEquals(WatchdogState.IDLE, sm.state)
        repo.resume(); assertTrue(sm.onSample(true, repo.state.value))
    }
}
