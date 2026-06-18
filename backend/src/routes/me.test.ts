/**
 * /v1/me tests — focused on the derived `verified` (import/data verification)
 * field added for the driver-verification gate. Runs against pglite + a seeded
 * session.
 */

import { describe, it, expect, beforeEach } from "vitest";
import { Hono } from "hono";
import { and, eq } from "drizzle-orm";
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

function patchMe(body: Record<string, unknown>, auth = token) {
  return app.request("/v1/me", {
    method: "PATCH",
    headers: {
      "content-type": "application/json",
      ...(auth ? { authorization: `Bearer ${auth}` } : {}),
    },
    body: JSON.stringify(body),
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

  it("is false when the only import failed", async () => {
    await seedImport("failed");
    const body = (await (await getMe()).json()) as { verified: boolean };
    expect(body.verified).toBe(false);
  });

  it("revokes: parsed → true, then flipping the import off 'parsed' → false", async () => {
    await seedImport("parsed");
    expect(((await (await getMe()).json()) as { verified: boolean }).verified).toBe(true);

    // Flip the import away from 'parsed' (e.g. found fraudulent) → verification
    // is recomputed and drops, since it's derived from the imports table.
    await db
      .update(imports)
      .set({ status: "revoked" })
      .where(and(eq(imports.driverId, driverId), eq(imports.status, "parsed")));
    expect(((await (await getMe()).json()) as { verified: boolean }).verified).toBe(false);
  });

  it("PATCH /v1/me also returns verified", async () => {
    await seedImport("parsed");
    const res = await patchMe({ city: "Guadalajara" });
    expect(res.status).toBe(200);
    const body = (await res.json()) as { verified: boolean; driver: { city: string } };
    expect(body.verified).toBe(true);
    expect(body.driver.city).toBe("Guadalajara");
  });

  it("does not leak another driver's parsed import", async () => {
    // A different driver's parsed import must not verify this driver.
    const [other] = await db
      .insert(drivers)
      .values({ phone: "+5215511115555", city: "CDMX" })
      .returning();
    await db
      .insert(imports)
      .values({ driverId: other!.id, platform: "uber", uploadType: "pdf", fileKey: "k", status: "parsed" });

    const body = (await (await getMe()).json()) as { verified: boolean };
    expect(body.verified).toBe(false);
  });

  it("401s without a bearer token", async () => {
    const res = await getMe("");
    expect(res.status).toBe(401);
  });
});
