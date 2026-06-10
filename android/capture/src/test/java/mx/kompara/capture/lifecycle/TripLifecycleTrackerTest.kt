package mx.kompara.capture.lifecycle

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import mx.kompara.data.db.dao.CostProfileDao
import mx.kompara.data.db.dao.OfferDao
import mx.kompara.data.db.dao.ShiftDao
import mx.kompara.data.db.dao.TripDao
import mx.kompara.data.db.entity.CostProfileEntity
import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.db.entity.OfferOutcome
import mx.kompara.data.db.entity.ShiftEntity
import mx.kompara.data.db.entity.TripEntity
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.parsers.model.OfferCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The offer→accept→trip→complete state machine and shift inferer in [TripLifecycleTracker] (B-039),
 * exercised against synthetic signal sequences with in-memory fake DAOs (no device, no Room). Times
 * are explicit epoch-millis on the signals, so the timing heuristics are tested deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripLifecycleTrackerTest {

    private val uber = "com.ubercab.driver"
    private val minute = 60_000L

    private fun card(fare: Double = 100.0, tripKm: Double = 10.0, tripMin: Double = 20.0) =
        OfferCard(platform = uber, fare = fare, tripDistanceKm = tripKm, tripDurationMin = tripMin)

    private fun tracker(
        offers: FakeOfferDao,
        trips: FakeTripDao,
        shifts: FakeShiftDao,
        rollups: CountingRollupTrigger,
    ) = TripLifecycleTracker(
        offerDao = offers,
        tripDao = trips,
        shiftDao = shifts,
        costProfileRepository = CostProfileRepository(FakeCostProfileDao()),
        engine = NetProfitEngine(),
        rollupTrigger = rollups,
        heuristics = TripStateHeuristics.DEFAULT,
    )

    @Test
    fun `seen then accepted then completed records offer, trip and earnings`() = runTest {
        val offers = FakeOfferDao()
        val trips = FakeTripDao()
        val shifts = FakeShiftDao()
        val rollups = CountingRollupTrigger()
        val t = tracker(offers, trips, shifts, rollups)

        val t0 = 1_000_000L
        t.handle(LifecycleSignal.OfferSeen(uber, t0, card(fare = 150.0, tripKm = 12.0)))
        // Card vanishes + trip-state within the accept window ⇒ accepted, trip opens.
        t.handle(LifecycleSignal.TripStateEntered(uber, t0 + 5_000L))
        // Trip-state keeps coming (trip in progress); should not open a second trip.
        t.handle(LifecycleSignal.TripStateEntered(uber, t0 + 60_000L))
        // Return to idle ⇒ trip closes.
        t.handle(LifecycleSignal.IdleStateEntered(uber, t0 + 600_000L))

        val offer = offers.all().single()
        assertEquals(OfferOutcome.ACCEPTED.name, offer.outcome)
        assertTrue(offer.accepted)
        assertNotNull(offer.resolvedAt)

        val trip = trips.all().single()
        assertEquals(offer.id, trip.offerId)
        assertNotNull(trip.endedAt)
        assertEquals(150.0, trip.grossMxn, 1e-9) // offer fare estimate
        assertEquals(12.0, trip.distanceKm, 1e-9)
        assertTrue("captured earnings are estimated", trip.estimated)
        assertTrue("rollup fired on trip close", rollups.count >= 1)
    }

    @Test
    fun `seen then quick idle is a decline with no trip`() = runTest {
        val offers = FakeOfferDao()
        val trips = FakeTripDao()
        val shifts = FakeShiftDao()
        val t = tracker(offers, trips, shifts, CountingRollupTrigger())

        val t0 = 2_000_000L
        t.handle(LifecycleSignal.OfferSeen(uber, t0, card()))
        // Idle within declineMax ⇒ DECLINED.
        t.handle(LifecycleSignal.IdleStateEntered(uber, t0 + 3_000L))

        val offer = offers.all().single()
        assertEquals(OfferOutcome.DECLINED.name, offer.outcome)
        assertFalse(offer.accepted)
        assertTrue("no trip on a decline", trips.all().isEmpty())
    }

    @Test
    fun `seen then slow idle with no trip is an expiry`() = runTest {
        val offers = FakeOfferDao()
        val trips = FakeTripDao()
        val shifts = FakeShiftDao()
        val t = tracker(offers, trips, shifts, CountingRollupTrigger())

        val t0 = 3_000_000L
        t.handle(LifecycleSignal.OfferSeen(uber, t0, card()))
        // Idle well past declineMax, still no trip ⇒ EXPIRED.
        t.handle(LifecycleSignal.IdleStateEntered(uber, t0 + 20_000L))

        assertEquals(OfferOutcome.EXPIRED.name, offers.all().single().outcome)
        assertTrue(trips.all().isEmpty())
    }

    @Test
    fun `a trip-state arriving after the accept window does not link the stale offer`() = runTest {
        val offers = FakeOfferDao()
        val trips = FakeTripDao()
        val shifts = FakeShiftDao()
        val t = tracker(offers, trips, shifts, CountingRollupTrigger())

        val t0 = 4_000_000L
        t.handle(LifecycleSignal.OfferSeen(uber, t0, card(fare = 99.0)))
        // Trip-state arrives long after the accept window ⇒ bare trip, offer not accepted.
        t.handle(LifecycleSignal.TripStateEntered(uber, t0 + 60_000L))

        val offer = offers.all().single()
        assertEquals(OfferOutcome.EXPIRED.name, offer.outcome) // resolved as stale, not accepted
        val trip = trips.all().single()
        assertNull("bare trip has no offer link", trip.offerId)
        assertEquals(0.0, trip.grossMxn, 1e-9)
    }

    @Test
    fun `next offer closes the prior open trip`() = runTest {
        val offers = FakeOfferDao()
        val trips = FakeTripDao()
        val shifts = FakeShiftDao()
        val t = tracker(offers, trips, shifts, CountingRollupTrigger())

        val t0 = 5_000_000L
        t.handle(LifecycleSignal.OfferSeen(uber, t0, card()))
        t.handle(LifecycleSignal.TripStateEntered(uber, t0 + 5_000L)) // trip opens
        // A new offer appears (driver back to receiving) ⇒ prior trip closes.
        t.handle(LifecycleSignal.OfferSeen(uber, t0 + 600_000L, card(fare = 200.0)))

        val firstTrip = trips.all().first { it.offerId != null }
        assertNotNull("prior trip closed", firstTrip.endedAt)
        assertEquals(t0 + 600_000L, firstTrip.endedAt)
    }

    @Test
    fun `a flicker trip shorter than the minimum is dropped to a zero trip`() = runTest {
        val offers = FakeOfferDao()
        val trips = FakeTripDao()
        val shifts = FakeShiftDao()
        val t = tracker(offers, trips, shifts, CountingRollupTrigger())

        val t0 = 6_000_000L
        t.handle(LifecycleSignal.OfferSeen(uber, t0, card(fare = 100.0, tripKm = 10.0)))
        t.handle(LifecycleSignal.TripStateEntered(uber, t0 + 5_000L)) // trip opens
        // Idle just 1s later — below minTripDuration ⇒ dropped (zeroed, endedAt == startedAt).
        t.handle(LifecycleSignal.IdleStateEntered(uber, t0 + 6_000L))

        val trip = trips.all().single()
        assertEquals(trip.startedAt, trip.endedAt)
        assertEquals(0.0, trip.grossMxn, 1e-9)
        assertEquals(0.0, trip.distanceKm, 1e-9)
    }

    // --- Shift inference ---------------------------------------------------------------------

    @Test
    fun `first event opens a shift and subsequent events keep it open`() = runTest {
        val offers = FakeOfferDao()
        val trips = FakeTripDao()
        val shifts = FakeShiftDao()
        val t = tracker(offers, trips, shifts, CountingRollupTrigger())

        val t0 = 7_000_000L
        t.handle(LifecycleSignal.OfferSeen(uber, t0, card()))
        t.handle(LifecycleSignal.IdleStateEntered(uber, t0 + 10 * minute))

        val shift = shifts.all().single()
        assertEquals(t0, shift.startedAt)
        assertNull("still open within the gap", shift.endedAt)
        assertEquals(t0 + 10 * minute, shift.lastEventAt)
    }

    @Test
    fun `a gap over 30 minutes closes the old shift and opens a new one`() = runTest {
        val offers = FakeOfferDao()
        val trips = FakeTripDao()
        val shifts = FakeShiftDao()
        val t = tracker(offers, trips, shifts, CountingRollupTrigger())

        val t0 = 8_000_000L
        t.handle(LifecycleSignal.OfferSeen(uber, t0, card()))
        val lastOfFirst = t0 + 5 * minute
        t.handle(LifecycleSignal.IdleStateEntered(uber, lastOfFirst))
        // 40-minute gap (> 30) ⇒ first shift closes retroactively at its last event; new shift opens.
        val resume = lastOfFirst + 40 * minute
        t.handle(LifecycleSignal.OfferSeen(uber, resume, card()))

        val all = shifts.all().sortedBy { it.startedAt }
        assertEquals(2, all.size)
        assertEquals("old shift closed at its last event", lastOfFirst, all[0].endedAt)
        assertEquals(resume, all[1].startedAt)
        assertNull(all[1].endedAt)
    }
}

// --- In-memory fakes -------------------------------------------------------------------------

private class FakeOfferDao : OfferDao {
    private val rows = mutableMapOf<Long, OfferEntity>()
    private var nextId = 1L

    fun all(): List<OfferEntity> = rows.values.sortedBy { it.id }

    override suspend fun insert(offer: OfferEntity): Long {
        val id = if (offer.id == 0L) nextId++ else offer.id
        rows[id] = offer.copy(id = id)
        return id
    }

    override suspend fun update(offer: OfferEntity) {
        rows[offer.id] = offer
    }

    override fun observeAll(): Flow<List<OfferEntity>> = MutableStateFlow(all())
    override fun observeByPlatform(platform: String): Flow<List<OfferEntity>> =
        MutableStateFlow(all().filter { it.platform == platform })

    override suspend fun findById(id: Long): OfferEntity? = rows[id]

    override suspend fun latestPending(): OfferEntity? =
        rows.values.filter { it.outcome == OfferOutcome.PENDING.name }.maxByOrNull { it.seenAt }

    override suspend fun seenBetween(from: Long, until: Long): List<OfferEntity> =
        rows.values.filter { it.seenAt in from until until }.sortedBy { it.seenAt }

    override fun observeSeenBetween(from: Long, until: Long): Flow<List<OfferEntity>> =
        MutableStateFlow(rows.values.filter { it.seenAt in from until until }.sortedBy { it.seenAt })
}

private class FakeTripDao : TripDao {
    private val rows = mutableMapOf<Long, TripEntity>()
    private var nextId = 1L

    fun all(): List<TripEntity> = rows.values.sortedBy { it.id }

    override suspend fun insert(trip: TripEntity): Long {
        val id = if (trip.id == 0L) nextId++ else trip.id
        rows[id] = trip.copy(id = id)
        return id
    }

    override suspend fun update(trip: TripEntity) {
        rows[trip.id] = trip
    }

    override suspend fun findById(id: Long): TripEntity? = rows[id]
    override fun observeAll(): Flow<List<TripEntity>> = MutableStateFlow(all())
    override fun observeSince(since: Long): Flow<List<TripEntity>> =
        MutableStateFlow(all().filter { it.startedAt >= since })

    override fun observeGrossSince(since: Long): Flow<Double> =
        MutableStateFlow(all().filter { it.startedAt >= since }.sumOf { it.grossMxn })

    override suspend fun latestOpen(): TripEntity? =
        rows.values.filter { it.endedAt == null }.maxByOrNull { it.startedAt }

    override suspend fun completedStartedBetween(from: Long, until: Long): List<TripEntity> =
        rows.values.filter { it.endedAt != null && it.startedAt in from until until }
            .sortedBy { it.startedAt }

    override fun observeCompletedStartedBetween(from: Long, until: Long): Flow<List<TripEntity>> =
        MutableStateFlow(
            rows.values.filter { it.endedAt != null && it.startedAt in from until until }
                .sortedBy { it.startedAt },
        )
}

private class FakeShiftDao : ShiftDao {
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

private class FakeCostProfileDao : CostProfileDao {
    override suspend fun upsert(profile: CostProfileEntity) = Unit
    override fun observe(id: Long): Flow<CostProfileEntity?> = MutableStateFlow(null)
    override suspend fun get(id: Long): CostProfileEntity? = null
}

private class CountingRollupTrigger : RollupTrigger {
    var count = 0
    override fun requestRollup() {
        count++
    }
}
