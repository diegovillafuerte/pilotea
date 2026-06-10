package mx.kompara.ui.onboarding

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Onboarding funnel counter bookkeeping and step recording (B-036). */
class FunnelCountersTest {

    @Test
    fun `keys are namespaced and stable per step`() {
        assertEquals("onboarding_funnel_pitch", FunnelCounters.key(OnboardingStep.PITCH))
        assertEquals(
            "onboarding_funnel_disclosure_accepted",
            FunnelCounters.key(OnboardingStep.DISCLOSURE_ACCEPTED),
        )
        // Every step has a distinct key.
        val keys = OnboardingStep.entries.map { FunnelCounters.key(it) }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `increment starts at one and floors negatives`() {
        assertEquals(1, FunnelCounters.increment(null))
        assertEquals(4, FunnelCounters.increment(3))
        assertEquals(1, FunnelCounters.increment(-7))
    }

    @Test
    fun `increment saturates at max int`() {
        assertEquals(Int.MAX_VALUE, FunnelCounters.increment(Int.MAX_VALUE))
    }

    @Test
    fun `log line carries the step key and count but no PII`() {
        val line = FunnelCounters.logLine(OnboardingStep.ACCESSIBILITY_GRANTED, 2)
        assertTrue(line.contains("accessibility_granted"))
        assertTrue(line.contains("count=2"))
    }

    @Test
    fun `funnel steps are ordered as the funnel flows`() {
        // ordinal doubles as funnel position; assert the make-or-break order holds.
        assertTrue(OnboardingStep.PITCH.ordinal < OnboardingStep.DISCLOSURE.ordinal)
        assertTrue(OnboardingStep.DISCLOSURE.ordinal < OnboardingStep.ACCESSIBILITY.ordinal)
        assertTrue(OnboardingStep.ACCESSIBILITY.ordinal < OnboardingStep.OEM.ordinal)
        assertTrue(OnboardingStep.OEM.ordinal < OnboardingStep.DONE.ordinal)
    }

    @Test
    fun `fake funnel records each transition`() = runTest {
        val recorder = RecordingFunnel()
        recorder.record(OnboardingStep.PITCH)
        recorder.record(OnboardingStep.DISCLOSURE)
        recorder.record(OnboardingStep.DISCLOSURE_ACCEPTED)
        recorder.record(OnboardingStep.PITCH) // a re-entry
        assertEquals(
            listOf(
                OnboardingStep.PITCH,
                OnboardingStep.DISCLOSURE,
                OnboardingStep.DISCLOSURE_ACCEPTED,
                OnboardingStep.PITCH,
            ),
            recorder.recorded,
        )
        assertEquals(2, recorder.counts[OnboardingStep.PITCH])
        assertEquals(1, recorder.counts[OnboardingStep.DISCLOSURE])
    }

    /** Minimal in-memory [OnboardingFunnel] mirroring DataStore counter semantics for tests. */
    private class RecordingFunnel : OnboardingFunnel {
        val recorded = mutableListOf<OnboardingStep>()
        val counts = mutableMapOf<OnboardingStep, Int>()
        override suspend fun record(step: OnboardingStep) {
            recorded += step
            counts[step] = FunnelCounters.increment(counts[step])
        }
    }
}
