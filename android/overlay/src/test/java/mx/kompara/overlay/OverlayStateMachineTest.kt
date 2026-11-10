package mx.kompara.overlay

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mx.kompara.capture.OfferEvent
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.metrics.*
import mx.kompara.parsers.model.OfferCard
import mx.kompara.ui.components.ReglaBand
import mx.kompara.ui.components.ReglaPosition
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OverlayStateMachineTest {
    private val threshold = PlatformThreshold(8.0, 200.0, 6.0, 150.0)
    private fun state(fare: Double = 100.0) = VerdictChipState.from(NetProfitEngine().evaluate(
        TripOffer("uber", fare, 2.0, 5.0, 8.0, 15.0), CostProfile.ZERO, threshold))
    private fun parsed(ts: Long = 1, session: String = "one", host: String = "uber") = OfferEvent.Parsed(
        host, ts, OfferCard(host, fare = 100.0, sessionId = session))
    private fun noCard(ts: Long = 2, host: String = "uber", reason: OfferEvent.Reason = OfferEvent.Reason.NOT_AN_OFFER) =
        OfferEvent.NoCard(host, ts, reason)
    private class Run(val events: MutableSharedFlow<OfferEvent>, val seen: MutableList<OverlayVisibility>)
    private fun TestScope.observe(evaluate: suspend (OfferEvent.Parsed) -> VerdictChipState = { state() }): Run {
        val events = MutableSharedFlow<OfferEvent>(extraBufferCapacity = 16)
        val seen = mutableListOf<OverlayVisibility>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            OverlayStateMachine(evaluate = evaluate).visibility(events).collect { seen += it }
        }
        return Run(events, seen)
    }

    @Test fun `Parsed clears stale content and shows without a debounce`() = runTest {
        val (events, seen) = observe().let { it.events to it.seen }
        events.emit(parsed()); testScheduler.runCurrent()
        assertTrue(seen[0] is OverlayVisibility.Reading)
        assertTrue(seen[1] is OverlayVisibility.Showing)
        assertEquals(0, testScheduler.currentTime)
    }

    // 0.2.14: a held/uncertain frame (LayoutUncertain) is only a heartbeat; it no longer detaches.
    @Test fun `held frames keep the chip and a signature-free frame soft-hides without losing the session`() = runTest {
        var evaluations = 0
        val run = observe { evaluations++; state() }
        run.events.emit(parsed()); testScheduler.runCurrent()
        repeat(3) {
            run.events.emit(OfferEvent.LayoutUncertain("uber", 2L + it)); testScheduler.runCurrent()
        }
        assertTrue("Held frames never change visibility", run.seen.last() is OverlayVisibility.Showing)
        run.events.emit(noCard(5, reason = OfferEvent.Reason.SIGNATURE_ABSENT)); testScheduler.runCurrent()
        assertEquals(OverlayVisibility.SoftHidden, run.seen.last())
        assertEquals("No grace or exit timer for the soft hide", 0, testScheduler.currentTime)
        assertEquals(1, evaluations)
        run.events.emit(parsed(6)); testScheduler.runCurrent()
        assertEquals("uber:one", (run.seen.last() as OverlayVisibility.Showing).offerKey)
        assertEquals(1, run.seen.filterIsInstance<OverlayVisibility.Reading>().size)
        assertEquals(2, evaluations)
        assertFalse(run.seen.contains(OverlayVisibility.Hidden))
    }

    @Test fun `another hosts uncertain geometry cannot suppress the current offer`() = runTest {
        val run = observe()
        run.events.emit(parsed()); testScheduler.runCurrent()
        run.events.emit(OfferEvent.LayoutUncertain("didi", 2)); testScheduler.runCurrent()
        assertTrue(run.seen.last() is OverlayVisibility.Showing)
    }

    @Test fun `confirmed absence and foreground departure hide immediately without grace`() = runTest {
        for (reason in listOf(OfferEvent.Reason.CONFIRMED_ABSENCE, OfferEvent.Reason.NOT_VISIBLE)) {
            val run = observe()
            run.events.emit(parsed()); testScheduler.runCurrent()
            run.events.emit(noCard(reason = reason)); testScheduler.runCurrent()
            assertEquals(OverlayVisibility.Hidden, run.seen.last())
            assertFalse(run.seen.contains(OverlayVisibility.Exiting))
            assertEquals(0, testScheduler.currentTime)
            run.events.emit(parsed(3)); testScheduler.runCurrent()
            assertEquals("uber:one", (run.seen.last() as OverlayVisibility.Showing).offerKey)
        }
    }

    @Test fun `same offer has 500ms grace then 160ms exit before detach`() = runTest {
        val run = observe(); run.events.emit(parsed()); testScheduler.runCurrent()
        run.events.emit(noCard()); testScheduler.runCurrent()
        testScheduler.advanceTimeBy(499); testScheduler.runCurrent()
        assertTrue(run.seen.last() is OverlayVisibility.Showing)
        testScheduler.advanceTimeBy(1); testScheduler.runCurrent()
        assertEquals(OverlayVisibility.Exiting, run.seen.last())
        testScheduler.advanceTimeBy(159); testScheduler.runCurrent()
        assertEquals(OverlayVisibility.Exiting, run.seen.last())
        testScheduler.advanceTimeBy(1); testScheduler.runCurrent()
        assertEquals(OverlayVisibility.Hidden, run.seen.last())
    }

    @Test fun `a Parsed inside the grace cancels the pending hide without another Reading`() = runTest {
        val run = observe(); run.events.emit(parsed()); testScheduler.runCurrent()
        run.events.emit(noCard()); testScheduler.advanceTimeBy(200)
        run.events.emit(parsed(3)); testScheduler.advanceTimeBy(1000); testScheduler.runCurrent()
        assertEquals(2, run.seen.size) // Reading + one distinct Showing
    }

    @Test fun `evaluate runs per Parsed but identical complete states are distinct`() = runTest {
        var calls = 0
        val run = observe { calls++; state() }
        run.events.emit(parsed()); testScheduler.runCurrent()
        run.events.emit(parsed(2)); testScheduler.runCurrent()
        assertEquals(2, calls); assertEquals(2, run.seen.size)
    }

    @Test fun `continuous absent frames do not postpone exit`() = runTest {
        val run = observe(); run.events.emit(parsed()); testScheduler.runCurrent()
        repeat(7) { run.events.emit(noCard(100L + it)); testScheduler.advanceTimeBy(100); testScheduler.runCurrent() }
        assertEquals(OverlayVisibility.Hidden, run.seen.last())
    }

    @Test fun `new offer clears rate while its evaluation is suspended and cancels old exit`() = runTest {
        val run = observe { if (it.card.sessionId == "two") delay(100); state() }
        run.events.emit(parsed()); testScheduler.runCurrent()
        run.events.emit(noCard()); testScheduler.advanceTimeBy(200)
        run.events.emit(parsed(3, "two")); testScheduler.runCurrent()
        assertEquals("uber:two", (run.seen.last() as OverlayVisibility.Reading).offerKey)
        testScheduler.advanceTimeBy(99); testScheduler.runCurrent()
        assertTrue(run.seen.last() is OverlayVisibility.Reading)
        testScheduler.advanceTimeBy(1); testScheduler.runCurrent()
        assertEquals("uber:two", (run.seen.last() as OverlayVisibility.Showing).offerKey)
        testScheduler.advanceTimeBy(1000); testScheduler.runCurrent()
        assertTrue(run.seen.none { it == OverlayVisibility.Hidden })
    }

    @Test fun `host change clears immediately even without a parsed offer`() = runTest {
        val run = observe(); run.events.emit(parsed()); testScheduler.runCurrent()
        run.events.emit(noCard(host = "didi")); testScheduler.runCurrent()
        assertEquals(OverlayVisibility.Hidden, run.seen.last())
        assertEquals(0, testScheduler.currentTime)
    }

    @Test fun `new host with same session id and money still clears first`() = runTest {
        val run = observe(); run.events.emit(parsed()); testScheduler.runCurrent()
        run.events.emit(parsed(3, host = "didi")); testScheduler.runCurrent()
        assertEquals(listOf("uber:one", "didi:one"), run.seen.filterIsInstance<OverlayVisibility.Reading>().map { it.offerKey })
    }

    @Test fun `disabled and unsupported hosts detach without grace or stale exit`() = runTest {
        for (event in listOf(OfferEvent.SpecDisabled("uber", 3), noCard(reason = OfferEvent.Reason.NO_SPEC))) {
            val run = observe(); run.events.emit(parsed()); testScheduler.runCurrent()
            run.events.emit(event); testScheduler.runCurrent()
            assertEquals(OverlayVisibility.Hidden, run.seen.last())
        }
        assertEquals(0, testScheduler.currentTime)
    }

    @Test fun `a late evaluation cannot resurrect an old host`() = runTest {
        val run = observe { delay(1000); state() }
        run.events.emit(parsed()); testScheduler.runCurrent()
        run.events.emit(noCard(host = "didi")); testScheduler.runCurrent()
        testScheduler.advanceTimeBy(1200); testScheduler.runCurrent()
        assertEquals(OverlayVisibility.Hidden, run.seen.last())
        assertTrue(run.seen.none { it is OverlayVisibility.Showing })
    }

    @Test fun `same offer changing judgement is one atomic state`() = runTest {
        var count = 0
        val run = observe { state(if (count++ == 0) 100.0 else 20.0) }
        run.events.emit(parsed()); testScheduler.runCurrent()
        run.events.emit(parsed(2)); testScheduler.runCurrent()
        assertEquals(1, run.seen.filterIsInstance<OverlayVisibility.Reading>().size)
        val states = run.seen.filterIsInstance<OverlayVisibility.Showing>().map { it.state.regla }
        assertEquals(listOf(ReglaPosition.ABOVE, ReglaPosition.BELOW), states.map { (it.band as ReglaBand.Rated).position })
    }

    @Test fun `offer returning during exit cancels detach`() = runTest {
        val run = observe(); run.events.emit(parsed()); testScheduler.runCurrent()
        run.events.emit(noCard()); testScheduler.advanceTimeBy(550); testScheduler.runCurrent()
        assertEquals(OverlayVisibility.Exiting, run.seen.last())
        run.events.emit(parsed(4)); testScheduler.advanceTimeBy(1000); testScheduler.runCurrent()
        assertTrue(run.seen.last() is OverlayVisibility.Showing)
        assertTrue(run.seen.none { it == OverlayVisibility.Hidden })
    }

    @Test fun `unchanged money still delivers changed host bounds on the same offer`() = runTest {
        val run = observe()
        val geometry = mx.kompara.parsers.model.HostGeometry("uber", "test", 1,
            mx.kompara.parsers.model.HostRect(0,0,393,852), mx.kompara.parsers.model.HostOrientation.PORTRAIT,
            mx.kompara.parsers.model.HostRect(0,80,393,128), mx.kompara.parsers.model.HostRect(0,545,393,838),
            listOf(mx.kompara.parsers.model.HostRect(20,750,373,810)), emptyList())
        run.events.emit(parsed().let { it.copy(card=it.card.copy(hostGeometry=geometry)) }); testScheduler.runCurrent()
        run.events.emit(parsed(2).let { it.copy(card=it.card.copy(hostGeometry=geometry.copy(capturedAtMs=2,
            offerCard=mx.kompara.parsers.model.HostRect(0,260,393,838)))) }); testScheduler.runCurrent()
        assertEquals(2,run.seen.filterIsInstance<OverlayVisibility.Showing>().size)
        assertEquals(1,run.seen.filterIsInstance<OverlayVisibility.Reading>().size)
        assertEquals(260,run.seen.filterIsInstance<OverlayVisibility.Showing>().last().event.card.hostGeometry?.offerCard?.top)
    }
    @Test fun `same offer swaps host appearance without a new reading or changing placement`() = runTest {
        val run = observe()
        val geometry = mx.kompara.parsers.model.HostGeometry("uber", "test", 1,
            mx.kompara.parsers.model.HostRect(0,0,393,852), mx.kompara.parsers.model.HostOrientation.PORTRAIT,
            mx.kompara.parsers.model.HostRect(0,80,393,128), mx.kompara.parsers.model.HostRect(0,545,393,838),
            emptyList(), emptyList(), appearance = mx.kompara.parsers.model.HostAppearance.LIGHT)
        val night = geometry.copy(appearance = mx.kompara.parsers.model.HostAppearance.DARK)
        run.events.emit(parsed().let { it.copy(card=it.card.copy(hostGeometry=geometry)) }); testScheduler.runCurrent()
        run.events.emit(parsed(2).let { it.copy(card=it.card.copy(hostGeometry=night)) }); testScheduler.runCurrent()
        assertEquals(2,run.seen.filterIsInstance<OverlayVisibility.Showing>().size)
        assertEquals(1,run.seen.filterIsInstance<OverlayVisibility.Reading>().size)
        assertEquals(mx.kompara.parsers.model.HostAppearance.DARK,
            run.seen.filterIsInstance<OverlayVisibility.Showing>().last().event.card.hostGeometry?.appearance)
        assertTrue(OverlayPositioning.sameLayout(geometry, night))
    }

}
