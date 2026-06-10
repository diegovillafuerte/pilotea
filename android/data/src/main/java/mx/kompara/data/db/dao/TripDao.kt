package mx.kompara.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.kompara.data.db.entity.TripEntity

@Dao
interface TripDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trip: TripEntity): Long

    @Query("SELECT * FROM trips ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE startedAt >= :since ORDER BY startedAt DESC")
    fun observeSince(since: Long): Flow<List<TripEntity>>

    @Query("SELECT COALESCE(SUM(grossMxn), 0.0) FROM trips WHERE startedAt >= :since")
    fun observeGrossSince(since: Long): Flow<Double>
}
