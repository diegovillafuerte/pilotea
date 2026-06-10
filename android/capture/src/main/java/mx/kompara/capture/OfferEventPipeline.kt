package mx.kompara.capture

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import mx.kompara.parsers.engine.SpecEngine
import mx.kompara.parsers.scrub.SnapshotScrubber
import mx.kompara.parsers.snapshot.ParserSnapshot
import mx.kompara.parsers.spec.ActiveSpecProvider
import mx.kompara.parsers.spec.BundledSpecs
import mx.kompara.parsers.spec.LoadedSpecs
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wires `:capture` to `:parsers` (TD-007, B-029; OTA specs B-033): consumes the coalesced
 * [ScreenSnapshot] stream from [EventPipeline], converts each to a framework-free [ParserSnapshot],
 * scrubs PII out of it, selects the matching spec from the *active* [LoadedSpecs] (remote-cached and
 * signature-verified when available, bundled otherwise), evaluates it with the [SpecEngine], and
 * emits a downstream [OfferEvent].
 *
 * Active-spec resolution (B-033): the [ActiveSpecProvider] supplies a [LoadedSpecs] whose
 * [LoadedSpecs.registry] already had remotely kill-switched packages removed. The pipeline keeps the
 * latest emitted value in a volatile field so the per-snapshot [process] stays synchronous; before
 * any emission it uses the bundled baseline ([initial]). When a snapshot's package is in
 * [LoadedSpecs.disabledPackages] the pipeline emits [OfferEvent.SpecDisabled] (not a silent
 * [OfferEvent.NoCard]) so the overlay can show "actualizando soporte…".
 *
 * The whole transform is a pure `map` over the snapshot flow, so it inherits [EventPipeline]'s
 * off-main-thread dispatch and debounce. Stateless and exception-safe: the engine never throws.
 *
 * PII posture (acceptance criterion "no raw screen text leaves the device"): every snapshot is run
 * through [SnapshotScrubber] BEFORE the engine sees it.
 */
@Singleton
class OfferEventPipeline @Inject constructor(
    private val source: EventPipeline,
    private val activeSpecs: ActiveSpecProvider,
    private val engine: SpecEngine,
    private val scrubber: SnapshotScrubber,
    private val versionCodes: HostVersionCodes,
) {
    /**
     * Latest active specs; seeded with the bundled baseline (loaded eagerly from `:parsers`
     * resources) so [process] works the instant the pipeline exists — before [trackActiveSpecs]
     * has collected anything from the OTA provider, and as the bundled-fallback when no remote
     * cache is available.
     */
    @Volatile
    private var loaded: LoadedSpecs = LoadedSpecs.bundled(BundledSpecs.load())

    /**
     * One [OfferEvent] per coalesced snapshot burst. Cold and shareable: collectors decide their own
     * scope (the overlay observes to draw/hide the verdict; the trip log persists parsed offers).
     */
    val offers: Flow<OfferEvent> = source.snapshots.map(::toOfferEvent)

    /**
     * Start tracking the active specs. Call once with a long-lived scope (the capture service's):
     * keeps [loaded] current as the OTA layer applies new bundles / kill switches. Returns nothing;
     * the collection is launched into [scope].
     */
    fun trackActiveSpecs(scope: CoroutineScope) {
        activeSpecs.specs.onEach { loaded = it }.launchIn(scope)
    }

    /** Run a single already-captured snapshot through the spec engine. Exposed for the service/tests. */
    fun process(snapshot: ScreenSnapshot): OfferEvent = toOfferEvent(snapshot)

    private fun toOfferEvent(snapshot: ScreenSnapshot): OfferEvent {
        val current = loaded
        // A remotely kill-switched platform: recognized, but deliberately turned off.
        if (current.isDisabled(snapshot.packageName)) {
            return OfferEvent.SpecDisabled(
                packageName = snapshot.packageName,
                timestampMs = snapshot.timestampMs,
            )
        }
        val parser: ParserSnapshot = scrubber.scrub(
            snapshot.toParserSnapshot(versionCodes.versionCodeOf(snapshot.packageName)),
        )
        val spec = current.registry.specFor(parser)
            ?: return OfferEvent.NoCard(
                packageName = snapshot.packageName,
                timestampMs = snapshot.timestampMs,
                reason = OfferEvent.Reason.NO_SPEC,
            )
        val card = engine.evaluate(parser, spec)
            ?: return OfferEvent.NoCard(
                packageName = snapshot.packageName,
                timestampMs = snapshot.timestampMs,
                reason = OfferEvent.Reason.NOT_AN_OFFER,
            )
        return OfferEvent.Parsed(
            packageName = snapshot.packageName,
            timestampMs = snapshot.timestampMs,
            card = card,
        )
    }
}

/**
 * Supplies the installed `versionCode` for a host package so the registry can pick a version-matched
 * spec. Backed on device by `PackageManager`; a fake is injected in tests. Kept as a fun interface so
 * the lookup stays trivially mockable and the pipeline never touches Android framework types itself.
 */
fun interface HostVersionCodes {
    /** The host's versionCode, or null when it can't be resolved (unknown ⇒ specs don't exclude). */
    fun versionCodeOf(packageName: String): Long?
}
