/**
 * Referral & partners program tests (B-056) — end-to-end against an in-memory pglite DB.
 *
 * Covers: driver-code auto-creation + idempotency, the full redemption happy path, EVERY abuse
 * guardrail (self-referral, repeat account, account-too-old, repeat device, unknown code),
 * premium_until extension math incl. stacking grants, /v1/me effective-tier with a grant, partner
 * codes (redeemer-only grant) + attribution counts, and admin auth on the partner endpoints.
 */

import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { eq, sql } from "drizzle-orm";
import { createApp } from "../app.js";
import { drivers, referralCodes, referralRedemptions, subscriptions } from "../db/schema.js";
import { createSession } from "../auth/sessions.js";
import { REFERRAL_ALPHABET, REFERRAL_CODE_LENGTH } from "../referrals/code.js";
import { makeTestDb, type TestDb } from "../test/db.js";
import { randomUUID } from "node:crypto";

let db: TestDb;
let app: ReturnType<typeof createApp>;

const asDb = (d: TestDb) => d as unknown as Parameters<typeof createApp>[0];
const ADMIN = "test-admin-secret";
let savedAdmin: string | undefined;

// eslint-disable-next-line @typescript-eslint/no-explicit-any -- response bodies are asserted field-by-field
async function json(res: Response): Promise<any> {
  return res.json();
}

/** Create a driver + session. `ageDays` back-dates created_at to exercise the new-driver rule. */
async function makeDriver(opts: { ageDays?: number; phone?: string } = {}) {
  const phone = opts.phone ?? `+52155${Math.floor(Math.random() * 1e8).toString().padStart(8, "0")}`;
  const [driver] = await db.insert(drivers).values({ phone }).returning();
  if (opts.ageDays !== undefined) {
    await db
      .update(drivers)
      .set({ createdAt: sql`now() - (${opts.ageDays} || ' days')::interval` })
      .where(eq(drivers.id, driver!.id));
  }
  const token = await createSession(asDb(db), driver!.id);
  return { id: driver!.id, token };
}

function mine(token: string) {
  return app.request("/v1/referrals/mine", { headers: { authorization: `Bearer ${token}` } });
}

function redeem(token: string, body: unknown) {
  return app.request("/v1/referrals/redeem", {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  });
}

function me(token: string) {
  return app.request("/v1/me", { headers: { authorization: `Bearer ${token}` } });
}

function adminReq(method: "GET" | "POST", path: string, body?: unknown, token = ADMIN) {
  return app.request(path, {
    method,
    headers: {
      authorization: `Bearer ${token}`,
      ...(body ? { "content-type": "application/json" } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
}

beforeEach(async () => {
  db = await makeTestDb();
  app = createApp(asDb(db));
  savedAdmin = process.env.ADMIN_TOKEN;
  process.env.ADMIN_TOKEN = ADMIN;
});

afterEach(() => {
  if (savedAdmin === undefined) delete process.env.ADMIN_TOKEN;
  else process.env.ADMIN_TOKEN = savedAdmin;
});

// ─── Code generation ──────────────────────────────────────────────────────────

describe("GET /v1/referrals/mine", () => {
  it("auto-creates a code on first call and is idempotent", async () => {
    const d = await makeDriver();
    const first = await json(await mine(d.token));
    expect(first.code).toHaveLength(REFERRAL_CODE_LENGTH);
    for (const ch of first.code) expect(REFERRAL_ALPHABET).toContain(ch);
    expect(first.redemptionsCount).toBe(0);
    expect(first.premiumDaysEarned).toBe(0);
    expect(first.premiumUntilMillis).toBeNull();

    const second = await json(await mine(d.token));
    expect(second.code).toBe(first.code);

    const rows = await db.select().from(referralCodes).where(eq(referralCodes.driverId, d.id));
    expect(rows).toHaveLength(1);
  });

  it("generates distinct codes for distinct drivers", async () => {
    const codes = new Set<string>();
    for (let i = 0; i < 25; i++) {
      const d = await makeDriver();
      codes.add((await json(await mine(d.token))).code);
    }
    expect(codes.size).toBe(25);
  });

  it("requires a session", async () => {
    const res = await app.request("/v1/referrals/mine");
    expect(res.status).toBe(401);
  });
});

// ─── Redemption happy path ──────────────────────────────────────────────────

describe("POST /v1/referrals/redeem — happy path", () => {
  it("grants 14/14 days to both sides and extends premium_until", async () => {
    const referrer = await makeDriver();
    const redeemer = await makeDriver({ ageDays: 1 });
    const code = (await json(await mine(referrer.token))).code;

    const res = await redeem(redeemer.token, { code, deviceId: randomUUID() });
    expect(res.status).toBe(200);
    const body = await json(res);
    expect(body.grantedDaysRedeemer).toBe(14);
    expect(body.grantedDaysReferrer).toBe(14);
    expect(body.premiumUntilMillis).toBeGreaterThan(Date.now());

    // Both drivers now have ~14 days of premium_until.
    const [ref] = await db.select().from(drivers).where(eq(drivers.id, referrer.id));
    const [red] = await db.select().from(drivers).where(eq(drivers.id, redeemer.id));
    const days = (d: Date | null) => (d!.getTime() - Date.now()) / 86_400_000;
    expect(days(ref!.premiumUntil)).toBeGreaterThan(13.9);
    expect(days(ref!.premiumUntil)).toBeLessThan(14.1);
    expect(days(red!.premiumUntil)).toBeGreaterThan(13.9);

    // Referrer's mine view reflects the redemption + earned days.
    const refMine = await json(await mine(referrer.token));
    expect(refMine.redemptionsCount).toBe(1);
    expect(refMine.premiumDaysEarned).toBe(14);
  });

  it("makes both drivers effective-premium on /v1/me with no Play purchase", async () => {
    const referrer = await makeDriver();
    const redeemer = await makeDriver({ ageDays: 2 });
    const code = (await json(await mine(referrer.token))).code;
    await redeem(redeemer.token, { code, deviceId: randomUUID() });

    const redMe = await json(await me(redeemer.token));
    expect(redMe.driver.tier).toBe("premium");
    expect(redMe.subscription).toBeNull(); // grant-based, not a Play sub
    expect(redMe.premiumUntilMillis).toBeGreaterThan(Date.now());

    const refMe = await json(await me(referrer.token));
    expect(refMe.driver.tier).toBe("premium");
  });
});

// ─── Stacking math ────────────────────────────────────────────────────────────

describe("premium_until stacking", () => {
  it("a referrer with two redemptions stacks to ~28 days", async () => {
    const referrer = await makeDriver();
    const code = (await json(await mine(referrer.token))).code;

    for (let i = 0; i < 2; i++) {
      const redeemer = await makeDriver({ ageDays: 1 });
      const res = await redeem(redeemer.token, { code, deviceId: randomUUID() });
      expect(res.status).toBe(200);
    }

    const [ref] = await db.select().from(drivers).where(eq(drivers.id, referrer.id));
    const days = (ref!.premiumUntil!.getTime() - Date.now()) / 86_400_000;
    expect(days).toBeGreaterThan(27.9);
    expect(days).toBeLessThan(28.1);

    const refMine = await json(await mine(referrer.token));
    expect(refMine.redemptionsCount).toBe(2);
    expect(refMine.premiumDaysEarned).toBe(28);
  });
});

// ─── Abuse guardrails ─────────────────────────────────────────────────────────

describe("POST /v1/referrals/redeem — abuse guardrails", () => {
  it("rejects an unknown code (404, Spanish)", async () => {
    const d = await makeDriver({ ageDays: 1 });
    const res = await redeem(d.token, { code: "ZZZZZZZZ", deviceId: randomUUID() });
    expect(res.status).toBe(404);
    expect((await json(res)).error).toMatch(/no existe/i);
  });

  it("rejects self-referral", async () => {
    const d = await makeDriver({ ageDays: 1 });
    const code = (await json(await mine(d.token))).code;
    const res = await redeem(d.token, { code, deviceId: randomUUID() });
    expect(res.status).toBe(400);
    expect((await json(res)).error).toMatch(/tu propio código/i);
  });

  it("rejects a second redemption by the same account", async () => {
    const referrerA = await makeDriver();
    const referrerB = await makeDriver();
    const codeA = (await json(await mine(referrerA.token))).code;
    const codeB = (await json(await mine(referrerB.token))).code;
    const redeemer = await makeDriver({ ageDays: 1 });

    expect((await redeem(redeemer.token, { code: codeA, deviceId: randomUUID() })).status).toBe(200);
    const res = await redeem(redeemer.token, { code: codeB, deviceId: randomUUID() });
    expect(res.status).toBe(409);
    expect((await json(res)).error).toMatch(/ya canjeaste/i);
  });

  it("rejects an account older than 30 days (new-driver requirement)", async () => {
    const referrer = await makeDriver();
    const code = (await json(await mine(referrer.token))).code;
    const old = await makeDriver({ ageDays: 31 });

    const res = await redeem(old.token, { code, deviceId: randomUUID() });
    expect(res.status).toBe(403);
    expect((await json(res)).error).toMatch(/conductores nuevos/i);
  });

  it("rejects a device already used for a redemption", async () => {
    const referrer = await makeDriver();
    const code = (await json(await mine(referrer.token))).code;
    const device = randomUUID();

    const redeemer1 = await makeDriver({ ageDays: 1 });
    expect((await redeem(redeemer1.token, { code, deviceId: device })).status).toBe(200);

    // A different fresh account on the SAME device is blocked.
    const redeemer2 = await makeDriver({ ageDays: 1 });
    const res = await redeem(redeemer2.token, { code, deviceId: device });
    expect(res.status).toBe(409);
    expect((await json(res)).error).toMatch(/dispositivo/i);

    // No grant landed for redeemer2.
    const [r2] = await db.select().from(drivers).where(eq(drivers.id, redeemer2.id));
    expect(r2!.premiumUntil).toBeNull();
  });

  it("requires a session", async () => {
    const res = await app.request("/v1/referrals/redeem", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ code: "ABCDEFGH", deviceId: randomUUID() }),
    });
    expect(res.status).toBe(401);
  });
});

// ─── Partner codes + attribution ────────────────────────────────────────────

describe("partner codes", () => {
  it("admin mints a partner code; redeeming grants the REDEEMER only", async () => {
    const create = await adminReq("POST", "/v1/admin/partners", { name: "Ruta Rentable MX" });
    expect(create.status).toBe(201);
    const { code } = await json(create);
    expect(code).toHaveLength(REFERRAL_CODE_LENGTH);

    const redeemer = await makeDriver({ ageDays: 1 });
    const res = await redeem(redeemer.token, { code, deviceId: randomUUID() });
    expect(res.status).toBe(200);
    const body = await json(res);
    expect(body.grantedDaysRedeemer).toBe(14);
    expect(body.grantedDaysReferrer).toBe(0); // no on-platform referrer

    const [red] = await db.select().from(drivers).where(eq(drivers.id, redeemer.id));
    expect(red!.premiumUntil!.getTime()).toBeGreaterThan(Date.now());
    expect((await json(await me(redeemer.token))).driver.tier).toBe("premium");
  });

  it("attribution export counts redemptions per partner (all-time + last 30d)", async () => {
    const code = (await json(await adminReq("POST", "/v1/admin/partners", { name: "Influencer A" }))).code;

    // Two redemptions: one recent, one back-dated beyond the 30d window.
    const r1 = await makeDriver({ ageDays: 1 });
    await redeem(r1.token, { code, deviceId: randomUUID() });
    const r2 = await makeDriver({ ageDays: 1 });
    await redeem(r2.token, { code, deviceId: randomUUID() });
    // Back-date r2's redemption to 40 days ago.
    await db
      .update(referralRedemptions)
      .set({ createdAt: sql`now() - interval '40 days'` })
      .where(eq(referralRedemptions.redeemerDriverId, r2.id));

    const res = await adminReq("GET", "/v1/admin/partners");
    expect(res.status).toBe(200);
    const body = await json(res);
    const partner = body.partners.find((p: { code: string }) => p.code === code);
    expect(partner.name).toBe("Influencer A");
    expect(partner.redemptionsAllTime).toBe(2);
    expect(partner.redemptionsLast30d).toBe(1);
  });

  it("admin endpoints require ADMIN_TOKEN", async () => {
    expect((await adminReq("GET", "/v1/admin/partners", undefined, "wrong")).status).toBe(401);
    expect(
      (await adminReq("POST", "/v1/admin/partners", { name: "x" }, "wrong")).status,
    ).toBe(401);

    delete process.env.ADMIN_TOKEN;
    expect((await adminReq("GET", "/v1/admin/partners", undefined, "anything")).status).toBe(503);
  });
});

// ─── Effective tier interaction with subscriptions ──────────────────────────

describe("/v1/me effective tier", () => {
  it("a Play subscription keeps premium even after a grant lapses", async () => {
    const referrer = await makeDriver();
    const redeemer = await makeDriver({ ageDays: 1 });
    const code = (await json(await mine(referrer.token))).code;
    await redeem(redeemer.token, { code, deviceId: randomUUID() });

    // Give the redeemer an active Play sub and expire their grant.
    await db.insert(subscriptions).values({
      driverId: redeemer.id,
      purchaseToken: `tok-${redeemer.id}`,
      productId: "kompara_premium",
      status: "active",
    });
    await db
      .update(drivers)
      .set({ premiumUntil: sql`now() - interval '1 day'` })
      .where(eq(drivers.id, redeemer.id));

    const body = await json(await me(redeemer.token));
    expect(body.driver.tier).toBe("premium");
    expect(body.subscription.status).toBe("active");
  });

  it("an expired grant with no subscription is free", async () => {
    const d = await makeDriver({ ageDays: 1 });
    await db
      .update(drivers)
      .set({ premiumUntil: sql`now() - interval '1 day'` })
      .where(eq(drivers.id, d.id));
    const body = await json(await me(d.token));
    expect(body.driver.tier).toBe("free");
    expect(body.premiumUntilMillis).toBeLessThan(Date.now());
  });
});
