package mx.kompara.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.kompara.data.db.entity.ShiftEntity

@Dao
interface ShiftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shift: ShiftEntity): Long

    @Update
    suspend fun update(shift: ShiftEntity)

    @Query("SELECT * FROM shifts ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<ShiftEntity?>
}
