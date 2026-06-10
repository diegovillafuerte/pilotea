/**
 * Breakage-alerting tests (B-034).
 *
 * computeAlerts: seed telemetry_counters and assert which (host, version) pairs
 * are flagged — a healthy version (low failure rate) is NOT flagged, a broken
 * new version (high failure rate, enough attempts) IS, and a noisy-but-tiny
 * sample stays below the attempts floor.
 *
 * GET /v1/telemetry/alerts: admin-token guard (503 without env, 401 missing,
 * 403 wrong, 200 correct) and that it surfaces the flagged pair.
 */

import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { createApp } from "../app.js";
import { telemetryCounters } from "../db/schema.js";
import { computeAlerts } from "./alerts.js";
import { makeTestDb, type TestDb } from "../test/db.js";

let db: TestDb;
const appDb = () => db as unknown as Parameters<typeof createApp>[0];
const alertDb = () => db as unknown as Parameters<typeof computeAlerts>[0];

// A counter row N days ago (telemetry_counters.day is a DATE).
function dayString(daysAgo: number): string {
  return new Date(Date.now() - daysAgo * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

async function seed() {
  await db.insert(telemetryCounters).values([
    // Healthy current Uber version: ~3% failures — NOT flagged.
    {
      hostPackage: "com.ubercab.driver",
      hostVersion: "4.500.10",
      specVersion: 3,
      attempts: 200,
      successes: 194,
      failures: 6,
      day: dayString(0),
    },
    // Broken NEW Uber version (UI rewrite): 60% failures, 100 attempts — FLAGGED.
    {
      hostPackage: "com.ubercab.driver",
      hostVersion: "4.999.0",
      specVersion: 3,
      attempts: 60,
      successes: 24,
      failures: 36,
      day: dayString(1),
    },
    // More of the broken version on another day — aggregates across the window.
    {
      hostPackage: "com.ubercab.driver",
      hostVersion: "4.999.0",
      specVersion: 3,
      attempts: 40,
      successes: 16,
      failures: 24,
      day: dayString(0),
    },
    // Noisy but tiny sample: 80% failures but only 10 attempts — below floor, NOT flagged.
    {
      hostPackage: "com.didiglobal.driver",
      hostVersion: "7.1.0",
      specVersion: 1,
      attempts: 10,
      successes: 2,
      failures: 8,
      day: dayString(0),
    },
    // Old data outside the 48h window — must be excluded.
    {
      hostPackage: "com.ubercab.driver",
      hostVersion: "4.999.0",
      specVersion: 3,
      attempts: 500,
      successes: 0,
      failures: 500,
      day: dayString(10),
    },
  ]);
}

beforeEach(async () => {
  db = await makeTestDb();
  await seed();
});

describe("computeAlerts", () => {
  it("flags the broken new version and not the healthy one", async () => {
    const stats = await computeAlerts(alertDb());

    const broken = stats.find(
      (s) => s.hostPackage === "com.ubercab.driver" && s.hostVersion === "4.999.0",
    );
    const healthy = stats.find(
      (s) => s.hostPackage === "com.ubercab.driver" && s.hostVersion === "4.500.10",
    );
    const tiny = stats.find((s) => s.hostPackage === "com.didiglobal.driver");

    // Broken version: aggregated 100 attempts / 60 failures = 60% > 20%, attempts ≥ 50.
    expect(broken).toBeDefined();
    expect(broken!.attempts).toBe(100);
    expect(broken!.failures).toBe(60);
    expect(broken!.failureRate).toBeCloseTo(0.6, 5);
    expect(broken!.flagged).toBe(true);

    // Healthy version: 3% failures → not flagged.
    expect(healthy).toBeDefined();
    expect(healthy!.flagged).toBe(false);

    // Tiny sample: high rate but below the attempts floor → not flagged.
    expect(tiny).toBeDefined();
    expect(tiny!.flagged).toBe(false);
  });

  it("excludes counters older than the window", async () => {
    const broken = (await computeAlerts(alertDb())).find(
      (s) => s.hostVersion === "4.999.0",
    );
    // The 10-day-old 500-failure row must NOT be summed in.
    expect(broken!.failures).toBe(60);
  });

  it("respects custom thresholds", async () => {
    // Raise the floor above the broken version's attempts → nothing flagged.
    const stats = await computeAlerts(alertDb(), { minAttempts: 1000 });
    expect(stats.every((s) => !s.flagged)).toBe(true);
  });
});

describe("GET /v1/telemetry/alerts", () => {
  const ORIGINAL = process.env.ADMIN_TOKEN;

  afterEach(() => {
    if (ORIGINAL === undefined) delete process.env.ADMIN_TOKEN;
    else process.env.ADMIN_TOKEN = ORIGINAL;
  });

  it("503s when ADMIN_TOKEN is not configured", async () => {
    delete process.env.ADMIN_TOKEN;
    const app = createApp(appDb());
    const res = await app.request("/v1/telemetry/alerts");
    expect(res.status).toBe(503);
  });

  it("401s without a bearer token", async () => {
    process.env.ADMIN_TOKEN = "s3cret-admin-token";
    const app = createApp(appDb());
    const res = await app.request("/v1/telemetry/alerts");
    expect(res.status).toBe(401);
  });

  it("403s with a wrong token", async () => {
    process.env.ADMIN_TOKEN = "s3cret-admin-token";
    const app = createApp(appDb());
    const res = await app.request("/v1/telemetry/alerts", {
      headers: { authorization: "Bearer wrong-token-here-padded" },
    });
    expect(res.status).toBe(403);
  });

  it("200s and returns flagged pairs with the correct token", async () => {
    process.env.ADMIN_TOKEN = "s3cret-admin-token";
    const app = createApp(appDb());
    const res = await app.request("/v1/telemetry/alerts", {
      headers: { authorization: "Bearer s3cret-admin-token" },
    });
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      flagged: { hostVersion: string }[];
      stats: unknown[];
    };
    expect(body.flagged.map((f) => f.hostVersion)).toContain("4.999.0");
    expect(body.flagged.every((f) => f.hostVersion !== "4.500.10")).toBe(true);
  });
});
