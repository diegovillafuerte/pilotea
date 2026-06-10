package mx.kompara.ui.stats

import mx.kompara.metrics.percentile.PercentileResult

/**
 * The percentile overlay for a stats surface (B-046): the per-metric standings to paint onto the
 * metric cards, plus the premium [locked] gate.
 *
 * @property byMetric backend metric key → the driver's [PercentileResult] for it; empty when no
 *   benchmarks are cached yet or no concrete platform is selected (so the cards just render bare).
 * @property locked true to render the premium-locked stand-in instead of the real standing. Set from
 *   `!(canSeeBenchmarks || debugPremium)` — see the viewmodels.
 */
data class PercentilesUiState(
    val byMetric: Map<String, PercentileResult>,
    val locked: Boolean,
) {
    /** True when there is at least one standing to show (and we're not locked). */
    val hasAny: Boolean get() = byMetric.isNotEmpty()

    companion object {
        /** No percentiles, not locked — the default before benchmarks arrive. */
        val EMPTY = PercentilesUiState(byMetric = emptyMap(), locked = false)
    }
}

/**
 * The inputs that drive a percentile recompute — the resolved platform and the period stats. Shared
 * by the Inicio and week-summary viewmodels so a recompute only fires when one of these changes.
 */
internal data class PercentileInputs(
    val platform: mx.kompara.data.model.Platform?,
    val period: PeriodStats,
)
