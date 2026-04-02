import { describe, it, expect, vi, beforeEach } from "vitest";
import { computePercentile } from "@/lib/percentiles/engine";

// ─── computePercentile (pure function, no DB needed) ──────────

describe("computePercentile", () => {
  // Standard breakpoints for testing
  const p10 = 30;
  const p25 = 40;
  const p50 = 50;
  const p75 = 65;
  const p90 = 80;

  it("returns 1 for very low values (below p10)", () => {
    const result = computePercentile(5, p10, p25, p50, p75, p90);
    expect(result).toBeGreaterThanOrEqual(1);
    expect(result).toBeLessThanOrEqual(10);
  });

  it("returns ~10 for value at p10", () => {
    const result = computePercentile(p10, p10, p25, p50, p75, p90);
    expect(result).toBe(10);
  });

  it("returns ~25 for value at p25", () => {
    const result = computePercentile(p25, p10, p25, p50, p75, p90);
    expect(result).toBe(25);
  });

  it("returns ~50 for value at p50", () => {
    const result = computePercentile(p50, p10, p25, p50, p75, p90);
    expect(result).toBe(50);
  });

  it("returns ~75 for value at p75", () => {
    const result = computePercentile(p75, p10, p25, p50, p75, p90);
    expect(result).toBe(75);
  });

  it("returns ~90 for value at p90", () => {
    const result = computePercentile(p90, p10, p25, p50, p75, p90);
    expect(result).toBe(90);
  });

  it("returns value between 10-25 for value between p10-p25", () => {
    const result = computePercentile(35, p10, p25, p50, p75, p90);
    expect(result).toBeGreaterThan(10);
    expect(result).toBeLessThan(25);
  });

  it("returns value between 25-50 for value between p25-p50", () => {
    const result = computePercentile(45, p10, p25, p50, p75, p90);
    expect(result).toBeGreaterThan(25);
    expect(result).toBeLessThan(50);
  });

  it("returns value between 50-75 for value between p50-p75", () => {
    const result = computePercentile(57, p10, p25, p50, p75, p90);
    expect(result).toBeGreaterThan(50);
    expect(result).toBeLessThan(75);
  });

  it("returns value between 75-90 for value between p75-p90", () => {
    const result = computePercentile(72, p10, p25, p50, p75, p90);
    expect(result).toBeGreaterThan(75);
    expect(result).toBeLessThan(90);
  });

  it("returns value above 90 for value above p90", () => {
    const result = computePercentile(100, p10, p25, p50, p75, p90);
    expect(result).toBeGreaterThan(90);
    expect(result).toBeLessThanOrEqual(99);
  });

  it("clamps at 99 maximum", () => {
    const result = computePercentile(10000, p10, p25, p50, p75, p90);
    expect(result).toBe(99);
  });

  it("clamps at 1 minimum", () => {
    const result = computePercentile(0, p10, p25, p50, p75, p90);
    expect(result).toBeGreaterThanOrEqual(1);
  });

  it("handles zero breakpoints without NaN", () => {
    const result = computePercentile(5, 0, 0, 10, 20, 30);
    expect(result).toBeGreaterThanOrEqual(1);
    expect(result).toBeLessThanOrEqual(99);
    expect(Number.isNaN(result)).toBe(false);
  });

  it("handles equal breakpoints without NaN", () => {
    const result = computePercentile(50, 50, 50, 50, 50, 50);
    expect(result).toBeGreaterThanOrEqual(1);
    expect(result).toBeLessThanOrEqual(99);
    expect(Number.isNaN(result)).toBe(false);
  });

  it("produces monotonically increasing percentiles for increasing values", () => {
    const values = [20, 30, 35, 40, 45, 50, 57, 65, 72, 80, 95];
    const percentiles = values.map((v) =>
      computePercentile(v, p10, p25, p50, p75, p90),
    );

    for (let i = 1; i < percentiles.length; i++) {
      expect(percentiles[i]).toBeGreaterThanOrEqual(percentiles[i - 1]);
    }
  });

  it("returns integer values", () => {
    const testValues = [15, 35, 45, 57, 72, 95];
    for (const v of testValues) {
      const result = computePercentile(v, p10, p25, p50, p75, p90);
      expect(Number.isInteger(result)).toBe(true);
    }
  });
});

// ─── calculatePercentiles (requires DB mocking) ──────────────

// Mock the DB module
vi.mock("@/lib/db", () => ({
  db: {
    select: () => ({
      from: () => ({
        where: () => ({
          limit: () => Promise.resolve([]),
        }),
      }),
    }),
  },
}));

vi.mock("@/lib/db/schema", () => ({
  populationStats: {
    city: "city",
    platform: "platform",
    metricName: "metric_name",
    period: "period",
  },
}));

vi.mock("drizzle-orm", () => ({
  and: (...args: unknown[]) => args,
  eq: (a: unknown, b: unknown) => [a, b],
}));

describe("calculatePercentiles", () => {
  beforeEach(() => {
    vi.resetModules();
  });

  it("skips null metrics", async () => {
    // We need to re-import after mocking
    const { calculatePercentiles } = await import("@/lib/percentiles/engine");

    const results = await calculatePercentiles("cdmx", "uber", {
      earnings_per_trip: null,
      earnings_per_km: undefined,
      earnings_per_hour: null,
      trips_per_hour: null,
      platform_commission_pct: null,
    });

    expect(results).toEqual([]);
  });

  it("returns empty array when no stats are available", async () => {
    const { calculatePercentiles } = await import("@/lib/percentiles/engine");

    const results = await calculatePercentiles("unknown_city", "uber", {
      earnings_per_trip: 50,
    });

    // With our mock returning empty arrays, no stats will be found
    expect(results).toEqual([]);
  });
});

// ─── PERCENTILE_METRICS coverage ──────────────────────────────

describe("PERCENTILE_METRICS", () => {
  it("contains exactly 5 metrics", async () => {
    const { PERCENTILE_METRICS } = await import("@/lib/percentiles/engine");
    expect(PERCENTILE_METRICS).toHaveLength(5);
  });

  it("includes all expected metrics", async () => {
    const { PERCENTILE_METRICS } = await import("@/lib/percentiles/engine");
    expect(PERCENTILE_METRICS).toContain("earnings_per_trip");
    expect(PERCENTILE_METRICS).toContain("earnings_per_km");
    expect(PERCENTILE_METRICS).toContain("earnings_per_hour");
    expect(PERCENTILE_METRICS).toContain("trips_per_hour");
    expect(PERCENTILE_METRICS).toContain("platform_commission_pct");
  });
});
