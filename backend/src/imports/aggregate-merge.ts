/**
 * weekly_aggregates write-path integrity (PR-A).
 *
 * Both the import path (POST /v1/imports) and the captured-sync path
 * (POST /v1/aggregates) upsert into the single weekly_aggregates row keyed by
 * (driver, platform, week). Source is NOT part of the key, so the upsert is
 * last-writer-wins — and a partial source (a commission-only Uber screenshot, or
 * a captured sync that lacks km) used to clobber good data with 0 / null.
 *
 * This module is the canonical, source-agnostic merge model the import-strategy
 * §0.5 review locked in:
 *
 *   1. NULL = "this source did not carry this metric" (never coerced to 0).
 *   2. Merge present-fields-only: an incoming null preserves the prior value
 *      (COALESCE(incoming, existing)); a present incoming value wins.
 *   3. RECOMPUTE the derived ratios (per-trip / -km / -hour, trips/hour) from the
 *      RESULTING merged raw fields — never carry a stored ratio independent of
 *      its numerator/denominator (these feed population_stats).
 *   4. Provenance: a row merged from both a captured and an imported contribution
 *      is 'mixed'; a pure import stays 'imported', a pure capture stays 'captured'.
 *   5. A FRESH row (no existing row) whose core earnings are all absent is NOT a
 *      real aggregate — refuse it rather than inserting a zero/junk row.
 *
 * Everything here is pure (no DB / IO) so it is trivially unit-testable and the
 * two routes share one implementation.
 */

/** Decimal columns are stored/returned by drizzle as strings; nullable. */
export type DecStr = string | null;

/** The mergeable column set of a weekly_aggregates row (raw + derived + source). */
export interface AggregateColumns {
  netEarnings: DecStr;
  grossEarnings: DecStr;
  totalTrips: number | null;
  totalKm: DecStr;
  hoursOnline: DecStr;
  earningsPerTrip: DecStr;
  earningsPerKm: DecStr;
  earningsPerHour: DecStr;
  tripsPerHour: DecStr;
  platformCommissionPct: DecStr;
  source: string;
}

/** Round to 2 decimals, matching the DECIMAL(10,2) columns and the parsers' maths. */
function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

function toNum(v: DecStr): number | null {
  if (v === null) return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

/** COALESCE(incoming, existing): the incoming value when present, else the prior. */
function coalesceDec(incoming: DecStr, existing: DecStr): DecStr {
  return incoming !== null ? incoming : existing;
}

function coalesceInt(incoming: number | null, existing: number | null): number | null {
  return incoming !== null ? incoming : existing;
}

/**
 * Recompute the four derived ratios from the merged RAW fields, mirroring the
 * parsers' calculateDerivedMetrics rounding (round to 2 decimals; null when the
 * inputs are missing or the denominator is ≤ 0). Returns decimal strings.
 */
function recomputeDerived(raw: {
  net: number | null;
  trips: number | null;
  km: number | null;
  hours: number | null;
}): Pick<
  AggregateColumns,
  "earningsPerTrip" | "earningsPerKm" | "earningsPerHour" | "tripsPerHour"
> {
  const { net, trips, km, hours } = raw;
  const epTrip = net !== null && trips !== null && trips > 0 ? round2(net / trips) : null;
  const epKm = net !== null && km !== null && km > 0 ? round2(net / km) : null;
  const epHour = net !== null && hours !== null && hours > 0 ? round2(net / hours) : null;
  const tphr = trips !== null && hours !== null && hours > 0 ? round2(trips / hours) : null;
  return {
    earningsPerTrip: epTrip === null ? null : epTrip.toFixed(2),
    earningsPerKm: epKm === null ? null : epKm.toFixed(2),
    earningsPerHour: epHour === null ? null : epHour.toFixed(2),
    tripsPerHour: tphr === null ? null : tphr.toFixed(2),
  };
}

/** True when the candidate carries no core earnings (net/gross/trips all null). */
export function hasNoCoreEarnings(v: {
  netEarnings: DecStr;
  grossEarnings: DecStr;
  totalTrips: number | null;
}): boolean {
  return v.netEarnings === null && v.grossEarnings === null && v.totalTrips === null;
}

export type MergeResult =
  | { kind: "rejected" }
  | { kind: "merged"; values: AggregateColumns };

/**
 * Merge an incoming aggregate contribution onto the existing row (or none).
 *
 * @param incoming the candidate column set parsed/validated from this request.
 * @param existing the current row's mergeable columns, or null on a fresh write.
 *
 * - No existing row + no core earnings → `rejected` (don't create a junk row).
 * - No existing row + has core earnings → insert the incoming values as-is, with
 *   derived ratios recomputed from its own raw fields.
 * - Existing row → COALESCE-merge incoming onto it, recompute derived ratios from
 *   the merged raw fields, and set the provenance ('mixed' iff the two sides have
 *   different non-'mixed' sources, else the shared source).
 */
export function mergeAggregate(
  incoming: AggregateColumns,
  existing: AggregateColumns | null,
): MergeResult {
  if (existing === null) {
    if (hasNoCoreEarnings(incoming)) return { kind: "rejected" };
    // Fresh insert: trust the incoming raw fields, but still recompute derived
    // ratios from them so a stored ratio is never inconsistent with its inputs.
    const derived = recomputeDerived({
      net: toNum(incoming.netEarnings),
      trips: incoming.totalTrips,
      km: toNum(incoming.totalKm),
      hours: toNum(incoming.hoursOnline),
    });
    return { kind: "merged", values: { ...incoming, ...derived } };
  }

  // COALESCE-merge raw fields: incoming wins where present, existing kept where
  // incoming is null.
  const netEarnings = coalesceDec(incoming.netEarnings, existing.netEarnings);
  const grossEarnings = coalesceDec(incoming.grossEarnings, existing.grossEarnings);
  const totalTrips = coalesceInt(incoming.totalTrips, existing.totalTrips);
  const totalKm = coalesceDec(incoming.totalKm, existing.totalKm);
  const hoursOnline = coalesceDec(incoming.hoursOnline, existing.hoursOnline);
  const platformCommissionPct = coalesceDec(
    incoming.platformCommissionPct,
    existing.platformCommissionPct,
  );

  // Derived ratios always come from the merged raw fields — never coalesced
  // independently (a stored ratio must agree with its numerator/denominator).
  const derived = recomputeDerived({
    net: toNum(netEarnings),
    trips: totalTrips,
    km: toNum(totalKm),
    hours: toNum(hoursOnline),
  });

  return {
    kind: "merged",
    values: {
      netEarnings,
      grossEarnings,
      totalTrips,
      totalKm,
      hoursOnline,
      ...derived,
      platformCommissionPct,
      source: mergeSource(existing.source, incoming.source),
    },
  };
}

/**
 * Provenance of a merged row. Two contributions of the same source keep it; a
 * captured + imported combination is 'mixed'. 'mixed' is absorbing (merging
 * anything into an already-mixed row stays mixed).
 */
export function mergeSource(existing: string, incoming: string): string {
  if (existing === incoming) return existing;
  if (existing === "mixed" || incoming === "mixed") return "mixed";
  // Different concrete sources (captured ↔ imported) → mixed.
  return "mixed";
}
