/**
 * Server-side session lifecycle: create, resolve, revoke.
 *
 * Ported from the legacy web app (src/lib/auth/session.ts) but transport-neutral
 * — the Android client carries the raw token as a bearer header rather than a
 * cookie. Only the SHA-256 hash of the token is ever stored.
 */

import { and, eq, gt } from "drizzle-orm";
import { sessions } from "../db/schema.js";
import type { Database } from "../db/client.js";
import {
  SESSION_EXPIRY_DAYS,
  generateSessionToken,
  sha256Hex,
} from "./tokens.js";

/**
 * Create a 30-day session for `driverId`. Returns the raw token; only its hash
 * is persisted, so the caller must return the raw token to the client now — it
 * cannot be recovered later.
 */
export async function createSession(db: Database, driverId: string): Promise<string> {
  const rawToken = generateSessionToken();
  const tokenHash = sha256Hex(rawToken);
  const expiresAt = new Date(Date.now() + SESSION_EXPIRY_DAYS * 24 * 60 * 60 * 1000);

  await db.insert(sessions).values({ driverId, tokenHash, expiresAt });
  return rawToken;
}

/**
 * Resolve a raw bearer token to a driver id, or null if the token is unknown or
 * expired. Lookup is by hash against the indexed `token_hash` column.
 */
export async function resolveSession(db: Database, rawToken: string): Promise<string | null> {
  const tokenHash = sha256Hex(rawToken);
  const [row] = await db
    .select({ driverId: sessions.driverId })
    .from(sessions)
    .where(and(eq(sessions.tokenHash, tokenHash), gt(sessions.expiresAt, new Date())))
    .limit(1);

  return row?.driverId ?? null;
}

/** Revoke (delete) the session identified by the raw token. Idempotent. */
export async function revokeSession(db: Database, rawToken: string): Promise<void> {
  const tokenHash = sha256Hex(rawToken);
  await db.delete(sessions).where(eq(sessions.tokenHash, tokenHash));
}
