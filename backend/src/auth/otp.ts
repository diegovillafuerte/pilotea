/**
 * WhatsApp OTP request + verification logic.
 *
 * Ported in spirit from the legacy web magic-link flow (src/lib/auth/magic-link.ts):
 * codes are hashed at rest, requests are rate-limited per phone over a trailing
 * window (in SQL, no infra dependency), and verification is single-use.
 */

import { and, desc, eq, gt, gte, isNull, sql } from "drizzle-orm";
import { drivers, devices, otpCodes } from "../db/schema.js";
import type { Database } from "../db/client.js";
import type { MessageSender } from "./message-sender.js";
import { createSession } from "./sessions.js";
import {
  OTP_EXPIRY_MINUTES,
  OTP_MAX_ATTEMPTS,
  OTP_RATE_LIMIT_MAX,
  OTP_RATE_LIMIT_WINDOW_MINUTES,
  generateOtpCode,
  hashesEqual,
  sha256Hex,
} from "./tokens.js";

/** Shape returned to the client on a successful verify. */
export interface DriverProfile {
  id: string;
  phone: string;
  name: string | null;
  city: string | null;
  platforms: string[] | null;
  tier: string;
}

export type VerifyResult =
  | { ok: true; token: string; driver: DriverProfile }
  | { ok: false; reason: "invalid_or_expired" };

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
 * Request an OTP for `phone`. Enforces the per-phone rate limit (max
 * {@link OTP_RATE_LIMIT_MAX} requests per {@link OTP_RATE_LIMIT_WINDOW_MINUTES})
 * by counting recent rows. Returns whether a code was actually sent; the route
 * always responds 200 regardless, to avoid phone-number enumeration.
 */
export async function requestOtp(
  db: Database,
  sender: MessageSender,
  phone: string,
): Promise<{ rateLimited: boolean }> {
  const windowStart = new Date(Date.now() - OTP_RATE_LIMIT_WINDOW_MINUTES * 60 * 1000);

  const rows = await db
    .select({ count: sql<number>`count(*)::int` })
    .from(otpCodes)
    .where(and(eq(otpCodes.phone, phone), gte(otpCodes.createdAt, windowStart)));

  const recentCount = rows[0]?.count ?? 0;
  if (recentCount >= OTP_RATE_LIMIT_MAX) {
    return { rateLimited: true };
  }

  const code = generateOtpCode();
  const expiresAt = new Date(Date.now() + OTP_EXPIRY_MINUTES * 60 * 1000);

  await db.insert(otpCodes).values({
    phone,
    codeHash: sha256Hex(code),
    expiresAt,
  });

  await sender.sendOtp(phone, code);
  return { rateLimited: false };
}

/**
 * Verify `code` for `phone`. On success: finds-or-creates the driver, attaches
 * the anonymous `deviceId` (and its captured data) if provided, mints a 30-day
 * session, and returns the raw token plus the driver profile.
 *
 * Security properties:
 * - Only the newest unconsumed, unexpired code for the phone is considered.
 * - A wrong guess increments `attempts`; once {@link OTP_MAX_ATTEMPTS} is hit the
 *   code is dead (treated as invalid) to bound brute force.
 * - On success the code is marked consumed (single use).
 */
export async function verifyOtp(
  db: Database,
  phone: string,
  code: string,
  deviceId?: string,
): Promise<VerifyResult> {
  const now = new Date();

  // Newest live challenge for this phone.
  const [challenge] = await db
    .select()
    .from(otpCodes)
    .where(
      and(eq(otpCodes.phone, phone), isNull(otpCodes.consumedAt), gt(otpCodes.expiresAt, now)),
    )
    .orderBy(desc(otpCodes.createdAt))
    .limit(1);

  if (!challenge || challenge.attempts >= OTP_MAX_ATTEMPTS) {
    return { ok: false, reason: "invalid_or_expired" };
  }

  if (!hashesEqual(challenge.codeHash, sha256Hex(code))) {
    await db
      .update(otpCodes)
      .set({ attempts: challenge.attempts + 1 })
      .where(eq(otpCodes.id, challenge.id));
    return { ok: false, reason: "invalid_or_expired" };
  }

  // Correct code: consume it (single use).
  await db.update(otpCodes).set({ consumedAt: now }).where(eq(otpCodes.id, challenge.id));

  // Find-or-create the driver for this phone.
  const [existing] = await db.select().from(drivers).where(eq(drivers.phone, phone)).limit(1);

  let driverRow: typeof drivers.$inferSelect;
  if (existing) {
    driverRow = existing;
  } else {
    const [created] = await db
      .insert(drivers)
      .values({ phone, anonymousDeviceId: deviceId ?? null })
      .returning();
    driverRow = created!;
  }

  // Anonymous → account merge: claim the device row and tag the driver. We do
  // this for both new and returning drivers so a driver who reinstalled and
  // used the app anonymously before re-logging in keeps their local identity.
  if (deviceId) {
    await mergeDevice(db, driverRow.id, deviceId);
    if (!driverRow.anonymousDeviceId) {
      const [updated] = await db
        .update(drivers)
        .set({ anonymousDeviceId: deviceId, updatedAt: now })
        .where(eq(drivers.id, driverRow.id))
        .returning();
      driverRow = updated!;
    }
  }

  const token = await createSession(db, driverRow.id);
  return { ok: true, token, driver: profileOf(driverRow) };
}

/**
 * Attach an anonymous device to a driver. Upserts the device row and points it
 * at the driver so any data keyed on the device id (telemetry, future local
 * rollups) is associated with the account. Conflicts on `device_id` simply set
 * the owning driver.
 */
async function mergeDevice(db: Database, driverId: string, deviceId: string): Promise<void> {
  await db
    .insert(devices)
    .values({ deviceId, driverId, lastSeenAt: new Date() })
    .onConflictDoUpdate({
      target: devices.deviceId,
      set: { driverId, lastSeenAt: new Date() },
    });
}

/**
 * Anonymous-first device registration. Idempotent: re-registering an existing
 * device just bumps `last_seen_at`. No account required.
 */
export async function registerDevice(db: Database, deviceId: string): Promise<void> {
  await db
    .insert(devices)
    .values({ deviceId, lastSeenAt: new Date() })
    .onConflictDoUpdate({ target: devices.deviceId, set: { lastSeenAt: new Date() } });
}
