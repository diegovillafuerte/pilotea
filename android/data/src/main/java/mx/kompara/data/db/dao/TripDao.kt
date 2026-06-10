package mx.kompara.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.kompara.data.db.entity.TripEntity

@Dao
interface TripDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun findById(id: Long): TripEntity?

    @Query("SELECT * FROM trips ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE startedAt >= :since ORDER BY startedAt DESC")
    fun observeSince(since: Long): Flow<List<TripEntity>>

    @Query("SELECT COALESCE(SUM(grossMxn), 0.0) FROM trips WHERE startedAt >= :since")
    fun observeGrossSince(since: Long): Flow<Double>

    /** The most recent still-open trip (endedAt IS NULL) — closed by the trip-end heuristic (B-039). */
    @Query("SELECT * FROM trips WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestOpen(): TripEntity?

    /**
     * Completed trips (endedAt set) that *started* in `[from, until)`. The rollup buckets a trip by
     * its start day/week so a trip that spans midnight lands in one bucket deterministically.
     */
    @Query(
        "SELECT * FROM trips WHERE endedAt IS NOT NULL AND startedAt >= :from AND startedAt < :until " +
            "ORDER BY startedAt ASC",
    )
    suspend fun completedStartedBetween(from: Long, until: Long): List<TripEntity>

    /**
     * Reactive twin of [completedStartedBetween] — completed trips that started in `[from, until)`,
     * recomputed as trips close. Backs the recommendations engine's "tus mejores horas" rule (B-048),
     * which buckets the current week's trips by (day-of-week, hour).
     */
    @Query(
        "SELECT * FROM trips WHERE endedAt IS NOT NULL AND startedAt >= :from AND startedAt < :until " +
            "ORDER BY startedAt ASC",
    )
    fun observeCompletedStartedBetween(from: Long, until: Long): Flow<List<TripEntity>>
}
