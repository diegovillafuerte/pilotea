package mx.kompara.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import mx.kompara.data.db.entity.TelemetryCounterEntity

/**
 * DAO for [TelemetryCounterEntity]. The hot path is [increment]: an
 * upsert-by-increment keyed on the composite PK so the collector can fold many
 * parse outcomes into a handful of rows without read-modify-write races.
 */
@Dao
interface TelemetryCounterDao {

    /**
     * Atomically add the given deltas to the counter for
     * `(hostPackage, hostVersionCode, specVersion, variant, dayUtc)`, inserting
     * a fresh row when none exists. Runs in a transaction so the insert + update
     * pair is consistent under concurrent collectors.
     */
    @Transaction
    suspend fun increment(
        hostPackage: String,
        hostVersionCode: Long,
        specVersion: Int,
        variant: String,
        dayUtc: String,
        attempts: Long = 0,
        successes: Long = 0,
        failures: Long = 0,
    ) {
        // Ensure the row exists (no-op on conflict), then add the deltas.
        insertIgnore(
            TelemetryCounterEntity(
                hostPackage = hostPackage,
                hostVersionCode = hostVersionCode,
                specVersion = specVersion,
                variant = variant,
                dayUtc = dayUtc,
            ),
        )
        addDeltas(
            hostPackage = hostPackage,
            hostVersionCode = hostVersionCode,
            specVersion = specVersion,
            variant = variant,
            dayUtc = dayUtc,
            attempts = attempts,
            successes = successes,
            failures = failures,
        )
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(row: TelemetryCounterEntity)

    @Query(
        """
        UPDATE telemetry_counters
        SET attempts = attempts + :attempts,
            successes = successes + :successes,
            failures = failures + :failures
        WHERE hostPackage = :hostPackage
          AND hostVersionCode = :hostVersionCode
          AND specVersion = :specVersion
          AND variant = :variant
          AND dayUtc = :dayUtc
        """,
    )
    suspend fun addDeltas(
        hostPackage: String,
        hostVersionCode: Long,
        specVersion: Int,
        variant: String,
        dayUtc: String,
        attempts: Long,
        successes: Long,
        failures: Long,
    )

    /** All accumulated counters, oldest day first — the uploader batches these. */
    @Query("SELECT * FROM telemetry_counters ORDER BY dayUtc ASC")
    suspend fun all(): List<TelemetryCounterEntity>

    /** Total number of queued counter rows (used to decide whether to flush). */
    @Query("SELECT COUNT(*) FROM telemetry_counters")
    suspend fun count(): Int

    /**
     * Delete a specific counter only if its values are unchanged since it was
     * uploaded — guards against dropping increments that arrived during the
     * in-flight upload. Returns the number of rows deleted (0 if it changed).
     */
    @Query(
        """
        DELETE FROM telemetry_counters
        WHERE hostPackage = :hostPackage
          AND hostVersionCode = :hostVersionCode
          AND specVersion = :specVersion
          AND variant = :variant
          AND dayUtc = :dayUtc
          AND attempts = :attempts
          AND successes = :successes
          AND failures = :failures
        """,
    )
    suspend fun deleteIfUnchanged(
        hostPackage: String,
        hostVersionCode: Long,
        specVersion: Int,
        variant: String,
        dayUtc: String,
        attempts: Long,
        successes: Long,
        failures: Long,
    ): Int

    /** Delete a batch on ack, each row only if unchanged since upload. */
    @Transaction
    suspend fun deleteAcked(rows: List<TelemetryCounterEntity>) {
        for (row in rows) {
            deleteIfUnchanged(
                hostPackage = row.hostPackage,
                hostVersionCode = row.hostVersionCode,
                specVersion = row.specVersion,
                variant = row.variant,
                dayUtc = row.dayUtc,
                attempts = row.attempts,
                successes = row.successes,
                failures = row.failures,
            )
        }
    }
}
