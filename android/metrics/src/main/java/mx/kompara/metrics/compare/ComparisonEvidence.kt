package mx.kompara.metrics.compare

import mx.kompara.data.accounting.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** A denominator's definition is part of the contract, not just its unit. */
enum class ComparisonMetric(val label: String) { PER_KM("Por km"), PER_TRIP("Por viaje"), PER_HOUR("Por hora") }
enum class ComparisonBasis { DRIVER_PAYMENT_BEFORE_COSTS, RUNNING_COST_CONTRIBUTION }

/** Only the authenticated import contract may create this proof; IMPORTED/session verification cannot. */
data class ConfirmedStatement(
    val record: IncomeRecord,
    val city: String,
    val certificationId: String,
    val confirmedFields: Set<MoneyField>,
    /** Explicit online time from the same statement; IncomeRecord.minutes may be trip duration. */
    val onlineMinutes: BigDecimal? = null,
) {
    fun value(week: LocalDate, app: String, metric: ComparisonMetric): BigDecimal? {
        val denominator = when (metric) {
            ComparisonMetric.PER_TRIP -> MoneyField.TRIPS
            ComparisonMetric.PER_KM -> MoneyField.DISTANCE
            ComparisonMetric.PER_HOUR -> MoneyField.HOURS
        }
        if (certificationId.isBlank() || record.app != app || record.source != MoneySource.SERVER_CONFIRMED_STATEMENT ||
            !record.eligible || record.inProgress != 0 || record.paymentBasis != PaymentBasis.DRIVER_PAYMENT ||
            record.coverage.start != week || record.coverage.endInclusive != week.plusDays(6) ||
            !confirmedFields.containsAll(setOf(MoneyField.PAYMENT, denominator)) ||
            record.fieldSources[MoneyField.PAYMENT] != record.id || record.fieldSources[denominator] != record.id) return null
        return if (metric == ComparisonMetric.PER_HOUR) onlineMinutes?.takeIf { it.signum() > 0 }?.let {
            record.payment?.multiply(BigDecimal(60))?.divide(it, MONEY_CONTEXT)
        } else paymentRate(record, metric)
    }
}

/** Certified cohort seam. The current population DTO/cache cannot supply this evidence. */
data class StatementCohort(
    val city: String,
    val app: String,
    val week: LocalDate,
    val metric: ComparisonMetric,
    val basis: ComparisonBasis,
    val source: MoneySource,
    val certificationId: String,
    val completeCoverage: Boolean,
    val synthetic: Boolean,
    val drivers: Int,
    val mean: BigDecimal,
    val quantiles: Map<Int, BigDecimal>,
) {
    fun matches(city: String, app: String, week: LocalDate, metric: ComparisonMetric): Boolean =
        this.city == city && this.app == app && this.week == week && this.metric == metric &&
            basis == ComparisonBasis.DRIVER_PAYMENT_BEFORE_COSTS && source == MoneySource.SERVER_CONFIRMED_STATEMENT &&
            certificationId.isNotBlank() && completeCoverage && !synthetic && drivers >= 20 && mean.signum() >= 0 &&
            quantiles.size >= 2 && quantiles.keys.all { it in 1..99 } &&
            quantiles.toSortedMap().values.zipWithNext().all { (a, b) -> a < b } && quantiles.values.all { it.signum() >= 0 }
}

/** No national, cross-app, cross-week, synthetic or net-benchmark fallback. */
object ComparisonEligibility {
    fun cohort(cohorts: List<StatementCohort>, city: String, app: String, week: LocalDate, metric: ComparisonMetric) =
        cohorts.singleOrNull { it.matches(city, app, week, metric) }

    fun rank(statement: ConfirmedStatement?, cohort: StatementCohort?): Int? {
        if (statement == null || cohort == null || statement.city != cohort.city ||
            !cohort.matches(cohort.city, cohort.app, cohort.week, cohort.metric)) return null
        val value = statement.value(cohort.week, cohort.app, cohort.metric) ?: return null
        val points = cohort.quantiles.toSortedMap().entries.toList()
        val upper = points.indexOfFirst { value <= it.value }
        // Retain the existing percentile engine's bounded tail convention for a generic quantile grid.
        if (upper == 0) {
            val first = points.first()
            if (first.value.signum() == 0) return null
            return value.divide(first.value, MONEY_CONTEXT).multiply(BigDecimal(first.key))
                .setScale(0, RoundingMode.HALF_UP).toInt().coerceIn(1, 99)
        }
        if (upper == -1) {
            val last = points.last()
            if (last.value.signum() == 0) return null
            val tail = value.subtract(last.value).divide(last.value.multiply(BigDecimal("0.5")), MONEY_CONTEXT)
                .multiply(BigDecimal(100 - last.key)).setScale(0, RoundingMode.HALF_UP)
            return BigDecimal(last.key).add(tail).coerceAtMost(BigDecimal(99)).toInt().coerceIn(1, 99)
        }
        val low = points[upper - 1]; val high = points[upper]
        return BigDecimal(low.key).add(value.subtract(low.value).divide(high.value.subtract(low.value), MONEY_CONTEXT)
            .multiply(BigDecimal(high.key - low.key))).setScale(0, RoundingMode.HALF_UP).toInt()
    }
}

fun paymentRate(record: IncomeRecord, metric: ComparisonMetric): BigDecimal? {
    if (!record.eligible || record.paymentBasis != PaymentBasis.DRIVER_PAYMENT) return null
    val denominator = when (metric) {
        ComparisonMetric.PER_TRIP -> record.trips.toBigDecimal()
        ComparisonMetric.PER_KM -> record.km
        // Captured minutes describe trip duration, never per-app online hours.
        ComparisonMetric.PER_HOUR -> null
    }
    return denominator?.takeIf { it.signum() > 0 }?.let { record.payment?.divide(it, MONEY_CONTEXT) }
}
