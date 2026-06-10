package mx.kompara.metrics.percentile

/**
 * A driver's standing on one metric against the population benchmarks (B-046).
 *
 * The on-device twin of the web engine's `PercentileResult`. [percentile] is the *raw* 1–99
 * percentile from [PercentileCalculator] (always "higher value ⇒ higher percentile");
 * [displayPercentile] is the reading the UI should show — inverted for metrics where lower is
 * better for the driver (commission %), so a high display percentile always means "good".
 *
 * @property metric the metric key (e.g. "earnings_per_hour"); matches backend `metric_name`.
 * @property value the driver's own number this percentile was computed for.
 * @property percentile raw percentile 1–99 (higher value ⇒ higher).
 * @property displayPercentile percentile the UI renders (inverted for [INVERTED][PercentileCalculator.INVERTED_METRICS] metrics).
 * @property sampleSize how many samples back the breakpoints used.
 * @property isNationalFallback true when the national row was used because the city had too few samples (< 20) or no row.
 * @property isSynthetic true while the breakpoints are still a synthetic seed (not yet folded from real consented data).
 */
data class PercentileResult(
    val metric: String,
    val value: Double,
    val percentile: Int,
    val displayPercentile: Int,
    val sampleSize: Int,
    val isNationalFallback: Boolean,
    val isSynthetic: Boolean,
) {
    /**
     * The "Top X%" figure for a higher-is-better metric: `100 - displayPercentile`. A driver at the
     * 78th display percentile is in the "top 22%". Clamped to at least 1 so we never render "Top 0%".
     */
    val topPercent: Int get() = (100 - displayPercentile).coerceAtLeast(1)
}
