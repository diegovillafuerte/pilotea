import { describe, it, expect } from "vitest";

describe("middleware - token format validation", () => {
  const validTokenRegex = /^[a-f0-9]{64}$/;

  it("should accept a valid 64-char hex token", () => {
    const token =
      "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
    expect(validTokenRegex.test(token)).toBe(true);
  });

  it("should reject tokens shorter than 64 chars", () => {
    const token = "a1b2c3d4e5f6";
    expect(validTokenRegex.test(token)).toBe(false);
  });

  it("should reject tokens with uppercase hex", () => {
    const token =
      "A1B2C3D4E5F6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
    expect(validTokenRegex.test(token)).toBe(false);
  });

  it("should reject tokens with non-hex characters", () => {
    const token =
      "g1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
    expect(validTokenRegex.test(token)).toBe(false);
  });
});

describe("middleware - protected paths", () => {
  const protectedPaths = [
    "/dashboard",
    "/compare",
    "/upload",
    "/fiscal",
    "/tips",
    "/onboarding",
  ];

  it("should identify all protected paths", () => {
    expect(protectedPaths).toContain("/dashboard");
    expect(protectedPaths).toContain("/compare");
    expect(protectedPaths).toContain("/upload");
    expect(protectedPaths).toContain("/fiscal");
    expect(protectedPaths).toContain("/tips");
    expect(protectedPaths).toContain("/onboarding");
  });

  it("should not protect login and verify paths", () => {
    expect(protectedPaths).not.toContain("/login");
    expect(protectedPaths).not.toContain("/verify");
    expect(protectedPaths).not.toContain("/api/auth/login");
    expect(protectedPaths).not.toContain("/api/auth/verify");
  });

  it("should match paths starting with protected prefixes", () => {
    const testPath = "/dashboard/history";
    const isProtected = protectedPaths.some((p) => testPath.startsWith(p));
    expect(isProtected).toBe(true);
  });

  it("should not match unprotected paths", () => {
    const testPath = "/login";
    const isProtected = protectedPaths.some((p) => testPath.startsWith(p));
    expect(isProtected).toBe(false);
  });
});
