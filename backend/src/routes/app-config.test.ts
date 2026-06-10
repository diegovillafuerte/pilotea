/**
 * App-config (paywall kill switch) route tests (B-050): GET /v1/config/app
 * (public) and PATCH /v1/config/app (admin).
 *
 * Asserts the public read returns the seeded default (paywall ON), defaults ON
 * when nothing is seeded (fail-soft so premium never accidentally unlocks), the
 * admin gate (503 unset / 401 missing-or-wrong / 200 right token), and that a
 * PATCH flips the singleton in place (no duplicate row).
 */

import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { appConfig } from "../db/schema.js";
import { appConfigRoutes } from "./app-config.js";
import { makeTestDb, type TestDb } from "../test/db.js";

function asDb(d: TestDb) {
  return d as unknown as Parameters<typeof appConfigRoutes>[0];
}

const TOKEN = "test-admin-secret-token";

let db: TestDb;
let app: Hono;
let savedToken: string | undefined;

function buildApp(d: TestDb): Hono {
  const a = new Hono();
  a.route("/v1", appConfigRoutes(asDb(d)));
  a.onError((err, c) => {
    if (err instanceof HTTPException) return c.json({ error: err.message }, err.status);
    throw err;
  });
  return a;
}

interface AppConfigResponse {
  paywallEnabled: boolean;
}

beforeEach(async () => {
  savedToken = process.env.ADMIN_TOKEN;
  db = await makeTestDb({ seedApp: true });
  app = buildApp(db);
});

afterEach(() => {
  if (savedToken === undefined) delete process.env.ADMIN_TOKEN;
  else process.env.ADMIN_TOKEN = savedToken;
});

describe("GET /v1/config/app", () => {
  it("returns the seeded default (paywall ON)", async () => {
    const res = await app.request("/v1/config/app");
    expect(res.status).toBe(200);
    const body = (await res.json()) as AppConfigResponse;
    expect(body.paywallEnabled).toBe(true);
  });

  it("is public — needs no auth header", async () => {
    const res = await app.request("/v1/config/app");
    expect(res.status).toBe(200);
  });

  it("defaults to ON when nothing is seeded (fail-soft, never auto-unlocks)", async () => {
    const empty = await makeTestDb();
    const emptyApp = buildApp(empty);
    const res = await emptyApp.request("/v1/config/app");
    expect(res.status).toBe(200);
    const body = (await res.json()) as AppConfigResponse;
    expect(body.paywallEnabled).toBe(true);
  });

  it("reflects a flag flipped to OFF (launch promo)", async () => {
    process.env.ADMIN_TOKEN = TOKEN;
    await app.request("/v1/config/app", {
      method: "PATCH",
      headers: { authorization: `Bearer ${TOKEN}`, "content-type": "application/json" },
      body: JSON.stringify({ paywallEnabled: false }),
    });
    const res = await app.request("/v1/config/app");
    const body = (await res.json()) as AppConfigResponse;
    expect(body.paywallEnabled).toBe(false);
  });
});

describe("PATCH /v1/config/app — admin gate", () => {
  const validBody = JSON.stringify({ paywallEnabled: false });

  it("503s when ADMIN_TOKEN is not configured (fail closed)", async () => {
    delete process.env.ADMIN_TOKEN;
    const res = await app.request("/v1/config/app", {
      method: "PATCH",
      headers: { "content-type": "application/json" },
      body: validBody,
    });
    expect(res.status).toBe(503);
  });

  it("401s with a missing token", async () => {
    process.env.ADMIN_TOKEN = TOKEN;
    const res = await app.request("/v1/config/app", {
      method: "PATCH",
      headers: { "content-type": "application/json" },
      body: validBody,
    });
    expect(res.status).toBe(401);
  });

  it("401s with a wrong token", async () => {
    process.env.ADMIN_TOKEN = TOKEN;
    const res = await app.request("/v1/config/app", {
      method: "PATCH",
      headers: { authorization: "Bearer nope", "content-type": "application/json" },
      body: validBody,
    });
    expect(res.status).toBe(401);
  });

  it("400s on an invalid body even with a valid token", async () => {
    process.env.ADMIN_TOKEN = TOKEN;
    const res = await app.request("/v1/config/app", {
      method: "PATCH",
      headers: { authorization: `Bearer ${TOKEN}`, "content-type": "application/json" },
      body: JSON.stringify({ paywallEnabled: "nope" }),
    });
    expect(res.status).toBe(400);
  });
});

describe("PATCH /v1/config/app — singleton upsert", () => {
  it("flips the flag in place without creating a second row", async () => {
    process.env.ADMIN_TOKEN = TOKEN;
    const res = await app.request("/v1/config/app", {
      method: "PATCH",
      headers: { authorization: `Bearer ${TOKEN}`, "content-type": "application/json" },
      body: JSON.stringify({ paywallEnabled: false }),
    });
    expect(res.status).toBe(200);
    const body = (await res.json()) as AppConfigResponse;
    expect(body.paywallEnabled).toBe(false);

    const rows = await db.select().from(appConfig);
    expect(rows).toHaveLength(1);
    expect(rows[0]!.paywallEnabled).toBe(false);
  });

  it("can be re-enabled (promo ends)", async () => {
    process.env.ADMIN_TOKEN = TOKEN;
    await app.request("/v1/config/app", {
      method: "PATCH",
      headers: { authorization: `Bearer ${TOKEN}`, "content-type": "application/json" },
      body: JSON.stringify({ paywallEnabled: false }),
    });
    const res = await app.request("/v1/config/app", {
      method: "PATCH",
      headers: { authorization: `Bearer ${TOKEN}`, "content-type": "application/json" },
      body: JSON.stringify({ paywallEnabled: true }),
    });
    const body = (await res.json()) as AppConfigResponse;
    expect(body.paywallEnabled).toBe(true);

    const rows = await db.select().from(appConfig);
    expect(rows).toHaveLength(1);
  });
});
