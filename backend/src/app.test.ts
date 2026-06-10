/**
 * API skeleton smoke tests — exercise each v1 route against an in-memory
 * pglite database via the Hono app's fetch handler (no network).
 */

import { describe, it, expect, beforeAll } from "vitest";
import { createApp } from "./app.js";
import { drivers } from "./db/schema.js";
import { makeTestDb, type TestDb } from "./test/db.js";

let db: TestDb;
let app: ReturnType<typeof createApp>;
let driverId: string;

const BEARER = { authorization: "Bearer test-token" } as const;

beforeAll(async () => {
  db = await makeTestDb({ seed: true });
  // createApp expects the runtime Database type; pglite TestDb is structurally
  // compatible for the query surface the routes use.
  app = createApp(db as unknown as Parameters<typeof createApp>[0]);
  const [d] = await db
    .insert(drivers)
    .values({ phone: "+5215512345678", city: "cdmx", tier: "free" })
    .returning();
  driverId = d!.id;
});

describe("GET /health", () => {
  it("reports ok", async () => {
    const res = await app.request("/health");
    expect(res.status).toBe(200);
    expect(await res.json()).toMatchObject({ status: "ok", service: "kompara-backend" });
  });
});

describe("GET /v1/benchmarks", () => {
  it("returns seeded population_stats for a city/platform", async () => {
    const res = await app.request("/v1/benchmarks?city=cdmx&platform=uber");
    expect(res.status).toBe(200);
    const body = (await res.json()) as { stats: unknown[] };
    // 5 efficiency metrics seeded per city/platform/period
    expect(body.stats.length).toBe(5);
  });

  it("400s when required query params are missing", async () => {
    const res = await app.request("/v1/benchmarks?city=cdmx");
    expect(res.status).toBe(400);
  });
});

describe("POST /v1/aggregates", () => {
  const payload = () => ({
    driverId,
    platform: "uber",
    weekStart: "2026-06-01",
    netEarnings: 4200.5,
    grossEarnings: 5600,
    totalTrips: 120,
    earningsPerTrip: 46.67,
    source: "captured" as const,
  });

  it("rejects requests without a bearer token", async () => {
    const res = await app.request("/v1/aggregates", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload()),
    });
    expect(res.status).toBe(401);
  });

  it("upserts a weekly aggregate and is idempotent on the unique key", async () => {
    const first = await app.request("/v1/aggregates", {
      method: "POST",
      headers: { "content-type": "application/json", ...BEARER },
      body: JSON.stringify(payload()),
    });
    expect(first.status).toBe(200);

    // Same driver × platform × week, different totals -> updates the same row.
    const second = await app.request("/v1/aggregates", {
      method: "POST",
      headers: { "content-type": "application/json", ...BEARER },
      body: JSON.stringify({ ...payload(), totalTrips: 130 }),
    });
    expect(second.status).toBe(200);
    const body = (await second.json()) as { aggregate: { totalTrips: number } };
    expect(body.aggregate.totalTrips).toBe(130);
  });
});

describe("POST /v1/telemetry", () => {
  it("accumulates counters on the unique key", async () => {
    const base = {
      hostPackage: "com.ubercab.driver",
      hostVersion: "4.500.10",
      specVersion: 3,
      day: "2026-06-09",
    };
    const r1 = await app.request("/v1/telemetry", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ ...base, attempts: 10, successes: 9, failures: 1 }),
    });
    expect(r1.status).toBe(200);

    const r2 = await app.request("/v1/telemetry", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ ...base, attempts: 5, successes: 5, failures: 0 }),
    });
    const body = (await r2.json()) as { counter: { attempts: number; successes: number } };
    expect(body.counter.attempts).toBe(15);
    expect(body.counter.successes).toBe(14);
  });
});

describe("GET /v1/parser-configs", () => {
  it("returns an empty list when no specs exist for a package", async () => {
    const res = await app.request("/v1/parser-configs?package=com.ubercab.driver");
    expect(res.status).toBe(200);
    const body = (await res.json()) as { configs: unknown[] };
    expect(body.configs).toEqual([]);
  });
});
