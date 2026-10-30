package mx.kompara.overlay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import mx.kompara.data.service.ReaderReadinessRepository
import mx.kompara.ui.components.ReglaBand
import mx.kompara.ui.components.ReglaUnavailable
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OfferEventBus
import mx.kompara.capture.lifecycle.LifecycleSignal
import mx.kompara.capture.lifecycle.OcrLifecycleBus
import mx.kompara.capture.lifecycle.RollupTrigger
import mx.kompara.capture.lifecycle.TripLifecycleTracker
import mx.kompara.data.db.dao.*
import mx.kompara.data.db.entity.*
import mx.kompara.data.service.ScreenRect
import mx.kompara.data.service.ScreenReaderState
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.OfferRecorder
import mx.kompara.ocr.OcrBlock
import mx.kompara.ocr.OcrBounds
import mx.kompara.ocr.OfferFramePipeline
import mx.kompara.parsers.model.HostRect
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.io.File

/** Parser → shared buses → actual lifecycle/recorder with in-memory DAO rows. Addresses are fake. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClearLifecycleBoundaryReplayTest {
    private fun fixture(): List<OcrBlock> {
        val json = JSONArray(File("../ocr/src/test/resources/uber_priority_s25_2026_10_29.json").readText())
        return List(json.length()) { i ->
            val b = json.getJSONObject(i)
            OcrBlock(b.getString("text"), OcrBounds(b.getInt("left"), b.getInt("top"),
                b.getInt("right"), b.getInt("bottom")))
        }
    }

    private class Replay {
        val pipeline = OfferFramePipeline()
        val offers = BoundaryOfferDao()
        val trips = BoundaryTripDao(offers)
        val shifts = BoundaryShiftDao()
        val parsed = mutableListOf<OfferEvent.Parsed>()
        val events = mutableListOf<OfferEvent>()
        val signals = mutableListOf<LifecycleSignal>()
        val ledgerSnapshots = mutableListOf<List<OfferEntity>>()
        private val store = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = error("No settings writes")
        }
        private val settings = SettingsRepository(store)
        val recorder = OfferRecorder(offers, CostProfileRepository(BoundaryCostProfileDao()),
            settings, NetProfitEngine())
        private val controller by lazy {
            val presentations = Proxy.newProxyInstance(OfferPresentationDao::class.java.classLoader,
                arrayOf(OfferPresentationDao::class.java)) { _, method, _ ->
                error("Evaluation must not record a presentation: ${method.name}")
            } as OfferPresentationDao
            OverlayController(recorder, settings, OverlayPrefs(store), PresentationRecorder(presentations),
                ReaderReadinessRepository(ApplicationProvider.getApplicationContext<Context>()))
        }
        suspend fun actualVerdict(event: OfferEvent.Parsed): VerdictChipState = suspendCoroutineUninterceptedOrReturn { continuation ->
            OverlayController::class.java.getDeclaredMethod("evaluate", OfferEvent.Parsed::class.java,
                Continuation::class.java).apply { isAccessible = true }.invoke(controller, event, continuation)
        }
        val tracker = TripLifecycleTracker(offers, trips, shifts, recorder, RollupTrigger {})
        private val epochStart = System.currentTimeMillis()
        private var elapsedMs = 0L
        private var nextFrameMs = 0L
        suspend fun depart(atMs: Long) {
            require(atMs >= elapsedMs)
            ShadowSystemClock.advanceBy(Duration.ofMillis(atMs - elapsedMs))
            elapsedMs = atMs
            val before = signals.size
            pipeline.onCaptureEnd(epochStart + atMs)
            yield()
            signals.drop(before).forEach { tracker.handle(it) }
            ledgerSnapshots += offers.all()
        }
        suspend fun frame(blocks: List<OcrBlock>, atMs: Long = nextFrameMs,
            chip: ScreenRect? = ScreenRect(325, 242, 1040, 572),
            host: String? = "com.ubercab.driver",
            accessibilityLabel: String? = null, accessibilityAgeMs: Long = 0L) {
            require(atMs >= elapsedMs)
            ShadowSystemClock.advanceBy(Duration.ofMillis(atMs - elapsedMs))
            elapsedMs = atMs
            nextFrameMs = atMs + 300
            val before = signals.size
            val parsedBefore = parsed.size
            ScreenReaderState.setForegroundHost(host, SystemClock.elapsedRealtime())
            pipeline.processOwnedFrame(blocks, chip, null, capturedAtMs = epochStart + atMs,
                displayBounds = HostRect(0, 0, 1080, 2340), density = 3f,
                accessibility = accessibilityLabel?.let { label ->
                    mx.kompara.capture.HostWindowEvidence(requireNotNull(host), epochStart + atMs - accessibilityAgeMs,
                        HostRect(0, 0, 1080, 2340), 3f, HostRect(0, 0, 1080, 100),
                        listOf(mx.kompara.parsers.model.HostTextBounds(label, HostRect(240, 1940, 840, 2030))),
                        windowId = 1)
                })
            yield()
            // The overlay records every parsed event and the lifecycle independently records
            // OfferSeen signals. Both real consumers must converge on one durable session row.
            parsed.drop(parsedBefore).forEach { event ->
                recorder.record(requireNotNull(event.card.sessionId), requireNotNull(event.card.sessionStartedAt),
                    OfferMapping.toTripOffer(event.card), event.card.captureSource, event.card.surge, event.timestampMs)
            }
            signals.drop(before).forEach { tracker.handle(it) }
            ledgerSnapshots += offers.all()
        }
    }

    private fun replay(body: suspend Replay.() -> Unit): Replay = runBlocking {
        val replay = Replay()
        val parsed = launch(Dispatchers.Unconfined) {
            OfferEventBus.events.collect {
                replay.events += it
                if (it is OfferEvent.Parsed) replay.parsed += it
            }
        }
        val lifecycle = launch(Dispatchers.Unconfined) {
            OcrLifecycleBus.signals.collect { replay.signals += it }
        }
        try { replay.body() } finally {
            parsed.cancelAndJoin(); lifecycle.cancelAndJoin()
            ScreenReaderState.setForegroundHost(null, SystemClock.elapsedRealtime())
        }
        replay
    }


    private fun idle(label: String = "Buscando viajes") = listOf(
        OcrBlock(label, OcrBounds(240, 1940, 840, 2030)))

    private fun nextOffer(differentLegs: Boolean = true) = fixture().map {
        it.copy(text = when (it.text) {
            "$79.45" -> "$147.46"
            "24 min (2.4 km)" -> if (differentLegs) "24 min (6.3 km)" else it.text
            else -> it.text
        })
    }

    @Test fun `decline idle then new route within 900ms starts fresh payment and never overwrites prior offer`() {
        val result = replay {
            frame(fixture(), 0)
            val original = offers.all().single()
            frame(idle(), 300)
            for (index in 0..4) {
                frame(nextOffer(), 900 + index * 900L)
                val next = parsed.last().card
                assertNotEquals("One positive idle capture ends the prior offer", original.sessionId, next.sessionId)
                assertEquals(147.46, next.fare!!, 0.001)
                assertEquals(6.3, next.tripDistanceKm!!, 0.001)
                assertEquals(2, offers.all().size)
                val retained = offers.all().first()
                assertEquals(original.sessionId, retained.sessionId)
                assertEquals(original.seenAt, retained.seenAt)
                assertEquals(79.45, retained.fareMxn!!, 0.001)
                assertEquals(2.4, retained.tripKm!!, 0.001)
            }
        }
        assertEquals(2, result.parsed.map { it.card.sessionId }.distinct().size)
        assertEquals(listOf("NOT_TAKEN", "PENDING"), result.offers.all().map { it.outcome })
    }

    @Test fun `idle is a new identity boundary even when consecutive offers have identical legs`() {
        val result = replay {
            frame(fixture(), 0)
            val original = offers.all().single()
            frame(idle("Buscando solicitud de viaje"), 900)
            frame(nextOffer(differentLegs = false), 1800)
            assertNotEquals(original.sessionId, parsed.last().card.sessionId)
            assertEquals("First new frame cannot show old money", 147.46, parsed.last().card.fare!!, 0.001)
            frame(nextOffer(differentLegs = false), 2700)
            assertEquals("New offer never becomes a revision of the prior DAO row", 2, offers.all().size)
            assertEquals(original.seenAt, offers.all().first().seenAt)
            assertEquals(79.45, offers.all().first().fareMxn!!, 0.001)
            assertTrue(offers.all().last().seenAt > original.seenAt)
        }
        assertEquals(listOf(79.45, 147.46), result.offers.all().map { it.fareMxn })
    }

    @Test fun `clear idle followed by unread payment cannot borrow money from previous session`() {
        val unread = nextOffer().filterNot { it.text == "$147.46" }
        val result = replay {
            frame(fixture(), 0)
            val originalId = parsed.last().card.sessionId
            frame(idle(), 900)
            frame(unread, 1800)
            assertNotEquals(originalId, parsed.last().card.sessionId)
            assertNull("Incomplete replacement must not inherit payment", parsed.last().card.fare)
            assertEquals("UNCONFIRMED", parsed.last().card.paymentBasis)
        }
        assertEquals(2, result.offers.all().size)
        assertEquals(79.45, result.offers.all().first().fareMxn!!, 0.001)
        assertNull(result.offers.all().last().fareMxn)
    }

    @Test fun `brief foreground departure resumes unchanged card without another ledger row`() {
        val steady = nextOffer(differentLegs = false)
        val result = replay {
            frame(steady, 0)
            val original = offers.all().single()
            depart(100)
            assertTrue("Leaving the host immediately hides its chip", events.last() is OfferEvent.NoCard)
            assertEquals(original.sessionId, offers.all().single().sessionId)
            assertEquals("PENDING", offers.all().single().outcome)
            frame(steady, 900)
            frame(steady, 1800)
            assertEquals("Lost foreground visibility is not observed card disappearance", original.sessionId,
                parsed.last().card.sessionId)
            assertEquals(1, offers.all().size)
            assertEquals(original.seenAt, offers.all().single().seenAt)
            assertEquals("PENDING", offers.all().single().outcome)
        }
        assertEquals(1, result.parsed.map { it.card.sessionId }.distinct().size)
        assertTrue(result.ledgerSnapshots.all { it.size == 1 && it.single().fareMxn == 147.46 })
    }


    @Test fun `switching target hosts suspends each unchanged card and resumes its original row`() {
        val didi = listOf(
            OcrBlock("DiDi", OcrBounds(96, 650, 400, 740)),
            OcrBlock("$132.59", OcrBounds(96, 770, 499, 897)),
            OcrBlock("6 min (1.2 km)", OcrBounds(190, 1420, 510, 1477)),
            OcrBlock("39 min (17.3 km)", OcrBounds(190, 1625, 541, 1682)),
            OcrBlock("Aceptar", OcrBounds(442, 1989, 640, 2046)),
        )
        val result = replay {
            frame(fixture(), 0)
            val uber = offers.all().single()
            frame(didi, 900, host = "com.didiglobal.driver")
            val other = offers.all().last()
            frame(fixture(), 1800)
            assertEquals(uber.sessionId, parsed.last().card.sessionId)
            frame(didi, 2700, host = "com.didiglobal.driver")
            assertEquals(other.sessionId, parsed.last().card.sessionId)
            assertEquals(2, offers.all().size)
            assertEquals(listOf(uber.seenAt, other.seenAt), offers.all().map { it.seenAt })
        }
        assertEquals(listOf(79.45, 132.59), result.offers.all().map { it.fareMxn })
        assertTrue(result.offers.all().all { it.outcome == "PENDING" })
    }

    @Test fun `returning to explicit idle retires suspended offer before new card arrives`() {
        val result = replay {
            frame(fixture(), 0)
            val original = offers.all().single()
            depart(100)
            frame(idle(), 900)
            assertEquals("NOT_TAKEN", offers.all().single().outcome)
            frame(nextOffer(differentLegs = false), 1800)
            assertNotEquals(original.sessionId, parsed.last().card.sessionId)
            assertEquals(147.46, parsed.last().card.fare!!, 0.001)
            assertEquals(2, offers.all().size)
            assertEquals(original.seenAt, offers.all().first().seenAt)
            assertEquals(79.45, offers.all().first().fareMxn!!, 0.001)
        }
        assertEquals(listOf("NOT_TAKEN", "PENDING"), result.offers.all().map { it.outcome })
    }

    @Test fun `corroborated replacement on return retires suspended identity without revising its payment`() {
        val replacement = nextOffer().map { if (it.text == "Aceptar") it.copy(text = "Coincidir") else it }
        val result = replay {
            frame(fixture(), 0)
            val original = offers.all().single()
            depart(100)
            frame(replacement, 900)
            assertEquals(original.sessionId, parsed.last().card.sessionId)
            assertNull("First replacement observation cannot inherit suspended payment", parsed.last().card.fare)
            assertEquals(79.45, offers.all().single().fareMxn!!, 0.001)
            frame(replacement, 1800)
            assertNotEquals(original.sessionId, parsed.last().card.sessionId)
            assertEquals(147.46, parsed.last().card.fare!!, 0.001)
            assertEquals(2, offers.all().size)
            assertEquals(original.seenAt, offers.all().first().seenAt)
            assertEquals(original.sessionId, offers.all().first().sessionId)
            assertEquals(79.45, offers.all().first().fareMxn!!, 0.001)
        }
        assertEquals(listOf("UNKNOWN", "PENDING"), result.offers.all().map { it.outcome })
    }


    @Test fun `extended empty OCR dropout never becomes positive card disappearance`() {
        val result = replay {
            frame(fixture(), 0)
            val original = offers.all().single()
            for (at in listOf(900L, 1800L, 2700L, 60_000L)) frame(emptyList(), at)
            assertEquals("No OCR evidence means no ledger transition", listOf(original), offers.all())
            frame(fixture(), 60_900)
            assertEquals(original.sessionId, parsed.last().card.sessionId)
            assertEquals(original.seenAt, offers.all().single().seenAt)
            assertEquals(79.45, parsed.last().card.fare!!, 0.001)
        }
        assertEquals(1, result.offers.all().size)
        assertEquals("PENDING", result.offers.all().single().outcome)
    }

    @Test fun `dropout and partial route cannot resolve pending replacement by resurrecting confirmed payment`() {
        val partial = nextOffer().map {
            if (it.text == "1 min (0.2 km)") it.copy(text = "— min (0.2 km)") else it
        }
        val result = replay {
            frame(fixture(), 0)
            val original = offers.all().single()
            frame(nextOffer(), 900)
            assertNull(parsed.last().card.fare)
            frame(emptyList(), 1800)
            frame(partial, 2700)
            assertEquals(original.sessionId, parsed.last().card.sessionId)
            assertNull("A partial route cannot disprove the replacement candidate", parsed.last().card.fare)
            assertEquals(ReglaBand.Unavailable(ReglaUnavailable.FARE), actualVerdict(parsed.last()).regla.band)
            frame(partial, 3600)
            assertNull(parsed.last().card.fare)
            frame(fixture(), 4500)
            assertEquals("Positive baseline evidence resolves uncertainty", 79.45, parsed.last().card.fare!!, 0.001)
            assertTrue(actualVerdict(parsed.last()).regla.band is ReglaBand.Rated)
            assertEquals(original.sessionId, parsed.last().card.sessionId)
            assertEquals(original.seenAt, offers.all().single().seenAt)
        }
        assertTrue(result.ledgerSnapshots.all { it.size == 1 && it.single().fareMxn == 79.45 })
    }


    @Test fun `positive accepted host screen ends card visibility and links the pending offer to a trip`() {
        val result = replay {
            frame(fixture(), 0)
            val offered = offers.all().single()
            frame(listOf(OcrBlock("Agenda de viajes", OcrBounds(100, 1800, 900, 1900))), 900)
            assertEquals("A positively observed accepted UI is not OCR dropout", 1, trips.all().size)
            assertEquals(offered.id, trips.all().single().offerId)
            assertEquals(79.45, trips.all().single().grossMxn!!, 0.001)
            assertEquals("ACCEPTED", offers.all().single().outcome)
            assertTrue(events.last() is OfferEvent.NoCard)
            frame(listOf(OcrBlock("Viaje en curso", OcrBounds(100, 1800, 900, 1900))), 1800)
            assertEquals("IN_PROGRESS", trips.all().single().lifecycle)
            frame(fixture(), 2700)
            assertNotEquals("The accepted card cannot be reused for a subsequent offer", offered.sessionId,
                parsed.last().card.sessionId)
        }
        assertEquals(2, result.offers.all().size)
        assertEquals(1, result.trips.all().size)
        assertEquals(listOf("ACCEPTED", "PENDING"), result.offers.all().map { it.outcome })
    }

    @Test fun `positive cancellation UI retires pending card without opening a trip`() {
        val result = replay {
            frame(fixture(), 0)
            val originalId = parsed.last().card.sessionId
            frame(listOf(OcrBlock("Viaje cancelado", OcrBounds(100, 1800, 900, 1900))), 900)
            assertEquals("Observed cancellation is positive lifecycle evidence", "UNKNOWN", offers.all().single().outcome)
            assertTrue(events.last() is OfferEvent.NoCard)
            frame(fixture(), 1800)
            assertNotEquals(originalId, parsed.last().card.sessionId)
        }
        assertEquals(2, result.offers.all().size)
        assertTrue(result.trips.all().isEmpty())
    }


    @Test fun `fresh accessibility idle is positive absence even when OCR reads nothing`() {
        val result = replay {
            frame(fixture(), 0)
            val original = offers.all().single()
            frame(emptyList(), 900, accessibilityLabel = "Buscando viajes")
            assertEquals("NOT_TAKEN", offers.all().single().outcome)
            assertTrue(events.last() is OfferEvent.NoCard)
            frame(nextOffer(differentLegs = false), 1800)
            assertNotEquals(original.sessionId, parsed.last().card.sessionId)
            assertEquals(147.46, parsed.last().card.fare!!, 0.001)
        }
        assertEquals(2, result.offers.all().size)
        assertEquals(listOf(79.45, 147.46), result.offers.all().map { it.fareMxn })
    }

    @Test fun `pre-offer accessibility idle cannot retire a newer observed card during OCR dropout`() {
        val result = replay {
            frame(fixture(), 0, accessibilityLabel = "Buscando viajes", accessibilityAgeMs = 50)
            val original = offers.all().single()
            frame(emptyList(), 900, accessibilityLabel = "Buscando viajes", accessibilityAgeMs = 950)
            assertEquals("Old node evidence cannot contradict a newer positive offer capture", "PENDING",
                offers.all().single().outcome)
            frame(fixture(), 1800)
            assertEquals(original.sessionId, parsed.last().card.sessionId)
            assertEquals(1, offers.all().size)
            assertEquals(original.seenAt, offers.all().single().seenAt)
        }
        assertEquals(1, result.parsed.map { it.card.sessionId }.distinct().size)
    }

    @Test fun `unresolved simultaneous route and payment change shows incomplete without writing inherited money`() {
        val result = replay {
            frame(fixture(), 0)
            val original = offers.all().single()
            frame(nextOffer(), 900)
            assertNull("A changed complete route with new money cannot display the preceding fare", parsed.last().card.fare)
            assertEquals("UNCONFIRMED", parsed.last().card.paymentBasis)
            val live = actualVerdict(parsed.last())
            assertEquals(ReglaBand.Unavailable(ReglaUnavailable.FARE), live.regla.band)
            assertEquals("—", live.grossPerKm)
            assertEquals(1, offers.all().size)
            assertEquals(79.45, offers.all().single().fareMxn!!, 0.001)
            assertEquals(original.seenAt, offers.all().single().seenAt)
        }
        assertTrue(result.trips.all().isEmpty())
    }
}
private class BoundaryOfferDao : OfferDao {
    override suspend fun hasSeenBefore(before: Long) = rows.values.any { it.seenAt < before }
    private val rows = mutableMapOf<Long, OfferEntity>()
    private var nextId = 1L

    fun all(): List<OfferEntity> = rows.values.sortedBy { it.id }

    override suspend fun insert(offer: OfferEntity): Long {
        if (offer.sessionId != null && rows.values.any { it.sessionId == offer.sessionId }) return -1L
        val id = if (offer.id == 0L) nextId++ else offer.id
        rows[id] = offer.copy(id = id)
        return id
    }

    override suspend fun attachShift(id: Long, shiftId: Long?) { rows[id]?.let { if (it.shiftId == null) rows[id] = it.copy(shiftId = shiftId) } }
    override suspend fun retireOlderPending(platform: String, before: Long, at: Long) {
        rows.values.filter { it.platform == platform && it.outcome == "PENDING" && it.seenAt < before && it.lastSeenAt <= at }
            .forEach { rows[it.id] = it.copy(outcome = "UNKNOWN", resolvedAt = at) }
    }
    override suspend fun forSession(sessionId: String) = rows.values.firstOrNull { it.sessionId == sessionId }
    override suspend fun touch(id: Long, at: Long) { rows[id]?.let { rows[id] = it.copy(lastSeenAt = maxOf(at, it.lastSeenAt)) } }

    override suspend fun update(offer: OfferEntity) {
        rows[offer.id] = offer
    }

    override suspend fun resolvePending(id: Long, outcome: String, at: Long, lastSeenAtMost: Long) {
        rows[id]?.takeIf { it.outcome == "PENDING" && it.lastSeenAt <= lastSeenAtMost }
            ?.let { rows[id] = it.copy(outcome = outcome, resolvedAt = at) }
    }

    override fun observeAll(): Flow<List<OfferEntity>> = MutableStateFlow(all())
    override fun observeByPlatform(platform: String): Flow<List<OfferEntity>> =
        MutableStateFlow(all().filter { it.platform == platform })

    override suspend fun findById(id: Long): OfferEntity? = rows[id]

    override suspend fun latestPending(platform: String?): OfferEntity? =
        rows.values.filter { it.outcome == OfferOutcome.PENDING.name && (platform == null || it.platform == platform) }.maxByOrNull { it.seenAt }

    override suspend fun seenBetween(from: Long, until: Long): List<OfferEntity> =
        rows.values.filter { it.seenAt in from until until }.sortedBy { it.seenAt }

    override fun observeSeenBetween(from: Long, until: Long): Flow<List<OfferEntity>> =
        MutableStateFlow(rows.values.filter { it.seenAt in from until until }.sortedBy { it.seenAt })
}

private class BoundaryTripDao(private val offers: BoundaryOfferDao? = null) : TripDao {
    override suspend fun startedBetween(from: Long, until: Long) = rows.values.filter { it.startedAt >= from && it.startedAt < until }.sortedBy { it.startedAt }

    private val rows = mutableMapOf<Long, TripEntity>()
    private var nextId = 1L

    fun all(): List<TripEntity> = rows.values.sortedBy { it.id }

    override suspend fun insert(trip: TripEntity): Long {
        val id = if (trip.id == 0L) nextId++ else trip.id
        rows[id] = trip.copy(id = id)
        return id
    }

    override suspend fun forOffer(offerId: Long) = rows.values.firstOrNull { it.offerId == offerId }
    override suspend fun offerForAcceptance(offerId: Long) = offers?.findById(offerId)
    override suspend fun resolveOffer(id: Long, outcome: String, accepted: Boolean, at: Long) {
        offers?.findById(id)?.let { offers.update(it.copy(outcome = outcome, accepted = accepted, resolvedAt = at)) }
    }

    override suspend fun update(trip: TripEntity) {
        rows[trip.id] = trip
    }

    override suspend fun findById(id: Long): TripEntity? = rows[id]
    override fun observeAll(): Flow<List<TripEntity>> = MutableStateFlow(all())
    override fun observeSince(since: Long): Flow<List<TripEntity>> =
        MutableStateFlow(all().filter { it.startedAt >= since })

    override fun observeGrossSince(since: Long): Flow<Double> =
        MutableStateFlow(all().filter { it.startedAt >= since }.sumOf { it.grossMxn ?: 0.0 })

    override suspend fun latestOpen(platform: String?): TripEntity? =
        rows.values.filter { it.endedAt == null && (platform == null || it.platform == platform) }.maxByOrNull { it.startedAt }

    override suspend fun completedStartedBetween(from: Long, until: Long): List<TripEntity> =
        rows.values.filter { it.endedAt != null && it.startedAt in from until until }
            .sortedBy { it.startedAt }

    override fun observeCompletedStartedBetween(from: Long, until: Long): Flow<List<TripEntity>> =
        MutableStateFlow(
            rows.values.filter { it.endedAt != null && it.startedAt in from until until }
                .sortedBy { it.startedAt },
        )
}

private class BoundaryShiftDao : ShiftDao {
    private val rows = mutableMapOf<Long, ShiftEntity>()
    private var nextId = 1L

    fun all(): List<ShiftEntity> = rows.values.sortedBy { it.id }

    override suspend fun insert(shift: ShiftEntity): Long {
        val id = if (shift.id == 0L) nextId++ else shift.id
        rows[id] = shift.copy(id = id)
        return id
    }

    override suspend fun update(shift: ShiftEntity) {
        rows[shift.id] = shift
    }

    override suspend fun findById(id: Long): ShiftEntity? = rows[id]
    override fun observeAll(): Flow<List<ShiftEntity>> = MutableStateFlow(all())
    override fun observeActive(): Flow<ShiftEntity?> =
        MutableStateFlow(rows.values.firstOrNull { it.endedAt == null })

    override suspend fun latestOpen(): ShiftEntity? =
        rows.values.filter { it.endedAt == null }.maxByOrNull { it.startedAt }

    override suspend fun overlapping(from: Long, until: Long): List<ShiftEntity> =
        rows.values.filter { it.startedAt < until && (it.endedAt == null || it.endedAt!! > from) }
            .sortedBy { it.startedAt }
}

private class BoundaryCostProfileDao : CostProfileDao {
    // These tests isolate thresholds/lifecycle from running costs: explicitly configured zero.
    private val costVersions = mutableListOf(mx.kompara.data.db.entity.CostVersionEntity.from(
        mx.kompara.data.accounting.RunningCostVersion(1, 0, 0,
            mx.kompara.data.accounting.RunningCostInputs(mx.kompara.data.accounting.RunningCostMode.DIRECT,
                directPerKm = java.math.BigDecimal.ZERO), mx.kompara.data.accounting.CostConfirmation.CONFIRMED, 0)))
    override suspend fun insertVersion(version: mx.kompara.data.db.entity.CostVersionEntity): Long {
        costVersions += version.copy(id = costVersions.size + 1L)
        return costVersions.size.toLong()
    }
    override suspend fun versions() = costVersions.toList()
    override fun observeVersions() = MutableStateFlow(costVersions.toList())

    override suspend fun upsert(profile: CostProfileEntity) = Unit
    override fun observe(id: Long): Flow<CostProfileEntity?> = MutableStateFlow(null)
    override suspend fun get(id: Long): CostProfileEntity? = null
}

