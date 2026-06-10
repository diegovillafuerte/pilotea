package mx.kompara.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.kompara.data.db.entity.DailyAggregateEntity
import mx.kompara.data.db.entity.WeeklyAggregateEntity

/**
 * Persistence for the day/week rollup tables (B-039).
 *
 * Writes use REPLACE on the composite `(platform, period, source)` key so recomputing a CAPTURED
 * bucket overwrites only that exact row. **The upsert helpers below are deliberately scoped to
 * `source = CAPTURED`**: nothing here ever touches an IMPORTED row, upholding the reconciliation
 * contract in [mx.kompara.data.db.entity.AggregateSource]. (B-045's import path owns IMPORTED rows.)
 */
@Dao
interface AggregateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeekly(rows: List<WeeklyAggregateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDaily(rows: List<DailyAggregateEntity>)

    @Query("SELECT * FROM weekly_aggregates ORDER BY weekStart DESC, platform ASC")
    fun observeWeekly(): Flow<List<WeeklyAggregateEntity>>

    @Query("SELECT * FROM daily_aggregates ORDER BY day DESC, platform ASC")
    fun observeDaily(): Flow<List<DailyAggregateEntity>>

    /** Captured weekly rows for one Monday-anchored week (one per active platform). */
    @Query(
        "SELECT * FROM weekly_aggregates WHERE weekStart = :weekStart AND source = 'CAPTURED' " +
            "ORDER BY platform ASC",
    )
    suspend fun capturedWeek(weekStart: String): List<WeeklyAggregateEntity>

    /**
     * All weekly rows ordered oldest→newest, for the streak calculator. Includes every source so a
     * streak counts both captured and imported weeks that have data.
     */
    @Query("SELECT * FROM weekly_aggregates ORDER BY weekStart ASC")
    suspend fun allWeekly(): List<WeeklyAggregateEntity>

    /** Delete the captured daily rows for a day so a recompute replaces (not accumulates) them. */
    @Query("DELETE FROM daily_aggregates WHERE day = :day AND source = 'CAPTURED'")
    suspend fun deleteCapturedDay(day: String)

    /** Delete the captured weekly rows for a week before rewriting them. */
    @Query("DELETE FROM weekly_aggregates WHERE weekStart = :weekStart AND source = 'CAPTURED'")
    suspend fun deleteCapturedWeek(weekStart: String)
}
