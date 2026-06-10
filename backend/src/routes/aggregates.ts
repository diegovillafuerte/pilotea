import { Hono } from "hono";
import { zValidator } from "@hono/zod-validator";
import { z } from "zod";
import { sql } from "drizzle-orm";
import { weeklyAggregates } from "../db/schema.js";
import { requireBearer } from "../middleware/auth.js";
import type { Database } from "../db/client.js";

const decimalString = z
  .union([z.number(), z.string()])
  .transform((v) => String(v));

const aggregateInput = z.object({
  driverId: z.string().uuid(),
  platform: z.string().min(1).max(20),
  weekStart: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "weekStart must be YYYY-MM-DD"),
  netEarnings: decimalString,
  grossEarnings: decimalString,
  totalTrips: z.number().int().nonnegative(),
  totalKm: decimalString.optional(),
  hoursOnline: decimalString.optional(),
  earningsPerTrip: decimalString.optional(),
  earningsPerKm: decimalString.optional(),
  earningsPerHour: decimalString.optional(),
  tripsPerHour: decimalString.optional(),
  platformCommissionPct: decimalString.optional(),
  source: z.enum(["captured", "imported"]).default("captured"),
});

/**
 * Build the aggregates router. Takes a Drizzle DB so tests can inject pglite.
 */
export function aggregatesRoutes(db: Database) {
  const app = new Hono();

  // POST /v1/aggregates — upsert a weekly aggregate (driver × platform × week)
  app.post("/aggregates", requireBearer, zValidator("json", aggregateInput), async (c) => {
    const body = c.req.valid("json");

    const [row] = await db
      .insert(weeklyAggregates)
      .values({
        driverId: body.driverId,
        platform: body.platform,
        weekStart: body.weekStart,
        netEarnings: body.netEarnings,
        grossEarnings: body.grossEarnings,
        totalTrips: body.totalTrips,
        totalKm: body.totalKm,
        hoursOnline: body.hoursOnline,
        earningsPerTrip: body.earningsPerTrip,
        earningsPerKm: body.earningsPerKm,
        earningsPerHour: body.earningsPerHour,
        tripsPerHour: body.tripsPerHour,
        platformCommissionPct: body.platformCommissionPct,
        source: body.source,
      })
      .onConflictDoUpdate({
        target: [
          weeklyAggregates.driverId,
          weeklyAggregates.platform,
          weeklyAggregates.weekStart,
        ],
        set: {
          netEarnings: body.netEarnings,
          grossEarnings: body.grossEarnings,
          totalTrips: body.totalTrips,
          totalKm: body.totalKm,
          hoursOnline: body.hoursOnline,
          earningsPerTrip: body.earningsPerTrip,
          earningsPerKm: body.earningsPerKm,
          earningsPerHour: body.earningsPerHour,
          tripsPerHour: body.tripsPerHour,
          platformCommissionPct: body.platformCommissionPct,
          source: body.source,
          updatedAt: sql`now()`,
        },
      })
      .returning();

    return c.json({ aggregate: row }, 200);
  });

  return app;
}
