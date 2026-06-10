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

    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun findById(id: Long): ShiftEntity?

    @Query("SELECT * FROM shifts ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<ShiftEntity?>

    /** The currently-open shift, if any (endedAt IS NULL). Snapshot read for the lifecycle tracker. */
    @Query("SELECT * FROM shifts WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestOpen(): ShiftEntity?

    /**
     * Shifts whose active window overlaps `[from, until)` — i.e. that started before `until` and
     * (are still open OR ended after `from`). Used to attribute online hours to a day/week bucket.
     */
    @Query(
        "SELECT * FROM shifts WHERE startedAt < :until AND (endedAt IS NULL OR endedAt > :from) " +
            "ORDER BY startedAt ASC",
    )
    suspend fun overlapping(from: Long, until: Long): List<ShiftEntity>
}
