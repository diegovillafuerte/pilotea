import { Hono } from "hono";
import { zValidator } from "@hono/zod-validator";
import { z } from "zod";
import { and, desc, eq } from "drizzle-orm";
import type { KeyObject } from "node:crypto";
import { parserConfigs } from "../db/schema.js";
import type { Database } from "../db/client.js";
import { buildActiveBundle } from "../spec/active-bundle.js";
import { signBundle } from "../spec/bundle.js";
import { loadSigningPrivateKey } from "../spec/signing-key.js";

const configQuery = z.object({
  package: z.string().min(1).max(255),
  // versionCode is reserved for future server-side range matching; accepted now
  // so clients can send it without breaking. Range resolution lands later.
  versionCode: z.coerce.number().int().nonnegative().optional(),
});

const bundleQuery = z.object({
  // Advisory hints — the bundle is the same for every device today, so both are optional and
  // unused by the handler; accepted so the client can send them without a 400.
  package: z.string().min(1).max(255).optional(),
  versionCode: z.coerce.number().int().nonnegative().optional(),
});

/**
 * Build the parser-configs router.
 *
 * - `GET /v1/parser-configs?package=` — legacy per-package list (B-041), unchanged.
 * - `GET /v1/parser-configs/bundle` — the active SIGNED bundle of all specs + kill switches the
 *   Android OTA layer fetches (B-033). The bundle is built from active `parser_configs` rows and
 *   signed with the ECDSA-P256 key (`loadSigningPrivateKey`); the client verifies it against the
 *   public key embedded in the app.
 *
 * The signing key is loaded once and reused. `signingKey` is injectable so tests can pass a
 * deterministic test key; production/dev fall back to {@link loadSigningPrivateKey}.
 */
export function parserConfigsRoutes(db: Database, signingKey?: KeyObject) {
  const app = new Hono();
  let cachedKey: KeyObject | undefined = signingKey;
  const key = (): KeyObject => (cachedKey ??= loadSigningPrivateKey());

  // GET /v1/parser-configs/bundle — the active signed bundle (must precede the param-free route).
  app.get("/parser-configs/bundle", zValidator("query", bundleQuery), async (c) => {
    const bundle = await buildActiveBundle(db);
    const signed = signBundle(bundle, key());
    return c.json(signed);
  });

  // GET /v1/parser-configs?package=&versionCode=
  app.get("/parser-configs", zValidator("query", configQuery), async (c) => {
    const { package: pkg } = c.req.valid("query");

    const rows = await db
      .select()
      .from(parserConfigs)
      .where(and(eq(parserConfigs.targetPackage, pkg), eq(parserConfigs.active, true)))
      .orderBy(desc(parserConfigs.specVersion));

    return c.json({ package: pkg, configs: rows });
  });

  return app;
}
