package mx.kompara.ui.stats

import mx.kompara.data.model.City
import mx.kompara.metrics.compare.*
import java.math.BigDecimal
import java.time.LocalDate

enum class CompareScope { APPS, CITY }
enum class ComparisonStatus { OWN_APPS, RANKED, NO_COHORT, UNCONFIRMED, UNAVAILABLE, EMPTY }
data class CompareChoice(val scope: CompareScope = CompareScope.APPS, val app: String = "UBER",
    val city: City = City.CDMX, val appsMetric: ComparisonMetric = ComparisonMetric.PER_KM,
    val cityMetric: ComparisonMetric = ComparisonMetric.PER_TRIP) {
    val metric get() = if (scope == CompareScope.APPS) appsMetric else cityMetric
}
data class ComparisonValue(val label: String, val value: BigDecimal?, val own: Boolean = true,
    val qualifier: String? = null, val locked: Boolean = false, val waiting: String? = null)
data class WeeklyComparison(
    val weekStart: LocalDate,
    val today: LocalDate,
    val choice: CompareChoice,
    val status: ComparisonStatus,
    val selector: String,
    val finding: String,
    val qualifier: String,
    val values: List<ComparisonValue>,
    val basis: String,
    val details: List<String>,
    val unavailable: Map<ComparisonMetric, String> = emptyMap(),
    val difference: String? = null,
    val rank: Int? = null,
    val singleApp: String? = null,
) {
    val showBars get() = status in setOf(ComparisonStatus.OWN_APPS, ComparisonStatus.RANKED, ComparisonStatus.NO_COHORT) &&
        (values.count { it.value != null } > 1 || values.any { it.locked })
    val locked get() = values.any { it.locked }
}
data class CompareUiState(
    val weekStart: LocalDate,
    val today: LocalDate,
    val choice: CompareChoice = CompareChoice(),
    val loading: Boolean = true,
    val comparison: WeeklyComparison? = null,
    val error: Boolean = false,
    val offline: Boolean = false,
    val weeks: List<LocalDate> = emptyList(),
    val contributing: Boolean = false,
    val saveError: Boolean = false,
)
