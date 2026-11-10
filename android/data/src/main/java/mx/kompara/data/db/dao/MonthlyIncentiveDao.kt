package mx.kompara.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import mx.kompara.data.db.entity.MonthlyIncentiveEntity

@Dao
interface MonthlyIncentiveDao {
    @Upsert suspend fun put(incentive: MonthlyIncentiveEntity)
    @Query("DELETE FROM monthly_incentives WHERE platform = :platform AND month = :month")
    suspend fun remove(platform: String, month: String)
    @Query("SELECT * FROM monthly_incentives WHERE month = :month ORDER BY platform")
    suspend fun forMonth(month: String): List<MonthlyIncentiveEntity>
    @Query("SELECT * FROM monthly_incentives WHERE month = :month ORDER BY platform")
    fun observeMonth(month: String): Flow<List<MonthlyIncentiveEntity>>
}
