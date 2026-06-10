/**
 * Server-side Play subscription verification seam (B-049).
 *
 * REAL verification calls the Google Play Developer API
 * (purchases.subscriptionsv2.get) with a service-account credential to confirm a
 * purchase token, read its authoritative state (active / canceled / in grace /
 * on hold / expired), trial status, and expiry — so the server never trusts a
 * client-supplied state blindly. That requires service-account creds that aren't
 * provisioned yet, so it ships behind this interface with a {@link StubVerifier}
 * that TRUSTS the client and logs a warning on every call.
 *
 * SECURITY — LAUNCH BLOCKER: the stub means a malicious client could self-grant
 * premium by POSTing a fabricated token + "active". Tracked in techdebt; the
 * real verifier (+ signed RTDN/Pub-Sub validation) must land before launch.
 */

/** The lifecycle status the server stores; mirrors `subscriptions.status`. */
export type SubscriptionStatus = "active" | "canceled" | "grace" | "hold" | "expired";

/** The verified (or, for the stub, client-asserted) view of a subscription. */
export interface VerifiedSubscription {
  productId: string;
  status: SubscriptionStatus;
  trial: boolean;
  /** Period end as a Date, when known. */
  expiresAt: Date | null;
}

/** What the client claims about a purchase, before verification. */
export interface PurchaseClaim {
  purchaseToken: string;
  productId: string;
  status: SubscriptionStatus;
  trial: boolean;
  expiresAt: Date | null;
}

export interface PlayVerifier {
  /**
   * Verify a purchase token against Play and return the authoritative subscription state. The stub
   * echoes the client's claim; the real impl ignores the claim and queries Play.
   */
  verify(claim: PurchaseClaim): Promise<VerifiedSubscription>;
}

/**
 * Trust-the-client stub. Echoes the claim verbatim and logs a WARNING so the gap is visible in
 * logs. NEVER ship to production without the real verifier — see the launch-blocker note above.
 */
export class StubVerifier implements PlayVerifier {
  async verify(claim: PurchaseClaim): Promise<VerifiedSubscription> {
    console.warn(
      `[StubVerifier] Trusting UNVERIFIED client subscription claim for token ` +
        `${claim.purchaseToken.slice(0, 8)}… (status=${claim.status}, trial=${claim.trial}). ` +
        `Real Play Developer API verification is a LAUNCH BLOCKER.`,
    );
    return {
      productId: claim.productId,
      status: claim.status,
      trial: claim.trial,
      expiresAt: claim.expiresAt,
    };
  }
}
