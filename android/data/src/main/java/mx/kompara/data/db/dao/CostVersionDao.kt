package mx.kompara.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.kompara.data.db.entity.CostVersionEntity
import mx.kompara.data.db.entity.CarCostVersionEntity

@Dao
interface CostVersionDao {
    @Insert suspend fun insertVersion(version: CostVersionEntity): Long
    @Query("SELECT * FROM running_cost_versions ORDER BY effectiveFrom, id")
    suspend fun versions(): List<CostVersionEntity>
    @Query("SELECT * FROM running_cost_versions ORDER BY effectiveFrom, id")
    fun observeVersions(): Flow<List<CostVersionEntity>>
}

@Dao
interface CarCostDao {
    @androidx.room.Transaction
    suspend fun stop(from: String, recordedAt: Long): Long {
        val futureDates = versions().map { it.effectiveFrom }.filter { it > from }.distinct()
        val id = insert(CarCostVersionEntity(effectiveFrom = from, weeklyMxn = null, recordedAt = recordedAt))
        futureDates.forEach { insert(CarCostVersionEntity(effectiveFrom = it, weeklyMxn = null, recordedAt = recordedAt)) }
        return id
    }
    @Insert suspend fun insert(version: CarCostVersionEntity): Long
    @Query("SELECT * FROM car_cost_versions ORDER BY effectiveFrom, id")
    suspend fun versions(): List<CarCostVersionEntity>
    @Query("SELECT * FROM car_cost_versions ORDER BY effectiveFrom, id")
    fun observeVersions(): Flow<List<CarCostVersionEntity>>
}
