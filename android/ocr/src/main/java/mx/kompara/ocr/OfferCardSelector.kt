package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.model.HostGeometryAdapter
import mx.kompara.parsers.model.HostLayoutObservation
import mx.kompara.parsers.model.HostGeometryDeriver
import mx.kompara.parsers.model.HostGeometrySource
import mx.kompara.parsers.model.HostRect
import mx.kompara.parsers.model.HostTextBounds
import mx.kompara.capture.HostWindowEvidence

/**
 * Picks the offer card for a frame from its OCR blocks, identifying the platform in the process.
 *
 * Uber is tried first: the two parsers are disjoint by fare format (Uber renders "MXN…", DiDi a bare
 * "$"), so whichever returns a card also names the host — `card.platform` is the host package, no
 * separate platform detector needed. (B-029-OCR: Uber's offer card is no longer accessibility-
 * readable, design §7 update 2026-06-15.)
 *
 * Cross-app hold: when the driver runs DiDi and an Uber broadcast pops, a frame where the Uber parse
 * momentarily drops (the animating map garbles the "Viaje:" trip-leg label) must NOT fall through to
 * DiDi. DiDi's bare-"$" regex would otherwise latch onto the on-screen "$0.00" earnings pill — or
 * Kompara's own "$0.00/km" chip text self-captured — and emit a verdict mis-attributed to DiDi (the
 * red "$0.00/km" chip seen over an Uber offer, 2026-06-15). So if the frame still carries the Uber
 * card *signature* (a non-bonus MXN fare + a leg), hold: return null and let [CardPresenceTracker]
 * keep the last good Uber verdict, rather than mis-attributing the screen to DiDi.
 */
internal fun selectOfferCard(
    uber: UberOcrParser,
    didi: DidiOcrParser,
    blocks: List<OcrBlock>,
    capturedAtMs: Long = 0,
    observation: HostLayoutObservation? = null,
    geometryAdapter: HostGeometryAdapter = HostGeometryAdapter(),
    displayBounds: HostRect? = null,
    density: Float = 1f,
    accessibility: HostWindowEvidence? = null,
    appearance: HostAppearanceSamples? = null,
): OfferCard? {
    val card = uber.parse(blocks)
        ?: (if (uber.hasCardSignature(blocks)) null else didi.parse(blocks)) ?: return null
    val nodes = accessibility?.takeIf {
        it.packageName == card.platform && it.displayBounds == displayBounds &&
            capturedAtMs - it.capturedAtMs in 0..1_000
    }
    val derived = displayBounds?.let { display ->
        HostGeometryDeriver.offer(card.platform, capturedAtMs, display, density,
            blocks.map { HostTextBounds(it.text, HostRect(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom)) } +
                nodes?.textBounds.orEmpty(),
            source = if (nodes != null) HostGeometrySource.OCR_AND_ACCESSIBILITY else HostGeometrySource.OCR_TEXT_BOUNDS,
            contentBounds = card.contentBounds, topChrome = nodes?.topChrome,
            observedCardBounds = nodes?.observedCardBounds,
            protectedControls = nodes?.protectedControls.orEmpty(), markers = nodes?.markers.orEmpty(),
            navigation = nodes?.navigation ?: false, recognizedOffer = true)
    }
    val geometry = geometryAdapter.adapt(card.platform, capturedAtMs, observation) ?: derived
    return card.copy(hostGeometry = geometry?.let { g ->
        g.copy(appearance = appearance?.appearance(g) ?: g.appearance)
    })
}
