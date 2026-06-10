package mx.kompara.data.db.entity

/**
 * What ultimately happened to a captured offer, inferred by the trip-lifecycle state machine
 * (B-039). Stored as the [name] string on [OfferEntity.outcome] so adding an outcome never
 * renumbers persisted rows.
 *
 * The lifecycle is: an offer card is [PENDING] the moment it's parsed; it then resolves to exactly
 * one of [ACCEPTED] (card dismissed + host window transitioned to a trip-like state), [DECLINED]
 * (card dismissed with no trip transition — the driver tapped no / the card timed out fast), or
 * [EXPIRED] (the card simply disappeared without any decision signal we could read). Only [ACCEPTED]
 * offers spawn a [TripEntity] and feed earnings; the rest exist purely for acceptance-rate analytics
 * and must never pollute the ledger.
 *
 * The DECLINED/EXPIRED split is a best-effort heuristic — on-device we cannot read the tap — so both
 * are treated identically for earnings (excluded) and only distinguished for analytics. See
 * `mx.kompara.capture.lifecycle.TripLifecycleTracker` for the inference rules and the on-device
 * calibration tech-debt note.
 */
enum class OfferOutcome {
    /** Just seen; not yet resolved. */
    PENDING,

    /** Inferred accepted: card gone + host transitioned to a trip-like state within the window. */
    ACCEPTED,

    /** Inferred declined: card gone with no trip transition, fast enough to look like a tap. */
    DECLINED,

    /** Card gone with no decision signal we could attribute — counted against acceptance like a decline. */
    EXPIRED,
}
