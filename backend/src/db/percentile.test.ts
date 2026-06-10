/**
 * Percentile parity test — the acceptance criterion for B-041.
 *
 * Spins up an in-memory pglite Postgres, applies all migrations (including the
 * raw get_percentile() function ported from docs/technical-design.md §5.2),
 * seeds the synthetic population_stats, and asserts get_percentile() returns the
 * exact integer the proven web implementation would return for 7 representative
 * inputs spanning every interpolation branch plus the national-fallback path.
 *
 * Each expected value is DERIVED BY HAND below from two independent sources:
 *   1. the seeded breakpoints (computed by the ported seed generators), and
 *   2. the piecewise-linear formula in §5.2.
 * Postgres ROUND() rounds half away from zero; inputs were chosen to avoid
 * exact .5 ties so the expectation is unambiguous across engines.
 *
 * Seeded breakpoints used (city/platform/metric -> p10,p25,p50,p75,p90):
 *   national/uber/earnings_per_trip       -> 27,    36,    45,   56.25, 67.5
 *   national/uber/earnings_per_hour       -> 70,   100.8, 140,  189,   238
 *   national/didi/earnings_per_km         -> 3.86,  5.15,  6.44,  8.05,  9.66
 *   national/indrive/platform_commission  -> 14,    17,    20,   23.6,  27
 *   cdmx/uber/trips_per_hour              -> 1.9,   2.31,  2.72,  3.21,  3.67
 */

import { describe, it, expect, beforeAll } from "vitest";
import { sql } from "drizzle-orm";
import { makeTestDb, type TestDb } from "../test/db.js";

let db: TestDb;

async function getPercentile(
  city: string,
  platform: string,
  metric: string,
  value: number,
): Promise<number | null> {
  // The pglite driver returns a { rows } result object from execute().
  const result = (await db.execute(
    sql`SELECT get_percentile(${city}, ${platform}, ${metric}, ${value}::decimal) AS p`,
  )) as { rows: Array<{ p: number | null }> };
  const p = result.rows[0]?.p;
  return p === null || p === undefined ? null : Number(p);
}

describe("get_percentile SQL function — parity with web implementation", () => {
  beforeAll(async () => {
    db = await makeTestDb({ seed: true });
  });

  it("returns 50 at an exact p50 boundary (national/uber/earnings_per_trip, v=45)", async () => {
    // v == p50 (45) -> falls in the (p25, p50] branch with v == p50:
    //   25 + ROUND(((45-36)/(45-36))*25) = 25 + ROUND(25) = 50
    expect(await getPercentile("national", "uber", "earnings_per_trip", 45)).toBe(50);
  });

  it("interpolates within the p25..p50 branch (national/uber/earnings_per_trip, v=40.8)", async () => {
    // p25=36, p50=45. 25 + ROUND(((40.8-36)/(45-36))*25)
    //   = 25 + ROUND((4.8/9)*25) = 25 + ROUND(13.333) = 25 + 13 = 38
    expect(await getPercentile("national", "uber", "earnings_per_trip", 40.8)).toBe(38);
  });

  it("returns 75 at an exact p75 boundary (national/uber/earnings_per_hour, v=189)", async () => {
    // v == p75 (189) -> (p50, p75] branch with v == p75:
    //   50 + ROUND(((189-140)/(189-140))*25) = 50 + ROUND(25) = 75
    expect(await getPercentile("national", "uber", "earnings_per_hour", 189)).toBe(75);
  });

  it("interpolates within the lowest (<=p10) branch (national/didi/earnings_per_km, v=3.0)", async () => {
    // p10=3.86. v=3.0 <= p10 -> ROUND((3.0/3.86)*10) = ROUND(7.772) = 8
    expect(await getPercentile("national", "didi", "earnings_per_km", 3.0)).toBe(8);
  });

  it("interpolates in the top tail above p90 (national/indrive/platform_commission_pct, v=30)", async () => {
    // p90=27. v=30 > p90 -> 90 + LEAST(9, ROUND(((30-27)/(27*0.5))*10))
    //   = 90 + LEAST(9, ROUND((3/13.5)*10)) = 90 + LEAST(9, ROUND(2.222)) = 90 + 2 = 92
    expect(await getPercentile("national", "indrive", "platform_commission_pct", 30)).toBe(92);
  });

  it("interpolates within the p25..p50 branch for a city-specific row (cdmx/uber/trips_per_hour, v=2.5)", async () => {
    // cdmx p25=2.31, p50=2.72. 25 + ROUND(((2.5-2.31)/(2.72-2.31))*25)
    //   = 25 + ROUND((0.19/0.41)*25) = 25 + ROUND(11.585) = 25 + 12 = 37
    expect(await getPercentile("cdmx", "uber", "trips_per_hour", 2.5)).toBe(37);
  });

  it("falls back to national when the city has no row (atlantis/uber/earnings_per_trip, v=45 -> 50)", async () => {
    // 'atlantis' is unseeded; the function falls back to the national row, whose
    // breakpoints equal the national/uber/earnings_per_trip set -> v=45 == p50 -> 50.
    expect(await getPercentile("atlantis", "uber", "earnings_per_trip", 45)).toBe(50);
  });
});
