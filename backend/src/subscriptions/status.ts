import type { SubscriptionStatus } from "./verifier.js";

/** Statuses that grant the premium tier (entitled). */
const ENTITLED: ReadonlySet<SubscriptionStatus> = new Set(["active", "canceled", "grace"]);

/** Whether a subscription [status] currently entitles the driver to premium. */
export function isEntitled(status: SubscriptionStatus): boolean {
  return ENTITLED.has(status);
}

/** Derive the driver `tier` ("premium" | "free") from a subscription status. */
export function tierForStatus(status: SubscriptionStatus): "premium" | "free" {
  return isEntitled(status) ? "premium" : "free";
}

/**
 * The driver's EFFECTIVE premium tier (B-056). Premium iff an entitled Play
 * subscription is active OR a referral/partner grant is still live
 * (`premiumUntil > now`). This is the single source of truth the /v1/me handler
 * and the Android entitlement merge both rely on so a grant unlocks premium with
 * no Play purchase, and a Play subscription still wins when no grant exists.
 *
 * @param subscriptionStatus the live subscription status, or null when the
 *   driver has never synced a purchase.
 * @param premiumUntil the grant expiry timestamp, or null when no grant exists.
 * @param now the reference instant (injected for deterministic tests).
 */
export function effectiveTier(
  subscriptionStatus: SubscriptionStatus | null,
  premiumUntil: Date | null,
  now: Date = new Date(),
): "premium" | "free" {
  const subPremium = subscriptionStatus !== null && isEntitled(subscriptionStatus);
  const grantPremium = premiumUntil !== null && premiumUntil.getTime() > now.getTime();
  return subPremium || grantPremium ? "premium" : "free";
}
