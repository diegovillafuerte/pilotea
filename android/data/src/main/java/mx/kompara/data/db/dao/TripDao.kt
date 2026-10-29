package mx.kompara.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import mx.kompara.data.db.entity.TripEntity

@Dao
interface TripDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trip: TripEntity): Long

    @Query("SELECT * FROM trips WHERE offerId = :offerId ORDER BY id LIMIT 1")
    suspend fun forOffer(offerId: Long): TripEntity?

    @Query("UPDATE offers SET outcome = :outcome, accepted = :accepted, resolvedAt = :at WHERE id = :id")
    suspend fun resolveOffer(id: Long, outcome: String, accepted: Boolean, at: Long)

    @Transaction
    suspend fun accept(trip: TripEntity): Long {
        val offerId = trip.offerId
        if (offerId != null) {
            forOffer(offerId)?.let { return it.id }
            resolveOffer(offerId, "ACCEPTED", true, trip.startedAt)
        }
        return insert(trip)
    }

    /** An explicit correction affects only this trip and keeps all captured economics for audit. */
    @Transaction
    suspend fun cancel(id: Long, at: Long, source: String = "USER"): Boolean {
        val trip = findById(id) ?: return false
        if (at < trip.lastObservedAt || trip.lifecycle == "CANCELLED") return false
        update(trip.copy(lifecycle = "CANCELLED", endedAt = at, lastObservedAt = at, cancellationSource = source))
        trip.offerId?.let { resolveOffer(it, "CANCELLED", false, at) }
        return true
    }

    @Transaction
    suspend fun confirmItemPayment(evidence: mx.kompara.data.db.entity.ItemizedPaymentEvidence): Boolean {
        val trip = findById(evidence.tripId) ?: return false
        if (trip.lifecycle != "FINISHED" || evidence.confirmedAt < trip.lastObservedAt) return false
        update(trip.copy(grossMxn = evidence.paymentMxn, paymentBasis = "DRIVER_PAYMENT", estimated = false,
            confirmationSource = "ITEMIZED_PAYMENT", confirmationEvidenceId = evidence.sourceRecordId,
            confirmedAt = evidence.confirmedAt))
        return true
    }

    @Update
    suspend fun update(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun findById(id: Long): TripEntity?

    @Query("SELECT * FROM trips ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE startedAt >= :since ORDER BY startedAt DESC")
    fun observeSince(since: Long): Flow<List<TripEntity>>

    @Query("SELECT COALESCE(SUM(grossMxn), 0.0) FROM trips WHERE startedAt >= :since AND lifecycle IN ('ACCEPTED_ESTIMATE','IN_PROGRESS','FINISHED') AND offerId IS NOT NULL AND paymentBasis = 'DRIVER_PAYMENT'")
    fun observeGrossSince(since: Long): Flow<Double>

    /** The most recent still-open trip (endedAt IS NULL) — closed by the trip-end heuristic (B-039). */
    @Query("SELECT * FROM trips WHERE endedAt IS NULL AND lifecycle IN ('ACCEPTED_ESTIMATE', 'IN_PROGRESS', 'UNKNOWN') AND (:platform IS NULL OR platform = :platform) ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestOpen(platform: String? = null): TripEntity?

    @Query("SELECT * FROM trips WHERE startedAt >= :from AND startedAt < :until ORDER BY startedAt ASC")
    suspend fun startedBetween(from: Long, until: Long): List<TripEntity>

    /**
     * Completed trips (endedAt set) that *started* in `[from, until)`. The rollup buckets a trip by
     * its start day/week so a trip that spans midnight lands in one bucket deterministically.
     */
    @Query(
        "SELECT * FROM trips WHERE endedAt IS NOT NULL AND lifecycle = 'FINISHED' AND startedAt >= :from AND startedAt < :until " +
            "ORDER BY startedAt ASC",
    )
    suspend fun completedStartedBetween(from: Long, until: Long): List<TripEntity>

    /**
     * Reactive twin of [completedStartedBetween] — completed trips that started in `[from, until)`,
     * recomputed as trips close. Backs the recommendations engine's "tus mejores horas" rule (B-048),
     * which buckets the current week's trips by (day-of-week, hour).
     */
    @Query(
        "SELECT * FROM trips WHERE endedAt IS NOT NULL AND lifecycle = 'FINISHED' AND startedAt >= :from AND startedAt < :until " +
            "ORDER BY startedAt ASC",
    )
    fun observeCompletedStartedBetween(from: Long, until: Long): Flow<List<TripEntity>>
}
