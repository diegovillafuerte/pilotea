/**
 * /v1/me tests — the derived `verified` (import/data verification) field added
 * for the driver-verification gate, and DELETE /v1/me's removal of the stored
 * import originals. Runs against pglite + a seeded session + in-memory storage.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { Hono } from "hono";
import { randomUUID } from "node:crypto";
import { and, eq } from "drizzle-orm";
import { drivers, imports } from "../db/schema.js";
import { createSession } from "../auth/sessions.js";
import { MemoryStorage } from "../imports/storage.js";
import { meRoutes } from "./me.js";
import { makeTestDb, type TestDb } from "../test/db.js";

/**
 * MemoryStorage whose deleteFile rejects for the keys in `failing` (an R2 outage), and can run a
 * one-shot `beforeDelete` hook to interleave a concurrent write mid-deletion.
 */
class FlakyStorage extends MemoryStorage {
  readonly failing = new Set<string>();
  beforeDelete: (() => Promise<void>) | null = null;

  override async deleteFile(key: string): Promise<void> {
    const hook = this.beforeDelete;
    this.beforeDelete = null;
    if (hook) await hook();
    if (this.failing.has(key)) {
      throw new Error(`Error al eliminar archivo de R2 (key: ${key}): service unavailable`);
    }
    return super.deleteFile(key);
  }
}

let db: TestDb;
let storage: FlakyStorage;
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

function deleteMe(auth = token) {
  return app.request("/v1/me", {
    method: "DELETE",
    headers: auth ? { authorization: `Bearer ${auth}` } : {},
  });
}

async function seedImport(status: string) {
  await db
    .insert(imports)
    .values({ driverId, platform: "uber", uploadType: "pdf", fileKey: "k", status });
}

/**
 * Seed a parsed import whose originals exist in storage, keyed exactly like POST /v1/imports keys
 * them: one file → `{driverId}/{importId}.pdf`; several (DiDi) → `{driverId}/{importId}_{i}.png`,
 * comma-joined into `file_key`. Returns the object keys.
 */
async function seedStoredImport(owner: string, files = 1): Promise<string[]> {
  const importId = randomUUID();
  const keys =
    files === 1
      ? [`${owner}/${importId}.pdf`]
      : Array.from({ length: files }, (_, i) => `${owner}/${importId}_${i}.png`);
  await db.insert(imports).values({
    id: importId,
    driverId: owner,
    platform: files === 1 ? "uber" : "didi",
    uploadType: files === 1 ? "pdf" : "screenshot",
    fileKey: keys.join(","),
    status: "parsed",
  });
  for (const key of keys) await storage.uploadFile(key, Buffer.from("pii"));
  return keys;
}

beforeEach(async () => {
  db = await makeTestDb();
  storage = new FlakyStorage();
  app = new Hono();
  app.route("/v1", meRoutes(asDb(db), storage));
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

describe("DELETE /v1/me — stored import originals", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("deletes a single-file import's original along with the account", async () => {
    const keys = await seedStoredImport(driverId);
    expect(storage.keys()).toEqual(keys);

    const res = await deleteMe();
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ ok: true });
    expect(storage.keys()).toHaveLength(0);
    expect(await db.select().from(drivers).where(eq(drivers.id, driverId))).toHaveLength(0);
    expect(await db.select().from(imports)).toHaveLength(0);
  });

  it("deletes every original of a multi-file (DiDi) import", async () => {
    await seedStoredImport(driverId, 2);
    expect(storage.keys()).toHaveLength(2);

    expect((await deleteMe()).status).toBe(200);
    expect(storage.keys()).toHaveLength(0);
  });

  it("leaves other drivers' originals and imports alone", async () => {
    const [other] = await db
      .insert(drivers)
      .values({ phone: "+5215511115555", city: "CDMX" })
      .returning();
    await seedStoredImport(driverId);
    const theirs = await seedStoredImport(other!.id, 2);

    expect((await deleteMe()).status).toBe(200);
    expect(storage.keys().sort()).toEqual([...theirs].sort());
    expect(await db.select().from(imports).where(eq(imports.driverId, other!.id))).toHaveLength(1);
  });

  it("fails closed on a storage error: 503, account intact, and a retry finishes the job", async () => {
    const [stuck] = await seedStoredImport(driverId);
    await seedStoredImport(driverId, 2);
    storage.failing.add(stuck!);
    const logged = vi.spyOn(console, "error").mockImplementation(() => {});

    const res = await deleteMe();
    expect(res.status).toBe(503);
    expect(logged).toHaveBeenCalledOnce();

    // Postgres is untouched: the driver, its import rows (the only pointers to the originals) and
    // its session all survive, so the client can simply retry.
    expect(await db.select().from(drivers).where(eq(drivers.id, driverId))).toHaveLength(1);
    expect(await db.select().from(imports).where(eq(imports.driverId, driverId))).toHaveLength(2);
    expect((await getMe()).status).toBe(200);
    // Only the failed object remains; the others already went (deleteFile is idempotent).
    expect(storage.keys()).toEqual([stuck]);

    storage.failing.clear();
    const retry = await deleteMe();
    expect(retry.status).toBe(200);
    expect(storage.keys()).toHaveLength(0);
    expect(await db.select().from(drivers).where(eq(drivers.id, driverId))).toHaveLength(0);
  });

  it("409s instead of deleting when an import lands mid-deletion, and the retry removes it too", async () => {
    await seedStoredImport(driverId);
    // An import commits after the snapshot, while the snapshot's originals are being deleted.
    let late: string[] = [];
    storage.beforeDelete = async () => {
      late = await seedStoredImport(driverId, 2);
    };

    const res = await deleteMe();
    expect(res.status).toBe(409);
    // The account survives with the late import's originals still pointed at by its row.
    expect(await db.select().from(drivers).where(eq(drivers.id, driverId))).toHaveLength(1);
    expect(storage.keys().sort()).toEqual([...late].sort());

    const retry = await deleteMe();
    expect(retry.status).toBe(200);
    expect(storage.keys()).toHaveLength(0);
    expect(await db.select().from(drivers).where(eq(drivers.id, driverId))).toHaveLength(0);
  });
});
