import { Hono } from "hono";
import { zValidator } from "@hono/zod-validator";
import { z } from "zod";
import { eq, sql } from "drizzle-orm";
import { drivers } from "../db/schema.js";
import { requireBearer } from "../middleware/auth.js";
import type { DriverProfile } from "../auth/otp.js";
import type { Database } from "../db/client.js";

const updateInput = z
  .object({
    name: z.string().trim().min(1).max(100).optional(),
    city: z.string().trim().min(1).max(100).optional(),
    platforms: z.array(z.string().min(1).max(20)).max(20).optional(),
  })
  .refine((v) => v.name !== undefined || v.city !== undefined || v.platforms !== undefined, {
    message: "at least one of name, city, platforms is required",
  });

function profileOf(row: typeof drivers.$inferSelect): DriverProfile {
  return {
    id: row.id,
    phone: row.phone,
    name: row.name,
    city: row.city,
    platforms: row.platforms ?? null,
    tier: row.tier,
  };
}

/**
 * Authenticated driver-profile router: read and update the current driver.
 * Mounted under /v1 so the paths are GET /v1/me and PATCH /v1/me.
 */
export function meRoutes(db: Database) {
  const app = new Hono();
  const guard = requireBearer(db);

  app.get("/me", guard, async (c) => {
    const driverId = c.get("driverId");
    const [row] = await db.select().from(drivers).where(eq(drivers.id, driverId)).limit(1);
    if (!row) return c.json({ error: "Driver not found" }, 404);
    return c.json({ driver: profileOf(row) }, 200);
  });

  app.patch("/me", guard, zValidator("json", updateInput), async (c) => {
    const driverId = c.get("driverId");
    const body = c.req.valid("json");

    const [row] = await db
      .update(drivers)
      .set({
        ...(body.name !== undefined ? { name: body.name } : {}),
        ...(body.city !== undefined ? { city: body.city } : {}),
        ...(body.platforms !== undefined ? { platforms: body.platforms } : {}),
        updatedAt: sql`now()`,
      })
      .where(eq(drivers.id, driverId))
      .returning();
    if (!row) return c.json({ error: "Driver not found" }, 404);
    return c.json({ driver: profileOf(row) }, 200);
  });

  return app;
}
