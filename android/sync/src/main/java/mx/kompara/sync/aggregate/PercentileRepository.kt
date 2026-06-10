package mx.kompara.sync.aggregate

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mx.kompara.data.model.City
import mx.kompara.data.model.PopulationStat
import mx.kompara.metrics.percentile.PercentileCalculator
import mx.kompara.metrics.percentile.PercentileResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Combines a driver's current-week aggregate metrics with the cached population benchmarks
 * ([BenchmarksRepository]) to produce per-metric [PercentileResult]s for the percentile UI (B-046).
 *
 * Lives in `:sync` because it depends on [BenchmarksRepository] (also `:sync`) for the cached
 * `Flow<List<PopulationStat>>`; the actual percentile math is the pure [PercentileCalculator] in
 * `:metrics`. The `:ui` viewmodels feed it the driver's metric values (built from their `PeriodStats`)
 * and observe the resulting flow.
 *
 * ## Metric source
 * Callers pass a map of metric-key → driver value (only the metrics they have). Keys are the backend
 * `metric_name`s (see [PercentileCalculator] / population_stats): `earnings_per_trip`,
 * `earnings_per_km`, `earnings_per_hour`, `trips_per_hour`, `platform_commission_pct`. A null/absent
 * value is skipped (no percentile rendered for a metric the driver has no number for), mirroring the
 * web engine.
 *
 * ## Reactivity & fallback
 * It observes [BenchmarksRepository.observeForPercentiles] (city cells + the `national` fallback) and
 * recomputes when the cache changes. The `national` row drives [PercentileCalculator]'s < 20-sample
 * fallback, which works offline once the benchmarks were fetched at least once.
 */
@Singleton
class PercentileRepository @Inject constructor(
    private val benchmarks: BenchmarksRepository,
) {

    /**
     * Reactive per-metric percentiles for the driver's [metricValues] on [city] × [platform]. Emits
     * an empty list while benchmarks aren't cached yet (the UI hides percentile elements then). The
     * order of [metricValues] iteration is preserved in the output.
     *
     * @param metricValues metric-key → driver value; null values are skipped.
     */
    fun observe(
        city: City,
        platform: String,
        metricValues: Map<String, Double?>,
    ): Flow<List<PercentileResult>> =
        benchmarks.observeForPercentiles(city, platform).map { stats ->
            combine(stats, metricValues)
        }

    /**
     * Pure combination of cached [stats] (city + national for one platform) and the driver's
     * [metricValues] into [PercentileResult]s. Exposed (not private) so the `:ui`/test layer can unit
     * test the combination logic without a repository or DAO. City vs national rows are split by the
     * `national` slug so [PercentileCalculator.percentileFor] can apply the sample-size fallback.
     */
    fun combine(
        stats: List<PopulationStat>,
        metricValues: Map<String, Double?>,
    ): List<PercentileResult> {
        if (stats.isEmpty()) return emptyList()
        val (national, city) = stats.partition { it.city == BenchmarksRepository.NATIONAL_CITY }
        return metricValues.mapNotNull { (metric, value) ->
            if (value == null) return@mapNotNull null
            PercentileCalculator.percentileFor(
                metric = metric,
                value = value,
                cityStats = city,
                nationalStats = national,
            )
        }
    }
}
