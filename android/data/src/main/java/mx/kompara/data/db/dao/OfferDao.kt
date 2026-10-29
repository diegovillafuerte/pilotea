package mx.kompara.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.kompara.data.db.entity.OfferEntity

@Dao
interface OfferDao {

    @Query("SELECT EXISTS(SELECT 1 FROM offers WHERE seenAt < :before)")
    suspend fun hasSeenBefore(before: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(offer: OfferEntity): Long

    @Query("SELECT * FROM offers WHERE sessionId = :sessionId")
    suspend fun forSession(sessionId: String): OfferEntity?

    @Query("UPDATE offers SET lastSeenAt = MAX(lastSeenAt, :at) WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("UPDATE offers SET shiftId = :shiftId WHERE id = :id AND shiftId IS NULL")
    suspend fun attachShift(id: Long, shiftId: Long?)

    @Query("UPDATE offers SET outcome = 'UNKNOWN', resolvedAt = :at WHERE platform = :platform AND outcome = 'PENDING' AND seenAt < :before AND lastSeenAt <= :at")
    suspend fun retireOlderPending(platform: String, before: Long, at: Long)

    @Update
    suspend fun update(offer: OfferEntity)

    @Query("SELECT * FROM offers ORDER BY seenAt DESC")
    fun observeAll(): Flow<List<OfferEntity>>

    @Query("SELECT * FROM offers WHERE platform = :platform ORDER BY seenAt DESC")
    fun observeByPlatform(platform: String): Flow<List<OfferEntity>>

    @Query("SELECT * FROM offers WHERE id = :id")
    suspend fun findById(id: Long): OfferEntity?

    /**
     * The most recent still-PENDING offer — the candidate the lifecycle state machine resolves when a
     * card disappears (B-039). One pending offer at a time is the norm (cards are modal).
     */
    @Query("SELECT * FROM offers WHERE outcome = 'PENDING' AND (:platform IS NULL OR platform = :platform) ORDER BY seenAt DESC LIMIT 1")
    suspend fun latestPending(platform: String? = null): OfferEntity?

    /** All offers seen in `[from, until)` (used by the rollup acceptance-rate math). */
    @Query("SELECT * FROM offers WHERE seenAt >= :from AND seenAt < :until ORDER BY seenAt ASC")
    suspend fun seenBetween(from: Long, until: Long): List<OfferEntity>

    /**
     * Reactive twin of [seenBetween] — offers seen in `[from, until)`, recomputed as new offers land.
     * Backs the recommendations engine's capture-powered rules (B-048), which need the current week's
     * offers (verdicts + outcomes) to count missed-good offers and acceptance behaviour.
     */
    @Query("SELECT * FROM offers WHERE seenAt >= :from AND seenAt < :until ORDER BY seenAt ASC")
    fun observeSeenBetween(from: Long, until: Long): Flow<List<OfferEntity>>
}
