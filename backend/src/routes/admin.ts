import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { foldPopulationStats } from "../jobs/fold-population-stats.js";
import type { Database } from "../db/client.js";

/**
 * Admin routes — operator-only endpoints guarded by a shared `ADMIN_TOKEN`.
 *
 * Auth is a constant-time comparison of the `Authorization: Bearer <ADMIN_TOKEN>`
 * header against the `ADMIN_TOKEN` env var (NOT a driver session). The token is
 * a high-entropy operator secret set in the deploy environment; if it is unset
 * the endpoints 503 (fail closed) rather than allowing unauthenticated access.
 *
 * Today the only admin action is {@link foldPopulationStats} — recomputing the
 * benchmark percentiles from accrued real aggregates (B-043). It is exposed as
 * an endpoint so it can be triggered on demand (e.g. from a cron hitting the
 * deployed instance) in addition to the standalone CLI script.
 */
export function adminRoutes(db: Database) {
  const app = new Hono();

  app.post("/admin/fold-stats", async (c) => {
    requireAdmin(c.req.header("authorization"));

    const windowParam = c.req.query("windowWeeks");
    const windowWeeks = windowParam !== undefined ? Number(windowParam) : undefined;
    if (windowWeeks !== undefined && (!Number.isInteger(windowWeeks) || windowWeeks <= 0)) {
      throw new HTTPException(400, { message: "windowWeeks must be a positive integer" });
    }

    const result = await foldPopulationStats(db, { windowWeeks });
    return c.json({
      windowStart: result.windowStart,
      foldedReal: result.foldedReal.length,
      keptSynthetic: result.keptSynthetic,
      cells: result.foldedReal,
    });
  });

  return app;
}

/**
 * Throw 401/503 unless the request carries the configured `ADMIN_TOKEN` as a
 * bearer. Fails closed: a missing/blank env var rejects every request (503) so
 * a misconfigured deploy never exposes the endpoint unauthenticated.
 */
function requireAdmin(authHeader: string | undefined): void {
  const expected = process.env.ADMIN_TOKEN;
  if (!expected || expected.length === 0) {
    throw new HTTPException(503, { message: "Admin endpoint disabled: ADMIN_TOKEN not configured" });
  }
  if (!authHeader || !authHeader.toLowerCase().startsWith("bearer ")) {
    throw new HTTPException(401, { message: "Missing or malformed admin token" });
  }
  const provided = authHeader.slice(7).trim();
  if (!timingSafeEqual(provided, expected)) {
    throw new HTTPException(401, { message: "Invalid admin token" });
  }
}

/**
 * Constant-time string comparison. Length is leaked (unavoidable for varying
 * secret lengths), but the per-character compare doesn't short-circuit, so an
 * attacker can't time-probe the matching prefix.
 */
function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}
