/**
 * Subscription endpoint tests (B-049) — run end-to-end against an in-memory pglite DB.
 *
 * Covers: sync persistence, /v1/me tier reflection (premium / free), RTDN status update by token,
 * idempotent token re-sync, the entitled-vs-not status mapping (hold → free), and session-auth
 * gating on the sync endpoint.
 */

import { describe, it, expect, beforeEach } from "vitest";
import { eq } from "drizzle-orm";
import { createApp } from "../app.js";
import { drivers, subscriptions } from "../db/schema.js";
import { createSession } from "../auth/sessions.js";
import { makeTestDb, type TestDb } from "../test/db.js";

let db: TestDb;
let app: ReturnType<typeof createApp>;
let driverId: string;
let token: string;

const asDb = (d: TestDb) => d as unknown as Parameters<typeof createApp>[0];
const TOKEN = "purchase-token-xyz";
const PRODUCT = "kompara_premium";

// eslint-disable-next-line @typescript-eslint/no-explicit-any -- response bodies are asserted field-by-field
async function json(res: Response): Promise<any> {
  return res.json();
}

function sync(body: unknown, headers: Record<string, string> = {}) {
  return app.request("/v1/subscriptions/sync", {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${token}`, ...headers },
    body: JSON.stringify(body),
  });
}

function rtdn(purchaseToken: string, status?: string) {
  const payload = { subscriptionNotification: { purchaseToken, subscriptionId: PRODUCT, status } };
  const data = Buffer.from(JSON.stringify(payload), "utf8").toString("base64");
  return app.request("/v1/rtdn", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ message: { data, messageId: "m1" } }),
  });
}

function me() {
  return app.request("/v1/me", { headers: { authorization: `Bearer ${token}` } });
}

beforeEach(async () => {
  db = await makeTestDb();
  app = createApp(asDb(db));
  const [driver] = await db
    .insert(drivers)
    .values({ phone: "+5215500000000" })
    .returning();
  driverId = driver!.id;
  token = await createSession(asDb(db), driverId);
});

describe("POST /v1/subscriptions/sync", () => {
  it("requires a session", async () => {
    const res = await app.request("/v1/subscriptions/sync", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ purchaseToken: TOKEN, productId: PRODUCT, status: "active", trial: false }),
    });
    expect(res.status).toBe(401);
  });

  it("persists a purchase and returns the server view", async () => {
    const res = await sync({
      purchaseToken: TOKEN,
      productId: PRODUCT,
      status: "active",
      trial: true,
      expiresAtMillis: 1_900_000_000_000,
    });
    expect(res.status).toBe(200);
    const body = await json(res);
    expect(body.subscription).toEqual({
      status: "active",
      trial: true,
      expiresAtMillis: 1_900_000_000_000,
    });

    const [row] = await db
      .select()
      .from(subscriptions)
      .where(eq(subscriptions.purchaseToken, TOKEN));
    expect(row!.driverId).toBe(driverId);
    expect(row!.productId).toBe(PRODUCT);
    expect(row!.status).toBe("active");
    expect(row!.trial).toBe(true);
  });

  it("is idempotent: re-syncing the same token updates the row in place", async () => {
    await sync({ purchaseToken: TOKEN, productId: PRODUCT, status: "active", trial: true });
    await sync({ purchaseToken: TOKEN, productId: PRODUCT, status: "canceled", trial: false });

    const rows = await db
      .select()
      .from(subscriptions)
      .where(eq(subscriptions.purchaseToken, TOKEN));
    expect(rows).toHaveLength(1);
    expect(rows[0]!.status).toBe("canceled");
    expect(rows[0]!.trial).toBe(false);
  });
});

describe("/v1/me tier reflection", () => {
  it("reflects premium for an active subscription", async () => {
    await sync({ purchaseToken: TOKEN, productId: PRODUCT, status: "active", trial: false });
    const body = await json(await me());
    expect(body.driver.tier).toBe("premium");
    expect(body.subscription.status).toBe("active");
  });

  it("reflects free for a held subscription (account hold suspends entitlement)", async () => {
    await sync({ purchaseToken: TOKEN, productId: PRODUCT, status: "hold", trial: false });
    const body = await json(await me());
    expect(body.driver.tier).toBe("free");
    expect(body.subscription.status).toBe("hold");
  });

  it("reflects premium during grace and cancel-but-not-yet-expired", async () => {
    await sync({ purchaseToken: TOKEN, productId: PRODUCT, status: "grace", trial: false });
    expect((await json(await me())).driver.tier).toBe("premium");

    await sync({ purchaseToken: TOKEN, productId: PRODUCT, status: "canceled", trial: false });
    expect((await json(await me())).driver.tier).toBe("premium");
  });

  it("is free with no subscription at all", async () => {
    const body = await json(await me());
    expect(body.driver.tier).toBe("free");
    expect(body.subscription).toBeNull();
  });
});

describe("POST /v1/rtdn", () => {
  it("updates the subscription status by token and re-derives tier", async () => {
    await sync({ purchaseToken: TOKEN, productId: PRODUCT, status: "active", trial: false });
    expect((await json(await me())).driver.tier).toBe("premium");

    const res = await rtdn(TOKEN, "expired");
    expect(res.status).toBe(200);
    expect(await json(res)).toMatchObject({ ok: true, handled: true });

    const [row] = await db
      .select()
      .from(subscriptions)
      .where(eq(subscriptions.purchaseToken, TOKEN));
    expect(row!.status).toBe("expired");

    // Tier on the driver row + /v1/me both fall back to free.
    const [driver] = await db.select().from(drivers).where(eq(drivers.id, driverId));
    expect(driver!.tier).toBe("free");
    expect((await json(await me())).driver.tier).toBe("free");
  });

  it("acknowledges an unknown token without erroring", async () => {
    const res = await rtdn("never-synced-token", "active");
    expect(res.status).toBe(200);
    expect(await json(res)).toMatchObject({ ok: true, handled: false });
  });

  it("400s on a malformed (non-base64-JSON) payload", async () => {
    const res = await app.request("/v1/rtdn", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ message: { data: "!!!not base64 json!!!" } }),
    });
    expect(res.status).toBe(400);
  });
});
