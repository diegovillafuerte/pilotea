package mx.kompara.data.db.entity

/**
 * Where an aggregate row's numbers came from. Part of the primary key of
 * [WeeklyAggregateEntity]/[DailyAggregateEntity] so the two sources coexist for the same
 * `(platform, period)` without ever overwriting each other (B-039 reconciliation contract).
 *
 * - [CAPTURED] — computed on device from the capture stream (offers → trips → shifts). Provisional:
 *   earnings are offer-fare *estimates* (see [TripEntity.estimated]).
 * - [IMPORTED] — derived from an uploaded weekly summary / PDF (B-043/B-045). Authoritative for
 *   realized earnings.
 *
 * **Reconciliation contract (read before touching the rollup writers):** a [CAPTURED] row is owned
 * by [mx.kompara.data.rollup.RollupCalculator] and is freely recomputed/overwritten by the rollup
 * worker. An [IMPORTED] row is owned by the import path (B-045) and is NEVER written by the rollup
 * worker. The two never share a primary key, so a recompute of captured aggregates can never clobber
 * imported data and vice-versa. Merge/precedence for display (prefer imported where it exists) is a
 * read-time concern for B-045, not a write-time overwrite.
 */
enum class AggregateSource {
    CAPTURED,
    IMPORTED,
}
