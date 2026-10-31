package mx.kompara.overlay

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest
import mx.kompara.capture.OfferEvent
import mx.kompara.ui.components.ReglaMotion

sealed interface OverlayVisibility {
    data object Hidden : OverlayVisibility
    /**
     * The latest frame showed no offer: invisible and untouchable at once, keeping the window,
     * verdict, session and position. The next Parsed of the same session reveals it again.
     */
    data object SoftHidden : OverlayVisibility
    /** Clear the prior offer before any suspendable evaluation/persistence. */
    data class Reading(val offerKey: String, val event: OfferEvent.Parsed) : OverlayVisibility
    data class Showing(val state: VerdictChipState, val offerKey: String, val event: OfferEvent.Parsed) : OverlayVisibility
    /** Same-offer absence has survived the grace; fade before detaching the window. */
    data object Exiting : OverlayVisibility
}

/** Same-offer reparses swap atomically; a new session/host can never inherit an old judgement. */
class OverlayStateMachine(
    private val graceMs: Long = DEFAULT_GRACE_MS,
    private val evaluate: suspend (OfferEvent.Parsed) -> VerdictChipState,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun visibility(events: Flow<OfferEvent>): Flow<OverlayVisibility> = flow {
        // State belongs to this collection, not the reusable machine instance.
        var host: String? = null
        var key: String? = null
        events
            // A held/uncertain frame is only the overlay's heartbeat: never a visibility change,
            // and it must not cancel an in-flight hide. Filtering inside transformLatest is too
            // late: it has already cancelled the grace.
            .filter { event -> event !is OfferEvent.LayoutUncertain }
            // A stream of missing OCR frames must not restart the 500ms timer indefinitely.
            .distinctUntilChanged { old, new ->
                old is OfferEvent.NoCard && new is OfferEvent.NoCard &&
                    old.packageName == new.packageName && old.reason == new.reason
            }
            .transformLatest { event ->
                when (event) {
                    is OfferEvent.Parsed -> {
                        val next = "${event.packageName}:${event.card.sessionId ?: event.card.sessionStartedAt ?: event.card.fare}"
                        if (key != next) {
                            key = next
                            host = event.packageName
                            emit(OverlayVisibility.Reading(next, event))
                        }
                        emit(OverlayVisibility.Showing(evaluate(event), next, event))
                    }
                    is OfferEvent.LayoutUncertain -> Unit
                    is OfferEvent.NoCard -> {
                        if (event.packageName != host || event.reason in setOf(OfferEvent.Reason.NO_SPEC,
                                OfferEvent.Reason.CONFIRMED_ABSENCE, OfferEvent.Reason.NOT_VISIBLE)) {
                            key = null; host = null
                            emit(OverlayVisibility.Hidden)
                        } else if (event.reason == OfferEvent.Reason.SIGNATURE_ABSENT) {
                            // Fast dismissal without ending the session: absence is not yet corroborated.
                            if (key != null) emit(OverlayVisibility.SoftHidden)
                        } else if (key != null) {
                            delay(graceMs)
                            emit(OverlayVisibility.Exiting)
                            delay(ReglaMotion.EXIT_MS.toLong())
                            key = null; host = null
                            emit(OverlayVisibility.Hidden)
                        }
                    }
                    is OfferEvent.SpecDisabled -> {
                        key = null; host = null
                        emit(OverlayVisibility.Hidden)
                    }
                }
            }
            .distinctUntilChanged { old, new ->
                // Refresh measured bounds even when the money is identical. Timestamp-only refreshes
                // with no geometry still dedupe; measured frames carry their own freshness timestamp.
                if (old is OverlayVisibility.Showing && new is OverlayVisibility.Showing)
                    old.offerKey == new.offerKey && old.state == new.state &&
                        old.event.card.hostGeometry == new.event.card.hostGeometry
                else old == new
            }
            .collect { emit(it) }
    }

    companion object { const val DEFAULT_GRACE_MS: Long = 500L }
}
