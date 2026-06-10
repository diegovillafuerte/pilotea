import { Hono } from "hono";
import { zValidator } from "@hono/zod-validator";
import { z } from "zod";
import { eq, sql } from "drizzle-orm";
import { drivers, subscriptions } from "../db/schema.js";
import { requireBearer } from "../middleware/auth.js";
import { tierForStatus } from "../subscriptions/status.js";
import { StubVerifier, type PlayVerifier, type SubscriptionStatus } from "../subscriptions/verifier.js";
import type { Database } from "../db/client.js";

const statusEnum = z.enum(["active", "canceled", "grace", "hold", "expired"]);

const syncInput = z.object({
  purchaseToken: z.string().min(1).max(1024),
  productId: z.string().min(1).max(100),
  status: statusEnum,
  trial: z.boolean().default(false),
  // Epoch millis when the current period ends, when the client knows it.
  expiresAtMillis: z.number().int().nonnegative().optional(),
});

/**
 * Real-Time Developer Notification (RTDN) shape. Play delivers these via Cloud Pub/Sub as a
 * base64-encoded `data` field. We validate the envelope + decoded payload shape here; signature /
 * Pub-Sub push-endpoint verification is the SAME launch-blocker techdebt as the StubVerifier.
 */
const rtdnInput = z.object({
  message: z.object({
    data: z.string().min(1), // base64(JSON DeveloperNotification)
    messageId: z.string().optional(),
  }),
  subscription: z.string().optional(),
});

/** Decoded subscriptionNotification payload (subset we act on). */
const subscriptionNotificationSchema = z.object({
  subscriptionNotification: z.object({
    purchaseToken: z.string().min(1),
    subscriptionId: z.string().optional(),
    // Google's notificationType is an int; we accept it but map status from our own field for the
    // stub. A real impl would re-verify the token against Play and ignore client-derived status.
    notificationType: z.number().int().optional(),
    status: statusEnum.optional(),
  }),
});

function millisToDate(millis: number | undefined): Date | null {
  return millis === undefined ? null : new Date(millis);
}

/**
 * Subscription router (B-049). Mounted under /v1.
 *
 * - POST /v1/subscriptions/sync — session-authed. The client posts a purchase token + state after
 *   a purchase or restore; we (stub-)verify, upsert by token (idempotent), and reflect the tier on
 *   the driver. Re-syncing the same token updates the row in place.
 * - POST /v1/rtdn — webhook stub. Validates the Pub/Sub envelope + decoded shape and updates the
 *   subscription status by token. Signature/Pub-Sub verification = launch-blocker techdebt.
 *
 * The [verifier] is injected so the real Play Developer API client can replace [StubVerifier]
 * without touching the route.
 */
export function subscriptionsRoutes(db: Database, verifier: PlayVerifier = new StubVerifier()) {
  const app = new Hono();
  const guard = requireBearer(db);

  app.post("/subscriptions/sync", guard, zValidator("json", syncInput), async (c) => {
    const driverId = c.get("driverId");
    const body = c.req.valid("json");

    // (Stub-)verify the claim against Play. The stub echoes it back and logs a WARNING.
    const verified = await verifier.verify({
      purchaseToken: body.purchaseToken,
      productId: body.productId,
      status: body.status,
      trial: body.trial,
      expiresAt: millisToDate(body.expiresAtMillis),
    });

    // Upsert by purchase token (idempotent re-sync). A token belongs to one driver; if a different
    // driver re-syncs the same token we re-point it (account/device change) and refresh state.
    const [row] = await db
      .insert(subscriptions)
      .values({
        driverId,
        purchaseToken: body.purchaseToken,
        productId: verified.productId,
        status: verified.status,
        trial: verified.trial,
        expiresAt: verified.expiresAt,
      })
      .onConflictDoUpdate({
        target: subscriptions.purchaseToken,
        set: {
          driverId,
          productId: verified.productId,
          status: verified.status,
          trial: verified.trial,
          expiresAt: verified.expiresAt,
          updatedAt: sql`now()`,
        },
      })
      .returning();
    if (!row) return c.json({ error: "Failed to record subscription" }, 500);

    await applyTier(db, driverId, verified.status);

    return c.json({ subscription: subscriptionDto(row) }, 200);
  });

  app.post("/rtdn", zValidator("json", rtdnInput), async (c) => {
    const { message } = c.req.valid("json");

    let decoded: unknown;
    try {
      decoded = JSON.parse(Buffer.from(message.data, "base64").toString("utf8"));
    } catch {
      return c.json({ error: "Malformed RTDN payload" }, 400);
    }

    const parsed = subscriptionNotificationSchema.safeParse(decoded);
    if (!parsed.success) {
      // Acknowledge non-subscription notifications (test/voided/one-time) without acting.
      return c.json({ ok: true, handled: false }, 200);
    }

    const note = parsed.data.subscriptionNotification;
    // Without the real verifier we trust the decoded status (defaulting to active). The real impl
    // would re-query Play by purchaseToken and ignore any client/payload-supplied status.
    const status: SubscriptionStatus = note.status ?? "active";

    const [row] = await db
      .update(subscriptions)
      .set({ status, updatedAt: sql`now()` })
      .where(eq(subscriptions.purchaseToken, note.purchaseToken))
      .returning();

    if (!row) {
      // Unknown token — acknowledge so Pub/Sub doesn't redeliver; nothing to update yet.
      return c.json({ ok: true, handled: false }, 200);
    }

    await applyTier(db, row.driverId, status);
    return c.json({ ok: true, handled: true }, 200);
  });

  return app;
}

/** Reflect a subscription status onto the driver's tier ("premium" | "free"). */
async function applyTier(db: Database, driverId: string, status: SubscriptionStatus): Promise<void> {
  await db
    .update(drivers)
    .set({ tier: tierForStatus(status), updatedAt: sql`now()` })
    .where(eq(drivers.id, driverId));
}

function subscriptionDto(row: typeof subscriptions.$inferSelect) {
  return {
    status: row.status,
    trial: row.trial,
    expiresAtMillis: row.expiresAt ? row.expiresAt.getTime() : null,
  };
}
