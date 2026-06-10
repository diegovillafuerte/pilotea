/**
 * Fiscal-config route tests (B-051): GET /v1/config/fiscal (public) and
 * PATCH /v1/config/fiscal (admin).
 *
 * Asserts the public read returns the latest year's seeded values, the admin
 * gate (503 unset / 401 missing-or-wrong / 200 right token), that a PATCH
 * upserts a year, and that GET tracks the newest year.
 */

import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { Hono } from "hono";
import { eq } from "drizzle-orm";
import { HTTPException } from "hono/http-exception";
import { fiscalConfig } from "../db/schema.js";
import { fiscalConfigRoutes } from "./fiscal-config.js";
import { makeTestDb, type TestDb } from "../test/db.js";

function asDb(d: TestDb) {
  return d as unknown as Parameters<typeof fiscalConfigRoutes>[0];
}

const TOKEN = "test-admin-secret-token";

let db: TestDb;
let app: Hono;
let savedToken: string | undefined;

function buildApp(d: TestDb): Hono {
  const a = new Hono();
  a.route("/v1", fiscalConfigRoutes(asDb(d)));
  a.onError((err, c) => {
    if (err instanceof HTTPException) return c.json({ error: err.message }, err.status);
    throw err;
  });
  return a;
}

interface FiscalResponse {
  imssMonthlyThresholdMxn: number;
  minimumWageDailyMxn: number;
  year: number;
  updatedAt: string;
}

beforeEach(async () => {
  savedToken = process.env.ADMIN_TOKEN;
  db = await makeTestDb({ seedFiscal: true });
  app = buildApp(db);
});

afterEach(() => {
  if (savedToken === undefined) delete process.env.ADMIN_TOKEN;
  else process.env.ADMIN_TOKEN = savedToken;
});

describe("GET /v1/config/fiscal", () => {
  it("returns the seeded 2026 values as numbers", async () => {
    const res = await app.request("/v1/config/fiscal");
    expect(res.status).toBe(200);
    const body = (await res.json()) as FiscalResponse;
    expect(body.year).toBe(2026);
    expect(body.imssMonthlyThresholdMxn).toBe(8364);
    expect(body.minimumWageDailyMxn).toBeCloseTo(278.8, 2);
    expect(typeof body.updatedAt).toBe("string");
  });

  it("is public — needs no auth header", async () => {
    const res = await app.request("/v1/config/fiscal");
    expect(res.status).toBe(200);
  });

  it("404s when no config is seeded (app falls back to its bundled default)", async () => {
    const empty = await makeTestDb();
    const emptyApp = buildApp(empty);
    const res = await emptyApp.request("/v1/config/fiscal");
    expect(res.status).toBe(404);
  });

  it("returns the newest year when multiple exist", async () => {
    process.env.ADMIN_TOKEN = TOKEN;
    await app.request("/v1/config/fiscal", {
      method: "PATCH",
      headers: { authorization: `Bearer ${TOKEN}`, "content-type": "application/json" },
      body: JSON.stringify({
        year: 2027,
        minimumWageDailyMxn: 300,
        imssMonthlyThresholdMxn: 9000,
      }),
    });
    const res = await app.request("/v1/config/fiscal");
    const body = (await res.json()) as FiscalResponse;
    expect(body.year).toBe(2027);
    expect(body.imssMonthlyThresholdMxn).toBe(9000);
  });
});

describe("PATCH /v1/config/fiscal — admin gate", () => {
  const validBody = JSON.stringify({
    year: 2026,
    minimumWageDailyMxn: 290,
    imssMonthlyThresholdMxn: 8700,
  });

  it("503s when ADMIN_TOKEN is not configured (fail closed)", async () => {
    delete process.env.ADMIN_TOKEN;
    const res = await app.request("/v1/config/fiscal", {
      method: "PATCH",
      headers: { "content-type": "application/json" },
      body: validBody,
    });
    expect(res.status).toBe(503);
  });

  it("401s with a missing token", async () => {
    process.env.ADMIN_TOKEN = TOKEN;
    const res = await app.request("/v1/config/fiscal", {
      method: "PATCH",
      headers: { "content-type": "application/json" },
      body: validBody,
    });
    expect(res.status).toBe(401);
  });

  it("401s with a wrong token", async () => {
    process.env.ADMIN_TOKEN = TOKEN;
    const res = await app.request("/v1/config/fiscal", {
      method: "PATCH",
      headers: { authorization: "Bearer nope", "content-type": "application/json" },
      body: validBody,
    });
    expect(res.status).toBe(401);
  });

  it("400s on an invalid body even with a valid token", async () => {
    process.env.ADMIN_TOKEN = TOKEN;
    const res = await app.request("/v1/config/fiscal", {
      method: "PATCH",
      headers: { authorization: `Bearer ${TOKEN}`, "content-type": "application/json" },
      body: JSON.stringify({ year: 2026, minimumWageDailyMxn: -1, imssMonthlyThresholdMxn: 8700 }),
    });
    expect(res.status).toBe(400);
  });
});

describe("PATCH /v1/config/fiscal — upsert", () => {
  it("updates an existing year in place (no duplicate row)", async () => {
    process.env.ADMIN_TOKEN = TOKEN;
    const res = await app.request("/v1/config/fiscal", {
      method: "PATCH",
      headers: { authorization: `Bearer ${TOKEN}`, "content-type": "application/json" },
      body: JSON.stringify({
        year: 2026,
        minimumWageDailyMxn: 285.5,
        imssMonthlyThresholdMxn: 8565,
      }),
    });
    expect(res.status).toBe(200);
    const body = (await res.json()) as FiscalResponse;
    expect(body.imssMonthlyThresholdMxn).toBe(8565);

    const rows = await db.select().from(fiscalConfig).where(eq(fiscalConfig.year, 2026));
    expect(rows).toHaveLength(1);
    expect(Number(rows[0]!.imssMonthlyThresholdMxn)).toBe(8565);
  });

  it("inserts a brand-new year", async () => {
    process.env.ADMIN_TOKEN = TOKEN;
    const res = await app.request("/v1/config/fiscal", {
      method: "PATCH",
      headers: { authorization: `Bearer ${TOKEN}`, "content-type": "application/json" },
      body: JSON.stringify({
        year: 2028,
        minimumWageDailyMxn: 320,
        imssMonthlyThresholdMxn: 9600,
      }),
    });
    expect(res.status).toBe(200);
    const rows = await db.select().from(fiscalConfig);
    expect(rows.map((r) => r.year).sort()).toEqual([2026, 2028]);
  });
});
