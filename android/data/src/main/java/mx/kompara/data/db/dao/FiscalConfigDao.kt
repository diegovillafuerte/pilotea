package mx.kompara.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.kompara.data.db.entity.FiscalConfigEntity

/**
 * Persistence for the cached fiscal config (B-051). One row per fiscal year; reads always take the
 * highest-year row (the latest values the backend has published). REPLACE on the `year` key so a
 * re-fetch of the same year overwrites in place.
 */
@Dao
interface FiscalConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: FiscalConfigEntity)

    /** The latest cached fiscal config (highest year), or null before the first fetch. */
    @Query("SELECT * FROM fiscal_config ORDER BY year DESC LIMIT 1")
    suspend fun latest(): FiscalConfigEntity?

    /** Reactive latest config, so the Fiscal tab re-renders when a fresh year is cached. */
    @Query("SELECT * FROM fiscal_config ORDER BY year DESC LIMIT 1")
    fun observeLatest(): Flow<FiscalConfigEntity?>
}
