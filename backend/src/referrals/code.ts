import { randomInt } from "node:crypto";

/**
 * Unambiguous 8-character referral-code alphabet (B-056).
 *
 * Excludes the easily-confused glyphs 0/O, 1/I/L so a code survives being read
 * aloud, dictated over WhatsApp, or typed by hand. 31 symbols → 31^8 ≈ 8.5e11
 * combinations, ample for collision-free generation at our scale (the DB unique
 * constraint is the backstop; see {@link generateReferralCode}).
 */
export const REFERRAL_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

/** Length of a generated referral code. */
export const REFERRAL_CODE_LENGTH = 8;

/**
 * Generate one random referral code from the unambiguous alphabet using a
 * cryptographically-strong source (`crypto.randomInt`), so codes aren't
 * guessable from a sequence. Uniqueness is enforced at the DB layer; callers
 * retry on the (vanishingly rare) unique-constraint collision.
 */
export function generateReferralCode(): string {
  let out = "";
  for (let i = 0; i < REFERRAL_CODE_LENGTH; i++) {
    out += REFERRAL_ALPHABET[randomInt(REFERRAL_ALPHABET.length)];
  }
  return out;
}

/** Normalize a user-entered code: trim + upper-case (the alphabet is upper-only). */
export function normalizeReferralCode(raw: string): string {
  return raw.trim().toUpperCase();
}
