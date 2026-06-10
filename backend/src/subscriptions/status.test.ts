/**
 * Unit tests for tier derivation (B-049 + B-056 grant-based premium). Focuses on
 * {@link effectiveTier}: a grant unlocks premium without a sub, an entitled sub unlocks it without a
 * grant, an expired/absent grant + non-entitled sub is free.
 */

import { describe, it, expect } from "vitest";
import { effectiveTier, isEntitled, tierForStatus } from "./status.js";

const now = new Date("2026-06-10T00:00:00Z");
const future = new Date(now.getTime() + 86_400_000);
const past = new Date(now.getTime() - 86_400_000);

describe("isEntitled / tierForStatus", () => {
  it("active, canceled, grace are entitled; hold, expired are not", () => {
    expect(isEntitled("active")).toBe(true);
    expect(isEntitled("canceled")).toBe(true);
    expect(isEntitled("grace")).toBe(true);
    expect(isEntitled("hold")).toBe(false);
    expect(isEntitled("expired")).toBe(false);
    expect(tierForStatus("active")).toBe("premium");
    expect(tierForStatus("hold")).toBe("free");
  });
});

describe("effectiveTier", () => {
  it("a live grant alone is premium (no subscription)", () => {
    expect(effectiveTier(null, future, now)).toBe("premium");
  });

  it("an entitled subscription alone is premium (no grant)", () => {
    expect(effectiveTier("active", null, now)).toBe("premium");
  });

  it("a non-entitled subscription plus a live grant is still premium", () => {
    expect(effectiveTier("expired", future, now)).toBe("premium");
    expect(effectiveTier("hold", future, now)).toBe("premium");
  });

  it("an expired grant with a non-entitled subscription is free", () => {
    expect(effectiveTier("expired", past, now)).toBe("free");
    expect(effectiveTier(null, past, now)).toBe("free");
  });

  it("no subscription and no grant is free", () => {
    expect(effectiveTier(null, null, now)).toBe("free");
  });

  it("the grant boundary is strict (premiumUntil must be in the future)", () => {
    expect(effectiveTier(null, now, now)).toBe("free");
  });
});
