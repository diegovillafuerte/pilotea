import { eq, sql } from "drizzle-orm";
import { drivers, premiumGrants } from "../db/schema.js";
import { effectiveTier } from "../subscriptions/status.js";
import type { Database } from "../db/client.js";
import type { SubscriptionStatus } from "../subscriptions/verifier.js";

/** Days granted to each side of a driver-to-driver referral redemption (B-056). */
export const REFERRAL_GRANT_DAYS = 14;

/** Days granted to the redeemer of a partner code (redeemer-side only). */
export const PARTNER_GRANT_DAYS = 14;

const MILLIS_PER_DAY = 24 * 60 * 60 * 1000;

/**
 * Append a premium-day grant for [driverId] and extend their `premiumUntil` by [days] (B-056).
 *
 * Stacking: the new expiry is `max(now, current premiumUntil) + days`, so a driver who already has a
 * live grant gets the new days ADDED to their remaining time rather than replacing it (two 14-day
 * grants → 28 days). An expired/absent grant starts from `now`. Writes the immutable ledger row first
 * (audit trail), then materializes the new `premiumUntil` onto the driver and refreshes the
 * denormalized tier (premium while a grant is live; see {@link effectiveTier}).
 *
 * Returns the driver's new `premiumUntil`.
 *
 * @param subscriptionStatus the driver's live subscription status (or null) — only used to keep the
 *   denormalized `tier` column correct; the grant itself always implies premium.
 */
export async function grantPremiumDays(
  db: Database,
  driverId: string,
  days: number,
  reason: string,
  sourceId: string | null,
  subscriptionStatus: SubscriptionStatus | null,
  now: Date = new Date(),
): Promise<Date> {
  await db.insert(premiumGrants).values({ driverId, days, reason, sourceId });

  const [row] = await db
    .select({ premiumUntil: drivers.premiumUntil })
    .from(drivers)
    .where(eq(drivers.id, driverId))
    .limit(1);

  const base =
    row?.premiumUntil && row.premiumUntil.getTime() > now.getTime() ? row.premiumUntil : now;
  const newUntil = new Date(base.getTime() + days * MILLIS_PER_DAY);

  await db
    .update(drivers)
    .set({
      premiumUntil: newUntil,
      tier: effectiveTier(subscriptionStatus, newUntil, now),
      updatedAt: sql`now()`,
    })
    .where(eq(drivers.id, driverId));

  return newUntil;
}
