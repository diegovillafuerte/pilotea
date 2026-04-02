import { describe, it, expect } from "vitest";
import {
  CITIES,
  CITY_KEYS,
  PLATFORMS,
  PLATFORM_KEYS,
} from "@/lib/constants";

describe("constants - cities", () => {
  it("should have exactly 40 cities", () => {
    expect(CITIES).toHaveLength(40);
  });

  it("should have unique city keys", () => {
    const keys = CITIES.map((c) => c.key);
    expect(new Set(keys).size).toBe(keys.length);
  });

  it("should have non-empty display names for all cities", () => {
    for (const city of CITIES) {
      expect(city.displayName.length).toBeGreaterThan(0);
    }
  });

  it("should have CITY_KEYS set matching city array", () => {
    expect(CITY_KEYS.size).toBe(40);
    for (const city of CITIES) {
      expect(CITY_KEYS.has(city.key)).toBe(true);
    }
  });

  it("should include the 10 priority cities", () => {
    const priorityCities = [
      "cdmx",
      "monterrey",
      "guadalajara",
      "puebla",
      "toluca",
      "tijuana",
      "leon",
      "queretaro",
      "merida",
      "cancun",
    ];
    for (const key of priorityCities) {
      expect(CITY_KEYS.has(key)).toBe(true);
    }
  });
});

describe("constants - platforms", () => {
  it("should have exactly 3 platforms", () => {
    expect(PLATFORMS).toHaveLength(3);
  });

  it("should include Uber, DiDi, and InDrive", () => {
    expect(PLATFORM_KEYS.has("uber")).toBe(true);
    expect(PLATFORM_KEYS.has("didi")).toBe(true);
    expect(PLATFORM_KEYS.has("indrive")).toBe(true);
  });

  it("should have unique platform keys", () => {
    const keys = PLATFORMS.map((p) => p.key);
    expect(new Set(keys).size).toBe(keys.length);
  });

  it("should have PLATFORM_KEYS set matching platform array", () => {
    expect(PLATFORM_KEYS.size).toBe(3);
    for (const platform of PLATFORMS) {
      expect(PLATFORM_KEYS.has(platform.key)).toBe(true);
    }
  });
});
