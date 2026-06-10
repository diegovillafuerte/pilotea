import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { zValidator } from "@hono/zod-validator";
import { z } from "zod";
import { sql } from "drizzle-orm";
import { timingSafeEqual } from "node:crypto";
import { telemetryCounters } from "../db/schema.js";
import { computeAlerts } from "../telemetry/alerts.js";
import type { Database } from "../db/client.js";

const telemetryInput = z.object({
  hostPackage: z.string().min(1).max(255),
  hostVersion: z.string().min(1).max(100),
  specVersion: z.number().int().nonnegative(),
  attempts: z.number().int().nonnegative().default(0),
  successes: z.number().int().nonnegative().default(0),
  failures: z.number().int().nonnegative().default(0),
  day: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "day must be YYYY-MM-DD"),
});

/**
 * Build the telemetry router. Ingests parser-health counters and accumulates
 * them per host package × version × spec × day so regressions surface fast.
 */
export function telemetryRoutes(db: Database) {
  const app = new Hono();

  // POST /v1/telemetry — accumulate parser-health counters
  app.post("/telemetry", zValidator("json", telemetryInput), async (c) => {
    const body = c.req.valid("json");

    const [row] = await db
      .insert(telemetryCounters)
      .values({
        hostPackage: body.hostPackage,
        hostVersion: body.hostVersion,
        specVersion: body.specVersion,
        attempts: body.attempts,
        successes: body.successes,
        failures: body.failures,
        day: body.day,
      })
      .onConflictDoUpdate({
        target: [
          telemetryCounters.hostPackage,
          telemetryCounters.hostVersion,
          telemetryCounters.specVersion,
          telemetryCounters.day,
        ],
        set: {
          attempts: sql`${telemetryCounters.attempts} + ${body.attempts}`,
          successes: sql`${telemetryCounters.successes} + ${body.successes}`,
          failures: sql`${telemetryCounters.failures} + ${body.failures}`,
          updatedAt: sql`now()`,
        },
      })
      .returning();

    return c.json({ counter: row }, 200);
  });

  // GET /v1/telemetry/alerts — admin-only breakage report. Computes per
  // (host_package, host_version) failure rate over the trailing 48h and flags
  // pairs above the threshold with enough attempts. Guarded by a static admin
  // token in `Authorization: Bearer <ADMIN_TOKEN>` (env), constant-time
  // compared. Intended for an internal dashboard + the cron alert script.
  app.get("/telemetry/alerts", async (c) => {
    requireAdminToken(c.req.header("authorization"));

    const stats = await computeAlerts(db);
    const flagged = stats.filter((s) => s.flagged);
    return c.json({ flagged, stats }, 200);
  });

  return app;
}

/**
 * Validate the admin bearer token against env ADMIN_TOKEN, constant-time. Throws
 * 401 on a missing/malformed header and 403 on a wrong token. A missing
 * ADMIN_TOKEN env disables the endpoint entirely (503) rather than letting it
 * run open.
 */
function requireAdminToken(header: string | undefined): void {
  const expected = process.env.ADMIN_TOKEN;
  if (!expected || expected.length === 0) {
    throw new HTTPException(503, { message: "Alerts endpoint not configured" });
  }
  if (!header || !header.toLowerCase().startsWith("bearer ")) {
    throw new HTTPException(401, { message: "Missing or malformed admin token" });
  }
  const provided = header.slice(7).trim();
  const a = Buffer.from(provided);
  const b = Buffer.from(expected);
  if (a.length !== b.length || !timingSafeEqual(a, b)) {
    throw new HTTPException(403, { message: "Invalid admin token" });
  }
}
