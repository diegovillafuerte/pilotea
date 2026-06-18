/**
 * /v1/me tests — focused on the derived `verified` (import/data verification)
 * field added for the driver-verification gate. Runs against pglite + a seeded
 * session.
 */

import { describe, it, expect, beforeEach } from "vitest";
import { Hono } from "hono";
import { drivers, imports } from "../db/schema.js";
import { createSession } from "../auth/sessions.js";
import { meRoutes } from "./me.js";
import { makeTestDb, type TestDb } from "../test/db.js";

let db: TestDb;
let app: Hono;
let token: string;
let driverId: string;

function asDb(d: TestDb) {
  return d as unknown as Parameters<typeof meRoutes>[0];
}

function getMe(auth = token) {
  return app.request("/v1/me", {
    headers: auth ? { authorization: `Bearer ${auth}` } : {},
  });
}

async function seedImport(status: string) {
  await db
    .insert(imports)
    .values({ driverId, platform: "uber", uploadType: "pdf", fileKey: "k", status });
}

beforeEach(async () => {
  db = await makeTestDb();
  app = new Hono();
  app.route("/v1", meRoutes(asDb(db)));
  const [driver] = await db
    .insert(drivers)
    .values({ phone: "+5215511114444", city: "CDMX" })
    .returning();
  driverId = driver!.id;
  token = await createSession(asDb(db), driverId);
});

describe("GET /v1/me — verified", () => {
  it("is false for a driver with no imports", async () => {
    const res = await getMe();
    expect(res.status).toBe(200);
    const body = (await res.json()) as { verified: boolean };
    expect(body.verified).toBe(false);
  });

  it("is true once the driver has a parsed import", async () => {
    await seedImport("parsed");
    const body = (await (await getMe()).json()) as { verified: boolean };
    expect(body.verified).toBe(true);
  });

  it("is false when the only import failed (and stays revocable)", async () => {
    await seedImport("failed");
    const body = (await (await getMe()).json()) as { verified: boolean };
    expect(body.verified).toBe(false);
  });

  it("401s without a bearer token", async () => {
    const res = await getMe("");
    expect(res.status).toBe(401);
  });
});
