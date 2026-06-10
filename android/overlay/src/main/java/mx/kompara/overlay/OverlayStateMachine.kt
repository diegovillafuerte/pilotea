package mx.kompara.overlay

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import mx.kompara.capture.OfferEvent
import mx.kompara.metrics.OfferMetrics

/**
 * What the overlay should currently be rendering. The controller maps each state onto attach /
 * update / detach of the window, but the *decision* of what to show lives here so it can be tested
 * with virtual time and no Android.
 */
sealed interface OverlayVisibility {

    /** No card on screen (or never shown yet): the window should be detached. */
    data object Hidden : OverlayVisibility

    /** A card is on screen: show the chip for [metrics]. */
    data class Showing(val metrics: OfferMetrics) : OverlayVisibility
}

/**
 * Turns the raw [OfferEvent] stream into an [OverlayVisibility] stream, applying the show/hide
 * policy:
 *  - `Parsed` → evaluate the offer and **show immediately** (latency matters; the card is up now).
 *  - `NoCard` → don't hide instantly. Wait [graceMs] before hiding so a momentary mis-parse or a
 *    one-frame `NoCard` between two `Parsed` events doesn't make the chip flicker. A `Parsed`
 *    arriving inside the grace window cancels the pending hide.
 *
 * Implemented with [transformLatest] + a [delay]: each new upstream event cancels the previous
 * collector, so a fresh `Parsed` during the grace simply restarts in the "show" branch and the
 * delayed-hide of the prior `NoCard` never fires. [distinctUntilChanged] collapses repeats so the
 * controller only re-renders on real changes.
 *
 * [evaluate] is the injected `OfferCard -> OfferMetrics` step (mapping + engine); kept as a
 * parameter so the state machine itself is pure and the engine wiring is tested separately.
 */
class OverlayStateMachine(
    private val graceMs: Long = DEFAULT_GRACE_MS,
    private val evaluate: (OfferEvent.Parsed) -> OfferMetrics,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun visibility(events: Flow<OfferEvent>): Flow<OverlayVisibility> =
        events
            .transformLatest { event ->
                when (event) {
                    is OfferEvent.Parsed -> emit(OverlayVisibility.Showing(evaluate(event)))
                    is OfferEvent.NoCard -> {
                        // Hold the current state for the grace window; if a new event arrives,
                        // transformLatest cancels this block before the delay completes.
                        kotlinx.coroutines.delay(graceMs)
                        emit(OverlayVisibility.Hidden)
                    }
                    is OfferEvent.SpecDisabled -> {
                        // The platform's parser was remotely kill-switched (B-033). There's no
                        // verdict to show — hide the chip. A dedicated "actualizando soporte para
                        // Uber/DiDi" overlay state can layer on later (owned by the overlay task);
                        // hiding immediately is the safe default so we never show a stale verdict.
                        emit(OverlayVisibility.Hidden)
                    }
                }
            }
            .distinctUntilChanged()

    companion object {
        /** Grace period before hiding on a [OfferEvent.NoCard], to absorb one-frame flicker. */
        const val DEFAULT_GRACE_MS: Long = 500L
    }
}
