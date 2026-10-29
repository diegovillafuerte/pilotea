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
            val enriched = existing.enrichMissingInputs(offer, observedAt)
            val updated = if (saved != null && enriched != existing) {
                val unit = PreferredMetric.valueOf(saved.unit)
                val goal = saved.goal.toDouble()
                val minimum = saved.minimum.toDouble()
                val threshold = PlatformThreshold(goal, goal, minimum, minimum)
                val version = costs.runningVersions().firstOrNull { it.id == saved.costVersionId }
                val result = engine.evaluate(TripOffer(offer.platform, enriched.fareMxn, enriched.pickupKm,
                    enriched.pickupMin, enriched.tripKm, enriched.tripMin, enriched.paymentBasis),
                    version?.inputs?.costPerKm, threshold, unit)
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
        val result = engine.evaluate(offer, version?.inputs?.costPerKm, saved.effectiveThreshold, saved.preferredMetric)
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
                result.perHourDecimal, result.verdict.unavailableReason))
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
    val missing = listOfNotNull(if (fareMxn == null) "fareMxn" else null, if (pickupKm == null) "pickupKm" else null,
        if (tripKm == null) "tripKm" else null, if (pickupMin == null) "pickupMin" else null, if (tripMin == null) "tripMin" else null)
    val verdict = Verdict(VerdictLevel.valueOf(requireNotNull(verdict)), km, hour, net, grossKm,
        missing, preferredMetric = PreferredMetric.valueOf(saved.unit), unavailableReason = saved.unavailableReason)
    return OfferMetrics(fareMxn, net, distanceKm, durationMin, grossKm, grossMin, km, hour?.div(60), hour, verdict,
        saved.contribution, saved.perKm, saved.perHour)
}

/** Fill unread fields while the same offer is pending; never replace already captured economics. */
internal fun OfferEntity.enrichMissingInputs(incoming: TripOffer, observedAt: Long): OfferEntity {
    if (outcome != "PENDING" || observedAt < lastSeenAt || platform != incoming.platform.uppercase()) return this
    val pairs = listOf(fareMxn to incoming.fareMxn, pickupKm to incoming.pickupKm, tripKm to incoming.tripKm,
        pickupMin to incoming.pickupMin, tripMin to incoming.tripMin)
    if (pairs.any { (old, fresh) -> old != null && fresh != null && old != fresh }) return this
    fun readable(value: Double?) = value?.takeIf { it.isFinite() && it >= 0 }
    return copy(fareMxn = fareMxn ?: readable(incoming.fareMxn), pickupKm = pickupKm ?: readable(incoming.pickupKm),
        tripKm = tripKm ?: readable(incoming.tripKm), pickupMin = pickupMin ?: readable(incoming.pickupMin),
        tripMin = tripMin ?: readable(incoming.tripMin),
        paymentBasis = if (paymentBasis == "UNCONFIRMED" && incoming.paymentBasis == "DRIVER_PAYMENT")
            "DRIVER_PAYMENT" else paymentBasis)
}
