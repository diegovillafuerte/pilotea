package mx.kompara.ui.paywall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure counter-key + increment maths for the gate funnel (B-050). */
class GateCountersTest {

    @Test
    fun `key namespaces by surface and event`() {
        assertEquals("gate_funnel_fiscal_gate_shown", GateCounters.key(GateSurface.FISCAL, GateEvent.GATE_SHOWN))
        assertEquals(
            "gate_funnel_benchmarks_trial_started",
            GateCounters.key(GateSurface.BENCHMARKS, GateEvent.TRIAL_STARTED),
        )
    }

    @Test
    fun `every surface-event pair yields a unique key`() {
        val keys = GateSurface.entries.flatMap { s -> GateEvent.entries.map { e -> GateCounters.key(s, e) } }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `increment starts at one and steps by one`() {
        assertEquals(1, GateCounters.increment(null))
        assertEquals(4, GateCounters.increment(3))
    }

    @Test
    fun `increment floors a corrupt negative at one and saturates at max`() {
        assertEquals(1, GateCounters.increment(-7))
        assertEquals(Int.MAX_VALUE, GateCounters.increment(Int.MAX_VALUE))
    }

    @Test
    fun `log line carries no personal data, only stable keys and the count`() {
        val line = GateCounters.logLine(GateSurface.HISTORY, GateEvent.PAYWALL_OPENED, 5)
        assertTrue(line.contains("surface=history"))
        assertTrue(line.contains("event=paywall_opened"))
        assertTrue(line.contains("count=5"))
    }

    @Test
    fun `enum keys are stable strings decoupled from constant names`() {
        // Renaming a constant must not silently reset a counter — the key is the source of truth.
        assertEquals("compare", GateSurface.COMPARE.key)
        assertEquals("gate_shown", GateEvent.GATE_SHOWN.key)
        assertNotEquals(GateSurface.COMPARE.key, GateSurface.GENERIC.key)
    }
}
