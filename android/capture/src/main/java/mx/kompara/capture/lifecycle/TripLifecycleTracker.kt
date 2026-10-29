package mx.kompara.capture.lifecycle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mx.kompara.data.db.dao.OfferDao
import mx.kompara.data.db.dao.ShiftDao
import mx.kompara.data.db.dao.TripDao
import mx.kompara.data.db.entity.*
import mx.kompara.metrics.OfferRecorder
import mx.kompara.metrics.TripOffer
import javax.inject.Inject
import javax.inject.Singleton

/** Folds observed host evidence into durable lifecycle states. Advertised minutes never end a trip. */
@Singleton
class TripLifecycleTracker @Inject constructor(
    private val offerDao: OfferDao,
    private val tripDao: TripDao,
    private val shiftDao: ShiftDao,
    private val recorder: OfferRecorder,
    private val rollupTrigger: RollupTrigger,
    private val heuristics: TripStateHeuristics = TripStateHeuristics.DEFAULT,
) {
    private val mutex = Mutex()
    suspend fun collect(signals: Flow<LifecycleSignal>) { signals.collect { handle(it) } }

    suspend fun handle(signal: LifecycleSignal) = mutex.withLock {
        val platform = PackagePlatform.of(signal.packageName).name
        val now = signal.timestampMs
        val shiftId = ensureShift(now)
        when (signal) {
            is LifecycleSignal.OfferSeen -> {
                val card = signal.card
                val session = card.sessionId ?: "${signal.packageName}:$now"
                offerDao.retireOlderPending(platform, card.sessionStartedAt ?: now, now)
                val row = recorder.record(session, card.sessionStartedAt ?: now,
                    TripOffer(platform.lowercase(), card.fare, card.pickupDistanceKm, card.pickupEtaMin,
                        card.tripDistanceKm, card.tripDurationMin, card.paymentBasis), card.captureSource, card.surge, observedAt = now)
                offerDao.attachShift(row.id, shiftId)
                offerDao.touch(row.id, now)
            }
            is LifecycleSignal.TripStateEntered -> {
                val open = tripDao.latestOpen(platform)
                if (open != null) {
                    if (now >= open.lastObservedAt) tripDao.update(open.copy(lastObservedAt = now,
                        lifecycle = if (open.offerId == null) TripLifecycle.UNKNOWN.name
                            else if (signal.inProgress) TripLifecycle.IN_PROGRESS.name else open.lifecycle))
                } else {
                    val pending = offerDao.latestPending(platform)
                    val linked = pending?.takeIf { now - it.lastSeenAt in 0..heuristics.acceptWindowMs }
                    if (pending != null && linked == null && now >= pending.lastSeenAt) {
                        offerDao.resolvePending(pending.id, OfferOutcome.UNKNOWN.name, now,
                            lastSeenAtMost = now - heuristics.acceptWindowMs - 1)
                    }
                    tripDao.accept(TripEntity(offerId = linked?.id, shiftId = shiftId ?: linked?.shiftId,
                        startedAt = now, platform = platform, grossMxn = linked?.fareMxn,
                        distanceKm = linked?.distanceKm, durationMin = linked?.durationMin,
                        lifecycle = if (linked == null) TripLifecycle.UNKNOWN.name
                            else if (signal.inProgress) TripLifecycle.IN_PROGRESS.name else TripLifecycle.ACCEPTED_ESTIMATE.name,
                        paymentBasis = linked?.paymentBasis ?: "UNCONFIRMED"))
                    rollupTrigger.requestRollup()
                }
            }
            is LifecycleSignal.IdleStateEntered -> {
                resolvePending(platform, now, OfferOutcome.NOT_TAKEN)
                val open = tripDao.latestOpen(platform)
                if (open != null && now >= open.lastObservedAt) {
                    val noise = now - open.startedAt < heuristics.minTripDurationMs
                    tripDao.update(open.copy(endedAt = now, lastObservedAt = now,
                        lifecycle = if (noise || open.offerId == null) TripLifecycle.UNKNOWN.name else TripLifecycle.FINISHED.name))
                    if (noise) open.offerId?.let { tripDao.resolveOffer(it, OfferOutcome.UNKNOWN.name, false, now) }
                    rollupTrigger.requestRollup()
                }
            }
            is LifecycleSignal.TripCancelled -> {
                val open = tripDao.latestOpen(platform)
                if (open != null && tripDao.cancel(open.id, now, "OBSERVED_HOST")) rollupTrigger.requestRollup()
                resolvePending(platform, now, OfferOutcome.UNKNOWN)
            }
            is LifecycleSignal.ObservationLost -> {
                // Losing visibility is not evidence that a taken trip ended or was cancelled.
                // Retain a pending candidate until a subsequent observed state resolves it.
            }
        }
    }

    suspend fun cancelTrip(tripId: Long, at: Long): Boolean = mutex.withLock {
        tripDao.cancel(tripId, at).also { if (it) rollupTrigger.requestRollup() }
    }

    private suspend fun resolvePending(platform: String, now: Long, outcome: OfferOutcome) {
        val pending = offerDao.latestPending(platform) ?: return
        offerDao.resolvePending(pending.id, outcome.name, now)
    }

    private suspend fun ensureShift(now: Long): Long? {
        val open = shiftDao.latestOpen() ?: return shiftDao.insert(ShiftEntity(startedAt = now, lastEventAt = now))
        if (now < open.lastEventAt) return open.id
        if (now - open.lastEventAt >= ShiftEntity.INACTIVITY_GAP_MS) {
            shiftDao.update(open.copy(endedAt = open.lastEventAt))
            return shiftDao.insert(ShiftEntity(startedAt = now, lastEventAt = now))
        }
        shiftDao.update(open.copy(lastEventAt = now))
        return open.id
    }
}

fun interface RollupTrigger { fun requestRollup() }
