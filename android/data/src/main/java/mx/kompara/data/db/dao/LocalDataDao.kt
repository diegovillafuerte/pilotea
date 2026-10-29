package mx.kompara.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/** Local history only. Account credentials, costs and preferences have separate owners. */
@Dao
abstract class LocalDataDao {
    @Query("DELETE FROM offer_presentations") abstract suspend fun presentations()
    @Query("DELETE FROM offers") abstract suspend fun offers()
    @Query("DELETE FROM trips") abstract suspend fun trips()
    @Query("DELETE FROM shifts") abstract suspend fun shifts()
    @Query("DELETE FROM daily_aggregates") abstract suspend fun days()
    @Query("DELETE FROM weekly_aggregates") abstract suspend fun weeks()
    @Query("DELETE FROM population_stats") abstract suspend fun benchmarks()
    @Query("DELETE FROM telemetry_counters") abstract suspend fun counters()
    @Query("DELETE FROM fixture_reports") abstract suspend fun reports()
    @Query("DELETE FROM import_drafts") abstract suspend fun importDrafts()
    @Query("DELETE FROM import_files") abstract suspend fun importFiles()
    @Query("DELETE FROM import_statements") abstract suspend fun importStatements()
    @Query("DELETE FROM import_selections") abstract suspend fun importSelections()
    @Transaction open suspend fun eraseHistory() {
        importSelections(); importStatements(); importFiles(); importDrafts();
        presentations(); offers(); trips(); shifts(); days(); weeks(); benchmarks(); counters(); reports()
    }
}
