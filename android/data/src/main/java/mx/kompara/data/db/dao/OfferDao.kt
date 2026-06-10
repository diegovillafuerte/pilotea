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
}
