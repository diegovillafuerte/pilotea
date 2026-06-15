package mx.kompara.capture.lifecycle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import mx.kompara.data.db.dao.OfferDao
import mx.kompara.data.db.dao.ShiftDao
import mx.kompara.data.db.dao.TripDao
import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.db.entity.OfferOutcome
import mx.kompara.data.db.entity.ShiftEntity
import mx.kompara.data.db.entity.TripEntity
import mx.kompara.metrics.CostProfileMapper
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.TripOffer
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The offer → accept → trip → complete state machine, and the shift inferer, that turn the capture
 * stream into the driver's automatic ledger (B-039). This is the [LifecycleSignal] collector — wired
 * exactly like the B-034 telemetry collector: an injected singleton that observes a derived flow
 * without restructuring the pipeline ([OfferEventLifecycleMapper] does the OfferEvent → signal
 * mapping; the service launches `tracker.collect(mapper.signals(offers))`).
 *
 * ## Lifecycle rules (all heuristic — see [TripStateHeuristics] + techdebt for calibration)
 * - **Offer seen** → persist an [OfferEntity] (PENDING) with the metrics verdict; remember it as the
 *   pending candidate.
 * - **Trip-state entered** while an offer is PENDING (within [TripStateHeuristics.acceptWindowMs] of
 *   the card vanishing) → mark that offer ACCEPTED and open a [TripEntity] (estimated, using offer
 *   fare/distance/duration). A trip-state with no pending offer also opens a bare trip.
 * - **Idle-state entered** → if an offer was PENDING and recent, mark it DECLINED (fast, looks like a
 *   tap) or EXPIRED (slow, no decision read); and close any open trip.
 * - **Next offer seen / idle** also closes a stale open trip (return to offer-capable state).
 *
 * ## Shift inference
 * The first signal after >= [ShiftEntity.INACTIVITY_GAP_MS] of silence opens a [ShiftEntity]; an open
 * shift whose [ShiftEntity.lastEventAt] is older than the gap is closed **lazily on the next signal**
 * (retroactively, at its last event) before the new shift opens. Closing strictly via the next event
 * is the documented choice (no idle WorkManager wakeups); a periodic [RollupTrigger] sweep would also
 * catch a shift that never sees another event, which the worker handles.
 *
 * On every trip close it fires [rollupTrigger] so daily/weekly aggregates stay current incrementally.
 */
@Singleton
class TripLifecycleTracker @Inject constructor(
    private val offerDao: OfferDao,
    private val tripDao: TripDao,
    private val shiftDao: ShiftDao,
    private val costProfileRepository: CostProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val engine: NetProfitEngine,
    private val rollupTrigger: RollupTrigger,
    private val heuristics: TripStateHeuristics = TripStateHeuristics.DEFAULT,
) {

    /** Track the [signals] flow forever. Started from the service with its long-lived scope. */
    suspend fun collect(signals: Flow<LifecycleSignal>) {
        signals.collect { handle(it) }
    }

    /** Fold a single [signal] into persisted offers/trips/shifts. Pure side effects; safe to test. */
    suspend fun handle(signal: LifecycleSignal) {
        val shiftId = ensureShift(signal.timestampMs)
        when (signal) {
            is LifecycleSignal.OfferSeen -> onOfferSeen(signal, shiftId)
            is LifecycleSignal.TripStateEntered -> onTripState(signal, shiftId)
            is LifecycleSignal.IdleStateEntered -> onIdleState(signal)
        }
    }

    // --- Offers -------------------------------------------------------------------------------

    private suspend fun onOfferSeen(signal: LifecycleSignal.OfferSeen, shiftId: Long?) {
        // A new offer appearing means any earlier still-pending offer never became a trip and any
        // open trip has ended (the driver is back to receiving requests). Resolve both first.
        resolveStalePending(signal.timestampMs)
        closeOpenTrip(signal.timestampMs)

        val platform = PackagePlatform.of(signal.packageName).name
        val card = signal.card
        val verdict = evaluateVerdict(platform, card)
        offerDao.insert(
            OfferEntity(
                seenAt = signal.timestampMs,
                platform = platform,
                fareMxn = card.fare ?: 0.0,
                distanceKm = (card.pickupDistanceKm ?: 0.0) + (card.tripDistanceKm ?: 0.0),
                durationMin = (card.pickupEtaMin ?: 0.0) + (card.tripDurationMin ?: 0.0),
                pickupKm = card.pickupDistanceKm,
                surge = card.surge,
                verdict = verdict,
                accepted = false,
                outcome = OfferOutcome.PENDING.name,
                resolvedAt = null,
                shiftId = shiftId,
            ),
        )
    }

    private suspend fun evaluateVerdict(platform: String, card: mx.kompara.parsers.model.OfferCard): String? {
        if (card.fare == null) return null
        val costProfile = CostProfileMapper.toCostProfileOrZero(costProfileRepository.get())
        // Grade against the driver's CONFIGURED floors + preferred metric — the SAME inputs the
        // overlay graded the live chip with. Reading PlatformThreshold.DEFAULT here persisted a
        // traffic-light colour one rung greener than the driver actually saw (B-083 / TD-025).
        val settings = settingsRepository.settings.first()
        val offer = TripOffer(
            platform = platform.lowercase(),
            fareMxn = card.fare,
            pickupKm = card.pickupDistanceKm,
            pickupMin = card.pickupEtaMin,
            tripKm = card.tripDistanceKm,
            tripMin = card.tripDurationMin,
        )
        return engine.evaluate(
            offer,
            costProfile,
            settings.effectiveThreshold,
            settings.preferredMetric,
        ).verdict.level.name
    }

    // --- Trips --------------------------------------------------------------------------------

    private suspend fun onTripState(signal: LifecycleSignal.TripStateEntered, shiftId: Long?) {
        // If a trip is already open, this is just the trip continuing — nothing to do.
        if (tripDao.latestOpen() != null) return

        val pending = offerDao.latestPending()
        if (pending != null && signal.timestampMs - pending.seenAt <= heuristics.acceptWindowMs) {
            // Accept: link the offer, open an estimated trip from the offer's numbers.
            offerDao.update(
                pending.copy(
                    accepted = true,
                    outcome = OfferOutcome.ACCEPTED.name,
                    resolvedAt = signal.timestampMs,
                ),
            )
            tripDao.insert(
                TripEntity(
                    offerId = pending.id,
                    shiftId = shiftId ?: pending.shiftId,
                    startedAt = signal.timestampMs,
                    endedAt = null,
                    platform = pending.platform,
                    grossMxn = pending.fareMxn,
                    distanceKm = pending.distanceKm,
                    durationMin = pending.durationMin,
                    estimated = true,
                ),
            )
        } else {
            // Trip-like state with no recent enough offer: any stale pending offer couldn't be
            // attributed to this trip (it's older than the accept window) — resolve it as
            // DECLINED/EXPIRED before opening a bare trip so it doesn't linger PENDING forever.
            resolveStalePending(signal.timestampMs)
            // Open a bare trip so the online time and a (zero-fare) trip aren't lost; earnings stay
            // estimated/unknown.
            tripDao.insert(
                TripEntity(
                    offerId = null,
                    shiftId = shiftId,
                    startedAt = signal.timestampMs,
                    endedAt = null,
                    platform = PackagePlatform.of(signal.packageName).name,
                    grossMxn = 0.0,
                    distanceKm = 0.0,
                    durationMin = 0.0,
                    estimated = true,
                ),
            )
        }
    }

    private suspend fun onIdleState(signal: LifecycleSignal.IdleStateEntered) {
        resolveStalePending(signal.timestampMs)
        closeOpenTrip(signal.timestampMs)
    }

    /** Close the open trip (if any) at [endedAt], discarding sub-[minTripDurationMs] noise trips. */
    private suspend fun closeOpenTrip(endedAt: Long) {
        val open = tripDao.latestOpen() ?: return
        if (endedAt - open.startedAt < heuristics.minTripDurationMs) {
            // Too short to be a real trip (UI flicker). Drop it by closing with zero-length and
            // marking it estimated/empty — simplest is to delete-by-overwrite to a closed noise row.
            // We keep the row but stamp endedAt == startedAt and zero economics so rollups ignore it
            // (a zero-trip contributes nothing). Deletion would need a delete query; this is enough.
            tripDao.update(open.copy(endedAt = open.startedAt, grossMxn = 0.0, distanceKm = 0.0, durationMin = 0.0))
            return
        }
        tripDao.update(open.copy(endedAt = endedAt))
        rollupTrigger.requestRollup()
    }

    /**
     * Resolve a still-PENDING offer when no acceptance happened: DECLINED if the idle/next-offer
     * signal arrived fast (looks like a tap), EXPIRED otherwise.
     */
    private suspend fun resolveStalePending(now: Long) {
        val pending = offerDao.latestPending() ?: return
        val elapsed = now - pending.seenAt
        val outcome = if (elapsed <= heuristics.declineMaxMs) OfferOutcome.DECLINED else OfferOutcome.EXPIRED
        offerDao.update(pending.copy(outcome = outcome.name, resolvedAt = now))
    }

    // --- Shifts -------------------------------------------------------------------------------

    /**
     * Ensure a shift covers [now]: open a new one if none is active or the active one has been idle
     * past the gap (closing the stale one retroactively at its last event). Returns the active shift
     * id. Always advances the open shift's [ShiftEntity.lastEventAt].
     */
    private suspend fun ensureShift(now: Long): Long? {
        val open = shiftDao.latestOpen()
        if (open == null) {
            return shiftDao.insert(ShiftEntity(startedAt = now, lastEventAt = now))
        }
        if (now - open.lastEventAt >= ShiftEntity.INACTIVITY_GAP_MS) {
            // Stale: close it at its last event, then open a fresh shift for the resumed activity.
            shiftDao.update(open.copy(endedAt = open.lastEventAt))
            return shiftDao.insert(ShiftEntity(startedAt = now, lastEventAt = now))
        }
        // Still active: advance the heartbeat.
        shiftDao.update(open.copy(lastEventAt = now))
        return open.id
    }
}

/**
 * A seam the tracker calls to recompute aggregates after a trip closes, without `:capture` depending
 * on the rollup worker's module (`:sync`/`:app` provides the impl). Decoupled so the tracker stays a
 * pure-ish state machine that's unit-testable with a fake trigger. (B-039)
 */
fun interface RollupTrigger {
    /** Request an (idempotent) recompute of daily/weekly aggregates. Fire-and-forget. */
    fun requestRollup()
}
