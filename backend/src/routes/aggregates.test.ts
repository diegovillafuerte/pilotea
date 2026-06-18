/**
 * Aggregates (captured-sync) endpoint tests — PR-A. The captured sync now funnels
 * through the canonical field-level merge, so a routine sync can no longer
 * blind-clobber a previously-imported commission/total to null. Runs end-to-end
 * against an in-memory pglite DB + a seeded session.
 */

import { describe, it, expect, beforeEach } from "vitest";
import { Hono } from "hono";
import { eq } from "drizzle-orm";
import { drivers, weeklyAggregates } from "../db/schema.js";
import { createSession } from "../auth/sessions.js";
import { aggregatesRoutes } from "./aggregates.js";
import { makeTestDb, type TestDb } from "../test/db.js";

let db: TestDb;
let app: Hono;
let token: string;
let driverId: string;

function asDb(d: TestDb) {
  return d as unknown as Parameters<typeof aggregatesRoutes>[0];
}

function postAggregate(body: Record<string, unknown>, auth = token) {
  return app.request("/v1/aggregates", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      ...(auth ? { authorization: `Bearer ${auth}` } : {}),
    },
    body: JSON.stringify(body),
  });
}

beforeEach(async () => {
  db = await makeTestDb();
  app = new Hono();
  app.route("/v1", aggregatesRoutes(asDb(db)));

  const [driver] = await db
    .insert(drivers)
    .values({ phone: "+5215511113333", city: "CDMX" })
    .returning();
  driverId = driver!.id;
  token = await createSession(asDb(db), driverId);
});

describe("POST /v1/aggregates", () => {
  it("creates a fresh captured row", async () => {
    const res = await postAggregate({
      platform: "uber",
      weekStart: "2025-03-24",
      netEarnings: "1000.00",
      grossEarnings: "1400.00",
      totalTrips: 40,
      totalKm: "500.00",
      hoursOnline: "20.00",
    });
    expect(res.status).toBe(200);
    const rows = await db.select().from(weeklyAggregates).where(eq(weeklyAggregates.driverId, driverId));
    expect(rows).toHaveLength(1);
    expect(rows[0]!.source).toBe("captured");
    expect(Number(rows[0]!.earningsPerKm)).toBe(2); // recomputed: 1000 / 500
  });

  it("does NOT clobber an imported commission/field the captured sync omits, and becomes 'mixed'", async () => {
    // Seed an imported week with a commission the captured reader can never see.
    await db.insert(weeklyAggregates).values({
      driverId,
      platform: "uber",
      weekStart: "2025-03-24",
      netEarnings: "3850.50",
      grossEarnings: "5200.00",
      totalTrips: 72,
      hoursOnline: "45.50",
      platformCommissionPct: "20.19",
      source: "imported",
    });

    // Captured sync: new net/trips/km/hours, but NO commission.
    const res = await postAggregate({
      platform: "uber",
      weekStart: "2025-03-24",
      netEarnings: "4000.00",
      grossEarnings: "5300.00",
      totalTrips: 80,
      totalKm: "600.00",
      hoursOnline: "50.00",
    });
    expect(res.status).toBe(200);

    const rows = await db.select().from(weeklyAggregates).where(eq(weeklyAggregates.driverId, driverId));
    expect(rows).toHaveLength(1);
    const row = rows[0]!;
    // Captured values win for what it carries…
    expect(Number(row.netEarnings)).toBe(4000);
    expect(row.totalTrips).toBe(80);
    expect(Number(row.totalKm)).toBe(600);
    // …but the imported commission is PRESERVED (not clobbered to null).
    expect(Number(row.platformCommissionPct)).toBe(20.19);
    // Captured landing on imported → mixed.
    expect(row.source).toBe("mixed");
    // Ratios recomputed from the merged raw fields.
    expect(Number(row.earningsPerKm)).toBe(6.67); // 4000 / 600
    expect(Number(row.earningsPerHour)).toBe(80); // 4000 / 50
  });
});
