package mx.kompara.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import mx.kompara.data.accounting.*
import java.math.BigDecimal
import java.time.LocalDate

/** Raw decimal strings, not display strings. Append-only, including same-instant revisions. */
@Entity(tableName = "running_cost_versions")
data class CostVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val effectiveFrom: Long,
    val recordedAt: Long,
    val mode: String,
    val confirmation: String,
    val confirmedAt: Long?,
    val wearPerKm: String,
    val fuelPricePerLitre: String?,
    val kmPerLitre: String?,
    val kwhPer100Km: String?,
    val pricePerKwh: String?,
    val directPerKm: String?,
    val correctsVersionId: Long? = null,
) {
    fun toDomain() = RunningCostVersion(id, effectiveFrom, recordedAt,
        RunningCostInputs(RunningCostMode.valueOf(mode), BigDecimal(wearPerKm),
            fuelPricePerLitre?.toBigDecimal(), kmPerLitre?.toBigDecimal(), kwhPer100Km?.toBigDecimal(),
            pricePerKwh?.toBigDecimal(), directPerKm?.toBigDecimal()), CostConfirmation.valueOf(confirmation), confirmedAt, correctsVersionId)

    companion object {
        fun from(version: RunningCostVersion) = with(version) {
            CostVersionEntity(id, effectiveFrom, recordedAt, inputs.mode.name, confirmation.name, confirmedAt,
                inputs.wearPerKm.toPlainString(), inputs.fuelPricePerLitre?.toPlainString(), inputs.kmPerLitre?.toPlainString(),
                inputs.kwhPer100Km?.toPlainString(), inputs.pricePerKwh?.toPlainString(), inputs.directPerKm?.toPlainString(), correctsVersionId)
        }
    }
}

@Entity(tableName = "car_cost_versions")
data class CarCostVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val effectiveFrom: String,
    val weeklyMxn: String?,
    val recordedAt: Long,
) {
    fun toDomain() = CarCostVersion(id, LocalDate.parse(effectiveFrom), weeklyMxn?.toBigDecimal(), recordedAt)
}
