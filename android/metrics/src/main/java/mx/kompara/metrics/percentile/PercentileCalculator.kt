package mx.kompara.metrics.percentile

import mx.kompara.data.model.PopulationStat
import kotlin.math.abs
import kotlin.math.floor

/**
 * On-device port of the backend `get_percentile(city, platform, metric, value)` SQL function
 * (`backend/migrations/0001_get_percentile_function.sql`) — B-046.
 *
 * Given a set of [PopulationStat] breakpoints and a driver's own [value], it returns the driver's
 * percentile (1–99) by **piecewise linear interpolation** across `p10/p25/p50/p75/p90`, clamped to
 * `1..99`. The interpolation formula and the integer rounding match the SQL byte-for-byte, so the
 * parity test (`PercentileCalculatorParityTest`) feeds the same inputs as the backend parity test
 * (`backend/src/db/percentile.test.ts`) and expects the same integers out.
 *
 * ## National fallback
 * The SQL falls back to the `national` row when the city-specific `sample_size < 20` (or the city
 * row is missing). [percentileFor] mirrors that: pass the city rows and the national rows, and it
 * picks the city cell only when its `sampleSize >= [MIN_SAMPLE_SIZE]`, otherwise the national cell.
 * When neither exists it returns null (the SQL returns NULL → the UI shows nothing).
 *
 * ## Commission inversion is a *display* concern
 * The raw percentile is always "higher value = higher percentile". For metrics where lower is
 * better for the driver (platform commission %), the inverted reading lives in
 * [PercentileResult.displayPercentile] — this calculator never inverts the raw percentile, exactly
 * like the SQL.
 *
 * Pure and deterministic — no Android, no IO — so it is fully unit-testable.
 */
object PercentileCalculator {

    /** Minimum city sample size before falling back to national (matches the SQL `< 20` guard). */
    const val MIN_SAMPLE_SIZE: Int = 20

    /**
     * Metrics where a *lower* value is better for the driver. Their [PercentileResult.displayPercentile]
     * is inverted (`100 - rawPercentile`) so "high display percentile = good" holds everywhere. Mirrors
     * the web engine's `INVERTED_METRICS`.
     */
    val INVERTED_METRICS: Set<String> = setOf("platform_commission_pct")

    /**
     * The percentile for a single [metric] [value], choosing the city cell when it has enough samples
     * and otherwise the national cell. Returns null when no usable breakpoints exist for the metric
     * (mirrors the SQL returning NULL). [isNationalFallback] in the result reflects which cell was
     * used.
     *
     * @param cityStats the cached breakpoints for the driver's city × platform (any metrics).
     * @param nationalStats the cached `national` breakpoints for the same platform (any metrics).
     */
    fun percentileFor(
        metric: String,
        value: Double,
        cityStats: List<PopulationStat>,
        nationalStats: List<PopulationStat>,
    ): PercentileResult? {
        val cityCell = cityStats.firstOrNull { it.metric == metric }
        val nationalCell = nationalStats.firstOrNull { it.metric == metric }

        // City row is used only when present AND it has >= MIN_SAMPLE_SIZE samples; otherwise national.
        val useCity = cityCell != null && cityCell.sampleSize >= MIN_SAMPLE_SIZE
        val chosen = if (useCity) cityCell else nationalCell ?: return null

        val raw = interpolate(value, chosen) ?: return null
        val inverted = metric in INVERTED_METRICS
        return PercentileResult(
            metric = metric,
            value = value,
            percentile = raw,
            displayPercentile = if (inverted) 100 - raw else raw,
            sampleSize = chosen.sampleSize,
            isNationalFallback = !useCity,
            isSynthetic = chosen.isSynthetic,
        )
    }

    /**
     * The raw percentile (1–99) for [value] against one breakpoint cell, or null when the cell can't
     * produce a result (a degenerate cell whose denominators are all zero, matching SQL `NULLIF` →
     * NULL). This is the verbatim port of the SQL `CASE` ladder + `GREATEST(1, LEAST(99, …))` clamp.
     */
    fun interpolate(value: Double, stats: PopulationStat): Int? {
        val p10 = stats.p10
        val p25 = stats.p25
        val p50 = stats.p50
        val p75 = stats.p75
        val p90 = stats.p90

        val raw: Long = when {
            value <= p10 ->
                nullSafeRatio(value, p10)?.let { pgRound(it * 10) }

            value <= p25 ->
                nullSafeRatio(value - p10, p25 - p10)?.let { 10 + pgRound(it * 15) }

            value <= p50 ->
                nullSafeRatio(value - p25, p50 - p25)?.let { 25 + pgRound(it * 25) }

            value <= p75 ->
                nullSafeRatio(value - p50, p75 - p50)?.let { 50 + pgRound(it * 25) }

            value <= p90 ->
                nullSafeRatio(value - p75, p90 - p75)?.let { 75 + pgRound(it * 15) }

            else ->
                // Top tail: 90 + LEAST(9, ROUND(((value - p90) / (p90 * 0.5)) * 10))
                nullSafeRatio(value - p90, p90 * 0.5)?.let { 90 + minOf(9L, pgRound(it * 10)) }
        } ?: return null

        // GREATEST(1, LEAST(99, …))
        return raw.coerceIn(1L, 99L).toInt()
    }

    /**
     * `a / NULLIF(b, 0)` — the SQL guard against a zero denominator. Returns null when [denominator]
     * is zero (the SQL `NULLIF(...,0)` makes the whole division NULL), matching the function returning
     * NULL for a degenerate breakpoint cell.
     */
    private fun nullSafeRatio(numerator: Double, denominator: Double): Double? =
        if (denominator == 0.0) null else numerator / denominator

    /**
     * Postgres `ROUND(numeric)` — rounds half **away from zero** (e.g. 2.5 → 3, -2.5 → -3), unlike
     * Kotlin's `Math.round`/banker's rounding. The backend parity inputs avoid exact .5 ties, but we
     * still match the engine's rounding mode so any future input lines up.
     */
    private fun pgRound(x: Double): Long {
        val f = floor(abs(x) + 0.5)
        return (if (x < 0) -f else f).toLong()
    }
}
