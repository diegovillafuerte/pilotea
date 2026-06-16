package mx.kompara.ui.stats

import mx.kompara.data.model.Platform
import mx.kompara.metrics.percentile.PercentileResult
import mx.kompara.metrics.recommendation.Recommendation

/**
 * The Comparar tab's data model (S-024): the driver's blended weekly value per metric, the city
 * average for each platform, and the driver's percentile vs. all drivers. Pure data — the screen
 * (B-087) formats it through [mx.kompara.ui.format.Formatters] and resolves labels from strings.xml.
 *
 * Built by [ComparisonBuilder]; weekly only (day/hour is deferred — see B-090).
 */

/** How a metric's numbers are formatted on screen. */
enum class MetricUnit { MXN, PER_HOUR, PER_KM, COUNT_PER_HOUR, PERCENT }

/** Why a platform column shows "—" instead of a number (drives the honest reason copy). */
enum class NaReason { NO_KM, NO_COMMISSION, NO_BENCHMARK }

/**
 * One Comparar metric's fixed spec. The per-platform N/A is intrinsic (Uber never reports km; DiDi
 * never breaks out commission), so it's declared here rather than inferred from missing data.
 */
data class CompareMetricSpec(
    val key: String,
    val unit: MetricUnit,
    val lowerIsBetter: Boolean = false,
    val uberNa: NaReason? = null,
    val didiNa: NaReason? = null,
)

/**
 * The 6 metrics, in table order (Ganancia neta, IPH, IPK, IPT, Viajes/hora, Take rate). `net_earnings`
 * and the 4 rates back onto `population_stats` (the `all` combined population — B-085); commission is
 * lower-is-better (inverted percentile) and absent on the captured path today.
 */
val COMPARE_METRICS: List<CompareMetricSpec> = listOf(
    CompareMetricSpec("net_earnings", MetricUnit.MXN),
    CompareMetricSpec("earnings_per_hour", MetricUnit.PER_HOUR),
    CompareMetricSpec("earnings_per_km", MetricUnit.PER_KM, uberNa = NaReason.NO_KM),
    CompareMetricSpec("earnings_per_trip", MetricUnit.MXN),
    CompareMetricSpec("trips_per_hour", MetricUnit.COUNT_PER_HOUR),
    CompareMetricSpec(
        "platform_commission_pct",
        MetricUnit.PERCENT,
        lowerIsBetter = true,
        didiNa = NaReason.NO_COMMISSION,
    ),
)

/**
 * One row of the benchmark table.
 *
 * @property tu the driver's blended value across apps, or null when they have no value for it.
 * @property uberAvg / [didiAvg] the city population average (`PopulationStat.mean`), or null (N/A).
 * @property percentile the driver's standing vs. all drivers for this metric (inverted upstream for
 *   lower-is-better metrics), or null when no benchmark is cached / no value.
 */
data class ComparisonRow(
    val metric: String,
    val unit: MetricUnit,
    val tu: Double?,
    val uberAvg: Double?,
    val didiAvg: Double?,
    val percentile: PercentileResult?,
    val lowerIsBetter: Boolean,
    val uberNa: NaReason?,
    val didiNa: NaReason?,
)

/**
 * The full weekly comparison for one week.
 *
 * @property standing the driver's overall city standing (the free hero), picked by the lead-metric
 *   priority $/hora → $/km → $/viaje; null when no benchmark resolved.
 * @property singlePlatform set when the driver used exactly one app this week (drives the "prueba otra
 *   app" copy); the table still shows both platform averages.
 * @property opportunities comparison-derived advice (B-088); empty until that lands or when gated.
 */
data class WeeklyComparison(
    val weekStart: String,
    val rows: List<ComparisonRow>,
    val standing: PercentileResult?,
    val standingMetric: String?,
    val platformsWithData: List<Platform>,
    val singlePlatform: Platform?,
    val opportunities: List<Recommendation> = emptyList(),
)

/** The Comparar tab's render data for one week. Null in [CompareUiState] ⇒ no data at all (empty). */
data class CompareUiData(
    val weekStart: String,
    val comparison: WeeklyComparison,
)
