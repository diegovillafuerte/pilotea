package mx.kompara.ocr

import mx.kompara.parsers.model.OfferCard

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
): OfferCard? =
    uber.parse(blocks)
        ?: if (uber.hasCardSignature(blocks)) null else didi.parse(blocks)
