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

    /** Captured daily rows for one local day (one per active platform). Backs the day-detail screen. */
    @Query(
        "SELECT * FROM daily_aggregates WHERE day = :day AND source = 'CAPTURED' " +
            "ORDER BY platform ASC",
    )
    suspend fun capturedDay(day: String): List<DailyAggregateEntity>

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

    // ─── B-043 consented aggregate sync ───────────────────────────────────────────────────────

    /**
     * Weekly rows that need uploading (B-043): never synced, or recomputed since the last sync.
     * "Dirty" = `lastSyncedAt IS NULL OR lastSyncedAt < computedAt`. Returns BOTH sources (captured
     * and imported) so the population data reflects every consented week the driver has; the
     * uploader marks each row's source on the wire. Oldest week first for stable, debuggable order.
     */
    @Query(
        "SELECT * FROM weekly_aggregates " +
            "WHERE lastSyncedAt IS NULL OR lastSyncedAt < computedAt " +
            "ORDER BY weekStart ASC, platform ASC",
    )
    suspend fun dirtyForSync(): List<WeeklyAggregateEntity>

    /**
     * Mark a row synced by stamping [syncedAt] on its primary key. Uses the row's own [computedAt]
     * snapshot is irrelevant here — the worker passes the time it observed the row, and the dirty
     * query compares against [WeeklyAggregateEntity.computedAt], so a row recomputed after upload is
     * re-queued. Scoped to the exact `(platform, weekStart, source)` key so a concurrent recompute of
     * a different row is untouched.
     */
    @Query(
        "UPDATE weekly_aggregates SET lastSyncedAt = :syncedAt " +
            "WHERE platform = :platform AND weekStart = :weekStart AND source = :source",
    )
    suspend fun markSynced(platform: String, weekStart: String, source: String, syncedAt: Long)
}
