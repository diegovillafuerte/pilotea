package mx.kompara.capture.lifecycle

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.OfferRecorder
import mx.kompara.metrics.TripOffer
import mx.kompara.metrics.recordedMetrics
import mx.kompara.data.db.entity.TripLifecycle
import mx.kompara.parsers.model.OfferCard
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The offer→accept→trip→complete state machine and shift inferer in [TripLifecycleTracker] (B-039),
 * exercised against synthetic signal sequences with in-memory fake DAOs (no device, no Room). Times
 * are explicit epoch-millis on the signals, so the timing heuristics are tested deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripLifecycleTrackerTest {

    private val uber = "com.ubercab.driver"
    private val minute = 60_000L

    // A real DataStore-backed SettingsRepository over a temp file (cheap, no Android): the verdict
    // path reads the driver's configured threshold/metric through it (B-083). Default = untuned, so
    // effectiveThreshold == PlatformThreshold.DEFAULT and the existing tests keep their behaviour.
    private lateinit var settingsFile: File
    private lateinit var settings: SettingsRepository

    @Before fun setUp() {
        settingsFile = File.createTempFile("tracker_settings", ".preferences_pb").also { it.delete() }
        settings = SettingsRepository(PreferenceDataStoreFactory.create(produceFile = { settingsFile }))
    }

    @After fun tearDown() {
        settingsFile.delete()
    }

    private fun card(fare: Double = 100.0, tripKm: Double = 10.0, tripMin: Double = 20.0) =
        OfferCard(platform = uber, paymentBasis = "DRIVER_PAYMENT", pickupDistanceKm = 0.0, pickupEtaMin = 0.0, fare = fare, tripDistanceKm = tripKm, tripDurationMin = tripMin)

    private fun tracker(
        offers: FakeOfferDao,
        trips: FakeTripDao,
        shifts: FakeShiftDao,
        rollups: CountingRollupTrigger,
        costDao: FakeCostProfileDao = FakeCostProfileDao(),
    ) = TripLifecycleTracker(
        offerDao = offers,
        tripDao = trips,
        shiftDao = shifts,
        recorder = OfferRecorder(offers, CostProfileRepository(costDao), settings, NetProfitEngine()),
        rollupTrigger = rollups,
        heuristics = TripStateHeuristics.DEFAULT,
    )

    @Test
    fun `seen then accepted then completed records offer, trip and earnings`() = runTest {
        val offers = FakeOfferDao()
        val trips = FakeTripDao(offers)
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
        assertEquals(150.0, trip.grossMxn!!, 1e-9) // offer fare estimate
        assertEquals(12.0, trip.distanceKm!!, 1e-9)
        assertTrue("captured earnings are estimated", trip.estimated)
        assertTrue("rollup fired on trip close", rollups.count >= 1)
    }

    @Test
    fun `observed idle resolves not taken with no trip`() = runTest {
        val offers = FakeOfferDao()
        val trips = FakeTripDao(offers)
        val shifts = FakeShiftDao()
        val t = tracker(offers, trips, shifts, CountingRollupTrigger())

        val t0 = 2_000_000L
        t.handle(LifecycleSignal.OfferSeen(uber, t0, card()))
        // Idle within declineMax ⇒ DECLINED.
        t.handle(LifecycleSignal.IdleStateEntered(uber, t0 + 3_000L))

        val offer = offers.all().single()
        assertEquals(OfferOutcome.NOT_TAKEN.name, offer.outcome)
        assertFalse(offer.accepted)
        assertTrue("no trip on a decline", trips.all().isEmpty())
    }

    @Test
    fun `observed idle after a long offer resolves not taken`() = runTest {
        val offers = FakeOfferDao()
        val trips = FakeTripDao(offers)
        val shifts = FakeShiftDao()
        val t = tracker(offers, trips, shifts, CountingRollupTrigger())

        val t0 = 3_000_000L
        t.handle(LifecycleSignal.OfferSeen(uber, t0, card()))
        // Idle well past declineMax, still no trip ⇒ EXPIRED.
        t.handle(LifecycleSignal.IdleStateEntered(uber, t0 + 20_000L))

        assertEquals(OfferOutcome.NOT_TAKEN.name, offers.all().single().outcome)
        assertTrue(trips.all().isEmpty())
    }

    @Test
    fun `a trip-state arriving after the accept window does not link the stale offer`() = runTest {
        val offers = FakeOfferDao()
        val trips = FakeTripDao(offers)
        val shifts = FakeShiftDao()
        val t = tracker(offers, trips, shifts, CountingRollupTrigger())

        val t0 = 4_000_000L
        t.handle(LifecycleSignal.OfferSeen(uber, t0, card(fare = 99.0)))
        // Trip-state arrives long after the accept window ⇒ bare trip, offer not accepted.
        t.handle(LifecycleSignal.TripStateEntered(uber, t0 + 60_000L))

        val offer = offers.all().single()
        assertEquals(OfferOutcome.UNKNOWN.name, offer.outcome) // resolved as stale, not accepted
        val trip = trips.all().single()
        assertNull("bare trip has no offer link", trip.offerId)
        assertNull(trip.grossMxn)
    }

    @Test
    fun `next offer can overlap and never closes the prior trip`() = runTest {
        val offers = FakeOfferDao()
        val trips = FakeTripDao(offers)
        val shifts = FakeShiftDao()
        val t = tracker(offers, trips, shifts, CountingRollupTrigger())

        val t0 = 5_000_000L
        t.handle(LifecycleSignal.OfferSeen(uber, t0, card()))
        t.handle(LifecycleSignal.TripStateEntered(uber, t0 + 5_000L)) // trip opens
        // A new offer appears (driver back to receiving) ⇒ prior trip closes.
        t.handle(LifecycleSignal.OfferSeen(uber, t0 + 600_000L, card(fare = 200.0)))

        val firstTrip = trips.all().first { it.offerId != null }
        assertNull("overlapping offer is not completion evidence", firstTrip.endedAt)
    }

    @Test
    fun `a short flicker becomes unknown and preserves captured economics`() = runTest {
        val offers = FakeOfferDao()
        val trips = FakeTripDao(offers)
        val shifts = FakeShiftDao()
        val t = tracker(offers, trips, shifts, CountingRollupTrigger())

        val t0 = 6_000_000L
        t.handle(LifecycleSignal.OfferSeen(uber, t0, card(fare = 100.0, tripKm = 10.0)))
        t.handle(LifecycleSignal.TripStateEntered(uber, t0 + 5_000L)) // trip opens
        // Idle just 1s later — below minTripDuration ⇒ dropped (zeroed, endedAt == startedAt).
        t.handle(LifecycleSignal.IdleStateEntered(uber, t0 + 6_000L))

        val trip = trips.all().single()
        assertEquals(TripLifecycle.UNKNOWN.name, trip.lifecycle)
        assertEquals(100.0, trip.grossMxn!!, 1e-9)
        assertEquals(10.0, trip.distanceKm!!, 1e-9)
    }

    // --- Persisted verdict colour (B-083) ----------------------------------------------------

    // A complete card (every leg present, so no missing-input YELLOW cap) worth exactly 9.0 $/km net
    // at zero marginal cost: GREEN under the 8.0/km default, YELLOW under a stricter 10.0/km floor.
    private fun ninePerKmCard() = OfferCard(
        platform = uber,
        paymentBasis = "DRIVER_PAYMENT",
        fare = 90.0,
        pickupDistanceKm = 0.0,
        pickupEtaMin = 0.0,
        tripDistanceKm = 10.0,
        tripDurationMin = 20.0,
    )

    @Test
    fun `ledger persists the driver's configured threshold colour, not the default (B-083)`() = runTest {
        val offers = FakeOfferDao()
        val t = tracker(offers, FakeTripDao(), FakeShiftDao(), CountingRollupTrigger())
        // The driver tuned a stricter floor (the on-device values: 10.0/7.5 $/km). 9.0 $/km is YELLOW
        // under it, even though it is GREEN under PlatformThreshold.DEFAULT (8.0/km).
        settings.setThreshold(
            PlatformThreshold(minPerKmMxn = 10.0, minPerHourMxn = 154.0, redPerKmMxn = 7.5, redPerHourMxn = 115.5),
        )

        t.handle(LifecycleSignal.OfferSeen(uber, 1_000_000L, ninePerKmCard()))

        assertEquals("YELLOW", offers.all().single().verdict)
    }

    @Test
    fun `same offer reads GREEN under the default threshold (B-083 control)`() = runTest {
        val offers = FakeOfferDao()
        val t = tracker(offers, FakeTripDao(), FakeShiftDao(), CountingRollupTrigger())
        // No tuning ⇒ effectiveThreshold == PlatformThreshold.DEFAULT. Same card, GREEN — confirms the
        // divergence the configured-threshold test guards against is real (one rung greener).
        t.handle(LifecycleSignal.OfferSeen(uber, 1_000_000L, ninePerKmCard()))

        assertEquals("GREEN", offers.all().single().verdict)
    }

    @Test fun `nullable legs and fare survive capture and unlinked trips never invent zero`() = runTest {
        val offers = FakeOfferDao(); val trips = FakeTripDao(offers); val shifts = FakeShiftDao()
        val tracker = tracker(offers, trips, shifts, CountingRollupTrigger())
        tracker.handle(LifecycleSignal.OfferSeen(uber, 1_000, OfferCard(uber, tripDistanceKm = 10.0)))
        val offer = offers.all().single()
        assertNull(offer.fareMxn); assertNull(offer.distanceKm); assertNull(offer.durationMin)
        assertEquals(10.0, offer.tripKm!!, 0.0)
        assertEquals("UNKNOWN", offer.verdict)
        tracker.handle(LifecycleSignal.TripStateEntered(uber, 100_000))
        val trip = trips.all().single()
        assertNull(trip.offerId); assertNull(trip.grossMxn); assertNull(trip.distanceKm)
        assertEquals("UNKNOWN", trip.lifecycle)
    }

    @Test fun `duplicate session after restart keeps first judgement and raw legs`() = runTest {
        val offers = FakeOfferDao(); val trips = FakeTripDao(offers); val shifts = FakeShiftDao()
        val first = tracker(offers, trips, shifts, CountingRollupTrigger())
        val card = ninePerKmCard().copy(sessionId = "offer-a", sessionStartedAt = 1_000)
        first.handle(LifecycleSignal.OfferSeen(uber, 1_000, card))
        val before = offers.all().single()
        settings.setThreshold(PlatformThreshold(20.0, 300.0, 15.0, 200.0))
        settings.setPreferredMetric(mx.kompara.data.settings.PreferredMetric.IPH)
        val restarted = tracker(offers, trips, shifts, CountingRollupTrigger())
        restarted.handle(LifecycleSignal.OfferSeen(uber, 40_000, card.copy(tripDistanceKm = 100.0)))
        val after = offers.all().single()
        assertEquals(before.evaluation, after.evaluation)
        assertEquals("GREEN", after.verdict)
        assertEquals(10.0, after.distanceKm!!, 0.0)
        // Acceptance is measured from the last observed card, not its first appearance.
        restarted.handle(LifecycleSignal.TripStateEntered(uber, 45_000))
        assertEquals(after.id, trips.all().single().offerId)
        restarted.handle(LifecycleSignal.TripStateEntered(uber, 46_000))
        assertEquals(1, trips.all().size)
    }

    @Test fun `partial offer recovers within its session using the original costs and limits`() = runTest {
        val offers = FakeOfferDao(); val trips = FakeTripDao(offers); val shifts = FakeShiftDao()
        val costDao = FakeCostProfileDao()
        val tracker = tracker(offers, trips, shifts, CountingRollupTrigger(), costDao)
        val complete = ninePerKmCard().copy(sessionId = "recovery", sessionStartedAt = 1_000)
        tracker.handle(LifecycleSignal.OfferSeen(uber, 1_000, complete.copy(fare = null, tripDurationMin = null)))
        val first = offers.all().single()
        assertEquals("FARE", first.evaluation!!.unavailableReason)
        settings.setThreshold(PlatformThreshold(20.0, 300.0, 15.0, 200.0))
        settings.setPreferredMetric(mx.kompara.data.settings.PreferredMetric.IPH)
        CostProfileRepository(costDao).saveRunningCosts(
            mx.kompara.data.accounting.RunningCostInputs(mx.kompara.data.accounting.RunningCostMode.DIRECT,
                directPerKm = java.math.BigDecimal("8")), effectiveFrom = 1_100, recordedAt = 1_100)
        tracker.handle(LifecycleSignal.OfferSeen(uber, 1_200, complete))
        val recovered = offers.all().single()
        assertEquals(first.id, recovered.id)
        assertEquals(first.seenAt, recovered.seenAt)
        assertEquals(first.evaluation!!.goal, recovered.evaluation!!.goal)
        assertEquals(first.evaluation!!.costVersionId, recovered.evaluation!!.costVersionId)
        assertEquals("GREEN", recovered.verdict)
        assertEquals("90.00".toBigDecimal(), recovered.evaluation!!.contribution!!.toBigDecimal().setScale(2))
        tracker.handle(LifecycleSignal.TripStateEntered(uber, 1_300))
        val trip = trips.all().single()
        assertEquals(90.0, trip.grossMxn!!, 0.0)
        assertTrue(trip.estimated)
        assertEquals("DRIVER_PAYMENT", trip.paymentBasis)
        val income = mx.kompara.data.rollup.capturedIncomeRecords(listOf(trip), offers.all(), 1_400,
            java.time.ZoneId.of("America/Mexico_City")).single()
        assertEquals(mx.kompara.data.accounting.MoneySource.CAPTURED_ESTIMATE, income.source)
        assertEquals(90.0, income.payment!!.toDouble(), 0.0)
    }

    @Test fun `partial recovery cannot merge different fares or change a resolved offer`() = runTest {
        val offers = FakeOfferDao(); val trips = FakeTripDao(offers)
        val tracker = tracker(offers, trips, FakeShiftDao(), CountingRollupTrigger())
        val complete = card().copy(sessionId = "same", sessionStartedAt = 1_000)
        tracker.handle(LifecycleSignal.OfferSeen(uber, 1_000, complete.copy(tripDurationMin = null)))
        tracker.handle(LifecycleSignal.OfferSeen(uber, 1_100, complete.copy(fare = 200.0)))
        assertNull(offers.all().single().tripMin)
        tracker.handle(LifecycleSignal.IdleStateEntered(uber, 1_200))
        tracker.handle(LifecycleSignal.OfferSeen(uber, 1_300, complete))
        assertNull(offers.all().single().tripMin)
        assertEquals("NOT_TAKEN", offers.all().single().outcome)
    }

    @Test fun `newer same-route OCR fares update pending verdict using frozen costs and limits`() = runTest {
        val offers = FakeOfferDao()
        val costDao = FakeCostProfileDao()
        val recorder = OfferRecorder(offers, CostProfileRepository(costDao), settings, NetProfitEngine())
        val offer = TripOffer("uber", 90.0, 0.0, 0.0, 10.0, 20.0)
        val first = recorder.record("ocr-revision", 1_000, offer, "OCR", false)
        val original = first.evaluation!!
        settings.setThreshold(PlatformThreshold(20.0, 300.0, 15.0, 200.0))
        settings.setPreferredMetric(mx.kompara.data.settings.PreferredMetric.IPH)
        CostProfileRepository(costDao).saveRunningCosts(
            mx.kompara.data.accounting.RunningCostInputs(mx.kompara.data.accounting.RunningCostMode.DIRECT,
                directPerKm = java.math.BigDecimal("8")), effectiveFrom = 1_100, recordedAt = 1_100)

        for ((index, fare) in listOf(120.0, 40.0).withIndex()) {
            val revised = recorder.record("ocr-revision", 1_000, offer.copy(fareMxn = fare), "OCR", false,
                observedAt = 1_200L + index * 100)
            assertEquals(first.id, revised.id)
            assertEquals(first.seenAt, revised.seenAt)
            assertEquals(fare, revised.fareMxn!!, 0.0)
            val evaluation = revised.evaluation!!
            assertEquals(original.evaluatedAt, evaluation.evaluatedAt)
            assertEquals(original.unit, evaluation.unit)
            assertEquals(original.goal, evaluation.goal)
            assertEquals(original.minimum, evaluation.minimum)
            assertEquals(original.costVersionId, evaluation.costVersionId)
            assertEquals(fare, evaluation.contribution!!.toDouble(), 0.0)
            assertEquals(fare / 10, evaluation.perKm!!.toDouble(), 0.0)
            assertEquals(if (fare == 120.0) "GREEN" else "RED", revised.verdict)
            assertEquals(fare / 10, revised.recordedMetrics().netPerKm!!, 0.0)
        }
        val missing = recorder.record("ocr-revision", 1_000, offer.copy(fareMxn = null), "OCR", false,
            observedAt = 1_400)
        assertEquals(40.0, missing.fareMxn!!, 0.0)
        assertEquals("RED", missing.verdict)
        assertEquals(1, offers.all().size)
    }

    @Test fun `fare revision requires newer OCR identity complete legs confirmed payment and pending outcome`() = runTest {
        val original = TripOffer("uber", 90.0, 0.0, 0.0, 10.0, 20.0)
        val changed = original.copy(fareMxn = 120.0)
        data class Case(val incoming: TripOffer = changed, val firstSource: String = "OCR",
            val source: String = "OCR", val at: Long = 1_100, val outcome: String = "PENDING")
        val cases = listOf(
            Case(firstSource = "ACCESSIBILITY"), Case(source = "ACCESSIBILITY"),
            Case(at = 999), Case(at = 1_000), Case(outcome = "ACCEPTED"), Case(outcome = "NOT_TAKEN"),
            Case(incoming = changed.copy(platform = "didi")),
            Case(incoming = changed.copy(pickupKm = 1.0)), Case(incoming = changed.copy(pickupMin = 1.0)),
            Case(incoming = changed.copy(tripKm = 11.0)), Case(incoming = changed.copy(tripMin = 21.0)),
            Case(incoming = changed.copy(pickupKm = null)), Case(incoming = changed.copy(pickupMin = null)),
            Case(incoming = changed.copy(tripKm = null)), Case(incoming = changed.copy(tripMin = null)),
            Case(incoming = changed.copy(paymentBasis = "UNCONFIRMED")),
            Case(incoming = changed.copy(fareMxn = Double.NaN)), Case(incoming = changed.copy(fareMxn = 0.0)),
        )
        for (case in cases) {
            val offers = FakeOfferDao()
            val recorder = OfferRecorder(offers, CostProfileRepository(FakeCostProfileDao()), settings, NetProfitEngine())
            val first = recorder.record("guarded", 1_000, original, case.firstSource, false)
            offers.update(first.copy(outcome = case.outcome))
            val after = recorder.record("guarded", 1_000, case.incoming, case.source, false, case.at)
            assertEquals(case.toString(), first.fareMxn, after.fareMxn)
            assertEquals(case.toString(), first.evaluation, after.evaluation)
            assertEquals(case.toString(), case.outcome, after.outcome)
        }
    }

    @Test fun `host switch cannot accept or finish another apps trip`() = runTest {
        val offers = FakeOfferDao(); val trips = FakeTripDao(offers); val shifts = FakeShiftDao()
        val tracker = tracker(offers, trips, shifts, CountingRollupTrigger())
        val didi = "com.didiglobal.driver"
        tracker.handle(LifecycleSignal.OfferSeen(uber, 1_000, card()))
        tracker.handle(LifecycleSignal.TripStateEntered(didi, 2_000))
        assertNull(trips.all().single().offerId)
        assertEquals("PENDING", offers.all().single().outcome)
        tracker.handle(LifecycleSignal.TripStateEntered(uber, 3_000))
        tracker.handle(LifecycleSignal.IdleStateEntered(didi, 100_000))
        assertNull(trips.all().first { it.platform == "UBER" }.endedAt)
        assertEquals(2, trips.all().size)
    }

    @Test fun `advertised minutes capture loss and restart cannot manufacture completion`() = runTest {
        val offers = FakeOfferDao(); val trips = FakeTripDao(offers); val shifts = FakeShiftDao()
        val tracker = tracker(offers, trips, shifts, CountingRollupTrigger())
        val start = java.time.LocalDateTime.parse("2026-10-19T14:32:00").atZone(java.time.ZoneId.of("America/Mexico_City")).toInstant().toEpochMilli()
        tracker.handle(LifecycleSignal.OfferSeen(uber, start, card(148.0, 12.0, 24.0)))
        tracker.handle(LifecycleSignal.TripStateEntered(uber, start + 1_000))
        tracker.handle(LifecycleSignal.ObservationLost(uber, start + 8 * minute))
        val restarted = tracker(offers, trips, shifts, CountingRollupTrigger())
        assertNull(trips.all().single().endedAt)
        restarted.handle(LifecycleSignal.TripStateEntered(uber, start + 9 * minute, inProgress = true))
        assertEquals("IN_PROGRESS", trips.all().single().lifecycle)
        // Fixture end 14:56 is an explicit observation, never inferred from the 24 min quote.
        restarted.handle(LifecycleSignal.IdleStateEntered(uber, start + 24 * minute))
        val trip = trips.all().single()
        assertEquals("FINISHED", trip.lifecycle)
        assertTrue(trip.estimated)
        assertNull(trip.confirmationEvidenceId)
        assertEquals(148.0, trip.grossMxn!!, 0.0)
    }

    @Test fun `cancellation removes only selected trip retaining money and judgement for audit`() = runTest {
        val offers = FakeOfferDao(); val trips = FakeTripDao(offers); val shifts = FakeShiftDao()
        val rollups = CountingRollupTrigger(); val tracker = tracker(offers, trips, shifts, rollups)
        tracker.handle(LifecycleSignal.OfferSeen(uber, 1_000, card()))
        tracker.handle(LifecycleSignal.TripStateEntered(uber, 2_000))
        val first = trips.all().single()
        tracker.handle(LifecycleSignal.IdleStateEntered(uber, 100_000))
        tracker.handle(LifecycleSignal.OfferSeen(uber, 101_000, card(150.0)))
        tracker.handle(LifecycleSignal.TripStateEntered(uber, 102_000))
        assertTrue(tracker.cancelTrip(first.id, 103_000))
        assertFalse(tracker.cancelTrip(first.id, 104_000))
        assertEquals("CANCELLED", trips.all().first().lifecycle)
        assertEquals(100.0, trips.all().first().grossMxn!!, 0.0)
        assertEquals("USER", trips.all().first().cancellationSource)
        assertEquals("ACCEPTED_ESTIMATE", trips.all().last().lifecycle)
        assertEquals("CANCELLED", offers.all().first().outcome)
        assertEquals("ACCEPTED", offers.all().last().outcome)
    }

    @Test fun `stale host observation cannot finish a newer trip`() = runTest {
        val offers = FakeOfferDao(); val trips = FakeTripDao(offers); val shifts = FakeShiftDao()
        val tracker = tracker(offers, trips, shifts, CountingRollupTrigger())
        tracker.handle(LifecycleSignal.OfferSeen(uber, 10_000, card()))
        tracker.handle(LifecycleSignal.TripStateEntered(uber, 11_000))
        tracker.handle(LifecycleSignal.TripStateEntered(uber, 100_000))
        tracker.handle(LifecycleSignal.IdleStateEntered(uber, 50_000))
        assertNull(trips.all().single().endedAt)
    }

    // --- Shift inference ---------------------------------------------------------------------

    @Test
    fun `first event opens a shift and subsequent events keep it open`() = runTest {
        val offers = FakeOfferDao()
        val trips = FakeTripDao(offers)
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
        val trips = FakeTripDao(offers)
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
    override suspend fun hasSeenBefore(before: Long) = rows.values.any { it.seenAt < before }
    private val rows = mutableMapOf<Long, OfferEntity>()
    private var nextId = 1L

    fun all(): List<OfferEntity> = rows.values.sortedBy { it.id }

    override suspend fun insert(offer: OfferEntity): Long {
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

private class FakeTripDao(private val offers: FakeOfferDao? = null) : TripDao {
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

private class CountingRollupTrigger : RollupTrigger {
    var count = 0
    override fun requestRollup() {
        count++
    }
}
