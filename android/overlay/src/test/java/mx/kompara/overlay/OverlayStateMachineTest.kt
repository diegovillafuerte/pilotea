package mx.kompara.overlay

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mx.kompara.capture.OfferEvent
import mx.kompara.metrics.OfferMetrics
import mx.kompara.metrics.Verdict
import mx.kompara.metrics.VerdictLevel
import mx.kompara.parsers.model.OfferCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the show/hide state machine with virtual time. The [OfferEvent] stream is a hot
 * [MutableSharedFlow] (mirroring `OfferEventPipeline.offers`); the visibility flow is collected on
 * the test's [kotlinx.coroutines.test.TestScope.backgroundScope] so it is auto-cancelled.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OverlayStateMachineTest {

    private fun greenMetrics() = OfferMetrics(
        grossMxn = 100.0, netMxn = 100.0, totalKm = 10.0, totalMin = 20.0,
        grossPerKm = 10.0, grossPerMin = 5.0, netPerKm = 10.0, netPerMin = 5.0, netPerHour = 300.0,
        verdict = Verdict(VerdictLevel.GREEN, 10.0, 300.0, 100.0, 10.0, emptyList()),
    )

    private fun parsed(ts: Long) = OfferEvent.Parsed(
        packageName = "com.ubercab.driver",
        timestampMs = ts,
        card = OfferCard(platform = "com.ubercab.driver", fare = 100.0),
    )

    private fun noCard(ts: Long) = OfferEvent.NoCard(
        packageName = "com.ubercab.driver",
        timestampMs = ts,
        reason = OfferEvent.Reason.NOT_AN_OFFER,
    )

    private fun machine() = OverlayStateMachine(graceMs = 500L, evaluate = { greenMetrics() })

    @Test
    fun `Parsed shows immediately`() = runTest {
        val events = MutableSharedFlow<OfferEvent>(extraBufferCapacity = 16)
        val seen = mutableListOf<OverlayVisibility>()
        // Collect on an unconfined dispatcher so the collector subscribes synchronously before the
        // first emit; otherwise a StandardTestDispatcher would buffer/drop into a non-subscriber.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            machine().visibility(events).collect { seen += it }
        }

        events.emit(parsed(1L))
        testScheduler.runCurrent()

        assertEquals(1, seen.size)
        assertTrue(seen.last() is OverlayVisibility.Showing)
    }

    @Test
    fun `NoCard hides only after the grace window`() = runTest {
        val events = MutableSharedFlow<OfferEvent>(extraBufferCapacity = 16)
        val seen = mutableListOf<OverlayVisibility>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            machine().visibility(events).collect { seen += it }
        }

        events.emit(parsed(1L))
        testScheduler.runCurrent()
        events.emit(noCard(2L))

        // Just before the grace elapses: still showing.
        testScheduler.advanceTimeBy(499L)
        testScheduler.runCurrent()
        assertTrue("hid too early", seen.last() is OverlayVisibility.Showing)

        // After the grace: hidden.
        testScheduler.advanceTimeBy(2L)
        testScheduler.runCurrent()
        assertEquals(OverlayVisibility.Hidden, seen.last())
    }

    @Test
    fun `a Parsed inside the grace cancels the pending hide (no flicker)`() = runTest {
        val events = MutableSharedFlow<OfferEvent>(extraBufferCapacity = 16)
        val seen = mutableListOf<OverlayVisibility>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            machine().visibility(events).collect { seen += it }
        }

        events.emit(parsed(1L))
        testScheduler.runCurrent()
        events.emit(noCard(2L))
        testScheduler.advanceTimeBy(200L) // inside the 500ms grace
        events.emit(parsed(3L)) // re-appears -> cancels the hide
        testScheduler.advanceTimeBy(1000L)
        testScheduler.runCurrent()

        // Never went Hidden; collapsed by distinctUntilChanged to a single Showing.
        assertTrue(seen.none { it is OverlayVisibility.Hidden })
        assertTrue(seen.last() is OverlayVisibility.Showing)
    }

    @Test
    fun `evaluate is invoked per Parsed to build the metrics`() = runTest {
        var calls = 0
        val machine = OverlayStateMachine(graceMs = 500L, evaluate = { calls++; greenMetrics() })
        val events = MutableSharedFlow<OfferEvent>(extraBufferCapacity = 16)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            machine.visibility(events).collect {}
        }

        events.emit(parsed(1L))
        events.emit(parsed(2L))
        testScheduler.runCurrent()

        assertEquals(2, calls)
    }
}
