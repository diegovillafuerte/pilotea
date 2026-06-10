package mx.kompara.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Service-health watchdog transition logic (B-036). */
class WatchdogStateMachineTest {

    @Test
    fun `idle and silent until onboarding is complete`() {
        val sm = WatchdogStateMachine()
        // Not armed: even a drop produces no banner and no notification.
        assertFalse(sm.onSample(armed = false, connected = false))
        assertEquals(WatchdogState.IDLE, sm.state)
        assertFalse(sm.onSample(armed = false, connected = true))
        assertEquals(WatchdogState.IDLE, sm.state)
    }

    @Test
    fun `armed and connected becomes healthy without notifying`() {
        val sm = WatchdogStateMachine()
        assertFalse(sm.onSample(armed = true, connected = true))
        assertEquals(WatchdogState.HEALTHY, sm.state)
    }

    @Test
    fun `cold start disconnected does not alarm before first connect`() {
        val sm = WatchdogStateMachine()
        // Armed but the service never came up yet — wait, don't alarm.
        assertFalse(sm.onSample(armed = true, connected = false))
        assertEquals(WatchdogState.IDLE, sm.state)
    }

    @Test
    fun `healthy then dropped notifies once and shows the banner`() {
        val sm = WatchdogStateMachine()
        sm.onSample(armed = true, connected = true)
        val notified = sm.onSample(armed = true, connected = false)
        assertTrue("drop from healthy should notify", notified)
        assertEquals(WatchdogState.DROPPED, sm.state)
    }

    @Test
    fun `repeated disconnected samples do not re-notify`() {
        val sm = WatchdogStateMachine()
        sm.onSample(armed = true, connected = true)
        assertTrue(sm.onSample(armed = true, connected = false))
        // Still down — banner stays, but no second notification.
        assertFalse(sm.onSample(armed = true, connected = false))
        assertEquals(WatchdogState.DROPPED, sm.state)
    }

    @Test
    fun `recovery clears the banner and re-arms for the next drop`() {
        val sm = WatchdogStateMachine()
        sm.onSample(armed = true, connected = true)
        sm.onSample(armed = true, connected = false) // dropped, notified
        // Driver re-enables.
        assertFalse(sm.onSample(armed = true, connected = true))
        assertEquals(WatchdogState.HEALTHY, sm.state)
        // A second kill should notify again.
        assertTrue(sm.onSample(armed = true, connected = false))
        assertEquals(WatchdogState.DROPPED, sm.state)
    }

    @Test
    fun `disarming after a drop goes back to idle`() {
        val sm = WatchdogStateMachine()
        sm.onSample(armed = true, connected = true)
        sm.onSample(armed = true, connected = false)
        assertFalse(sm.onSample(armed = false, connected = false))
        assertEquals(WatchdogState.IDLE, sm.state)
    }
}
