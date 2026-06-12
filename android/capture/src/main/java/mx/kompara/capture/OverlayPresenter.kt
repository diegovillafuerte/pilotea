package mx.kompara.capture

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * The seam the accessibility service uses to drive the verdict overlay, without `:capture` having to
 * depend on `:overlay` (the overlay depends on `:capture` for [OfferEvent], so the arrow must point
 * one way only). The real implementation lives in `:overlay` (`OverlayController`) and is bound into
 * the graph there; the service just injects this interface and hands it the offer stream.
 *
 * Keeping it an interface also means the service stays unit-testable with a fake presenter, and a
 * sibling agent's telemetry collector can sit on the same [OfferEventPipeline.offers] flow
 * independently — both are plain collectors of one cold flow.
 */
interface OverlayPresenter {

    /**
     * Begin showing/hiding the verdict overlay in response to [events]. Collected on [scope] (the
     * service scope), so when the service is destroyed and the scope is cancelled, the overlay
     * collection stops with it. Implementations must be safe to call once per service connection.
     *
     * [overlayContext] MUST be the accessibility service itself: a `TYPE_ACCESSIBILITY_OVERLAY`
     * window can only be added through the service's own [android.view.WindowManager], which carries
     * the accessibility window token. Using the application context throws `BadTokenException`.
     */
    fun start(scope: CoroutineScope, events: Flow<OfferEvent>, overlayContext: Context)

    /** Tear down any attached overlay window. Called from the service's onDestroy. */
    fun stop()
}
