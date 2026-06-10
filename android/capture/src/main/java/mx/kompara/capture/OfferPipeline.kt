package mx.kompara.capture

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import mx.kompara.data.di.DefaultDispatcher
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.OfferMetrics
import mx.kompara.parsers.OfferParser
import mx.kompara.parsers.ParsedOffer
import javax.inject.Inject

/**
 * Event → parsed offer → verdict hot path (android-technical-design.md §1), wired from injected
 * collaborators. The actual AccessibilityService that feeds it node trees arrives in B-027; this
 * pipeline is the testable seam between parsing and the metrics engine.
 *
 * Work runs on the injected [DefaultDispatcher] so the budget-sensitive hot path stays off the
 * main thread and remains controllable in tests.
 */
class OfferPipeline @Inject constructor(
    private val parser: OfferParser,
    private val engine: NetProfitEngine,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {
    /** Parse a captured card; returns null when it isn't a recognizable offer. */
    suspend fun parse(textLines: List<String>): ParsedOffer? = withContext(dispatcher) {
        parser.parse(textLines)
    }

    /** Evaluate an already-parsed offer into net metrics + verdict using injected collaborators. */
    suspend fun evaluate(
        offer: ParsedOffer,
        evaluate: (ParsedOffer) -> OfferMetrics,
    ): OfferMetrics = withContext(dispatcher) {
        evaluate(offer)
    }
}
