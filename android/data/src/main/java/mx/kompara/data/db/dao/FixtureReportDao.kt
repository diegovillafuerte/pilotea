package mx.kompara.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import mx.kompara.data.db.entity.FixtureReportEntity

/**
 * DAO for [FixtureReportEntity] — store-and-forward queue for consented,
 * PII-scrubbed fixture reports awaiting upload.
 */
@Dao
interface FixtureReportDao {

    @Insert
    suspend fun insert(report: FixtureReportEntity): Long

    /** Oldest-first batch the uploader sends, capped by [limit]. */
    @Query("SELECT * FROM fixture_reports ORDER BY createdAt ASC LIMIT :limit")
    suspend fun oldest(limit: Int): List<FixtureReportEntity>

    @Query("SELECT COUNT(*) FROM fixture_reports")
    suspend fun count(): Int

    @Delete
    suspend fun delete(report: FixtureReportEntity)

    @Query("DELETE FROM fixture_reports WHERE id = :id")
    suspend fun deleteById(id: Long)
}
