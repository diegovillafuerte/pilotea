package mx.kompara.capture

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mx.kompara.parsers.engine.SpecEngine
import mx.kompara.parsers.scrub.SnapshotScrubber
import mx.kompara.parsers.spec.BundledSpecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end wiring test for TD-007 (B-029): a [ScreenSnapshot] fed through [OfferEventPipeline]
 * (the real bundled registry + spec engine + PII scrubber) produces the right [OfferEvent].
 *
 * Runs under Robolectric only for a real `android.graphics.Rect`; everything else is plain JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class OfferEventPipelineTest {

    private val versionCodes = HostVersionCodes { 1000L }

    /** An [EventPipeline] backed by [reader]; emits only what its snapshot stream is driven to. */
    private fun eventPipeline(
        dispatcher: TestDispatcher,
        reader: SnapshotReader = SnapshotReader { _, _ -> null },
    ) = EventPipeline(reader, dispatcher, debounceMs = 80L)

    private fun offerPipeline(source: EventPipeline) = OfferEventPipeline(
        source = source,
        registry = BundledSpecs.registry(),
        engine = SpecEngine(),
        scrubber = SnapshotScrubber(),
        versionCodes = versionCodes,
    )

    private fun node(text: String?, viewId: String? = null, top: Int = 0) = SnapshotNode(
        text = text,
        viewId = viewId,
        className = "android.view.View",
        bounds = Rect(0, top, 700, top + 50),
        depth = 3,
        index = top,
    )

    private fun uberOfferSnapshot() = ScreenSnapshot(
        packageName = "com.ubercab.driver",
        timestampMs = 123L,
        nodes = listOf(
            node("UberX", top = 0),
            node("MX\$87.50", viewId = "com.ubercab.driver:id/upfront_fare", top = 100),
            node("4.92", viewId = "com.ubercab.driver:id/rider_rating", top = 200),
            node("Pasajero: Juan Pérez", top = 250),
            node("5 min (2.1 km) de distancia", top = 300),
            node("25 min (12.4 km) de viaje", top = 400),
            node("Aceptar", top = 500),
        ),
    )

    @Test
    fun `a captured uber offer snapshot becomes an OfferEvent_Parsed with extracted fields`() {
        val pipeline = offerPipeline(eventPipeline(UnconfinedTestDispatcher()))
        val event = pipeline.process(uberOfferSnapshot())

        assertTrue(event is OfferEvent.Parsed)
        val parsed = event as OfferEvent.Parsed
        assertEquals("com.ubercab.driver", parsed.packageName)
        assertEquals(123L, parsed.timestampMs)
        assertEquals("com.ubercab.driver", parsed.card.platform)
        assertEquals("standard", parsed.card.variant)
        assertEquals(87.5, parsed.card.fare!!, 1e-6)
        assertEquals(2.1, parsed.card.pickupDistanceKm!!, 1e-6)
        assertEquals(5.0, parsed.card.pickupEtaMin!!, 1e-6)
        assertEquals(12.4, parsed.card.tripDistanceKm!!, 1e-6)
        assertEquals(25.0, parsed.card.tripDurationMin!!, 1e-6)
        assertFalse(parsed.card.surge)
    }

    @Test
    fun `PII is scrubbed before it can reach a downstream consumer`() {
        val pipeline = offerPipeline(eventPipeline(UnconfinedTestDispatcher()))
        val parsed = pipeline.process(uberOfferSnapshot()) as OfferEvent.Parsed
        val allText = parsed.card.raw.values.joinToString(" ")
        assertFalse("passenger name leaked into the offer event", allText.contains("Juan"))
    }

    @Test
    fun `an unknown host package yields NoCard NO_SPEC`() {
        val pipeline = offerPipeline(eventPipeline(UnconfinedTestDispatcher()))
        val snap = ScreenSnapshot(
            packageName = "com.example.notarideapp",
            timestampMs = 1L,
            nodes = listOf(node("MX\$87.50"), node("25 min (12.4 km) de viaje")),
        )
        assertEquals(
            OfferEvent.NoCard("com.example.notarideapp", 1L, OfferEvent.Reason.NO_SPEC),
            pipeline.process(snap),
        )
    }

    @Test
    fun `a matching package that is not an offer card yields NoCard NOT_AN_OFFER`() {
        val pipeline = offerPipeline(eventPipeline(UnconfinedTestDispatcher()))
        // Uber package but a trip-in-progress screen (detector noneOf excludes it).
        val snap = ScreenSnapshot(
            packageName = "com.ubercab.driver",
            timestampMs = 2L,
            nodes = listOf(node("Viaje en curso"), node("MX\$87.50"), node("25 min (12.4 km) de viaje")),
        )
        assertEquals(
            OfferEvent.NoCard("com.ubercab.driver", 2L, OfferEvent.Reason.NOT_AN_OFFER),
            pipeline.process(snap),
        )
    }

    @Test
    fun `offers flow emits one OfferEvent per coalesced snapshot burst`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val reader = SnapshotReader { pkg, ts ->
            if (pkg == "com.ubercab.driver") uberOfferSnapshot().copy(timestampMs = ts) else null
        }
        val source = eventPipeline(dispatcher, reader)
        val pipeline = offerPipeline(source)

        val collected = mutableListOf<OfferEvent>()
        // Subscribe to offers first (snapshots is a replay=0 SharedFlow), then mirror the service by
        // starting the snapshot fan-out. Both run on backgroundScope so they are auto-cancelled when
        // the test body ends (otherwise runTest would hang waiting on the never-completing collector).
        pipeline.offers.onEach { collected += it }.launchIn(backgroundScope)
        source.start(backgroundScope)
        testScheduler.runCurrent()

        // A burst of content-changed events coalesces into one snapshot -> one OfferEvent.
        repeat(5) { source.submit(CaptureEvent("com.ubercab.driver", 10L + it)) }
        testScheduler.advanceTimeBy(81L)
        testScheduler.runCurrent()

        assertEquals(1, collected.size)
        assertTrue(collected.single() is OfferEvent.Parsed)
    }
}
