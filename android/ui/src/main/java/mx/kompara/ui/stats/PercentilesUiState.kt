package mx.kompara.ui.stats

import mx.kompara.billing.GateState
import mx.kompara.metrics.percentile.PercentileResult

/**
 * The percentile overlay for a stats surface (B-046): the per-metric standings to paint onto the
 * metric cards, plus the premium [gateState] that decides whether to show them.
 *
 * @property byMetric backend metric key → the driver's [PercentileResult] for it; empty when no
 *   benchmarks are cached yet or no concrete platform is selected (so the cards just render bare).
 * @property gateState the resolved BENCHMARKS [GateState]. Carried (not just a `locked` boolean) so the
 *   surface can distinguish LOCKED (pay) from NEEDS_VERIFICATION (import to verify) — PR-E.
 */
data class PercentilesUiState(
    val byMetric: Map<String, PercentileResult>,
    val gateState: GateState,
) {
    /** True to render the premium-locked stand-in instead of the real standing (any non-unlocked state). */
    val locked: Boolean get() = !gateState.isUnlocked

    /** True when there is at least one standing to show. */
    val hasAny: Boolean get() = byMetric.isNotEmpty()

    companion object {
        /** No percentiles, unlocked — the default before benchmarks arrive. */
        val EMPTY = PercentilesUiState(byMetric = emptyMap(), gateState = GateState.UNLOCKED)
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
