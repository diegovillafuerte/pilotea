package mx.kompara.data.model

import mx.kompara.data.db.entity.PopulationStatEntity

/**
 * A population benchmark for one `(city, platform, metric)` — the percentile breakpoints a driver's
 * own number is compared against (B-043 → B-046 percentiles).
 *
 * This is the clean domain read shape exposed by `BenchmarksRepository`: it carries no Room types, so
 * the percentile feature (B-046) and any UI depend on this and not on [PopulationStatEntity]. The
 * five breakpoints + mean mirror the backend; [isSynthetic] lets the UI caveat estimated benchmarks.
 */
data class PopulationStat(
    val city: String,
    val platform: String,
    val metric: String,
    val period: String,
    val sampleSize: Int,
    val p10: Double,
    val p25: Double,
    val p50: Double,
    val p75: Double,
    val p90: Double,
    val mean: Double,
    /** False once folded from real consented data; true while still a synthetic seed. */
    val isSynthetic: Boolean,
) {
    companion object {
        /** Map the Room cache row into the domain shape. */
        fun from(e: PopulationStatEntity): PopulationStat = PopulationStat(
            city = e.city,
            platform = e.platform,
            metric = e.metricName,
            period = e.period,
            sampleSize = e.sampleSize,
            p10 = e.p10,
            p25 = e.p25,
            p50 = e.p50,
            p75 = e.p75,
            p90 = e.p90,
            mean = e.mean,
            isSynthetic = e.isSynthetic,
        )
    }
}
