package mx.kompara.overlay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
import mx.kompara.data.service.ReaderReadinessRepository
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.OfferRecorder
import mx.kompara.ocr.OcrBlock
import mx.kompara.ocr.OcrBounds
import mx.kompara.ocr.OfferFramePipeline
import mx.kompara.parsers.model.HostRect
import mx.kompara.ui.components.ReglaBand
import mx.kompara.ui.components.ReglaUnavailable
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.io.File
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/** Parser → shared buses → actual lifecycle/recorder with in-memory DAO rows. Addresses are fake. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfferInstanceLedgerReplayTest {
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
        val offers = ReplayOfferDao()
        val trips = ReplayTripDao(offers)
        val shifts = ReplayShiftDao()
        val parsed = mutableListOf<OfferEvent.Parsed>()
        val signals = mutableListOf<LifecycleSignal>()
        val ledgerSnapshots = mutableListOf<List<OfferEntity>>()
        private val store = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = error("No settings writes")
        }
        private val settings = SettingsRepository(store)
        val recorder = OfferRecorder(offers, CostProfileRepository(ReplayCostProfileDao()),
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
        suspend fun frame(blocks: List<OcrBlock>, atMs: Long = nextFrameMs) {
            require(atMs >= elapsedMs)
            ShadowSystemClock.advanceBy(Duration.ofMillis(atMs - elapsedMs))
            elapsedMs = atMs
            nextFrameMs = atMs + 300
            val before = signals.size
            val parsedBefore = parsed.size
            pipeline.process(blocks, ScreenRect(325, 242, 1040, 572), null, capturedAtMs = epochStart + atMs,
                displayBounds = HostRect(0, 0, 1080, 2340), density = 3f)
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
            OfferEventBus.events.collect { if (it is OfferEvent.Parsed) replay.parsed += it }
        }
        val lifecycle = launch(Dispatchers.Unconfined) {
            OcrLifecycleBus.signals.collect { replay.signals += it }
        }
        try { replay.body() } finally { parsed.cancelAndJoin(); lifecycle.cancelAndJoin() }
        replay
    }

    @Test fun `Trip Radar replacing exclusive identical legs cannot inherit or persist its payment`() {
        val original = fixture()
        val radar = original.filterNot { it.text == "$79.45" }.map {
            it.copy(text = when (it.text) {
                "Exclusivo" -> "Radar de solicitud"
                "Aceptar" -> "Coincidir"
                else -> it.text
            })
        }
        val result = replay { frame(original); frame(radar) }
        assertEquals(2, result.parsed.size)
        assertEquals("trip_radar", result.parsed.last().card.variant)
        assertNull("Replacement must not borrow the previous exclusive fare", result.parsed.last().card.fare)
        assertNotEquals(result.parsed.first().card.sessionId, result.parsed.last().card.sessionId)
        assertEquals(2, result.offers.all().size)
        assertEquals(79.45, result.offers.all().first().fareMxn!!, 0.001)
        assertNull(result.offers.all().last().fareMxn)
        assertEquals("UNKNOWN", result.offers.all().first().outcome)
        assertEquals("PENDING", result.offers.all().last().outcome)
    }

    @Test fun `stacked first card disappearing cannot lend fare to the newly active identical route`() {
        val first = fixture().filter { it.bounds.top >= 650 }.map {
            it.copy(bounds = it.bounds.copy(top = 650 + ((it.bounds.top - 650) * .45).toInt(),
                bottom = 650 + ((it.bounds.bottom - 650) * .45).toInt()))
        }
        val second = first.filterNot { it.text == "$79.45" }.map {
            it.copy(bounds = it.bounds.copy(top = it.bounds.top + 800, bottom = it.bounds.bottom + 800))
        }
        val result = replay { frame(first + second); frame(second) }
        assertEquals(2, result.parsed.size)
        assertEquals(79.45, result.parsed.first().card.fare!!, 0.001)
        assertNull(result.parsed.last().card.fare)
        assertNotEquals(result.parsed.first().card.sessionId, result.parsed.last().card.sessionId)
        assertEquals(2, result.offers.all().size)
        assertNull(result.offers.all().last().fareMxn)
    }

    @Test fun `inactive stacked card disappearing does not duplicate the unchanged active offer`() {
        val first = fixture().filter { it.bounds.top >= 650 }.map {
            it.copy(bounds = it.bounds.copy(top = 650 + ((it.bounds.top - 650) * .45).toInt(),
                bottom = 650 + ((it.bounds.bottom - 650) * .45).toInt()))
        }
        val second = first.map {
            it.copy(bounds = it.bounds.copy(top = it.bounds.top + 800, bottom = it.bounds.bottom + 800))
        }
        val result = replay { frame(first + second); frame(first) }
        assertEquals(2, result.parsed.size)
        assertEquals(1, result.parsed.map { it.card.sessionId }.distinct().size)
        assertEquals(1, result.offers.all().size)
        assertEquals(79.45, result.offers.all().single().fareMxn!!, 0.001)
    }

    @Test fun `partial pickup legs produce one durable record across normal partial partial normal`() {
        val full = fixture()
        val partial = full.map { if (it.text == "1 min (0.2 km)") it.copy(text = "— min (0.2 km)") else it }
        val result = replay { frame(full); frame(partial); frame(partial); frame(full) }
        assertEquals("All four frames pass parsing and the outlier guard", 4, result.parsed.size)
        assertEquals("One durable offer even while pickup minutes are unreadable", 1, result.offers.all().size)
        assertEquals(1, result.parsed.map { it.card.sessionId }.distinct().size)
        assertEquals("PENDING", result.offers.all().single().outcome)
        assertEquals(79.45, result.offers.all().single().fareMxn!!, 0.001)
        assertEquals(1.0, result.offers.all().single().pickupMin!!, 0.0)
    }

    @Test fun `overlay cannot rehydrate retained ledger fare after route confidence is lost`() {
        val full = fixture()
        val partial = full.map { if (it.text == "1 min (0.2 km)") it.copy(text = "— min (0.2 km)") else it }
        val result = replay {
            frame(full)
            assertTrue(actualVerdict(parsed.last()).regla.band is ReglaBand.Rated)
            frame(partial)
            assertNull(parsed.last().card.fare)
            assertEquals(79.45, offers.all().single().fareMxn!!, 0.001)
            val current = actualVerdict(parsed.last())
            assertTrue(current.regla.band is ReglaBand.Unavailable)
            assertEquals(ReglaUnavailable.FARE, (current.regla.band as ReglaBand.Unavailable).input)
            frame(full)
            assertNull("Reacquisition in an existing instance needs confirmation", parsed.last().card.fare)
            assertTrue(actualVerdict(parsed.last()).regla.band is ReglaBand.Unavailable)
            frame(full)
            assertEquals(79.45, parsed.last().card.fare!!, 0.001)
            assertTrue(actualVerdict(parsed.last()).regla.band is ReglaBand.Rated)
        }
        assertEquals(1, result.offers.all().size)
        assertEquals(79.45, result.offers.all().single().fareMxn!!, 0.001)
    }

    @Test fun `alternating candidates never flip displayed fare or write ledger corrections`() {
        val full = fixture()
        val candidate = full.map { if (it.text == "$79.45") it.copy(text = "$99.45") else it }
        val result = replay {
            frame(full)
            repeat(12) { frame(if (it % 2 == 0) candidate else full, (it + 1) * 350L) }
        }
        assertEquals(13, result.parsed.size)
        assertTrue(result.parsed.all { it.card.fare == 79.45 })
        assertEquals(1, result.signals.filterIsInstance<LifecycleSignal.OfferSeen>().size)
        assertTrue(result.ledgerSnapshots.all { it.single().fareMxn == 79.45 })
        assertEquals(1, result.offers.all().size)
    }

    @Test fun `expired samples cannot promote one candidate into displayed or persisted confirmed payment`() {
        val full = fixture()
        val candidate = full.map { if (it.text == "$79.45") it.copy(text = "$99.45") else it }
        val unread = full.filterNot { it.text == "$79.45" }
        val result = replay {
            frame(full, 0); frame(full, 300)
            frame(unread, 600); frame(unread, 900); frame(unread, 1200)
            frame(candidate, 3000)
            assertEquals(79.45, parsed.last().card.fare!!, 0.001)
            assertEquals(79.45, offers.all().single().fareMxn!!, 0.001)
            frame(unread, 60_000)
            assertEquals(79.45, parsed.last().card.fare!!, 0.001)
            assertEquals(79.45, offers.all().single().fareMxn!!, 0.001)
            assertEquals(1, signals.filterIsInstance<LifecycleSignal.OfferSeen>().size)
            frame(candidate, 60_300)
            assertEquals(79.45, parsed.last().card.fare!!, 0.001)
            assertEquals(79.45, offers.all().single().fareMxn!!, 0.001)
            frame(candidate, 60_600)
            assertEquals(99.45, parsed.last().card.fare!!, 0.001)
            assertEquals(99.45, offers.all().single().fareMxn!!, 0.001)
        }
        assertEquals(1, result.offers.all().size)
        assertEquals(2, result.signals.filterIsInstance<LifecycleSignal.OfferSeen>().size)
    }

    @Test fun `empty capture creates no phantom offers and an unread returning card cannot borrow departed money`() {
        val full = fixture()
        val unread = full.filterNot { it.text == "$79.45" }
        val result = replay {
            frame(emptyList())
            assertTrue(offers.all().isEmpty())
            assertTrue(parsed.isEmpty())
            frame(full)
            val original = offers.all().single()
            frame(emptyList())
            assertEquals(listOf(original), offers.all())
            frame(unread)
            assertNull(parsed.last().card.fare)
        }
        assertEquals(2, result.offers.all().size)
        assertEquals(79.45, result.offers.all().first().fareMxn!!, 0.001)
        assertNull(result.offers.all().last().fareMxn)
        assertNotEquals(result.offers.all().first().sessionId, result.offers.all().last().sessionId)
        assertTrue(result.trips.all().isEmpty())
        assertTrue(result.offers.all().all { it.seenAt > 1_700_000_000_000L && it.lastSeenAt >= it.seenAt })
    }

    @Test fun `repeated confirmation revises one record and disappearance cannot persist retained money`() {
        val full = fixture()
        val candidate = full.map { if (it.text == "$79.45") it.copy(text = "$99.45") else it }
        val unread = full.filterNot { it.text == "$79.45" }
        val result = replay {
            frame(full); frame(full); frame(unread); frame(unread)
            frame(candidate)
            assertEquals(79.45, parsed.last().card.fare!!, 0.001)
            assertEquals(79.45, offers.all().single().fareMxn!!, 0.001)
            frame(candidate)
            assertEquals(99.45, parsed.last().card.fare!!, 0.001)
            assertEquals(99.45, offers.all().single().fareMxn!!, 0.001)
            val before = offers.all()
            frame(emptyList()); frame(emptyList()); frame(emptyList())
            pipeline.onCaptureEnd(System.currentTimeMillis())
            yield()
            assertEquals("Absent frames cannot write retained payment", before, offers.all())
        }
        val row = result.offers.all().single()
        assertEquals(1, result.parsed.map { it.card.sessionId }.distinct().size)
        assertEquals(2, result.signals.filterIsInstance<LifecycleSignal.OfferSeen>().size)
        assertTrue("Offer timestamp must be epoch time", row.seenAt > 1_700_000_000_000L)
        assertTrue(row.lastSeenAt >= row.seenAt)
        assertEquals(row.seenAt, row.evaluation!!.evaluatedAt)
        assertTrue(result.trips.all().isEmpty())
        assertTrue(result.shifts.all().all { it.startedAt > 1_700_000_000_000L })
    }
}

private class ReplayOfferDao : OfferDao {
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

private class ReplayTripDao(private val offers: ReplayOfferDao? = null) : TripDao {
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

private class ReplayShiftDao : ShiftDao {
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

private class ReplayCostProfileDao : CostProfileDao {
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

