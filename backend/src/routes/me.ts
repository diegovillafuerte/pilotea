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
import type { StorageAdapter } from "../imports/storage.js";
import { storageFromEnv } from "../imports/storage.js";

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
 * Authenticated driver-profile router: read, update and delete the current driver.
 * Mounted under /v1 so the paths are GET, PATCH and DELETE /v1/me. Takes the storage
 * adapter holding the import originals (which account deletion must remove) so tests
 * can inject in-memory storage, mirroring `importsRoutes`.
 */
export function meRoutes(db: Database, storage: StorageAdapter = storageFromEnv()) {
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
  // In Postgres, every child table that references drivers.id is ON DELETE CASCADE (sessions,
  // weekly_aggregates, imports, subscriptions, referral_codes, referral_redemptions,
  // premium_grants) or SET NULL (devices — the anonymous install identity survives so the reader
  // keeps working post-deletion). The bearer session is revoked implicitly by the sessions
  // cascade.
  //
  // The uploaded import originals (Uber/DiDi/inDrive PDFs + screenshots, which carry PII) live in
  // object storage, not Postgres, and `imports.file_key` is the only pointer to them — the cascade
  // would drop those rows and orphan the objects for good. So they are deleted explicitly, first:
  //   1. Snapshot the driver's imports and delete every original, outside any transaction (a slow
  //      or stalled R2 call must not hold a DB connection or row lock). Fail closed: if any delete
  //      fails, Postgres is left untouched and the request 503s so the client can retry. Originals
  //      already deleted stay deleted (nothing reads them back), and deleteFile is idempotent.
  //   2. Delete the driver row under a FOR UPDATE lock, unless an import landed after the
  //      snapshot (its originals weren't deleted): then 409, and the retry's snapshot includes it.
  //      While the lock is held, a racing import insert blocks on its FK check, then fails once
  //      the driver is gone, and POST /v1/imports removes that upload itself.
  // So the account is never deleted while an original it references survives (gaps: TD-046).
  app.delete("/me", guard, async (c) => {
    const driverId = c.get("driverId");

    const snapshot = await db
      .select({ id: imports.id, fileKey: imports.fileKey })
      .from(imports)
      .where(eq(imports.driverId, driverId));
    // file_key is comma-joined for multi-file (DiDi) imports; see the imports schema.
    const keys = new Set(snapshot.flatMap((r) => r.fileKey.split(",")).filter(Boolean));
    const results = await Promise.allSettled([...keys].map((key) => storage.deleteFile(key)));
    const failures = results.filter((r) => r.status === "rejected");
    if (failures.length > 0) {
      const reason: unknown = failures[0]!.reason;
      console.error(
        `Account deletion aborted: ${failures.length}/${keys.size} stored file(s) not deleted:`,
        reason instanceof Error ? reason.message : reason,
      );
      return c.json(
        { error: "Could not delete stored import files; account not deleted. Retry." },
        503,
      );
    }

    const cleared = new Set(snapshot.map((r) => r.id));
    const outcome = await db.transaction(async (tx) => {
      const [driver] = await tx
        .select({ id: drivers.id })
        .from(drivers)
        .where(eq(drivers.id, driverId))
        .for("update");
      if (!driver) return "not_found" as const;

      const current = await tx
        .select({ id: imports.id })
        .from(imports)
        .where(eq(imports.driverId, driverId));
      if (current.some((r) => !cleared.has(r.id))) return "raced" as const;

      await tx.delete(drivers).where(eq(drivers.id, driverId));
      return "deleted" as const;
    });

    if (outcome === "not_found") return c.json({ error: "Driver not found" }, 404);
    if (outcome === "raced") {
      return c.json(
        { error: "An import arrived during deletion; account not deleted. Retry." },
        409,
      );
    }
    return c.json({ ok: true }, 200);
  });

  return app;
}
