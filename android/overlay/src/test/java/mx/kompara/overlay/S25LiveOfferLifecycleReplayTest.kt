package mx.kompara.overlay

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OfferEventBus
import mx.kompara.capture.lifecycle.LifecycleSignal
import mx.kompara.capture.lifecycle.OcrLifecycleBus
import mx.kompara.capture.lifecycle.RollupTrigger
import mx.kompara.capture.lifecycle.TripLifecycleTracker
import mx.kompara.data.service.ReaderMemory
import mx.kompara.data.service.ReaderMemoryStore
import mx.kompara.data.service.ReaderReadinessRepository
import mx.kompara.data.service.ScreenRect
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.OfferRecorder
import mx.kompara.ocr.OcrBlock
import mx.kompara.ocr.OcrBounds
import mx.kompara.ocr.OfferFramePipeline
import mx.kompara.ocr.UberOcrParser
import mx.kompara.parsers.model.HostOrientation
import mx.kompara.parsers.model.HostRect
import mx.kompara.ui.components.ReglaBand
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.time.Clock
import java.time.Duration

/** Real S25 geometry → readiness → pipeline/bus → verdict/placement and durable lifecycle rows.
 * The $147.46 case uses the reported S25 values on the anonymized capture's measured geometry.
 * No fixture contains a real address; the chip is absent initially and supplies its own next mask.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class S25LiveOfferLifecycleReplayTest {
    private val markers = listOf("Navegar", "En camino", "Recoger a", "Desliza para aceptar",
        "Punto de encuentro", "Llegar a")

    private fun fixture(fare: Double): List<OcrBlock> {
        val json = JSONArray(File("../ocr/src/test/resources/uber_priority_s25_2026_10_29.json").readText())
        return List(json.length()) { index ->
            val b = json.getJSONObject(index)
            OcrBlock(b.getString("text"), OcrBounds(b.getInt("left"), b.getInt("top"),
                b.getInt("right"), b.getInt("bottom")))
        }.filterNot { it.text in setOf("Sin lectura", "falta la tarifa", "tu meta $10.00", "tu mínimo $7.50") }
            .map { block -> if (fare == 79.45) block else block.copy(text = when (block.text) {
                "$79.45" -> "$147.46"
                "25 min • 2.6 km • $30.56/km est." -> "53 min • 7.0 km • $21.07/km est."
                "1 min (0.2 km)" -> "4 min (0.7 km)"
                "24 min (2.4 km)" -> "49 min (6.3 km)"
                else -> block.text
            }) }
    }

    private fun background(text: String) = OcrBlock(text, OcrBounds(50, 80, 900, 130))
    private fun withMarker(fare: Double, marker: String, address: Boolean) =
        if (address) fixture(fare).map {
            if (it.text.startsWith("Calle Falsa")) it.copy(text = "$marker — Calle Falsa 123, Colonia Ejemplo") else it
        } else fixture(fare) + background(marker)

    private class Replay(private val scope: TestScope) {
        private val viewport = OverlayViewport(HostRect(0, 0, 1080, 2340), 3f, HostOrientation.PORTRAIT)
        val offers = BoundaryOfferDao()
        val trips = BoundaryTripDao(offers)
        val parsed = mutableListOf<OfferEvent.Parsed>()
        val signals = mutableListOf<LifecycleSignal>()
        val visibilities = mutableListOf<OverlayVisibility>()
        var displayed: OverlayPlacement.Displayed? = null
            private set
        private val placementEvidence = PlacementEvidence()
        private val store = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = error("No settings writes")
        }
        private val settings = SettingsRepository(store)
        private val recorder = OfferRecorder(offers, CostProfileRepository(BoundaryCostProfileDao()), settings, NetProfitEngine())
        private val tracker = TripLifecycleTracker(offers, trips, BoundaryShiftDao(), recorder, RollupTrigger {})
        private val readiness = ReaderReadinessRepository(object : ReaderMemoryStore {
            private var memory = ReaderMemory()
            override fun load() = memory
            override fun save(memory: ReaderMemory) { this.memory = memory }
        }, Clock.systemDefaultZone()).apply {
            observeEnvironment(permission = true, batteryRestricted = false)
            supportedApps(setOf(UberOcrParser.UBER_PACKAGE))
            bound()
            captureRunning(true)
        }
        private val pipeline = OfferFramePipeline(readiness)
        private var elapsedMs = 0L
        private val epoch = System.currentTimeMillis()
        private val jobs = listOf(
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
                OfferEventBus.events.collect { if (it is OfferEvent.Parsed) parsed += it }
            },
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
                OcrLifecycleBus.signals.collect { signals += it }
            },
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
                OverlayStateMachine(evaluate = { event ->
                    recorder.record(requireNotNull(event.card.sessionId), requireNotNull(event.card.sessionStartedAt),
                        OfferMapping.toTripOffer(event.card), event.card.captureSource, event.card.surge, event.timestampMs)
                    VerdictChipState.from(NetProfitEngine().evaluate(OfferMapping.toTripOffer(event.card),
                        "2.84".toBigDecimal(), PlatformThreshold.DEFAULT, PreferredMetric.IPK), PlatformThreshold.DEFAULT)
                }).visibility(OfferEventBus.events).collect { visibility ->
                    visibilities += visibility
                    when (visibility) {
                        is OverlayVisibility.Reading -> { placementEvidence.reset(); displayed = null }
                        is OverlayVisibility.Showing -> {
                            val event = visibility.event
                            val evidence = placementEvidence.observe(requireNotNull(event.card.hostGeometry), viewport)
                            displayed = if (evidence.needsUnobscuredCapture) null else OverlayPositioning.place(
                                evidence.geometry, event.packageName, viewport, event.timestampMs,
                                ChipSize(720, 336), ChipSize(720, 200),
                                if (evidence.preferPreferredPosition) null else displayed?.position) as? OverlayPlacement.Displayed
                        }
                        else -> displayed = null
                    }
                }
            },
        )

        suspend fun frame(blocks: List<OcrBlock>, atMs: Long) {
            require(atMs >= elapsedMs)
            ShadowSystemClock.advanceBy(Duration.ofMillis(atMs - elapsedMs))
            elapsedMs = atMs
            val before = signals.size
            val mask = displayed?.let { p -> p.position.bounds(p.size) }
                ?.let { ScreenRect(it.left, it.top, it.right, it.bottom) }
            pipeline.processOwnedFrame(blocks, mask, null, epoch + atMs, token = readiness.captureToken(),
                displayBounds = viewport.bounds, density = viewport.density,
                foregroundPackage = UberOcrParser.UBER_PACKAGE)
            scope.testScheduler.runCurrent()
            signals.drop(before).forEach { tracker.handle(it) }
        }

        fun assertChip(fare: Double, context: String = "") {
            assertTrue("The real bus must publish a card before a chip can exist: $context", parsed.isNotEmpty())
            assertEquals(fare, parsed.last().card.fare!!, 0.000001)
            val showing = visibilities.last() as? OverlayVisibility.Showing
            assertNotNull("The actual visibility flow must expose a showing verdict", showing)
            assertTrue(showing!!.state.regla.band is ReglaBand.Rated)
            assertNotNull("The actual placement gates must admit the chip", displayed)
            val bounds = displayed!!.position.bounds(displayed!!.size)
            assertFalse("The chip must leave the offer unobscured", bounds.overlaps(parsed.last().card.hostGeometry!!.offerCard))
        }

        fun close() { jobs.forEach { it.cancel() } }
    }

    private fun assertLiveMarkers(fare: Double) = runTest {
        for (marker in markers) for (address in listOf(false, true)) {
            val replay = Replay(this)
            try {
                repeat(3) { index ->
                    replay.frame(withMarker(fare, marker, address), index * 300L)
                    replay.assertChip(fare, "$marker, address=$address, frame=$index")
                    assertEquals("Every live frame publishes ($marker, address=$address)", index + 1, replay.parsed.size)
                    assertEquals(1, replay.offers.all().size)
                    assertEquals("PENDING", replay.offers.all().single().outcome)
                    assertTrue("A visible offer cannot become a trip", replay.trips.all().isEmpty())
                }
            } finally { replay.close() }
        }
    }

    @Test fun `79_45 S25 live offer publishes and displays with every marker in background or fake address`() = assertLiveMarkers(79.45)
    @Test fun `147_46 S25 live offer publishes and displays with every marker in background or fake address`() = assertLiveMarkers(147.46)

    @Test fun `idle then live contaminated offer then accepted header links exactly one ledger row and trip`() = runTest {
        for (fare in listOf(79.45, 147.46)) {
            val replay = Replay(this)
            try {
                replay.frame(listOf(OcrBlock("Buscando viajes", OcrBounds(240, 1940, 840, 2030))), 0)
                assertTrue(replay.parsed.isEmpty())
                assertNull(replay.displayed)
                repeat(3) { index ->
                    replay.frame(withMarker(fare, "Desliza para aceptar", address = true) + background("Navegar"), 300L + index * 300)
                    replay.assertChip(fare)
                }
                val offered = replay.offers.all().single()
                assertTrue(replay.trips.all().isEmpty())
                val accepted = listOf(background("Agenda de viajes"))
                replay.frame(accepted, 1200)
                replay.frame(accepted, 1500)
                assertEquals("Exactly one durable row through idle, live and accepted frames", 1, replay.offers.all().size)
                assertEquals("ACCEPTED", replay.offers.all().single().outcome)
                val trip = replay.trips.all().single()
                assertEquals(offered.id, trip.offerId)
                assertEquals(fare, trip.grossMxn!!, 0.000001)
                assertNull("Accepted screen closes the chip", replay.displayed)
                assertEquals(OverlayVisibility.Hidden, replay.visibilities.last())
                // The accepted boundary must preserve immediate fresh payment for the next offer.
                val nextFare = if (fare == 79.45) 147.46 else 79.45
                replay.frame(fixture(nextFare), 1800)
                replay.assertChip(nextFare)
                assertNotEquals(offered.sessionId, replay.parsed.last().card.sessionId)
                assertEquals(listOf("ACCEPTED", "PENDING"), replay.offers.all().map { it.outcome })
                assertEquals(1, replay.trips.all().size)
            } finally { replay.close() }
        }
    }

    @Test fun `uncalibrated background during dropout cannot retire the live offer or create a trip`() = runTest {
        for (fare in listOf(79.45, 147.46)) for (marker in markers) {
            val replay = Replay(this)
            try {
                replay.frame(fixture(fare), 0)
                replay.assertChip(fare)
                val original = replay.offers.all().single()
                replay.frame(listOf(background(marker)), 300)
                assertEquals("Uncalibrated background cannot resolve $marker", "PENDING", replay.offers.all().single().outcome)
                assertTrue("Uncalibrated background cannot open a trip: $marker", replay.trips.all().isEmpty())
                replay.frame(fixture(fare), 600)
                replay.assertChip(fare)
                assertEquals(original.sessionId, replay.parsed.last().card.sessionId)
                assertEquals("Dropout does not split the ledger", 1, replay.offers.all().size)
            } finally { replay.close() }
        }
    }
}
