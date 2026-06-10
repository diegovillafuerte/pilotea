import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { zValidator } from "@hono/zod-validator";
import { z } from "zod";
import { desc } from "drizzle-orm";
import { fiscalConfig } from "../db/schema.js";
import { requireAdmin } from "./admin.js";
import type { Database } from "../db/client.js";

/**
 * Fiscal-config routes for the IMSS threshold tracker (B-051).
 *
 * The 2025 Mexican platform-work reform ties IMSS social-security coverage to a
 * driver earning ≥ 1 monthly minimum wage per platform per calendar month. Both
 * the daily minimum wage and the derived monthly threshold are indexed yearly
 * (CONASAMI publishes a new minimum wage each December), so the Android app
 * reads them from here rather than baking them into a release.
 *
 *  - GET  /v1/config/fiscal — PUBLIC. Returns the latest year's values. The same
 *    figures for every device; no auth needed (a bearer, if present, is ignored).
 *    The app caches the response and falls back to bundled defaults when this is
 *    unreachable, so it never blocks the UI.
 *  - PATCH /v1/config/fiscal — ADMIN (ADMIN_TOKEN bearer). Upserts a year's
 *    values so a new year (or a corrected figure) is an operator action, not an
 *    app update. Same fail-closed gate as the other admin endpoints.
 *
 * Decimal money fields are returned as numbers so the client reads a plain JSON
 * number; the daily→monthly relationship is intentionally NOT recomputed here —
 * the stored `imssMonthlyThresholdMxn` is the authoritative reform-reporting
 * figure (see schema + seed comments).
 */

/** Wire shape of a single fiscal-config row. */
interface FiscalConfigResponse {
  imssMonthlyThresholdMxn: number;
  minimumWageDailyMxn: number;
  year: number;
  updatedAt: string;
}

const patchBody = z.object({
  year: z.number().int().min(2020).max(2100),
  // CONASAMI general-zone daily minimum wage; must be positive.
  minimumWageDailyMxn: z.number().positive().max(100000),
  // One monthly minimum wage — the IMSS coverage threshold; must be positive.
  imssMonthlyThresholdMxn: z.number().positive().max(10000000),
});

export function fiscalConfigRoutes(db: Database) {
  const app = new Hono();

  // GET /v1/config/fiscal — public, returns the latest year's values.
  app.get("/config/fiscal", async (c) => {
    const [row] = await db
      .select()
      .from(fiscalConfig)
      .orderBy(desc(fiscalConfig.year))
      .limit(1);

    if (!row) {
      // No config seeded yet — fail soft with 404 so the app uses its bundled
      // default rather than treating an empty table as a transport error.
      throw new HTTPException(404, { message: "No fiscal config available" });
    }

    return c.json(toResponse(row));
  });

  // PATCH /v1/config/fiscal — admin-only upsert of a year's values.
  app.patch("/config/fiscal", zValidator("json", patchBody), async (c) => {
    requireAdmin(c.req.header("authorization"));
    const { year, minimumWageDailyMxn, imssMonthlyThresholdMxn } = c.req.valid("json");

    const [row] = await db
      .insert(fiscalConfig)
      .values({
        year,
        minimumWageDailyMxn: String(minimumWageDailyMxn),
        imssMonthlyThresholdMxn: String(imssMonthlyThresholdMxn),
        updatedAt: new Date(),
      })
      .onConflictDoUpdate({
        target: fiscalConfig.year,
        set: {
          minimumWageDailyMxn: String(minimumWageDailyMxn),
          imssMonthlyThresholdMxn: String(imssMonthlyThresholdMxn),
          updatedAt: new Date(),
        },
      })
      .returning();

    return c.json(toResponse(row!));
  });

  return app;
}

/** Map a DB row (decimals as strings) to the numeric wire response. */
function toResponse(row: typeof fiscalConfig.$inferSelect): FiscalConfigResponse {
  return {
    imssMonthlyThresholdMxn: Number(row.imssMonthlyThresholdMxn),
    minimumWageDailyMxn: Number(row.minimumWageDailyMxn),
    year: row.year,
    updatedAt: row.updatedAt.toISOString(),
  };
}
