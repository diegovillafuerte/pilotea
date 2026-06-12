/**
 * WhatsApp OTP auth flow tests — run end-to-end against an in-memory pglite DB
 * via the Hono app's fetch handler, with a fake message sender that captures
 * the OTP code that would have been sent over WhatsApp.
 *
 * Covers: happy path, wrong code, expiry, per-phone rate limit, session auth on
 * /v1/me, anonymous device → account merge, and logout revocation.
 */

import { describe, it, expect, beforeEach } from "vitest";
import { sql } from "drizzle-orm";
import { createApp } from "../app.js";
import { devices, drivers, otpCodes } from "../db/schema.js";
import type { MessageSender } from "../auth/message-sender.js";
import { makeTestDb, type TestDb } from "../test/db.js";

/** Fake sender that records the last code sent per phone. */
class FakeSender implements MessageSender {
  readonly sent: { phone: string; code: string }[] = [];
  async sendOtp(phone: string, code: string): Promise<void> {
    this.sent.push({ phone, code });
  }
  lastCodeFor(phone: string): string | undefined {
    return [...this.sent].reverse().find((s) => s.phone === phone)?.code;
  }
}

let db: TestDb;
let sender: FakeSender;
let app: ReturnType<typeof createApp>;

const PHONE = "+5215598765432";

function asDb(d: TestDb) {
  return d as unknown as Parameters<typeof createApp>[0];
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any -- response bodies are asserted field-by-field
async function json(res: Response): Promise<any> {
  return res.json();
}

function post(path: string, body: unknown, headers: Record<string, string> = {}) {
  return app.request(path, {
    method: "POST",
    headers: { "content-type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
}

beforeEach(async () => {
  db = await makeTestDb();
  sender = new FakeSender();
  app = createApp(asDb(db), sender);
});

describe("POST /v1/auth/otp/request", () => {
  it("sends a 6-digit code and always returns 200", async () => {
    const res = await post("/v1/auth/otp/request", { phone: PHONE });
    expect(res.status).toBe(200);
    expect(await json(res)).toEqual({ ok: true });
    const code = sender.lastCodeFor(PHONE);
    expect(code).toMatch(/^\d{6}$/);
  });

  it("does not store the plaintext code (hashed at rest)", async () => {
    await post("/v1/auth/otp/request", { phone: PHONE });
    const code = sender.lastCodeFor(PHONE)!;
    const rows = await db.select().from(otpCodes);
    expect(rows).toHaveLength(1);
    expect(rows[0]!.codeHash).not.toBe(code);
    expect(rows[0]!.codeHash).toHaveLength(64);
  });

  it("400s on a malformed phone", async () => {
    const res = await post("/v1/auth/otp/request", { phone: "not-a-phone" });
    expect(res.status).toBe(400);
  });

  it("rate limits to 3 requests per phone, still returning 200 (no enumeration)", async () => {
    for (let i = 0; i < 3; i++) {
      const r = await post("/v1/auth/otp/request", { phone: PHONE });
      expect(r.status).toBe(200);
    }
    // 4th within the window is silently dropped — response shape is identical.
    const fourth = await post("/v1/auth/otp/request", { phone: PHONE });
    expect(fourth.status).toBe(200);
    expect(await json(fourth)).toEqual({ ok: true });

    const rows = await db.select().from(otpCodes);
    expect(rows).toHaveLength(3); // 4th never created a code
    expect(sender.sent).toHaveLength(3); // 4th never sent
  });
});

describe("POST /v1/auth/otp/verify", () => {
  it("happy path: creates a new driver, mints a session, returns the profile", async () => {
    await post("/v1/auth/otp/request", { phone: PHONE });
    const code = sender.lastCodeFor(PHONE)!;

    const res = await post("/v1/auth/otp/verify", { phone: PHONE, code });
    expect(res.status).toBe(200);
    const body = await json(res);
    expect(body.token).toMatch(/^[0-9a-f]{64}$/);
    expect(body.driver).toMatchObject({ phone: PHONE, tier: "free" });
    expect(body.driver.id).toBeTruthy();

    const drvRows = await db.select().from(drivers);
    expect(drvRows).toHaveLength(1);
  });

  it("returns the existing driver on a repeat login (no duplicate)", async () => {
    await post("/v1/auth/otp/request", { phone: PHONE });
    const first = await post("/v1/auth/otp/verify", {
      phone: PHONE,
      code: sender.lastCodeFor(PHONE)!,
    });
    const firstId = (await json(first)).driver.id;

    await post("/v1/auth/otp/request", { phone: PHONE });
    const second = await post("/v1/auth/otp/verify", {
      phone: PHONE,
      code: sender.lastCodeFor(PHONE)!,
    });
    const secondId = (await json(second)).driver.id;

    expect(secondId).toBe(firstId);
    expect(await db.select().from(drivers)).toHaveLength(1);
  });

  it("rejects a wrong code with 401 and creates no driver", async () => {
    await post("/v1/auth/otp/request", { phone: PHONE });
    const res = await post("/v1/auth/otp/verify", { phone: PHONE, code: "000000" });
    expect(res.status).toBe(401);
    expect(await db.select().from(drivers)).toHaveLength(0);
  });

  it("rejects an expired code with 401", async () => {
    await post("/v1/auth/otp/request", { phone: PHONE });
    const code = sender.lastCodeFor(PHONE)!;
    // Force the code to be expired.
    await db.update(otpCodes).set({ expiresAt: new Date(Date.now() - 1000) });

    const res = await post("/v1/auth/otp/verify", { phone: PHONE, code });
    expect(res.status).toBe(401);
  });

  it("anonymous → account merge: claims the device and tags the driver", async () => {
    const deviceId = "11111111-2222-4333-8444-555555555555"; // valid UUID v4
    // Device was registered anonymously first.
    await post("/v1/devices/register", { deviceId });

    await post("/v1/auth/otp/request", { phone: PHONE });
    const res = await post("/v1/auth/otp/verify", {
      phone: PHONE,
      code: sender.lastCodeFor(PHONE)!,
      deviceId,
    });
    expect(res.status).toBe(200);
    const driverId = (await json(res)).driver.id;

    const [device] = await db.select().from(devices).where(sql`device_id = ${deviceId}`);
    expect(device!.driverId).toBe(driverId);

    const [driver] = await db.select().from(drivers).where(sql`id = ${driverId}`);
    expect(driver!.anonymousDeviceId).toBe(deviceId);
  });
});

describe("session auth + /v1/me", () => {
  async function login(phone = PHONE): Promise<string> {
    await post("/v1/auth/otp/request", { phone });
    const res = await post("/v1/auth/otp/verify", { phone, code: sender.lastCodeFor(phone)! });
    return (await json(res)).token as string;
  }

  it("401s without a bearer token", async () => {
    const res = await app.request("/v1/me");
    expect(res.status).toBe(401);
  });

  it("401s with an unknown bearer token", async () => {
    const res = await app.request("/v1/me", {
      headers: { authorization: "Bearer deadbeef" },
    });
    expect(res.status).toBe(401);
  });

  it("returns the driver profile for a valid session", async () => {
    const token = await login();
    const res = await app.request("/v1/me", {
      headers: { authorization: `Bearer ${token}` },
    });
    expect(res.status).toBe(200);
    expect((await json(res)).driver).toMatchObject({ phone: PHONE });
  });

  it("PATCH /v1/me updates name, city, and platforms", async () => {
    const token = await login();
    const res = await app.request("/v1/me", {
      method: "PATCH",
      headers: { "content-type": "application/json", authorization: `Bearer ${token}` },
      body: JSON.stringify({ name: "Ana", city: "cdmx", platforms: ["uber", "didi"] }),
    });
    expect(res.status).toBe(200);
    const body = await json(res);
    expect(body.driver).toMatchObject({
      name: "Ana",
      city: "cdmx",
      platforms: ["uber", "didi"],
    });
  });

  it("PATCH /v1/me 400s on an empty body", async () => {
    const token = await login();
    const res = await app.request("/v1/me", {
      method: "PATCH",
      headers: { "content-type": "application/json", authorization: `Bearer ${token}` },
      body: JSON.stringify({}),
    });
    expect(res.status).toBe(400);
  });
});

describe("POST /v1/auth/logout", () => {
  it("revokes the session so the token no longer authenticates", async () => {
    await post("/v1/auth/otp/request", { phone: PHONE });
    const verify = await post("/v1/auth/otp/verify", {
      phone: PHONE,
      code: sender.lastCodeFor(PHONE)!,
    });
    const token = (await json(verify)).token as string;

    // Token works before logout.
    const before = await app.request("/v1/me", {
      headers: { authorization: `Bearer ${token}` },
    });
    expect(before.status).toBe(200);

    const logout = await post("/v1/auth/logout", {}, { authorization: `Bearer ${token}` });
    expect(logout.status).toBe(200);

    // Token is dead after logout.
    const after = await app.request("/v1/me", {
      headers: { authorization: `Bearer ${token}` },
    });
    expect(after.status).toBe(401);
  });

  it("401s without a bearer token", async () => {
    const res = await post("/v1/auth/logout", {});
    expect(res.status).toBe(401);
  });
});

describe("POST /v1/devices/register (anonymous-first)", () => {
  it("registers a device with no account and is idempotent", async () => {
    const deviceId = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"; // valid UUID v4
    const first = await post("/v1/devices/register", { deviceId });
    expect(first.status).toBe(200);
    const second = await post("/v1/devices/register", { deviceId });
    expect(second.status).toBe(200);

    const rows = await db.select().from(devices);
    expect(rows).toHaveLength(1);
    expect(rows[0]!.driverId).toBeNull(); // anonymous — not yet claimed
  });
});
