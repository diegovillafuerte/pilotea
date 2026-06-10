package mx.kompara.capture

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mx.kompara.parsers.engine.SpecEngine
import mx.kompara.parsers.scrub.SnapshotScrubber
import mx.kompara.parsers.snapshot.ParserSnapshot
import mx.kompara.parsers.spec.SpecRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wires `:capture` to `:parsers` (TD-007, B-029): consumes the coalesced [ScreenSnapshot] stream
 * from [EventPipeline], converts each to a framework-free [ParserSnapshot], scrubs PII out of it,
 * selects the matching spec from the [SpecRegistry], evaluates it with the [SpecEngine], and emits a
 * downstream [OfferEvent] — `Parsed` when a card is recognized, `NoCard` otherwise.
 *
 * The whole transform is a pure `map` over the snapshot flow, so it inherits [EventPipeline]'s
 * off-main-thread dispatch and debounce. Stateless and exception-safe: the engine never throws, and
 * a host the registry doesn't target degrades to a `NoCard(NO_SPEC)` event rather than an error.
 *
 * PII posture (acceptance criterion "no raw screen text leaves the device"): every snapshot is run
 * through [SnapshotScrubber] BEFORE the engine sees it, so the [OfferEvent.Parsed.card]'s `raw` map
 * — the only place screen text survives — is already scrubbed. Downstream consumers (overlay/trip
 * log) therefore never receive un-masked passenger names, plates, phones, or street addresses.
 */
@Singleton
class OfferEventPipeline @Inject constructor(
    private val source: EventPipeline,
    private val registry: SpecRegistry,
    private val engine: SpecEngine,
    private val scrubber: SnapshotScrubber,
    private val versionCodes: HostVersionCodes,
) {
    /**
     * One [OfferEvent] per coalesced snapshot burst. Cold and shareable: collectors decide their own
     * scope (the overlay observes to draw/hide the verdict; the trip log persists parsed offers).
     */
    val offers: Flow<OfferEvent> = source.snapshots.map(::toOfferEvent)

    /** Run a single already-captured snapshot through the spec engine. Exposed for the service/tests. */
    fun process(snapshot: ScreenSnapshot): OfferEvent = toOfferEvent(snapshot)

    private fun toOfferEvent(snapshot: ScreenSnapshot): OfferEvent {
        val parser: ParserSnapshot = scrubber.scrub(
            snapshot.toParserSnapshot(versionCodes.versionCodeOf(snapshot.packageName)),
        )
        val spec = registry.specFor(parser)
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
