/**
 * Tests for the parser-config OTA bundle endpoint + signing round-trip (B-033).
 *
 * Runs against an in-memory pglite DB seeded with the launch-day parser specs (uber + didi +
 * inDrive). Uses a deterministic test signing key (not the committed dev key) so the test owns both
 * halves and can assert the signature verifies — and that a tampered payload does not.
 *
 * Covers:
 *  - GET /v1/parser-configs/bundle returns a { payload, signature } envelope whose payload parses to
 *    a SpecBundle carrying every seeded spec and an empty killSwitches;
 *  - the signature verifies against the matching public key, and fails on a tampered payload;
 *  - a kill switch (no active row for a package that has rows) surfaces in killSwitches;
 *  - signBundle → verifyBundle round-trips (the contract the signing script relies on).
 */

import { describe, it, expect, beforeAll } from "vitest";
import { Hono } from "hono";
import { eq } from "drizzle-orm";
import { generateKeyPairSync, type KeyObject } from "node:crypto";
import { parserConfigs } from "../db/schema.js";
import { parserConfigsRoutes } from "./parser-configs.js";
import { signBundle, verifyBundle, type SignedSpecBundle, type SpecBundle } from "../spec/bundle.js";
import { makeTestDb, type TestDb } from "../test/db.js";

let testKey: { publicKey: KeyObject; privateKey: KeyObject };

beforeAll(() => {
  testKey = generateKeyPairSync("ec", { namedCurve: "P-256" });
});

// pglite TestDb is structurally compatible with the runtime Database the route expects (same
// schema), but its query-result HKT differs; cast as the existing route tests do.
function asDb(d: TestDb) {
  return d as unknown as Parameters<typeof parserConfigsRoutes>[0];
}

function appWith(db: TestDb) {
  const app = new Hono();
  app.route("/v1", parserConfigsRoutes(asDb(db), testKey.privateKey));
  return app;
}

describe("GET /v1/parser-configs/bundle", () => {
  it("returns a signed bundle of the seeded specs that verifies", async () => {
    const db = await makeTestDb({ seedSpecs: true });
    const res = await appWith(db).request("/v1/parser-configs/bundle");
    expect(res.status).toBe(200);

    const signed = (await res.json()) as SignedSpecBundle;
    expect(typeof signed.payload).toBe("string");
    expect(typeof signed.signature).toBe("string");

    // The signature verifies against the matching public key, and the decoded bundle is well-shaped.
    const bundle = verifyBundle(signed, testKey.publicKey);
    expect(bundle).not.toBeNull();
    const ok = bundle as SpecBundle;
    expect(ok.bundleVersion).toBeGreaterThanOrEqual(1);
    expect(ok.specs).toHaveLength(3);
    const packages = ok.specs.map((s) => (s as { targetPackage: string }).targetPackage).sort();
    expect(packages).toEqual(["com.sdu.didi.gsui", "com.ubercab.driver", "sinet.startup.inDriver"]);
    expect(ok.killSwitches).toEqual({});
  });

  it("rejects a tampered payload (signature no longer matches)", async () => {
    const db = await makeTestDb({ seedSpecs: true });
    const res = await appWith(db).request("/v1/parser-configs/bundle");
    const signed = (await res.json()) as SignedSpecBundle;

    const tampered: SignedSpecBundle = {
      payload: signed.payload.replace("\"bundleVersion\":1", "\"bundleVersion\":999"),
      signature: signed.signature,
    };
    expect(verifyBundle(tampered, testKey.publicKey)).toBeNull();
  });

  it("surfaces a kill switch when a package has rows but none active", async () => {
    const db = await makeTestDb({ seedSpecs: true });
    // Flip every Uber row inactive → Uber is now a kill switch, DiDi + inDrive still served.
    await db
      .update(parserConfigs)
      .set({ active: false })
      .where(eq(parserConfigs.targetPackage, "com.ubercab.driver"));

    const res = await appWith(db).request("/v1/parser-configs/bundle");
    const signed = (await res.json()) as SignedSpecBundle;
    const bundle = verifyBundle(signed, testKey.publicKey) as SpecBundle;

    expect(bundle.killSwitches["com.ubercab.driver"]).toBe(true);
    expect(
      bundle.specs.map((s) => (s as { targetPackage: string }).targetPackage).sort(),
    ).toEqual(["com.sdu.didi.gsui", "sinet.startup.inDriver"]);
  });

  it("works on an empty table (bundleVersion floored at 1, no specs)", async () => {
    const db = await makeTestDb(); // no seedSpecs
    const res = await appWith(db).request("/v1/parser-configs/bundle");
    const signed = (await res.json()) as SignedSpecBundle;
    const bundle = verifyBundle(signed, testKey.publicKey) as SpecBundle;
    expect(bundle.bundleVersion).toBe(1);
    expect(bundle.specs).toHaveLength(0);
  });
});

describe("signBundle / verifyBundle round-trip", () => {
  it("a bundle signed then verified with the matching key decodes identically", () => {
    const bundle: SpecBundle = {
      bundleVersion: 7,
      generatedAt: "2026-06-10T12:00:00.000Z",
      specs: [{ targetPackage: "com.ubercab.driver", specVersion: 7 }],
      killSwitches: { "com.example.broken": true },
    };
    const signed = signBundle(bundle, testKey.privateKey);
    const decoded = verifyBundle(signed, testKey.publicKey);
    expect(decoded).toEqual(bundle);
  });

  it("a signature from a different key fails verification", () => {
    const other = generateKeyPairSync("ec", { namedCurve: "P-256" });
    const bundle: SpecBundle = {
      bundleVersion: 1,
      generatedAt: "x",
      specs: [],
      killSwitches: {},
    };
    const signed = signBundle(bundle, other.privateKey);
    expect(verifyBundle(signed, testKey.publicKey)).toBeNull();
  });
});
