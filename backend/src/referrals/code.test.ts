/**
 * Unit tests for referral-code generation (B-056): length, unambiguous alphabet, uniqueness at
 * scale, and the user-input normalizer.
 */

import { describe, it, expect } from "vitest";
import {
  generateReferralCode,
  normalizeReferralCode,
  REFERRAL_ALPHABET,
  REFERRAL_CODE_LENGTH,
} from "./code.js";

describe("generateReferralCode", () => {
  it("produces an 8-char code from the unambiguous alphabet", () => {
    const code = generateReferralCode();
    expect(code).toHaveLength(REFERRAL_CODE_LENGTH);
    for (const ch of code) expect(REFERRAL_ALPHABET).toContain(ch);
  });

  it("excludes the easily-confused glyphs 0 O 1 I L", () => {
    for (const ch of "01OIL") expect(REFERRAL_ALPHABET).not.toContain(ch);
  });

  it("is effectively unique across 10k draws", () => {
    const seen = new Set<string>();
    for (let i = 0; i < 10_000; i++) seen.add(generateReferralCode());
    // Collisions in 31^8 space over 10k draws should be ~0; allow a tiny margin.
    expect(seen.size).toBeGreaterThan(9_995);
  });
});

describe("normalizeReferralCode", () => {
  it("trims and upper-cases user input", () => {
    expect(normalizeReferralCode("  abcdef23 ")).toBe("ABCDEF23");
  });
});
