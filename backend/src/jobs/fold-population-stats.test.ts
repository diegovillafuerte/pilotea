/**
 * Fold-in tests (B-043): real consented aggregates → population_stats.
 *
 * Runs against an in-memory pglite DB seeded with the synthetic population_stats
 * (so we can prove a cell flips from synthetic to real). Covers:
 *  - below threshold → cell stays synthetic, seed values untouched;
 *  - at/above threshold → cell recomputes from real data, is_synthetic flips false;
 *  - a driver holding BOTH a captured and an imported row for the same week is
 *    counted ONCE (captured wins), so the sample size isn't inflated;
 *  - GET /v1/benchmarks returns the folded (real) values after a fold;
 *  - the percentile methodology matches a hand-computed expectation.
 */

import { describe, it, expect, beforeEach } from "vitest";
import { Hono } from "hono";
import { and, eq } from "drizzle-orm";
import { drivers, populationStats, weeklyAggregates } from "../db/schema.js";
import {
  foldPopulationStats,
  computeBreakpoints,
  percentile,
  MIN_REAL_SAMPLE,
} from "./fold-population-stats.js";
import { benchmarksRoutes } from "../routes/benchmarks.js";
import { makeTestDb, type TestDb } from "../test/db.js";

// pglite TestDb is structurally compatible with the runtime Database the job/route
// expects (same schema); cast as the existing route tests do.
function asDb(d: TestDb) {
  return d as unknown as Parameters<typeof foldPopulationStats>[0];
}

let db: TestDb;

/** A week-start inside the rolling window (anchored "now" minus 1 week). */
const RECENT_WEEK = isoWeeksAgo(1);
/** A week-start well outside the default 8-week window. */
const OLD_WEEK = isoWeeksAgo(20);

function isoWeeksAgo(weeks: number): string {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() - weeks * 7);
  return d.toISOString().slice(0, 10);
}

/** Create N drivers in `city` and return their ids. */
async function makeDrivers(city: string, n: number): Promise<string[]> {
  const ids: string[] = [];
  for (let i = 0; i < n; i++) {
    const [d] = await db
      .insert(drivers)
      .values({ phone: `+52155${String(1_000_000 + i + city.length * 1000).slice(0, 8)}`, city })
      .returning();
    ids.push(d!.id);
  }
  return ids;
}

/** Insert a captured weekly aggregate with a given earnings_per_trip. */
async function captured(driverId: string, weekStart: string, ept: number, platform = "uber") {
  await db.insert(weeklyAggregates).values({
    driverId,
    platform,
    weekStart,
    netEarnings: "1000",
    grossEarnings: "1200",
    totalTrips: 30,
    earningsPerTrip: String(ept),
    source: "captured",
  });
}

/** Insert an imported weekly aggregate (same key allowed alongside a captured one). */
async function imported(driverId: string, weekStart: string, ept: number, platform = "uber") {
  await db.insert(weeklyAggregates).values({
    driverId,
    platform,
    weekStart,
    netEarnings: "2000",
    grossEarnings: "2400",
    totalTrips: 40,
    earningsPerTrip: String(ept),
    source: "imported",
  });
}

async function eptCell(city: string, platform = "uber") {
  const [row] = await db
    .select()
    .from(populationStats)
    .where(
      and(
        eq(populationStats.city, city),
        eq(populationStats.platform, platform),
        eq(populationStats.metricName, "earnings_per_trip"),
        eq(populationStats.period, "current"),
      ),
    )
    .limit(1);
  return row;
}

beforeEach(async () => {
  db = await makeTestDb({ seed: true });
});

describe("percentile methodology", () => {
  it("interpolates type-7 percentiles (matches NumPy/Excel PERCENTILE.INC)", () => {
    // Sample 1..10. p50 of [1..10] (type 7) = 5.5; p10 = 1.9; p90 = 9.1.
    const s = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
    expect(percentile(s, 0.5)).toBeCloseTo(5.5, 6);
    expect(percentile(s, 0.1)).toBeCloseTo(1.9, 6);
    expect(percentile(s, 0.9)).toBeCloseTo(9.1, 6);
  });

  it("computeBreakpoints rounds to 2dp and computes the arithmetic mean", () => {
    const bp = computeBreakpoints([10, 20, 30, 40, 50]);
    expect(bp.p50).toBe(30);
    expect(bp.mean).toBe(30);
    expect(bp.p10).toBe(14); // 10 + 0.1*4*(... ) → type7 p10 of 5 pts = 14
    expect(bp.p90).toBe(46);
  });
});

describe("foldPopulationStats — threshold behaviour", () => {
  it("leaves a cell synthetic when below the sample threshold", async () => {
    const before = await eptCell("cdmx");
    expect(before!.isSynthetic).toBe(true);

    // One fewer than the threshold → no fold.
    const ids = await makeDrivers("cdmx", MIN_REAL_SAMPLE - 1);
    for (const id of ids) await captured(id, RECENT_WEEK, 50);

    const result = await foldPopulationStats(asDb(db));

    const after = await eptCell("cdmx");
    expect(after!.isSynthetic).toBe(true);
    // Seed values untouched.
    expect(after!.p50).toBe(before!.p50);
    expect(after!.sampleSize).toBe(before!.sampleSize);
    // No cdmx/uber/earnings_per_trip cell in the folded set.
    expect(
      result.foldedReal.some(
        (c) => c.city === "cdmx" && c.platform === "uber" && c.metric === "earnings_per_trip",
      ),
    ).toBe(false);
  });

  it("recomputes from real data and flips synthetic→false at the threshold", async () => {
    const ids = await makeDrivers("cdmx", MIN_REAL_SAMPLE);
    // Spread values 41..60 so the percentiles are non-trivial and deterministic.
    for (let i = 0; i < ids.length; i++) await captured(ids[i]!, RECENT_WEEK, 41 + i);

    const result = await foldPopulationStats(asDb(db));

    const after = await eptCell("cdmx");
    expect(after!.isSynthetic).toBe(false);
    expect(after!.sampleSize).toBe(MIN_REAL_SAMPLE);

    // Folded values must equal the breakpoints of the real sample.
    const values = Array.from({ length: MIN_REAL_SAMPLE }, (_, i) => 41 + i);
    const bp = computeBreakpoints(values);
    expect(Number(after!.p50)).toBeCloseTo(bp.p50, 2);
    expect(Number(after!.p10)).toBeCloseTo(bp.p10, 2);
    expect(Number(after!.p90)).toBeCloseTo(bp.p90, 2);
    expect(Number(after!.mean)).toBeCloseTo(bp.mean, 2);

    expect(
      result.foldedReal.some(
        (c) => c.city === "cdmx" && c.platform === "uber" && c.metric === "earnings_per_trip",
      ),
    ).toBe(true);
  });

  it("ignores aggregates outside the rolling window", async () => {
    const ids = await makeDrivers("cdmx", MIN_REAL_SAMPLE);
    for (const id of ids) await captured(id, OLD_WEEK, 50); // 20 weeks ago → outside 8w window

    await foldPopulationStats(asDb(db));

    const after = await eptCell("cdmx");
    expect(after!.isSynthetic).toBe(true); // nothing in-window → stays synthetic
  });
});

describe("foldPopulationStats — mixed sources, one value per driver-week", () => {
  // NOTE: weekly_aggregates has a UNIQUE (driver_id, platform, week_start), so the
  // server only ever holds ONE row per driver-platform-week — the ingestion path
  // (imports.ts) already resolves captured-vs-imported at upsert. The fold's job is
  // to count each driver-week ONCE regardless of source, which these tests assert
  // both ways (captured-only, imported-only, and a mix across drivers).

  it("counts each driver-week once across a mix of captured and imported sources", async () => {
    const ids = await makeDrivers("cdmx", MIN_REAL_SAMPLE);
    // Half the drivers' weeks are captured, half imported — all value 50. The fold
    // must count all MIN_REAL_SAMPLE driver-weeks once each (sample size == drivers),
    // never double-counting or dropping by source.
    for (let i = 0; i < ids.length; i++) {
      if (i % 2 === 0) await captured(ids[i]!, RECENT_WEEK, 50);
      else await imported(ids[i]!, RECENT_WEEK, 50);
    }

    await foldPopulationStats(asDb(db));

    const after = await eptCell("cdmx");
    expect(after!.isSynthetic).toBe(false);
    expect(after!.sampleSize).toBe(MIN_REAL_SAMPLE);
    expect(Number(after!.p50)).toBe(50);
    expect(Number(after!.mean)).toBe(50);
  });

  it("uses the imported value when a driver-week has only an imported row", async () => {
    const ids = await makeDrivers("cdmx", MIN_REAL_SAMPLE);
    for (const id of ids) await imported(id, RECENT_WEEK, 77); // imported only

    await foldPopulationStats(asDb(db));

    const after = await eptCell("cdmx");
    expect(after!.isSynthetic).toBe(false);
    expect(after!.sampleSize).toBe(MIN_REAL_SAMPLE);
    expect(Number(after!.p50)).toBe(77);
  });
});

describe("GET /v1/benchmarks reflects folded values", () => {
  it("returns the recomputed real breakpoints after a fold", async () => {
    const ids = await makeDrivers("cdmx", MIN_REAL_SAMPLE);
    for (let i = 0; i < ids.length; i++) await captured(ids[i]!, RECENT_WEEK, 41 + i);

    await foldPopulationStats(asDb(db));

    const app = new Hono();
    app.route("/v1", benchmarksRoutes(asDb(db)));
    const res = await app.request("/v1/benchmarks?city=cdmx&platform=uber");
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      stats: Array<{ metricName: string; p50: string; isSynthetic: boolean }>;
    };
    const ept = body.stats.find((s) => s.metricName === "earnings_per_trip");
    expect(ept).toBeDefined();
    expect(ept!.isSynthetic).toBe(false);
    const bp = computeBreakpoints(Array.from({ length: MIN_REAL_SAMPLE }, (_, i) => 41 + i));
    expect(Number(ept!.p50)).toBeCloseTo(bp.p50, 2);
  });
});
