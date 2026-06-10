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
