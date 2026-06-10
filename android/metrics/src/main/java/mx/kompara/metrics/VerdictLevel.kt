package mx.kompara.metrics

/**
 * Traffic-light level the overlay paints on an offer.
 *
 * - [GREEN]  — both the $/km and $/hr floors are met; take it.
 * - [YELLOW] — exactly one floor is met (marginal), or only partial data was available to judge.
 * - [RED]    — neither floor is met, or the fare is missing so it can't be judged at all.
 */
enum class VerdictLevel {
    GREEN,
    YELLOW,
    RED,
}
