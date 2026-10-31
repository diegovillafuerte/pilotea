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
        /** The ledger row each evaluation read (production paints the chip from this row). */
        val rows = mutableListOf<mx.kompara.data.db.entity.OfferEntity>()
        /** What each evaluation displayed: production's row-backed verdict (OverlayController.evaluate). */
        val verdicts = mutableListOf<RowBackedVerdict>()
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
                // Production-faithful: the chip shows the ledger row's evaluation, never the incoming card.
                OverlayStateMachine(evaluate = { event ->
                    RowBackedVerdict.evaluate(recorder, event, PlatformThreshold.DEFAULT)
                        .also { verdicts += it; rows += it.row }.state
                }).visibility(OfferEventBus.events).collect { visibility ->
                    visibilities += visibility
                    when (visibility) {
                        // OverlayController semantics: Reading swaps content in place, keeping the window.
                        is OverlayVisibility.Reading -> placementEvidence.reset()
                        is OverlayVisibility.Showing -> {
                            val event = visibility.event
                            val evidence = placementEvidence.observe(requireNotNull(event.card.hostGeometry), viewport)
                            val current = displayed
                            val keep = current != null && !(evidence.dock && !current.docked) &&
                                OverlayPositioning.valid(current.position, current.size, evidence.geometry, viewport,
                                    current.overMarker, current.docked)
                            displayed = OverlayPositioning.place(
                                evidence.geometry, event.packageName, viewport, event.timestampMs,
                                ChipSize(720, 336), ChipSize(720, 200), current?.position?.takeIf { keep },
                                evidence.dock, keep && current!!.docked) as? OverlayPlacement.Displayed
                        }
                        OverlayVisibility.SoftHidden, OverlayVisibility.Exiting -> Unit
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
            assertTrue("The chip displays the row's payment: $context", verdicts.last().paymentShown)
            assertEquals("The chip displays the row-backed fare: $context", fare, verdicts.last().row.fareMxn!!, 0.000001)
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

    // A2: a Radar re-order A -> B -> A mints a fresh session for the returning A (no resume), so its
    // OfferSeen retires B and the accept links to A's fare. Cost: one extra "offer seen" row for A.
    @Test fun `Radar A then B then A keeps one pending row for the card on screen and links the accept to A`() = runTest {
        val own = mapOf("a" to (129.55 to 12.2), "b" to (104.41 to 7.3))
        val blocks = mapOf("a" to RealGeometryFixtures.radar(), "b" to RealGeometryFixtures.uberx())
        val replay = Replay(this)
        try {
            listOf("a", "a", "b", "b", "a", "a").forEachIndexed { i, offer ->
                replay.frame(blocks.getValue(offer), i * 850L)
                val pending = replay.offers.all().filter { it.outcome == "PENDING" }
                assertTrue("step $i ($offer): at most one PENDING row: $pending", pending.size <= 1)
                if (i % 2 == 1) {
                    // Each visit's second frame confirms the card on screen.
                    val (fare, trip) = own.getValue(offer)
                    assertEquals("step $i: the PENDING row is the card on screen", fare, pending.single().fareMxn!!, 1e-6)
                    assertEquals(trip, pending.single().tripKm!!, 1e-6)
                    assertEquals(fare, replay.parsed.last().card.fare!!, 1e-6)
                    assertEquals("step $i: the chip reads a live row, never a frozen one", "PENDING", replay.rows.last().outcome)
                    assertEquals(fare, replay.rows.last().fareMxn!!, 1e-6)
                }
            }
            val accepted = listOf(background("Agenda de viajes"))
            replay.frame(accepted, 6 * 850L)
            replay.frame(accepted, 6 * 850L + 300)
            val trip = replay.trips.all().single()
            val linked = replay.offers.all().single { it.id == trip.offerId }
            assertEquals("The accept links to the row carrying A's fare", 129.55, linked.fareMxn!!, 1e-6)
            assertEquals("ACCEPTED", linked.outcome)
            assertEquals(129.55, trip.grossMxn!!, 1e-6)
            val b = replay.offers.all().single { it.fareMxn == 104.41 }
            assertEquals("B is retired, never pending or accepted", "UNKNOWN", b.outcome)
        } finally { replay.close() }
    }

    // Codex R2 #2: production displays the ledger row's evaluation. A same-trip fare revision must
    // reach that row (and the lifecycle's copy) even when the pickup leg moved; a pending frame of a
    // different trip leg, still stamped with this session, never enriches it.
    @Test fun `a same-trip fare revision reaches the displayed row and another trip leg never enriches it`() = runTest {
        for (pickup in listOf("1 min (0.2 km)", "1 min (0.3 km)")) {
            val km = if (pickup.contains("0.3")) 0.3 else 0.2
            val misread = fixture(79.45).map { if (it.text == "$79.45") it.copy(text = "$19.45") else it }
            val later = fixture(79.45).map { if (it.text == "1 min (0.2 km)") it.copy(text = pickup) else it }
            val replay = Replay(this)
            try {
                replay.frame(misread, 0)
                replay.frame(later, 850)
                replay.frame(later, 1700)
                replay.assertChip(79.45, "$pickup f2: the second agreeing frame")
                replay.frame(later, 2550)
                replay.assertChip(79.45, "$pickup f3")
                assertEquals(km, replay.verdicts.last().row.pickupKm!!, 1e-6)
                val row = replay.offers.all().single()
                assertEquals(79.45, row.fareMxn!!, 1e-6)
                assertEquals(km, row.pickupKm!!, 1e-6)
                assertEquals(2.4, row.tripKm!!, 1e-6)
                // The next card's first frame (6.3 km trip) is published under this session id.
                replay.frame(fixture(147.46), 3400)
                assertEquals(row.sessionId, replay.parsed.last().card.sessionId)
                assertNull(replay.parsed.last().card.fare)
                assertFalse("Sin lectura over the next card", replay.verdicts.last().paymentShown)
                val after = replay.offers.all().single()
                assertEquals("Another trip leg never enriches the row", row.copy(lastSeenAt = after.lastSeenAt), after)
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
