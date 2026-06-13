package mx.kompara.overlay.simulator

import mx.kompara.data.model.Platform
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.overlay.VerdictChipState
import mx.kompara.parsers.snapshot.DemoSnapshots

/**
 * Render-ready state for the offer simulator screen. The screen is a dumb projection of this — the
 * [SimulatorViewModel] runs the real pipeline and the Composable just paints the result.
 *
 * @property platform which host the demo is showing (Uber / DiDi), driven by the toggle
 * @property stepIndex 0-based index into [offers] (the 3-step good → marginal → bad script)
 * @property offers the three demo offers for the current platform, already replayed to verdicts
 * @property threshold the driver's current $/km (+ $/h) floor for [platform]; the slider edits it
 * @property preferredMetric which metric decides the semáforo (B-079); the playground slider edits
 *   that metric's green floor
 */
data class SimulatorUiState(
    val platform: Platform,
    val stepIndex: Int,
    val offers: List<SimulatorStep>,
    val threshold: PlatformThreshold,
    val preferredMetric: PreferredMetric = PreferredMetric.DEFAULT,
) {
    /** The offer currently on screen, or null before the first evaluation lands. */
    val current: SimulatorStep? get() = offers.getOrNull(stepIndex)

    val stepCount: Int get() = offers.size
    val isFirstStep: Boolean get() = stepIndex <= 0
    val isLastStep: Boolean get() = stepIndex >= offers.size - 1

    companion object {
        /** Empty initial state before the repositories emit. Uber, step 0, no offers yet. */
        fun initial(): SimulatorUiState = SimulatorUiState(
            platform = Platform.UBER,
            stepIndex = 0,
            offers = emptyList(),
            threshold = PlatformThreshold.DEFAULT,
        )
    }
}

/**
 * One replayed demo offer plus everything the screen needs to render it: the chip [chipState] (from
 * the real verdict), the mock host-card [visibleText], and the demo's intended [shape] (drives which
 * guided explanation copy to show).
 */
data class SimulatorStep(
    val id: String,
    val shape: DemoSnapshots.Shape,
    val chipState: VerdictChipState,
    val visibleText: List<String>,
    val result: SimulatorResult,
)
