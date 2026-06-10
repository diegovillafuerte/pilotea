import { Hono } from "hono";
import { zValidator } from "@hono/zod-validator";
import { z } from "zod";
import { sql } from "drizzle-orm";
import { telemetryCounters } from "../db/schema.js";
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

  return app;
}
