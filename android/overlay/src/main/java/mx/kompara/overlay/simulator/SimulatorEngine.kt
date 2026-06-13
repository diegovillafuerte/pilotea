package mx.kompara.overlay.simulator

import mx.kompara.data.model.Platform
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.metrics.CostProfile
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.OfferMetrics
import mx.kompara.overlay.OfferMapping
import mx.kompara.parsers.engine.SpecEngine
import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.snapshot.DemoSnapshots
import mx.kompara.parsers.snapshot.ParserSnapshot
import mx.kompara.parsers.spec.SpecRegistry

/**
 * Replays the bundled demo offer snapshots through the **real** Kompara pipeline:
 *
 * ```
 * ParserSnapshot ──SpecEngine──▶ OfferCard ──OfferMapping──▶ TripOffer ──NetProfitEngine──▶ OfferMetrics ──▶ Verdict
 * ```
 *
 * Nothing here is faked: the same [SpecEngine] + [SpecRegistry] that read live Uber/DiDi cards parse
 * the demo snapshots, and the same [NetProfitEngine] that powers the floating overlay produces the
 * verdict — applying the driver's *current* threshold and cost profile. So when the driver nudges
 * the $/km floor in the simulator, the demo verdicts re-grade exactly as a real offer would
 * (asserted by `SimulatorEngineTest.thresholdFlipsVerdict`).
 *
 * Pure and Android-free (it only touches classpath resources), so the whole replay is unit-testable
 * on the JVM without an emulator.
 */
class SimulatorEngine(
    private val registry: SpecRegistry,
    private val specEngine: SpecEngine = SpecEngine(),
    private val netProfitEngine: NetProfitEngine = NetProfitEngine(),
) {

    /**
     * Evaluate a single demo [offer] against the driver's [costProfile] and the [threshold] for its
     * platform.
     *
     * @throws IllegalStateException if no bundled spec recognizes the demo snapshot — that means the
     *   demo fixtures drifted from the shipped specs, which a test must catch loudly rather than
     *   silently showing a blank chip.
     */
    fun evaluate(
        offer: DemoSnapshots.DemoOffer,
        costProfile: CostProfile,
        threshold: PlatformThreshold,
        preferredMetric: PreferredMetric = PreferredMetric.DEFAULT,
    ): SimulatorResult {
        val snapshot = DemoSnapshots.load(offer)
        return evaluate(offer, snapshot, costProfile, threshold, preferredMetric)
    }

    /** Overload that takes a pre-loaded [snapshot] (lets the UI cache the decode). */
    fun evaluate(
        offer: DemoSnapshots.DemoOffer,
        snapshot: ParserSnapshot,
        costProfile: CostProfile,
        threshold: PlatformThreshold,
        preferredMetric: PreferredMetric = PreferredMetric.DEFAULT,
    ): SimulatorResult {
        val spec = registry.specFor(snapshot)
            ?: error("No bundled spec recognizes demo snapshot ${offer.id} (${snapshot.packageName})")
        val card = specEngine.evaluate(snapshot, spec)
            ?: error("Spec ${spec.targetPackage} did not detect demo snapshot ${offer.id} as an offer card")

        val tripOffer = OfferMapping.toTripOffer(card)
        val metrics = netProfitEngine.evaluate(tripOffer, costProfile, threshold, preferredMetric)
        val platform = OfferMapping.platformOf(card.platform)

        return SimulatorResult(
            offer = offer,
            platform = platform,
            card = card,
            visibleText = visibleLines(snapshot),
            metrics = metrics,
        )
    }

    private fun visibleLines(snapshot: ParserSnapshot): List<String> =
        snapshot.nodes
            .sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
            .mapNotNull { it.text?.takeIf(String::isNotBlank) }

    companion object {
        /** Build a [SimulatorEngine] over the same bundled specs the live reader uses. */
        fun bundled(): SimulatorEngine =
            SimulatorEngine(registry = mx.kompara.parsers.spec.BundledSpecs.registry())
    }
}

/**
 * The fully-replayed result for one demo offer: the canonical [platform], the parsed [card], the
 * [metrics]/verdict the real engine produced, and the [visibleText] lines (in reading order) so the
 * UI can render a faithful mock of the host-app offer card.
 */
data class SimulatorResult(
    val offer: DemoSnapshots.DemoOffer,
    val platform: Platform,
    val card: OfferCard,
    val visibleText: List<String>,
    val metrics: OfferMetrics,
)
