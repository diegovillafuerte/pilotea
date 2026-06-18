import { Hono } from "hono";
import { zValidator } from "@hono/zod-validator";
import { z } from "zod";
import { and, desc, eq, sql } from "drizzle-orm";
import { drivers, subscriptions, imports } from "../db/schema.js";
import { requireBearer } from "../middleware/auth.js";
import { effectiveTier } from "../subscriptions/status.js";
import type { SubscriptionStatus } from "../subscriptions/verifier.js";
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

function profileOf(row: typeof drivers.$inferSelect, tier: string): DriverProfile {
  return {
    id: row.id,
    phone: row.phone,
    name: row.name,
    city: row.city,
    platforms: row.platforms ?? null,
    tier,
  };
}

/** A driver's current subscription block for the /v1/me response, or null when none. */
interface SubscriptionView {
  status: SubscriptionStatus;
  trial: boolean;
  expiresAtMillis: number | null;
}

/**
 * Resolve the driver's current subscription (most-recently-updated row) and the EFFECTIVE tier
 * (B-049, extended B-056). Tier is "premium" iff an entitled Play subscription is active OR a
 * referral/partner grant is still live (`premiumUntil > now`); see {@link effectiveTier}. Computing
 * it from live state means it can never drift from the `drivers.tier` column.
 *
 * The driver's `premiumUntil` is passed in (read from the driver row by the caller) so the grant is
 * surfaced to the client (`premiumUntilMillis`) and the Android entitlement merge can unlock premium
 * without a Play purchase.
 */
async function resolveSubscription(
  db: Database,
  driverId: string,
  premiumUntil: Date | null,
): Promise<{
  tier: "premium" | "free";
  subscription: SubscriptionView | null;
  premiumUntilMillis: number | null;
}> {
  const [sub] = await db
    .select()
    .from(subscriptions)
    .where(eq(subscriptions.driverId, driverId))
    .orderBy(desc(subscriptions.updatedAt))
    .limit(1);

  const status = sub ? (sub.status as SubscriptionStatus) : null;
  const premiumUntilMillis = premiumUntil ? premiumUntil.getTime() : null;
  return {
    tier: effectiveTier(status, premiumUntil),
    subscription: sub
      ? {
          status: status!,
          trial: sub.trial,
          expiresAtMillis: sub.expiresAt ? sub.expiresAt.getTime() : null,
        }
      : null,
    premiumUntilMillis,
  };
}

/**
 * Import/data verification (derived, revocable). A driver is "verified" once they
 * have at least one successfully-parsed import — proof they uploaded a real
 * Uber/DiDi statement. It is DERIVED from the `imports` table (not a frozen
 * column) so it has a free revocation path: flipping an import's status away from
 * 'parsed' (e.g. to 'failed'/'revoked' if later found fraudulent) drops the
 * driver below the threshold automatically. PR-A guarantees a 'parsed' import
 * carried real core earnings (no-core imports are rejected as 'failed').
 *
 * NOTE: v1 bar = "≥1 parsed import". Strengthening it (min data_completeness,
 * recent-week, per-platform) is deferred — see the account-onboarding design §0.5.
 */
async function isVerified(db: Database, driverId: string): Promise<boolean> {
  // EXISTS-style: stop at the first parsed import rather than counting all.
  const rows = await db
    .select({ id: imports.id })
    .from(imports)
    .where(and(eq(imports.driverId, driverId), eq(imports.status, "parsed")))
    .limit(1);
  return rows.length > 0;
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
    const { tier, subscription, premiumUntilMillis } = await resolveSubscription(
      db,
      driverId,
      row.premiumUntil,
    );
    const verified = await isVerified(db, driverId);
    return c.json({ driver: profileOf(row, tier), subscription, premiumUntilMillis, verified }, 200);
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
    const { tier, subscription, premiumUntilMillis } = await resolveSubscription(
      db,
      driverId,
      row.premiumUntil,
    );
    const verified = await isVerified(db, driverId);
    return c.json({ driver: profileOf(row, tier), subscription, premiumUntilMillis, verified }, 200);
  });

  // DELETE /v1/me — hard-delete the driver account (Play data-safety requirement: B-069).
  // Every child table that references drivers.id is ON DELETE CASCADE (sessions, weekly_aggregates,
  // imports, subscriptions, referral_codes, referral_redemptions, premium_grants) or SET NULL
  // (devices — the anonymous install identity survives so the reader keeps working post-deletion), so
  // a single delete removes all of the driver's PII and consented data. The bearer session is revoked
  // implicitly by the sessions cascade.
  app.delete("/me", guard, async (c) => {
    const driverId = c.get("driverId");
    const [row] = await db.delete(drivers).where(eq(drivers.id, driverId)).returning();
    if (!row) return c.json({ error: "Driver not found" }, 404);
    return c.json({ ok: true }, 200);
  });

  return app;
}
