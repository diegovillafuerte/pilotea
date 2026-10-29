package mx.kompara.data.settings

import mx.kompara.data.db.dao.CarCostDao
import mx.kompara.data.db.entity.CarCostVersionEntity
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** G5 supplies the explicitly chosen date. Removal appends a stop; it never deletes history. */
@Singleton
class CarCostRepository @Inject constructor(private val dao: CarCostDao) {
    val versions = dao.observeVersions()
    suspend fun get() = dao.versions().map { it.toDomain() }
    suspend fun save(weeklyMxn: BigDecimal, from: LocalDate, recordedAt: Long): Long {
        require(weeklyMxn.signum() >= 0)
        return dao.insert(CarCostVersionEntity(effectiveFrom = from.toString(), weeklyMxn = weeklyMxn.toPlainString(), recordedAt = recordedAt))
    }
    suspend fun remove(from: LocalDate, recordedAt: Long): Long =
        dao.stop(from.toString(), recordedAt)
}
