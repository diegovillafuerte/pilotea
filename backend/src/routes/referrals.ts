import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { zValidator } from "@hono/zod-validator";
import { z } from "zod";
import { and, count, desc, eq, gte, sql } from "drizzle-orm";
import {
  drivers,
  premiumGrants,
  referralCodes,
  referralRedemptions,
  subscriptions,
} from "../db/schema.js";
import { requireBearer } from "../middleware/auth.js";
import { requireAdmin } from "./admin.js";
import { generateReferralCode, normalizeReferralCode } from "../referrals/code.js";
import {
  grantPremiumDays,
  PARTNER_GRANT_DAYS,
  REFERRAL_GRANT_DAYS,
} from "../referrals/grants.js";
import type { SubscriptionStatus } from "../subscriptions/verifier.js";
import type { Database } from "../db/client.js";

/**
 * Referral & partners program (B-056).
 *
 * Two proven growth loops share one set of tables:
 *  - Driver-to-driver referrals: every account auto-mints a code on its first
 *    GET /v1/referrals/mine; redeeming a code grants {@link REFERRAL_GRANT_DAYS}
 *    premium days to BOTH the referrer and the redeemer.
 *  - Ruta Rentable Partners: operators mint partner codes (POST /v1/admin/partners)
 *    bound to a driver-influencer's name; redeeming one grants
 *    {@link PARTNER_GRANT_DAYS} days to the REDEEMER ONLY (the influencer is paid
 *    manually off the attribution export, GET /v1/admin/partners).
 *
 * Abuse guardrails on redemption (each returns a specific Spanish message):
 *  - the code must exist;
 *  - a driver can't redeem their OWN code (self-referral);
 *  - a driver can redeem at most once, ever (unique redeemerDriverId);
 *  - the redeeming account must be NEW (< {@link MAX_REDEEMER_ACCOUNT_AGE_DAYS}
 *    days old) — the loop rewards bringing in fresh drivers, not reactivation;
 *  - a device can be used for at most one redemption, ever (unique device id) —
 *    a coarse heuristic against farming codes across throwaway accounts.
 */

/** A redeeming account must be younger than this to redeem (new-driver requirement). */
export const MAX_REDEEMER_ACCOUNT_AGE_DAYS = 30;

const ATTRIBUTION_WINDOW_DAYS = 30;
const MILLIS_PER_DAY = 24 * 60 * 60 * 1000;

const redeemInput = z.object({
  code: z.string().trim().min(1).max(16),
  deviceId: z.string().uuid(),
});

const createPartnerInput = z.object({
  name: z.string().trim().min(1).max(100),
});

/** Spanish error copy for every redemption failure mode. */
const ES = {
  codeNotFound: "Ese código no existe. Revisa que lo hayas escrito bien.",
  selfReferral: "No puedes usar tu propio código de invitación.",
  alreadyRedeemed: "Ya canjeaste un código de invitación antes. Solo se puede una vez.",
  accountTooOld:
    "Los códigos de invitación son solo para conductores nuevos (cuentas de menos de 30 días).",
  deviceUsed: "Este dispositivo ya canjeó un código de invitación.",
} as const;

export function referralRoutes(db: Database) {
  const app = new Hono();
  const guard = requireBearer(db);

  /**
   * GET /v1/referrals/mine — the signed-in driver's own code + their referral stats. Auto-creates the
   * driver's code on first call (idempotent: a retry returns the existing code, never a duplicate).
   */
  app.get("/referrals/mine", guard, async (c) => {
    const driverId = c.get("driverId");
    const code = await ensureDriverCode(db, driverId);

    const [redemptions] = await db
      .select({ c: count() })
      .from(referralRedemptions)
      .where(eq(referralRedemptions.codeId, code.id));

    const [earned] = await db
      .select({ days: sql<number>`coalesce(sum(${premiumGrants.days}), 0)` })
      .from(premiumGrants)
      .where(
        and(eq(premiumGrants.driverId, driverId), eq(premiumGrants.reason, "referral_referrer")),
      );

    const [driver] = await db
      .select({ premiumUntil: drivers.premiumUntil })
      .from(drivers)
      .where(eq(drivers.id, driverId))
      .limit(1);

    return c.json({
      code: code.code,
      redemptionsCount: redemptions?.c ?? 0,
      premiumDaysEarned: Number(earned?.days ?? 0),
      premiumUntilMillis: driver?.premiumUntil ? driver.premiumUntil.getTime() : null,
    });
  });

  /**
   * POST /v1/referrals/redeem — redeem a referral or partner code. Runs every abuse guardrail (see
   * file header) and, on success, grants premium days and extends `premiumUntil` for the entitled
   * sides. Validation failures surface as 4xx with the Spanish copy in {@link ES}.
   */
  app.post("/referrals/redeem", guard, zValidator("json", redeemInput), async (c) => {
    const redeemerId = c.get("driverId");
    const { code: rawCode, deviceId } = c.req.valid("json");
    const normalized = normalizeReferralCode(rawCode);

    const [codeRow] = await db
      .select()
      .from(referralCodes)
      .where(eq(referralCodes.code, normalized))
      .limit(1);
    if (!codeRow) throw new HTTPException(404, { message: ES.codeNotFound });

    // Self-referral: redeeming your own driver code.
    if (codeRow.driverId && codeRow.driverId === redeemerId) {
      throw new HTTPException(400, { message: ES.selfReferral });
    }

    // One redemption per account, ever.
    const [priorByDriver] = await db
      .select({ id: referralRedemptions.id })
      .from(referralRedemptions)
      .where(eq(referralRedemptions.redeemerDriverId, redeemerId))
      .limit(1);
    if (priorByDriver) throw new HTTPException(409, { message: ES.alreadyRedeemed });

    // New-driver requirement: the redeeming account must be < 30 days old.
    const [redeemer] = await db
      .select({ createdAt: drivers.createdAt })
      .from(drivers)
      .where(eq(drivers.id, redeemerId))
      .limit(1);
    if (!redeemer) throw new HTTPException(404, { message: "Driver not found" });
    const accountAgeDays = (Date.now() - redeemer.createdAt.getTime()) / MILLIS_PER_DAY;
    if (accountAgeDays >= MAX_REDEEMER_ACCOUNT_AGE_DAYS) {
      throw new HTTPException(403, { message: ES.accountTooOld });
    }

    // Device fraud heuristic: one redemption per device, ever.
    const [priorByDevice] = await db
      .select({ id: referralRedemptions.id })
      .from(referralRedemptions)
      .where(eq(referralRedemptions.redeemerDeviceId, deviceId))
      .limit(1);
    if (priorByDevice) throw new HTTPException(409, { message: ES.deviceUsed });

    const isPartner = codeRow.type === "partner";
    const redeemerDays = isPartner ? PARTNER_GRANT_DAYS : REFERRAL_GRANT_DAYS;
    const referrerDays = isPartner ? 0 : REFERRAL_GRANT_DAYS;

    // Record the redemption first; its unique constraints (account + device) are the race-safe
    // backstop if two requests slip past the reads above.
    let redemption;
    try {
      [redemption] = await db
        .insert(referralRedemptions)
        .values({
          codeId: codeRow.id,
          redeemerDriverId: redeemerId,
          redeemerDeviceId: deviceId,
          grantedDaysReferrer: referrerDays,
          grantedDaysRedeemer: redeemerDays,
        })
        .returning();
    } catch {
      // Unique violation on (account | device) — a concurrent redemption won.
      throw new HTTPException(409, { message: ES.alreadyRedeemed });
    }

    const redeemerStatus = await currentStatus(db, redeemerId);
    const redeemerUntil = await grantPremiumDays(
      db,
      redeemerId,
      redeemerDays,
      "referral_redeemer",
      redemption!.id,
      redeemerStatus,
    );

    // Driver-to-driver: grant the referrer their side too (partner codes pay no on-platform days).
    if (!isPartner && codeRow.driverId) {
      const referrerStatus = await currentStatus(db, codeRow.driverId);
      await grantPremiumDays(
        db,
        codeRow.driverId,
        referrerDays,
        "referral_referrer",
        redemption!.id,
        referrerStatus,
      );
    }

    return c.json({
      grantedDaysRedeemer: redeemerDays,
      grantedDaysReferrer: referrerDays,
      premiumUntilMillis: redeemerUntil.getTime(),
    });
  });

  /**
   * POST /v1/admin/partners — operator mints a partner code for a driver-influencer (ADMIN_TOKEN).
   * The code is driver-less (driverId NULL) and type=partner: redeeming it pays the redeemer only.
   */
  app.post("/admin/partners", zValidator("json", createPartnerInput), async (c) => {
    requireAdmin(c.req.header("authorization"));
    const { name } = c.req.valid("json");
    const code = await insertUniqueCode(db, { driverId: null, type: "partner", name });
    return c.json({ code: code.code, name, type: "partner" }, 201);
  });

  /**
   * GET /v1/admin/partners — attribution export for manual payouts (ADMIN_TOKEN). One row per partner
   * code with its redemption counts over the last 30 days and all-time, as plain JSON.
   */
  app.get("/admin/partners", async (c) => {
    requireAdmin(c.req.header("authorization"));
    const since = new Date(Date.now() - ATTRIBUTION_WINDOW_DAYS * MILLIS_PER_DAY);

    const partners = await db
      .select()
      .from(referralCodes)
      .where(eq(referralCodes.type, "partner"))
      .orderBy(desc(referralCodes.createdAt));

    const rows = await Promise.all(
      partners.map(async (p) => {
        const [all] = await db
          .select({ c: count() })
          .from(referralRedemptions)
          .where(eq(referralRedemptions.codeId, p.id));
        const [recent] = await db
          .select({ c: count() })
          .from(referralRedemptions)
          .where(
            and(
              eq(referralRedemptions.codeId, p.id),
              gte(referralRedemptions.createdAt, since),
            ),
          );
        return {
          code: p.code,
          name: p.name,
          createdAtMillis: p.createdAt.getTime(),
          redemptionsAllTime: all?.c ?? 0,
          redemptionsLast30d: recent?.c ?? 0,
        };
      }),
    );

    return c.json({ windowDays: ATTRIBUTION_WINDOW_DAYS, partners: rows });
  });

  return app;
}

/** The driver's live subscription status (most-recent row), or null when they've never synced. */
async function currentStatus(db: Database, driverId: string): Promise<SubscriptionStatus | null> {
  const [sub] = await db
    .select({ status: subscriptions.status })
    .from(subscriptions)
    .where(eq(subscriptions.driverId, driverId))
    .orderBy(desc(subscriptions.updatedAt))
    .limit(1);
  return sub ? (sub.status as SubscriptionStatus) : null;
}

/**
 * Return the driver's referral code, creating it on first call. Idempotent: a concurrent create
 * collides on the unique driverId and we re-read the winner's row.
 */
async function ensureDriverCode(
  db: Database,
  driverId: string,
): Promise<typeof referralCodes.$inferSelect> {
  const [existing] = await db
    .select()
    .from(referralCodes)
    .where(eq(referralCodes.driverId, driverId))
    .limit(1);
  if (existing) return existing;

  try {
    return await insertUniqueCode(db, { driverId, type: "driver", name: null });
  } catch {
    // A concurrent first-call created it; return that row.
    const [row] = await db
      .select()
      .from(referralCodes)
      .where(eq(referralCodes.driverId, driverId))
      .limit(1);
    if (row) return row;
    throw new HTTPException(500, { message: "Failed to create referral code" });
  }
}

/**
 * Insert a referral-code row, retrying on the (vanishingly rare) random-code collision until a free
 * code is found. The DB unique constraint on `code` is the source of truth; we just retry a fresh
 * random code. The bounded loop also surfaces a driverId-unique collision (concurrent driver-code
 * create) as a thrown error, which {@link ensureDriverCode} catches and resolves by reading the
 * winner's row.
 */
async function insertUniqueCode(
  db: Database,
  values: { driverId: string | null; type: "driver" | "partner"; name: string | null },
): Promise<typeof referralCodes.$inferSelect> {
  const MAX_ATTEMPTS = 6;
  let lastErr: unknown;
  for (let i = 0; i < MAX_ATTEMPTS; i++) {
    const code = generateReferralCode();
    try {
      const [row] = await db
        .insert(referralCodes)
        .values({ ...values, code })
        .returning();
      return row!;
    } catch (err) {
      lastErr = err;
    }
  }
  throw lastErr ?? new HTTPException(500, { message: "Failed to generate referral code" });
}
