import { randomBytes, createHash } from "crypto";
import { db } from "@/lib/db";
import { sessions, drivers } from "@/lib/db/schema";
import { eq, and, gt } from "drizzle-orm";
import { cookies } from "next/headers";

const SESSION_EXPIRY_DAYS = 30;
const COOKIE_NAME = "kompara_session";

/**
 * Hash a raw session token with SHA-256.
 */
export function hashToken(token: string): string {
  return createHash("sha256").update(token).digest("hex");
}

/**
 * Create a new session for the given driver.
 * Returns the raw token (to be stored in the cookie).
 */
export async function createSession(driverId: string): Promise<string> {
  const rawToken = randomBytes(32).toString("hex");
  const tokenHash = hashToken(rawToken);
  const expiresAt = new Date(
    Date.now() + SESSION_EXPIRY_DAYS * 24 * 60 * 60 * 1000,
  );

  await db.insert(sessions).values({
    driverId,
    tokenHash,
    expiresAt,
  });

  return rawToken;
}

/**
 * Verify a session token and return the driver_id if valid.
 */
export async function verifySession(
  rawToken: string,
): Promise<string | null> {
  const tokenHash = hashToken(rawToken);
  const now = new Date();

  const [session] = await db
    .select({ driverId: sessions.driverId })
    .from(sessions)
    .where(and(eq(sessions.tokenHash, tokenHash), gt(sessions.expiresAt, now)))
    .limit(1);

  return session?.driverId ?? null;
}

/**
 * Destroy a session by its raw token.
 */
export async function destroySession(rawToken: string): Promise<void> {
  const tokenHash = hashToken(rawToken);
  await db.delete(sessions).where(eq(sessions.tokenHash, tokenHash));
}

/**
 * Set the session cookie in the response.
 */
export async function setSessionCookie(rawToken: string): Promise<void> {
  const cookieStore = await cookies();
  cookieStore.set(COOKIE_NAME, rawToken, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    maxAge: SESSION_EXPIRY_DAYS * 24 * 60 * 60,
    path: "/",
  });
}

/**
 * Clear the session cookie.
 */
export async function clearSessionCookie(): Promise<void> {
  const cookieStore = await cookies();
  cookieStore.delete(COOKIE_NAME);
}

/**
 * Get the session token from the request cookie.
 */
export async function getSessionToken(): Promise<string | null> {
  const cookieStore = await cookies();
  return cookieStore.get(COOKIE_NAME)?.value ?? null;
}

/**
 * Get the current driver from the session, with driver data.
 * Returns null if not authenticated.
 */
export async function getCurrentDriver() {
  const token = await getSessionToken();
  if (!token) return null;

  const driverId = await verifySession(token);
  if (!driverId) return null;

  const [driver] = await db
    .select()
    .from(drivers)
    .where(eq(drivers.id, driverId))
    .limit(1);

  return driver ?? null;
}
