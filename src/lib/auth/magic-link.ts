import { randomBytes, createHash } from "crypto";
import { db } from "@/lib/db";
import { magicLinks } from "@/lib/db/schema";
import { eq, and, isNull, gt, gte } from "drizzle-orm";

const TOKEN_EXPIRY_MINUTES = 15;
const RATE_LIMIT_WINDOW_MINUTES = 15;
const RATE_LIMIT_MAX = 3;

/**
 * Generate a cryptographic token (64-char hex string from 32 random bytes).
 */
function generateToken(): string {
  return randomBytes(32).toString("hex");
}

/**
 * Hash a magic link token with SHA-256.
 * Magic link tokens are hashed before storage, just like session tokens,
 * so a DB leak doesn't expose usable login tokens.
 */
function hashToken(token: string): string {
  return createHash("sha256").update(token).digest("hex");
}

/**
 * Check rate limit for a phone number.
 * Returns true if the phone has exceeded the rate limit.
 */
export async function isRateLimited(phone: string): Promise<boolean> {
  const windowStart = new Date(
    Date.now() - RATE_LIMIT_WINDOW_MINUTES * 60 * 1000,
  );

  const recentLinks = await db
    .select({ id: magicLinks.id })
    .from(magicLinks)
    .where(
      and(eq(magicLinks.phone, phone), gte(magicLinks.createdAt, windowStart)),
    );

  return recentLinks.length >= RATE_LIMIT_MAX;
}

/**
 * Create a new magic link for the given phone number.
 * Stores a SHA-256 hash of the token in the DB.
 * Returns the raw token (to be sent via WhatsApp).
 */
export async function createMagicLink(phone: string): Promise<string> {
  const rawToken = generateToken();
  const tokenHash = hashToken(rawToken);
  const expiresAt = new Date(Date.now() + TOKEN_EXPIRY_MINUTES * 60 * 1000);

  await db.insert(magicLinks).values({
    phone,
    token: tokenHash,
    expiresAt,
  });

  return rawToken;
}

/**
 * Validate a magic link token.
 * Returns the phone number if valid, or null if the token is
 * invalid, expired, or already used.
 *
 * Uses an atomic UPDATE...WHERE...RETURNING to prevent race conditions:
 * only one concurrent request can successfully consume the token.
 */
export async function validateMagicLink(
  token: string,
): Promise<string | null> {
  const tokenHash = hashToken(token);
  const now = new Date();

  // Atomic: UPDATE sets used_at only if token is unused and not expired.
  // RETURNING gives us the row only if the update matched, preventing
  // two concurrent requests from both consuming the same token.
  const [consumed] = await db
    .update(magicLinks)
    .set({ usedAt: now })
    .where(
      and(
        eq(magicLinks.token, tokenHash),
        isNull(magicLinks.usedAt),
        gt(magicLinks.expiresAt, now),
      ),
    )
    .returning({ phone: magicLinks.phone });

  return consumed?.phone ?? null;
}
