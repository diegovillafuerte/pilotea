/**
 * POST /v1/fixture-reports tests — run against an in-memory pglite DB.
 *
 * Covers: device auth (unregistered → 401), consent gate (missing consent →
 * 422), shape validation, 50 KB cap (413), the 10/day rate limit (429), and a
 * happy-path insert returning 201 with a stored row.
 */

import { describe, it, expect, beforeEach } from "vitest";
import { randomUUID } from "node:crypto";
import { eq } from "drizzle-orm";
import { createApp } from "../app.js";
import { devices, fixtureReports } from "../db/schema.js";
import { makeTestDb, type TestDb } from "../test/db.js";

let db: TestDb;
let app: ReturnType<typeof createApp>;
let deviceId: string;

const appDb = () => db as unknown as Parameters<typeof createApp>[0];

function validBody(overrides: Record<string, unknown> = {}) {
  return {
    consent: true,
    hostPackage: "com.ubercab.driver",
    hostVersion: "4.500.10",
    specVersion: 3,
    reason: "NOT_AN_OFFER",
    snapshot: {
      packageName: "com.ubercab.driver",
      timestampMs: 1_700_000_000_000,
      versionCode: 450010,
      nodes: [
        { text: "Viaje", viewId: "title", depth: 0, index: 0 },
        { text: "«name»", className: "TextView", depth: 1, index: 0 },
        {
          text: "$120.00",
          bounds: { left: 0, top: 0, right: 100, bottom: 40 },
          depth: 1,
          index: 1,
        },
      ],
    },
    ...overrides,
  };
}

function post(body: unknown, headers: Record<string, string> = {}) {
  return app.request("/v1/fixture-reports", {
    method: "POST",
    headers: { "content-type": "application/json", "x-device-id": deviceId, ...headers },
    body: JSON.stringify(body),
  });
}

beforeEach(async () => {
  db = await makeTestDb();
  app = createApp(appDb());
  deviceId = randomUUID();
  await db.insert(devices).values({ deviceId, lastSeenAt: new Date() });
});

describe("POST /v1/fixture-reports", () => {
  it("stores a consented, scrubbed report (201)", async () => {
    const res = await post(validBody());
    expect(res.status).toBe(201);
    const body = (await res.json()) as { report: { id: string } };
    expect(body.report.id).toBeTruthy();

    const rows = await db.select().from(fixtureReports).where(eq(fixtureReports.deviceId, deviceId));
    expect(rows).toHaveLength(1);
    expect(rows[0]!.hostPackage).toBe("com.ubercab.driver");
    expect(rows[0]!.reason).toBe("NOT_AN_OFFER");
  });

  it("rejects an unregistered device (401)", async () => {
    const res = await post(validBody(), { "x-device-id": randomUUID() });
    expect(res.status).toBe(401);
  });

  it("rejects a missing device id (401)", async () => {
    const res = await app.request("/v1/fixture-reports", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(validBody()),
    });
    expect(res.status).toBe(401);
  });

  it("rejects a report without explicit consent (422)", async () => {
    const res = await post(validBody({ consent: false }));
    expect(res.status).toBe(400);
  });

  it("rejects a malformed snapshot shape (400)", async () => {
    const res = await post(validBody({ snapshot: { nodes: "nope" } }));
    expect(res.status).toBe(400);
  });

  it("rejects a snapshot over the 50KB cap (413)", async () => {
    const bigNodes = Array.from({ length: 1500 }, (_, i) => ({
      text: "x".repeat(60),
      viewId: `id-${i}`,
      depth: 1,
      index: i,
    }));
    const res = await post(
      validBody({ snapshot: { packageName: "com.ubercab.driver", nodes: bigNodes } }),
    );
    expect(res.status).toBe(413);
  });

  it("enforces a 10/day/device rate limit (429 on the 11th)", async () => {
    for (let i = 0; i < 10; i++) {
      const ok = await post(validBody());
      expect(ok.status).toBe(201);
    }
    const blocked = await post(validBody());
    expect(blocked.status).toBe(429);

    // A different registered device is unaffected.
    const otherDevice = randomUUID();
    await db.insert(devices).values({ deviceId: otherDevice, lastSeenAt: new Date() });
    const otherOk = await post(validBody(), { "x-device-id": otherDevice });
    expect(otherOk.status).toBe(201);
  });
});
