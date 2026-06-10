package mx.kompara.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.kompara.data.db.entity.CostProfileEntity

@Dao
interface CostProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: CostProfileEntity)

    @Query("SELECT * FROM cost_profile WHERE id = :id")
    fun observe(id: Long = CostProfileEntity.SINGLETON_ID): Flow<CostProfileEntity?>

    @Query("SELECT * FROM cost_profile WHERE id = :id")
    suspend fun get(id: Long = CostProfileEntity.SINGLETON_ID): CostProfileEntity?
}
