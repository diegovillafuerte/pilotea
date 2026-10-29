package mx.kompara.data.db.dao

import androidx.room.Transaction
import mx.kompara.data.db.entity.CostVersionEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.kompara.data.db.entity.CostProfileEntity

@Dao
interface CostProfileDao : CostVersionDao {

    @Transaction
    suspend fun saveVersion(profile: CostProfileEntity, version: CostVersionEntity, correctEfficiency: Boolean = false): Long {
        if (correctEfficiency) {
            require(version.mode == "GAS" && version.kmPerLitre != null)
            // Preserve each period's own price/wear and every original evaluation. Never infer
            // efficiency for legacy, direct or electric history, or change scheduled future costs.
            val originals = versions().filter { it.mode == "GAS" && it.correctsVersionId == null && it.effectiveFrom < version.effectiveFrom }
            originals.forEach { old -> insertVersion(old.copy(id = 0, recordedAt = version.recordedAt,
                kmPerLitre = version.kmPerLitre, correctsVersionId = old.id)) }
        }
        upsert(profile)
        return insertVersion(version)
    }

    @Transaction
    suspend fun initializeVersion(version: CostVersionEntity) {
        if (versions().isEmpty()) insertVersion(version)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: CostProfileEntity)

    @Query("SELECT * FROM cost_profile WHERE id = :id")
    fun observe(id: Long = CostProfileEntity.SINGLETON_ID): Flow<CostProfileEntity?>

    @Query("SELECT * FROM cost_profile WHERE id = :id")
    suspend fun get(id: Long = CostProfileEntity.SINGLETON_ID): CostProfileEntity?
}
