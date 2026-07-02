package mx.kompara.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

/** The pure gating decision for the silent-screenshot capture lane (B-091). */
class SilentLaneGateTest {

    private val maxFailures = 5

    @Test
    fun `captures when a host app is foreground and the screen is on`() {
        assertEquals(
            LaneAction.CAPTURE,
            SilentLaneGate.decide(hostForeground = true, screenInteractive = true, consecutiveHardFailures = 0, maxHardFailures = maxFailures),
        )
    }

    @Test
    fun `idles when no host app is foreground`() {
        assertEquals(
            LaneAction.IDLE,
            SilentLaneGate.decide(hostForeground = false, screenInteractive = true, consecutiveHardFailures = 0, maxHardFailures = maxFailures),
        )
    }

    @Test
    fun `idles when the screen is off even though a host is foreground`() {
        assertEquals(
            LaneAction.IDLE,
            SilentLaneGate.decide(hostForeground = true, screenInteractive = false, consecutiveHardFailures = 0, maxHardFailures = maxFailures),
        )
    }

    @Test
    fun `does not disable while under the hard-failure threshold`() {
        assertEquals(
            LaneAction.CAPTURE,
            SilentLaneGate.decide(hostForeground = true, screenInteractive = true, consecutiveHardFailures = maxFailures - 1, maxHardFailures = maxFailures),
        )
    }

    @Test
    fun `disables once the hard-failure threshold is reached, regardless of host state`() {
        assertEquals(
            LaneAction.DISABLE,
            SilentLaneGate.decide(hostForeground = true, screenInteractive = true, consecutiveHardFailures = maxFailures, maxHardFailures = maxFailures),
        )
        // Disable wins even if nothing is foreground — the lane is standing down for good.
        assertEquals(
            LaneAction.DISABLE,
            SilentLaneGate.decide(hostForeground = false, screenInteractive = false, consecutiveHardFailures = maxFailures + 3, maxHardFailures = maxFailures),
        )
    }
}
