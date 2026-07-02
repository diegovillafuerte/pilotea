import { Hono } from "hono";
import { zValidator } from "@hono/zod-validator";
import { z } from "zod";
import { requireBearer } from "../middleware/auth.js";
import type { Database } from "../db/client.js";
import { writeMergedAggregate } from "../imports/aggregate-write.js";
import type { AggregateColumns } from "../imports/aggregate-merge.js";

// A weekly-aggregate money/metric value. Accepts a number or a numeric string but VALIDATES it:
//  - must be a well-formed decimal (no "abc"/"1e5"/"'; DROP" reaching the DECIMAL column → a 500),
//  - must fit the target column's precision, so a client can't POST a value that Zod passes but the
//    DECIMAL column overflows (still a 500) or that skews the paid population percentiles once folded
//    (fold-population-stats does Number(v) on it).
// The magnitude check is applied AFTER rounding to 2 dp, matching the column's scale, so a value like
// 99999999.999 (which rounds UP to 1e8 and overflows DECIMAL(10,2)) is also caught.
function boundedDecimal(maxExclusive: number) {
  return z
    .union([z.number(), z.string()])
    .transform((v) => String(v).trim())
    .refine((s) => /^-?\d+(\.\d+)?$/.test(s), { message: "must be a decimal number" })
    .refine((s) => Math.abs(Math.round(Number(s) * 100) / 100) < maxExclusive, {
      message: "value out of range",
    });
}
// net/gross/km/hours + the (ignored) ratio fields all target DECIMAL(10,2) → max 99,999,999.99.
const decimalString = boundedDecimal(100_000_000);
// platform_commission_pct is DECIMAL(5,2) → max 999.99; a looser bound would overflow the column.
const pctString = boundedDecimal(1_000);

const aggregateInput = z.object({
  platform: z.string().min(1).max(20),
  weekStart: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "weekStart must be YYYY-MM-DD"),
  netEarnings: decimalString,
  grossEarnings: decimalString,
  totalTrips: z.number().int().nonnegative(),
  totalKm: decimalString.optional(),
  hoursOnline: decimalString.optional(),
  // Derived ratios are still ACCEPTED for wire compatibility (the Android client
  // sends them) but are IGNORED: the server recomputes them from the raw fields
  // in writeMergedAggregate so a stored ratio always agrees with its
  // numerator/denominator. Kept in the schema to avoid a silent contract break;
  // the route maps them to null below.
  earningsPerTrip: decimalString.optional(),
  earningsPerKm: decimalString.optional(),
  earningsPerHour: decimalString.optional(),
  tripsPerHour: decimalString.optional(),
  platformCommissionPct: pctString.optional(),
  source: z.enum(["captured", "imported"]).default("captured"),
});

/**
 * Build the aggregates router. Takes a Drizzle DB so tests can inject pglite.
 */
export function aggregatesRoutes(db: Database) {
  const app = new Hono();

  // POST /v1/aggregates — upsert a weekly aggregate (driver × platform × week).
  //
  // PR-A: this no longer blind-overwrites the row. It funnels through the same
  // canonical field-level merge as the import path (writeMergedAggregate): the
  // incoming captured values win for the fields they carry, fields they omit
  // keep their prior (possibly imported) value — so a routine captured sync can
  // no longer clobber an imported commission/total to null. Derived ratios are
  // recomputed from the merged raw fields; a captured contribution landing on an
  // imported row becomes source='mixed'.
  app.post("/aggregates", requireBearer(db), zValidator("json", aggregateInput), async (c) => {
    const body = c.req.valid("json");
    // Ownership comes from the authenticated session, never the request body.
    const driverId = c.get("driverId");

    // Map the request to the mergeable column set, PRESERVING absence as null
    // (an omitted metric must not clobber a known value). Derived ratios are
    // intentionally left null — writeMergedAggregate recomputes them from the
    // merged raw fields, so a stored ratio always agrees with its inputs.
    const incoming: AggregateColumns = {
      netEarnings: body.netEarnings,
      grossEarnings: body.grossEarnings,
      totalTrips: body.totalTrips,
      totalKm: body.totalKm ?? null,
      hoursOnline: body.hoursOnline ?? null,
      earningsPerTrip: null,
      earningsPerKm: null,
      earningsPerHour: null,
      tripsPerHour: null,
      platformCommissionPct: body.platformCommissionPct ?? null,
      source: body.source,
    };

    const result = await writeMergedAggregate(
      db,
      { driverId, platform: body.platform, weekStart: body.weekStart },
      incoming,
    );

    // A captured sync always carries core earnings (net/gross/trips are required
    // above), so 'rejected' is unreachable here — guard defensively for the type.
    if (result.kind === "rejected") {
      return c.json({ error: "Faltan las ganancias de la semana." }, 422);
    }

    return c.json({ aggregate: result.row }, 200);
  });

  return app;
}
