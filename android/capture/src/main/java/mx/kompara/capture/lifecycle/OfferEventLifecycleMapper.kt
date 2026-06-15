package mx.kompara.capture.lifecycle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import mx.kompara.capture.EventPipeline
import mx.kompara.capture.KomparaAccessibilityService
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OfferEventPipeline
import mx.kompara.capture.ScreenSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reduces the coalesced [ScreenSnapshot] stream into the [LifecycleSignal] stream the
 * [TripLifecycleTracker] consumes (B-039). This is the only place that touches the messy "what is
 * this screen?" judgement; the tracker downstream sees a clean four-signal vocabulary.
 *
 * It runs each snapshot back through [OfferEventPipeline.process] (the *same* pure spec evaluation the
 * overlay/telemetry use — no pipeline restructuring) to learn whether the screen is a parsed offer
 * card. When it isn't, it classifies the window as trip-like vs idle using [TripStateMarkers] matched
 * against the snapshot's view-ids + text — which is where the **window-state signals** come from.
 *
 * Markers are data-driven and documented as needing on-device calibration (see [TripStateMarkers] and
 * techdebt). The snapshot is consumed read-only; no PII leaves this layer (marker matching is local).
 */
@Singleton
class OfferEventLifecycleMapper @Inject constructor(
    private val eventPipeline: EventPipeline,
    private val offerPipeline: OfferEventPipeline,
    private val markers: TripStateMarkers = TripStateMarkers.DEFAULT,
) {

    /**
     * The lifecycle-signal stream driven by the live capture pipeline's coalesced snapshots. Hot
     * (the underlying [EventPipeline.snapshots] is a SharedFlow fed by the connected service), so it
     * is safe to collect from an application-scoped coroutine exactly like the B-034 telemetry
     * collector — it simply emits nothing until the accessibility service is connected.
     */
    fun signals(): Flow<LifecycleSignal> = signals(eventPipeline.snapshots)

    /**
     * Map the given [snapshots] flow to lifecycle signals. Exposed for tests.
     *
     * Snapshots from [KomparaAccessibilityService.OCR_OWNED_PACKAGES] are dropped: their offer cards
     * aren't in the node tree (design §7.1), so the OCR path owns their whole lifecycle via
     * [OcrLifecycleBus] — one writer per platform, exactly as the overlay bus does. Without this the
     * node path would emit (always-Idle, since it can't read the card) signals that fight the OCR
     * verdict's offer/trip signals on the merged stream `:app` feeds the tracker.
     */
    fun signals(snapshots: Flow<ScreenSnapshot>): Flow<LifecycleSignal> =
        snapshots
            .filterNot { it.packageName in KomparaAccessibilityService.OCR_OWNED_PACKAGES }
            .map { classify(it) }

    /** Classify a single snapshot. Exposed for tests. */
    fun classify(snapshot: ScreenSnapshot): LifecycleSignal {
        return when (val event = offerPipeline.process(snapshot)) {
            is OfferEvent.Parsed -> LifecycleSignal.OfferSeen(
                packageName = snapshot.packageName,
                timestampMs = snapshot.timestampMs,
                card = event.card,
            )
            // Not a card: decide trip-like vs idle from the window's view-ids/text.
            else -> {
                val hints = snapshot.nodes.asSequence()
                    .flatMap { sequenceOf(it.viewId, it.text) }
                    .filterNotNull()
                    .toList()
                if (markers.isTripLike(snapshot.packageName, hints)) {
                    LifecycleSignal.TripStateEntered(snapshot.packageName, snapshot.timestampMs)
                } else {
                    LifecycleSignal.IdleStateEntered(snapshot.packageName, snapshot.timestampMs)
                }
            }
        }
    }
}
