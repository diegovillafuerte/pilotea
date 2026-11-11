package mx.kompara.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mx.kompara.data.db.dao.MonthlyIncentiveDao
import mx.kompara.data.db.entity.MonthlyIncentiveEntity
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** The one amount per app and month the driver types for IMSS (M4): incentives and referral bonuses, no tips. */
@Singleton
class MonthlyIncentiveRepository @Inject constructor(private val dao: MonthlyIncentiveDao) {
    /** Whole pesos by platform name; an app without an entry is absent. */
    fun observe(month: YearMonth): Flow<Map<String, Int>> =
        dao.observeMonth(month.toString()).map { rows -> rows.associate { it.platform to it.amountMxn } }

    suspend fun set(platform: String, month: YearMonth, amountMxn: Int, updatedAt: Long) {
        require(amountMxn >= 0)
        dao.put(MonthlyIncentiveEntity(platform.uppercase(), month.toString(), amountMxn, updatedAt))
    }

    suspend fun remove(platform: String, month: YearMonth) = dao.remove(platform.uppercase(), month.toString())
}
