package mx.kompara.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import mx.kompara.data.db.entity.OfferPresentationEntity

import kotlinx.coroutines.flow.Flow

data class SuppressedOffer(val offerId: Long, val seenAt: Long, val cause: String?)

@Dao
interface OfferPresentationDao {
    @Query("SELECT COUNT(*) FROM offer_presentations p JOIN offers o ON o.sessionId = p.sessionId WHERE o.seenAt >= :from AND o.seenAt < :until AND p.outcome = 'SUPPRESSED'")
    fun observeSuppressedCount(from: Long, until: Long): Flow<Int>

    @Query("SELECT o.id AS offerId, o.seenAt, p.cause FROM offer_presentations p JOIN offers o ON o.sessionId = p.sessionId WHERE o.seenAt >= :from AND o.seenAt < :until AND p.outcome = 'SUPPRESSED' ORDER BY o.seenAt DESC")
    fun observeSuppressedOffers(from: Long, until: Long): Flow<List<SuppressedOffer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun record(presentation: OfferPresentationEntity)

    @Query("SELECT * FROM offer_presentations WHERE sessionId = :sessionId")
    suspend fun forSession(sessionId: String): OfferPresentationEntity?

    @Query("SELECT p.* FROM offer_presentations p JOIN offers o ON o.sessionId = p.sessionId WHERE o.seenAt >= :from AND o.seenAt < :until")
    suspend fun seenBetween(from: Long, until: Long): List<OfferPresentationEntity>
}
