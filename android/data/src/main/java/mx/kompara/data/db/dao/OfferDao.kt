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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(offer: OfferEntity): Long

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
    @Query("SELECT * FROM offers WHERE outcome = 'PENDING' ORDER BY seenAt DESC LIMIT 1")
    suspend fun latestPending(): OfferEntity?

    /** All offers seen in `[from, until)` (used by the rollup acceptance-rate math). */
    @Query("SELECT * FROM offers WHERE seenAt >= :from AND seenAt < :until ORDER BY seenAt ASC")
    suspend fun seenBetween(from: Long, until: Long): List<OfferEntity>
}
