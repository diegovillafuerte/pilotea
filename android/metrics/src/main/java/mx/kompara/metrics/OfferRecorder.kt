package mx.kompara.metrics

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mx.kompara.data.accounting.CostSchedule
import mx.kompara.data.db.dao.OfferDao
import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.db.entity.OfferEvaluation
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.RateBasis
import javax.inject.Inject
import javax.inject.Singleton

/** One judgement for both ledger and overlay, serialized and durable across process restarts. */
@Singleton
class OfferRecorder @Inject constructor(
    private val offers: OfferDao,
    private val costs: CostProfileRepository,
    private val settings: SettingsRepository,
    private val engine: NetProfitEngine,
) {
    private val mutex = Mutex()

    suspend fun record(sessionId: String, seenAt: Long, offer: TripOffer, source: String, surge: Boolean, observedAt: Long = seenAt): OfferEntity = mutex.withLock {
        offers.forSession(sessionId)?.let { existing ->
            val saved = existing.evaluation
            val enriched = existing.enrichMissingInputs(offer, observedAt,
                allowOcrRevision = source == "OCR" && saved?.source == "OCR")
            val updated = if (saved != null && enriched != existing) {
                val unit = PreferredMetric.valueOf(saved.unit)
                val goal = saved.goal.toDouble()
                val minimum = saved.minimum.toDouble()
                val threshold = PlatformThreshold(goal, goal, minimum, minimum)
                val version = costs.runningVersions().firstOrNull { it.id == saved.costVersionId }
                val result = engine.evaluate(TripOffer(offer.platform, enriched.fareMxn, enriched.pickupKm,
                    enriched.pickupMin, enriched.tripKm, enriched.tripMin, enriched.paymentBasis),
                    version?.inputs?.costPerKm, threshold, unit, RateBasis.fromName(saved.rateBasis))
                enriched.copy(distanceKm = result.totalKm, durationMin = result.totalMin,
                    verdict = result.verdict.level.name, evaluation = saved.copy(
                        paymentBasis = enriched.paymentBasis, contribution = result.contributionDecimal,
                        perKm = result.perKmDecimal, perHour = result.perHourDecimal,
                        unavailableReason = result.verdict.unavailableReason))
                    .let { offers.enrichPending(existing, it) }
            } else existing
            offers.touch(existing.id, observedAt)
            return@withLock updated.copy(lastSeenAt = maxOf(updated.lastSeenAt, observedAt))
        }
        val saved = settings.settings.first()
        val version = CostSchedule(costs.runningVersions()).runningAt(seenAt)
        val result = engine.evaluate(offer, version?.inputs?.costPerKm, saved.effectiveThreshold, saved.preferredMetric, saved.rateBasis)
        val threshold = saved.effectiveThreshold
        val km = saved.preferredMetric == PreferredMetric.IPK
        val row = OfferEntity(seenAt = seenAt, platform = offer.platform.uppercase(), fareMxn = offer.fareMxn,
            distanceKm = result.totalKm, durationMin = result.totalMin, pickupKm = offer.pickupKm,
            tripKm = offer.tripKm, pickupMin = offer.pickupMin, tripMin = offer.tripMin, surge = surge,
            verdict = result.verdict.level.name, sessionId = sessionId, lastSeenAt = observedAt, paymentBasis = offer.paymentBasis,
            evaluation = OfferEvaluation(seenAt, saved.preferredMetric.name,
                (if (km) threshold.minPerKmMxn else threshold.minPerHourMxn).toBigDecimal().toPlainString(),
                (if (km) threshold.redPerKmMxn else threshold.redPerHourMxn).toBigDecimal().toPlainString(),
                version?.id, source, offer.paymentBasis, result.contributionDecimal, result.perKmDecimal,
                result.perHourDecimal, result.verdict.unavailableReason, saved.rateBasis.name))
        val id = offers.insert(row)
        if (id == -1L) requireNotNull(offers.forSession(sessionId)) else row.copy(id = id)
    }
}

/** Rehydrate the frozen evaluation; never run old offers through today's limits or costs. */
fun OfferEntity.recordedMetrics(): OfferMetrics {
    val saved = requireNotNull(evaluation)
    val km = saved.perKm?.toDouble()
    val hour = saved.perHour?.toDouble()
    val net = saved.contribution?.toDouble()
    val grossKm = distanceKm?.takeIf { it > 0 }?.let { fareMxn?.div(it) }
    val grossMin = durationMin?.takeIf { it > 0 }?.let { fareMxn?.div(it) }
    val grossHour = durationMin?.takeIf { it > 0 }?.let { fareMxn?.times(60)?.div(it) }
    val missing = listOfNotNull(if (fareMxn == null) "fareMxn" else null, if (pickupKm == null) "pickupKm" else null,
        if (tripKm == null) "tripKm" else null, if (pickupMin == null) "pickupMin" else null, if (tripMin == null) "tripMin" else null)
    val verdict = Verdict(VerdictLevel.valueOf(requireNotNull(verdict)), km, hour, net, grossKm,
        missing, preferredMetric = PreferredMetric.valueOf(saved.unit), unavailableReason = saved.unavailableReason,
        rateBasis = RateBasis.fromName(saved.rateBasis))
    // The legs' decimal sum, as the engine added them; the Double total only for rows without both legs.
    fun legs(a: Double?, b: Double?, total: Double?) = (a?.toBigDecimal()?.let { b?.toBigDecimal()?.add(it) } ?: total?.toBigDecimal())?.toPlainString()
    return OfferMetrics(fareMxn, net, distanceKm, durationMin, grossKm, grossMin, km, hour?.div(60), hour, verdict,
        saved.contribution, saved.perKm, saved.perHour, grossHour, legs(pickupKm, tripKm, distanceKm), legs(pickupMin, tripMin, durationMin))
}

/**
 * Fill unread inputs, keyed on the TRIP leg (the offer identity): a different trip leg is another
 * offer (e.g. the next card's pending frame, still stamped with this session) and is refused whole.
 * Only a STABILIZER-CONFIRMED observation revises: on the OCR path a payment-bearing card (driver
 * payment with a valid fare and complete legs) is FareStabilizer's output, which carries only the
 * first reveal or a revision two consecutive agreeing frames confirmed, for the fare AND the pickup
 * leg (round 4 policy: a single frame never revises the row, whatever the outlier guard said). Such
 * a newer observation of the same complete trip leg revises the fare and takes its (confirmed)
 * pickup, which may change live. A payment-less observation, a single guard-blind frame included,
 * only fills what is missing and never overwrites, so the row's economics are confirmed ones.
 */
internal fun OfferEntity.enrichMissingInputs(incoming: TripOffer, observedAt: Long, allowOcrRevision: Boolean = false): OfferEntity {
    if (outcome != "PENDING" || observedAt < lastSeenAt || platform != incoming.platform.uppercase()) return this
    fun known(value: Double?) = value != null && value.isFinite() && value >= 0
    val trip = listOf(tripKm to incoming.tripKm, tripMin to incoming.tripMin)
    val confirmed = allowOcrRevision && observedAt > lastSeenAt &&
        trip.all { (old, fresh) -> known(old) && old == fresh } &&
        known(incoming.pickupKm) && known(incoming.pickupMin) &&
        incoming.paymentBasis == "DRIVER_PAYMENT" && incoming.fareMxn?.let { it.isFinite() && it > 0 } == true
    val reviseFare = confirmed && paymentBasis == "DRIVER_PAYMENT"
    val pairs = trip + (if (confirmed) emptyList() else listOf(pickupKm to incoming.pickupKm, pickupMin to incoming.pickupMin)) +
        (if (reviseFare) emptyList() else listOf(fareMxn to incoming.fareMxn))
    if (pairs.any { (old, fresh) -> old != null && fresh != null && old != fresh }) return this
    fun readable(value: Double?) = value?.takeIf { it.isFinite() && it >= 0 }
    return copy(fareMxn = if (reviseFare) incoming.fareMxn else fareMxn ?: readable(incoming.fareMxn),
        pickupKm = if (confirmed) incoming.pickupKm else pickupKm ?: readable(incoming.pickupKm),
        tripKm = tripKm ?: readable(incoming.tripKm),
        pickupMin = if (confirmed) incoming.pickupMin else pickupMin ?: readable(incoming.pickupMin),
        tripMin = tripMin ?: readable(incoming.tripMin),
        paymentBasis = if (paymentBasis == "UNCONFIRMED" && incoming.paymentBasis == "DRIVER_PAYMENT")
            "DRIVER_PAYMENT" else paymentBasis)
}
