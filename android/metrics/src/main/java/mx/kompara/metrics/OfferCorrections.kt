package mx.kompara.metrics

import androidx.room.withTransaction
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.metrics.rollup.RollupRecomputer
import java.io.Serializable
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** Narrow undo receipt: restores lifecycle only, never old economics over newer payment evidence. */
data class CancellationReceipt(
    val tripId: Long, val offerId: Long, val cancelledAt: Long, val lifecycle: String,
    val endedAt: Long?, val observedAt: Long, val resolvedAt: Long?, val startedAt: Long,
) : Serializable

class OfferCorrections @Inject constructor(
    private val database: KomparaDatabase,
    private val rollups: RollupRecomputer,
    private val zone: ZoneId,
) {
    suspend fun cancel(offerId: Long, at: Long): CancellationReceipt? = database.withTransaction {
        val offer = database.offerDao().findById(offerId) ?: return@withTransaction null
        val trip = database.tripDao().forOffer(offerId) ?: return@withTransaction null
        if (offer.outcome != "ACCEPTED" || trip.lifecycle !in setOf("ACCEPTED_ESTIMATE", "IN_PROGRESS", "FINISHED")) return@withTransaction null
        if (!database.tripDao().cancel(trip.id, at)) return@withTransaction null
        rollups.recomputeWeek(Instant.ofEpochMilli(trip.startedAt).atZone(zone).toLocalDate())
        CancellationReceipt(trip.id, offer.id, at, trip.lifecycle, trip.endedAt, trip.lastObservedAt, offer.resolvedAt, trip.startedAt)
    }

    suspend fun undo(receipt: CancellationReceipt): Boolean = database.withTransaction {
        val trip = database.tripDao().findById(receipt.tripId) ?: return@withTransaction false
        val offer = database.offerDao().findById(receipt.offerId) ?: return@withTransaction false
        if (trip.offerId != offer.id || trip.lifecycle != "CANCELLED" || trip.cancellationSource != "USER" ||
            trip.lastObservedAt != receipt.cancelledAt || offer.outcome != "CANCELLED" || offer.resolvedAt != receipt.cancelledAt) return@withTransaction false
        database.tripDao().update(trip.copy(lifecycle = receipt.lifecycle, endedAt = receipt.endedAt,
            lastObservedAt = receipt.observedAt, cancellationSource = null))
        database.offerDao().update(offer.copy(outcome = "ACCEPTED", accepted = true, resolvedAt = receipt.resolvedAt))
        rollups.recomputeWeek(Instant.ofEpochMilli(receipt.startedAt).atZone(zone).toLocalDate())
        true
    }
}
