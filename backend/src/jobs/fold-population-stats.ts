/**
 * Fold real consented weekly aggregates into population_stats (B-043).
 *
 * The consented data exchange runs two directions: drivers push their derived
 * weekly aggregates up (POST /v1/aggregates), and the app pulls city benchmarks
 * down (GET /v1/benchmarks). This job is the bridge — it turns the accumulating
 * real `weekly_aggregates` rows into the percentile breakpoints that power the
 * benchmarks, replacing the synthetic seeds city-by-city as real data grows.
 *
 * ── What it does ────────────────────────────────────────────────────────────
 * For every (city, platform, metric) cell, over a rolling N-week window
 * (default 8 weeks), it:
 *   1. collects one real value per **driver-week** (see de-dup rule below),
 *   2. if the cell has at least {@link MIN_REAL_SAMPLE} distinct driver-weeks,
 *      recomputes p10/p25/p50/p75/p90/mean from the real values and flips
 *      `is_synthetic = false`,
 *   3. otherwise leaves the existing synthetic seed untouched.
 *
 * Only `period = 'current'` is folded (the only period the seeds and the
 * benchmarks endpoint use today).
 *
 * ── City bucketing ──────────────────────────────────────────────────────────
 * A driver's city comes from `drivers.city` (lower-cased, trimmed). A row is
 * folded into its own city bucket **and** into the synthetic `national`
 * aggregate, so the national fallback also accrues real data. Drivers whose
 * city is null/blank or not one of the seeded cities still contribute to
 * `national` (the fallback), they just don't create a per-city cell.
 *
 * ── De-dup rule: one value per driver-week, captured beats imported ─────────
 * A driver can hold two rows for the same (platform, weekStart): a live
 * `captured` row and an `imported` one (upload parsing). They must count **once**
 * — and `captured` wins, mirroring the ingestion conflict rule in imports.ts
 * ("captured beats imported"). We dedupe to a single value per
 * (driverId, platform, weekStart) before the percentile math so a driver who
 * both captures and imports the same week is never double-counted.
 *
 * ── Methodology: percentile computation ─────────────────────────────────────
 * Breakpoints are computed in TypeScript (not SQL) so the same code path runs
 * under the pglite test harness and production Postgres. We use the
 * **nearest-rank with linear interpolation** method (a.k.a. "type 7" / the
 * default of NumPy's `percentile` and Excel's `PERCENTILE.INC`): for a sorted
 * sample of length n and percentile fraction q in [0,1],
 *     rank = q * (n - 1)
 *     value = sample[floor(rank)] + frac(rank) * (sample[ceil(rank)] - sample[floor(rank)])
 * This is stable, well-defined for any n ≥ 1, and matches the breakpoint
 * semantics the seed generators approximate. Mean is the arithmetic mean.
 * All outputs are rounded to 2 decimals to match the DECIMAL(10,2) columns.
 *
 * The metric value for a driver-week is the relevant column on `weekly_aggregates`
 * (e.g. `earnings_per_trip` → earningsPerTrip). Rows whose metric column is null
 * (the metric wasn't observed that week) are skipped for that metric only.
 */

import { and, eq, gte, isNotNull, sql } from "drizzle-orm";
import { drivers, populationStats, weeklyAggregates } from "../db/schema.js";
import type { Database } from "../db/client.js";

/** Minimum distinct real driver-weeks before a cell switches off its synthetic seed. */
export const MIN_REAL_SAMPLE = 20;

/** Rolling window (in weeks) of recent aggregates folded into the current stats. */
export const DEFAULT_WINDOW_WEEKS = 8;

/** The synthetic-fallback bucket every real driver-week also feeds. */
export const NATIONAL_CITY = "national";

/** The only period the seeds + benchmarks endpoint use today. */
const PERIOD = "current";

/**
 * The five metrics, mapped to their `weekly_aggregates` column. Mirrors the
 * METRICS list the seed generators + population_stats.metric_name use.
 */
const METRIC_COLUMNS = {
  earnings_per_trip: weeklyAggregates.earningsPerTrip,
  earnings_per_km: weeklyAggregates.earningsPerKm,
  earnings_per_hour: weeklyAggregates.earningsPerHour,
  trips_per_hour: weeklyAggregates.tripsPerHour,
  platform_commission_pct: weeklyAggregates.platformCommissionPct,
} as const;

type MetricName = keyof typeof METRIC_COLUMNS;

const METRIC_NAMES = Object.keys(METRIC_COLUMNS) as MetricName[];

interface Breakpoints {
  p10: number;
  p25: number;
  p50: number;
  p75: number;
  p90: number;
  mean: number;
}

/** Per-cell fold outcome, returned for logging/tests. */
export interface FoldedCell {
  city: string;
  platform: string;
  metric: MetricName;
  sampleSize: number;
  isSynthetic: boolean;
}

export interface FoldResult {
  /** Cells that crossed the threshold and were recomputed from real data. */
  foldedReal: FoldedCell[];
  /** Cells left as synthetic seeds (below threshold or no data). */
  keptSynthetic: number;
  /** Window start date (inclusive), YYYY-MM-DD. */
  windowStart: string;
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

/**
 * Type-7 percentile (linear interpolation between closest ranks) over a sorted,
 * ascending array of finite numbers. `q` is a fraction in [0,1]. Exported for
 * direct unit testing of the methodology.
 */
export function percentile(sortedAsc: number[], q: number): number {
  const n = sortedAsc.length;
  if (n === 0) throw new Error("percentile of empty sample");
  if (n === 1) return sortedAsc[0]!;
  const rank = q * (n - 1);
  const lo = Math.floor(rank);
  const hi = Math.ceil(rank);
  const frac = rank - lo;
  return sortedAsc[lo]! + frac * (sortedAsc[hi]! - sortedAsc[lo]!);
}

/** Compute all six breakpoints from a set of real metric values. */
export function computeBreakpoints(values: number[]): Breakpoints {
  const sorted = [...values].sort((a, b) => a - b);
  const mean = sorted.reduce((s, v) => s + v, 0) / sorted.length;
  return {
    p10: round2(percentile(sorted, 0.1)),
    p25: round2(percentile(sorted, 0.25)),
    p50: round2(percentile(sorted, 0.5)),
    p75: round2(percentile(sorted, 0.75)),
    p90: round2(percentile(sorted, 0.9)),
    mean: round2(mean),
  };
}

/** ISO date (YYYY-MM-DD) `weeks` weeks before `from` (default: now). */
export function windowStartDate(weeks: number, from: Date = new Date()): string {
  const d = new Date(from.getTime());
  d.setUTCDate(d.getUTCDate() - weeks * 7);
  return d.toISOString().slice(0, 10);
}

/** Lower-case + trim a driver city; empty/blank → null (no per-city bucket). */
function normalizeCity(city: string | null): string | null {
  if (!city) return null;
  const c = city.trim().toLowerCase();
  return c.length > 0 ? c : null;
}

/**
 * One real driver-week of metric values, already de-duped to captured-beats-
 * imported. Keyed by (driverId, platform, weekStart); carries the driver's
 * normalized city so the caller can bucket it.
 */
interface DriverWeek {
  city: string | null;
  platform: string;
  values: Partial<Record<MetricName, number>>;
}

/**
 * Fold real `weekly_aggregates` into `population_stats`.
 *
 * @param db Drizzle database (postgres in prod, pglite in tests).
 * @param opts.windowWeeks rolling window size (default {@link DEFAULT_WINDOW_WEEKS}).
 * @param opts.now reference "today" for the window (tests inject a fixed date).
 */
export async function foldPopulationStats(
  db: Database,
  opts: { windowWeeks?: number; now?: Date } = {},
): Promise<FoldResult> {
  const windowWeeks = opts.windowWeeks ?? DEFAULT_WINDOW_WEEKS;
  const windowStart = windowStartDate(windowWeeks, opts.now);

  // Pull every aggregate in-window joined to its driver's city. We dedupe and
  // bucket in memory: the data volume per fold (rolling 8 weeks of consented
  // aggregates) is modest, and keeping the percentile math in one place keeps
  // the methodology identical across Postgres and pglite.
  const rows = await db
    .select({
      driverId: weeklyAggregates.driverId,
      platform: weeklyAggregates.platform,
      weekStart: weeklyAggregates.weekStart,
      source: weeklyAggregates.source,
      city: drivers.city,
      earningsPerTrip: weeklyAggregates.earningsPerTrip,
      earningsPerKm: weeklyAggregates.earningsPerKm,
      earningsPerHour: weeklyAggregates.earningsPerHour,
      tripsPerHour: weeklyAggregates.tripsPerHour,
      platformCommissionPct: weeklyAggregates.platformCommissionPct,
    })
    .from(weeklyAggregates)
    .innerJoin(drivers, eq(weeklyAggregates.driverId, drivers.id))
    .where(gte(weeklyAggregates.weekStart, windowStart));

  // De-dup to one DriverWeek per (driverId, platform, weekStart); captured wins.
  const driverWeeks = new Map<string, { source: string; dw: DriverWeek }>();
  for (const r of rows) {
    const key = `${r.driverId}|${r.platform}|${r.weekStart}`;
    const existing = driverWeeks.get(key);
    // captured beats imported: keep an existing captured row over an imported one.
    if (existing && existing.source === "captured" && r.source !== "captured") continue;
    const values: Partial<Record<MetricName, number>> = {};
    const colVals: Record<MetricName, string | null> = {
      earnings_per_trip: r.earningsPerTrip,
      earnings_per_km: r.earningsPerKm,
      earnings_per_hour: r.earningsPerHour,
      trips_per_hour: r.tripsPerHour,
      platform_commission_pct: r.platformCommissionPct,
    };
    for (const m of METRIC_NAMES) {
      const v = colVals[m];
      if (v !== null && v !== undefined) {
        const num = Number(v);
        if (Number.isFinite(num)) values[m] = num;
      }
    }
    driverWeeks.set(key, {
      source: r.source,
      dw: { city: normalizeCity(r.city), platform: r.platform, values },
    });
  }

  // Bucket real values per (city, platform, metric). Every driver-week feeds
  // both its own city (when known + seeded) and the national fallback.
  const buckets = new Map<string, number[]>();
  const bucketKey = (city: string, platform: string, metric: MetricName) =>
    `${city}|${platform}|${metric}`;
  const push = (city: string, platform: string, metric: MetricName, value: number) => {
    const k = bucketKey(city, platform, metric);
    const arr = buckets.get(k);
    if (arr) arr.push(value);
    else buckets.set(k, [value]);
  };

  for (const { dw } of driverWeeks.values()) {
    for (const m of METRIC_NAMES) {
      const v = dw.values[m];
      if (v === undefined) continue;
      // National always accrues.
      push(NATIONAL_CITY, dw.platform, m, v);
      // Per-city only when the driver reported a (normalized) city.
      if (dw.city && dw.city !== NATIONAL_CITY) push(dw.city, dw.platform, m, v);
    }
  }

  // Recompute cells that crossed the threshold; leave the rest as synthetic.
  const foldedReal: FoldedCell[] = [];
  for (const [k, values] of buckets) {
    if (values.length < MIN_REAL_SAMPLE) continue; // below threshold → keep synthetic seed
    const [city, platform, metric] = k.split("|") as [string, string, MetricName];
    const bp = computeBreakpoints(values);

    await db
      .insert(populationStats)
      .values({
        city,
        platform,
        metricName: metric,
        period: PERIOD,
        sampleSize: values.length,
        p10: String(bp.p10),
        p25: String(bp.p25),
        p50: String(bp.p50),
        p75: String(bp.p75),
        p90: String(bp.p90),
        mean: String(bp.mean),
        isSynthetic: false,
      })
      .onConflictDoUpdate({
        target: [
          populationStats.city,
          populationStats.platform,
          populationStats.metricName,
          populationStats.period,
        ],
        set: {
          sampleSize: values.length,
          p10: String(bp.p10),
          p25: String(bp.p25),
          p50: String(bp.p50),
          p75: String(bp.p75),
          p90: String(bp.p90),
          mean: String(bp.mean),
          isSynthetic: false,
          updatedAt: sql`now()`,
        },
      });

    foldedReal.push({ city, platform, metric, sampleSize: values.length, isSynthetic: false });
  }

  // Count remaining synthetic cells (for logging/observability).
  const [{ count } = { count: 0 }] = await db
    .select({ count: sql<number>`count(*)::int` })
    .from(populationStats)
    .where(and(eq(populationStats.isSynthetic, true), isNotNull(populationStats.id)));

  return { foldedReal, keptSynthetic: Number(count), windowStart };
}
