/**
 * Admin endpoint tests (B-043): POST /v1/admin/fold-stats.
 *
 * Asserts the ADMIN_TOKEN gate (503 when unset, 401 on missing/wrong token,
 * 200 with the right token) and that an authorized call actually folds real
 * aggregates into population_stats.
 */

import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { Hono } from "hono";
import { and, eq } from "drizzle-orm";
import { HTTPException } from "hono/http-exception";
import { drivers, populationStats, weeklyAggregates } from "../db/schema.js";
import { adminRoutes } from "./admin.js";
import { MIN_REAL_SAMPLE } from "../jobs/fold-population-stats.js";
import { makeTestDb, type TestDb } from "../test/db.js";

function asDb(d: TestDb) {
  return d as unknown as Parameters<typeof adminRoutes>[0];
}

const TOKEN = "test-admin-secret-token";

let db: TestDb;
let app: Hono;
let savedToken: string | undefined;

function buildApp(d: TestDb): Hono {
  const a = new Hono();
  a.route("/v1", adminRoutes(asDb(d)));
  // Mirror the production app's error mapping so HTTPException → JSON status.
  a.onError((err, c) => {
    if (err instanceof HTTPException) return c.json({ error: err.message }, err.status);
    throw err;
  });
  return a;
}

function isoWeeksAgo(weeks: number): string {
  const dt = new Date();
  dt.setUTCDate(dt.getUTCDate() - weeks * 7);
  return dt.toISOString().slice(0, 10);
}

beforeEach(async () => {
  savedToken = process.env.ADMIN_TOKEN;
  db = await makeTestDb({ seed: true });
  app = buildApp(db);
});

afterEach(() => {
  if (savedToken === undefined) delete process.env.ADMIN_TOKEN;
  else process.env.ADMIN_TOKEN = savedToken;
});

describe("auth gate", () => {
  it("503s when ADMIN_TOKEN is not configured (fail closed)", async () => {
    delete process.env.ADMIN_TOKEN;
    const res = await app.request("/v1/admin/fold-stats", { method: "POST" });
    expect(res.status).toBe(503);
  });

  it("401s with a missing token", async () => {
    process.env.ADMIN_TOKEN = TOKEN;
    const res = await app.request("/v1/admin/fold-stats", { method: "POST" });
    expect(res.status).toBe(401);
  });

  it("401s with a wrong token", async () => {
    process.env.ADMIN_TOKEN = TOKEN;
    const res = await app.request("/v1/admin/fold-stats", {
      method: "POST",
      headers: { authorization: "Bearer not-the-token" },
    });
    expect(res.status).toBe(401);
  });

  it("200s with the correct token", async () => {
    process.env.ADMIN_TOKEN = TOKEN;
    const res = await app.request("/v1/admin/fold-stats", {
      method: "POST",
      headers: { authorization: `Bearer ${TOKEN}` },
    });
    expect(res.status).toBe(200);
  });
});

describe("fold action", () => {
  it("folds accrued real aggregates into population_stats", async () => {
    process.env.ADMIN_TOKEN = TOKEN;
    const week = isoWeeksAgo(1);
    for (let i = 0; i < MIN_REAL_SAMPLE; i++) {
      const [d] = await db
        .insert(drivers)
        .values({ phone: `+5215599${String(100000 + i).slice(0, 6)}`, city: "guadalajara" })
        .returning();
      await db.insert(weeklyAggregates).values({
        driverId: d!.id,
        platform: "didi",
        weekStart: week,
        netEarnings: "900",
        grossEarnings: "1100",
        totalTrips: 25,
        earningsPerKm: String(5 + i * 0.1),
        source: "captured",
      });
    }

    const res = await app.request("/v1/admin/fold-stats", {
      method: "POST",
      headers: { authorization: `Bearer ${TOKEN}` },
    });
    expect(res.status).toBe(200);
    const body = (await res.json()) as { foldedReal: number; cells: Array<{ city: string }> };
    expect(body.foldedReal).toBeGreaterThan(0);

    const [cell] = await db
      .select()
      .from(populationStats)
      .where(
        and(
          eq(populationStats.city, "guadalajara"),
          eq(populationStats.platform, "didi"),
          eq(populationStats.metricName, "earnings_per_km"),
          eq(populationStats.period, "current"),
        ),
      )
      .limit(1);
    expect(cell!.isSynthetic).toBe(false);
    expect(cell!.sampleSize).toBe(MIN_REAL_SAMPLE);
  });
});
