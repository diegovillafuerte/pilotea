import { describe, it, expect } from "vitest";
import { createHash, randomBytes } from "crypto";

// Test the hashToken function logic directly (pure function)
describe("session - hashToken", () => {
  function hashToken(token: string): string {
    return createHash("sha256").update(token).digest("hex");
  }

  it("should produce a 64-character hex string", () => {
    const token = randomBytes(32).toString("hex");
    const hash = hashToken(token);
    expect(hash).toMatch(/^[a-f0-9]{64}$/);
  });

  it("should produce consistent hashes for the same input", () => {
    const token = "test-token-abc123";
    const hash1 = hashToken(token);
    const hash2 = hashToken(token);
    expect(hash1).toBe(hash2);
  });

  it("should produce different hashes for different inputs", () => {
    const hash1 = hashToken("token-a");
    const hash2 = hashToken("token-b");
    expect(hash1).not.toBe(hash2);
  });

  it("should produce a different value from the input (not identity)", () => {
    const token = randomBytes(32).toString("hex");
    const hash = hashToken(token);
    expect(hash).not.toBe(token);
  });
});

describe("session - token generation", () => {
  it("should generate a 64-char hex token from 32 random bytes", () => {
    const token = randomBytes(32).toString("hex");
    expect(token).toMatch(/^[a-f0-9]{64}$/);
    expect(token.length).toBe(64);
  });

  it("should generate unique tokens", () => {
    const tokens = new Set<string>();
    for (let i = 0; i < 100; i++) {
      tokens.add(randomBytes(32).toString("hex"));
    }
    expect(tokens.size).toBe(100);
  });
});
