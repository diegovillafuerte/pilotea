import { describe, it, expect, vi, beforeEach } from "vitest";
import { createHash } from "crypto";

// Mock the database module with chained query builders
const mockSelectResult: unknown[] = [];

const mockWhereLimit = {
  limit: vi.fn().mockImplementation(() => Promise.resolve(mockSelectResult)),
};

const mockWhere = {
  where: vi.fn().mockReturnValue(mockWhereLimit),
};

const mockFrom = {
  from: vi.fn().mockReturnValue(mockWhere),
};

const mockSelectFn = vi.fn().mockReturnValue(mockFrom);

const mockInsertValues = {
  values: vi.fn().mockImplementation(() => Promise.resolve()),
};

const mockInsertFn = vi.fn().mockReturnValue(mockInsertValues);

const mockUpdateReturning = {
  returning: vi
    .fn()
    .mockImplementation(() => Promise.resolve(mockSelectResult)),
};

const mockUpdateWhere = {
  where: vi.fn().mockReturnValue(mockUpdateReturning),
};

const mockUpdateSet = {
  set: vi.fn().mockReturnValue(mockUpdateWhere),
};

const mockUpdateFn = vi.fn().mockReturnValue(mockUpdateSet);

vi.mock("@/lib/db", () => ({
  db: {
    select: mockSelectFn,
    insert: mockInsertFn,
    update: mockUpdateFn,
  },
}));

describe("magic-link", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSelectResult.length = 0;
  });

  describe("isRateLimited", () => {
    it("should return false when no recent links exist", async () => {
      const { isRateLimited } = await import("@/lib/auth/magic-link");
      // mockSelectResult is empty = no recent links
      // isRateLimited doesn't use .limit, it counts all results
      mockWhere.where.mockReturnValueOnce(Promise.resolve(mockSelectResult));
      const result = await isRateLimited("+5215512345678");
      expect(result).toBe(false);
    });

    it("should return true when 3 or more recent links exist", async () => {
      const { isRateLimited } = await import("@/lib/auth/magic-link");
      mockSelectResult.push({ id: "1" }, { id: "2" }, { id: "3" });
      mockWhere.where.mockReturnValueOnce(Promise.resolve(mockSelectResult));
      const result = await isRateLimited("+5215512345678");
      expect(result).toBe(true);
    });
  });

  describe("createMagicLink", () => {
    it("should return a 64-character hex token", async () => {
      const { createMagicLink } = await import("@/lib/auth/magic-link");
      const token = await createMagicLink("+5215512345678");
      expect(token).toMatch(/^[a-f0-9]{64}$/);
    });

    it("should store a hashed token in the database, not the raw token", async () => {
      const { createMagicLink } = await import("@/lib/auth/magic-link");
      const rawToken = await createMagicLink("+5215512345678");

      expect(mockInsertFn).toHaveBeenCalled();
      const storedValues = mockInsertValues.values.mock.calls[0][0];

      // The stored token should be the SHA-256 hash, not the raw token
      const expectedHash = createHash("sha256")
        .update(rawToken)
        .digest("hex");
      expect(storedValues.token).toBe(expectedHash);
      expect(storedValues.token).not.toBe(rawToken);
      expect(storedValues.phone).toBe("+5215512345678");
    });
  });

  describe("validateMagicLink", () => {
    it("should return null when no matching token found (atomic update returns empty)", async () => {
      const { validateMagicLink } = await import("@/lib/auth/magic-link");
      // mockSelectResult is empty = no match from UPDATE...RETURNING
      const result = await validateMagicLink("nonexistent");
      expect(result).toBeNull();
    });

    it("should return phone when atomic update consumes the token", async () => {
      const { validateMagicLink } = await import("@/lib/auth/magic-link");
      mockSelectResult.push({ phone: "+5215512345678" });

      const result = await validateMagicLink("validtoken");
      expect(result).toBe("+5215512345678");
      expect(mockUpdateFn).toHaveBeenCalled();
    });
  });
});
