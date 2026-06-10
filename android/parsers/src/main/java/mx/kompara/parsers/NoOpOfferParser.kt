package mx.kompara.parsers

import mx.kompara.data.model.Platform
import javax.inject.Inject

/**
 * Placeholder parser that recognizes nothing. It proves the DI wiring end-to-end until the
 * declarative spec engine and per-app specs land (B-027+).
 */
class NoOpOfferParser @Inject constructor() : OfferParser {
    override val platform: Platform = Platform.UNKNOWN
    override fun parse(textLines: List<String>): ParsedOffer? = null
}
