package mx.kompara.data.accounting

import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/** Decimal inputs remain unrounded until presentation; never reconstruct them from a displayed rate. */
val MONEY_CONTEXT: MathContext = MathContext.DECIMAL128
fun Double.decimal(): BigDecimal = BigDecimal.valueOf(this)
fun BigDecimal.per(divisor: BigDecimal): BigDecimal? =
    if (divisor.signum() <= 0) null else divide(divisor, MONEY_CONTEXT)

enum class RunningCostMode { GAS, ELECTRIC, DIRECT, LEGACY }
enum class CostConfirmation { TYPICAL, CONFIRMED, LEGACY_CONFIRMED }

data class RunningCostInputs(
    val mode: RunningCostMode,
    val wearPerKm: BigDecimal = BigDecimal.ZERO,
    val fuelPricePerLitre: BigDecimal? = null,
    val kmPerLitre: BigDecimal? = null,
    val kwhPer100Km: BigDecimal? = null,
    val pricePerKwh: BigDecimal? = null,
    /** DIRECT is the total running cost, including wear. LEGACY stores the old resolved energy cost. */
    val directPerKm: BigDecimal? = null,
) {
    val costPerKm: BigDecimal?
        get() {
            if (listOfNotNull(wearPerKm, fuelPricePerLitre, kmPerLitre, kwhPer100Km, pricePerKwh, directPerKm)
                    .any { it.signum() < 0 }) return null
            return when (mode) {
                RunningCostMode.GAS -> fuelPricePerLitre?.let { price -> kmPerLitre?.let { price.per(it) } }?.add(wearPerKm)
                RunningCostMode.ELECTRIC -> kwhPer100Km?.let { consumption ->
                    pricePerKwh?.multiply(consumption)?.divide(BigDecimal(100), MONEY_CONTEXT)
                }?.add(wearPerKm)
                RunningCostMode.DIRECT -> directPerKm
                RunningCostMode.LEGACY -> directPerKm?.add(wearPerKm)
            }
        }

    companion object {
        /** Labelled initial estimate, not a live fuel quote or a confirmed personal profile. */
        val TYPICAL = RunningCostInputs(RunningCostMode.GAS, BigDecimal("0.80"), BigDecimal("24.50"), BigDecimal("12"))
    }
}

data class RunningCostVersion(
    val id: Long,
    val effectiveFrom: Long,
    val recordedAt: Long,
    val inputs: RunningCostInputs,
    val confirmation: CostConfirmation,
    val confirmedAt: Long? = null,
    /** Explicit efficiency correction for period accounting; the offer's judgement stays frozen. */
    val correctsVersionId: Long? = null,
)

data class CarCostVersion(
    val id: Long,
    val effectiveFrom: LocalDate,
    /** Null is a dated removal; zero is an explicitly configured zero cost. */
    val weeklyMxn: BigDecimal?,
    val recordedAt: Long,
) {
    init { require(weeklyMxn == null || weeklyMxn.signum() >= 0) }
}

class CostSchedule(val running: List<RunningCostVersion>, val car: List<CarCostVersion> = emptyList()) {
    fun accountingVersion(id: Long?): RunningCostVersion? {
        val original = running.singleOrNull { it.id == id } ?: return null
        val root = original.correctsVersionId ?: original.id
        return running.filter { it.correctsVersionId == root }.maxByOrNull { it.id } ?: original
    }
    fun runningAt(epochMs: Long): RunningCostVersion? = running.filter { it.effectiveFrom <= epochMs }
        .maxWithOrNull(compareBy<RunningCostVersion> { it.effectiveFrom }.thenBy { it.id })
    fun carAt(date: LocalDate): CarCostVersion? = car.filter { it.effectiveFrom <= date }
        .maxWithOrNull(compareBy<CarCostVersion> { it.effectiveFrom }.thenBy { it.id })
}
