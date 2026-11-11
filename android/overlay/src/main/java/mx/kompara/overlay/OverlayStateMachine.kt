package mx.kompara.overlay

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest
import mx.kompara.capture.OfferEvent
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.ui.components.ReglaMotion

sealed interface OverlayVisibility {
    data object Hidden : OverlayVisibility
    /**
     * The latest frame showed no offer: invisible and untouchable at once, keeping the window,
     * verdict, session and position. The next Parsed of the same session reveals it again.
     */
    data object SoftHidden : OverlayVisibility
    /**
     * Clear the prior offer before any suspendable evaluation/persistence. [notice] (WP-AD): the reader's unread
     * notice, not an offer; [event] is then its placement (the stripped card, identity-unresolved), never evaluated.
     */
    data class Reading(val offerKey: String, val event: OfferEvent.Parsed, val notice: Boolean = false) : OverlayVisibility
    data class Showing(val state: VerdictChipState, val offerKey: String, val event: OfferEvent.Parsed,
        val notice: Boolean = false) : OverlayVisibility
    /** Same-offer absence has survived the grace; fade before detaching the window. */
    data object Exiting : OverlayVisibility
}

/** Same-offer reparses swap atomically; a new session/host can never inherit an old judgement. */
class OverlayStateMachine(
    private val graceMs: Long = DEFAULT_GRACE_MS,
    /** The driver's unit for the unread notice's pill (WP-AD); an offer's verdict carries its row's own. */
    private val preferred: () -> PreferredMetric = { PreferredMetric.DEFAULT },
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
                    is OfferEvent.Unreadable -> {
                        // WP-AD: a notice under its own key, one per host, never a session's. Its pill is built
                        // here, never by [evaluate] (no ledger row); as an identity-unresolved placement it takes
                        // no touch. A Parsed that follows swaps in place under its own key.
                        val next = "${event.packageName}:$UNREAD_KEY"
                        val placement = OfferEvent.Parsed(event.packageName, event.timestampMs, event.card, identityUnresolved = true)
                        if (key != next) {
                            key = next
                            host = event.packageName
                            emit(OverlayVisibility.Reading(next, placement, notice = true))
                        }
                        emit(OverlayVisibility.Showing(VerdictChipState.unread(preferred()), next, placement, notice = true))
                    }
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

    companion object {
        const val DEFAULT_GRACE_MS: Long = 500L
        /** WP-AD: the unread notice's key suffix; a session key is its UUID (or start time, or fare). */
        private const val UNREAD_KEY = "unread"
    }
}
