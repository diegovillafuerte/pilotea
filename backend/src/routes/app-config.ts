import { Hono } from "hono";
import { zValidator } from "@hono/zod-validator";
import { z } from "zod";
import { desc } from "drizzle-orm";
import { appConfig } from "../db/schema.js";
import { requireAdmin } from "./admin.js";
import type { Database } from "../db/client.js";

/**
 * App-config routes — the remote kill switch for premium gating (B-050).
 *
 *  - GET   /v1/config/app — PUBLIC. Returns `{ paywallEnabled }`. The same value
 *    for every device; no auth needed (a bearer, if present, is ignored). The app
 *    caches the response and falls back to `paywallEnabled = true` (gating ON)
 *    when this is unreachable or empty, so a transport hiccup never accidentally
 *    unlocks premium.
 *  - PATCH /v1/config/app — ADMIN (ADMIN_TOKEN bearer). Upserts the singleton so
 *    flipping the paywall is an operator action, not an app release. Setting
 *    `paywallEnabled = false` puts the app in "launch promo mode" (everything
 *    unlocked); reverting to `true` re-enables gating. Same fail-closed admin gate
 *    as the other operator endpoints.
 *
 * The table is a single-row singleton (`singleton` column unique + always TRUE), so
 * the upsert targets that row; the read takes the latest-updated row regardless,
 * tolerating a stray duplicate.
 */

/** Wire shape of the app-config response. */
interface AppConfigResponse {
  /** True = premium gating active (normal). False = everything unlocked (launch promo). */
  paywallEnabled: boolean;
}

const patchBody = z.object({
  paywallEnabled: z.boolean(),
});

export function appConfigRoutes(db: Database) {
  const app = new Hono();

  // GET /v1/config/app — public, returns the current flags (default ON if unseeded).
  app.get("/config/app", async (c) => {
    const [row] = await db
      .select()
      .from(appConfig)
      .orderBy(desc(appConfig.updatedAt))
      .limit(1);

    // Unseeded table → fail soft to gating-ON so the app never unlocks premium by
    // accident; the app itself also defaults TRUE when this read fails entirely.
    return c.json<AppConfigResponse>({
      paywallEnabled: row ? row.paywallEnabled : true,
    });
  });

  // PATCH /v1/config/app — admin-only upsert of the singleton.
  app.patch("/config/app", zValidator("json", patchBody), async (c) => {
    requireAdmin(c.req.header("authorization"));
    const { paywallEnabled } = c.req.valid("json");

    const [row] = await db
      .insert(appConfig)
      .values({ singleton: true, paywallEnabled, updatedAt: new Date() })
      .onConflictDoUpdate({
        target: appConfig.singleton,
        set: { paywallEnabled, updatedAt: new Date() },
      })
      .returning();

    return c.json<AppConfigResponse>({ paywallEnabled: row!.paywallEnabled });
  });

  return app;
}
