/**
 * Crypto helpers for OTP + session tokens.
 *
 * Mirrors the legacy web app's hashing discipline (src/lib/auth/session.ts):
 * only SHA-256 hashes are persisted, never the raw OTP code or session token,
 * so a database leak does not expose anything usable for account takeover.
 */

import { randomBytes, createHash, timingSafeEqual } from "node:crypto";

/** Session lifetime — 30 days, matching the web app. */
export const SESSION_EXPIRY_DAYS = 30;

/** OTP lifetime — 10 minutes (B-042 spec). */
export const OTP_EXPIRY_MINUTES = 10;

/** Max requests per phone within the rate-limit window. */
export const OTP_RATE_LIMIT_MAX = 3;

/** Rate-limit window in minutes. */
export const OTP_RATE_LIMIT_WINDOW_MINUTES = 15;

/** Max verification attempts against a single (newest) OTP before it's dead. */
export const OTP_MAX_ATTEMPTS = 5;

/**
 * SHA-256 hex digest. Used for both OTP codes and session tokens so the same
 * fixed-length (64-char) column type holds either.
 */
export function sha256Hex(value: string): string {
  return createHash("sha256").update(value).digest("hex");
}

/** Generate a 6-digit numeric OTP, zero-padded (e.g. "048213"). */
export function generateOtpCode(): string {
  // randomInt-style draw from crypto bytes, modulo 1e6, zero-padded to 6.
  const n = randomBytes(4).readUInt32BE(0) % 1_000_000;
  return n.toString().padStart(6, "0");
}

/** Generate a 256-bit (32-byte) session token as a 64-char hex string. */
export function generateSessionToken(): string {
  return randomBytes(32).toString("hex");
}

/**
 * Constant-time comparison of two SHA-256 hex digests. Both are fixed 64-char
 * hex strings, so lengths always match; we still guard length to keep
 * timingSafeEqual from throwing on a malformed input.
 */
export function hashesEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  return timingSafeEqual(Buffer.from(a), Buffer.from(b));
}
