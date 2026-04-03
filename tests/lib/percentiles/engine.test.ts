import { describe, it, expect, vi, beforeEach } from "vitest";
import type { ParsedMetrics, PercentileResult } from "@/lib/percentiles/engine";

// Mock getDirectClient before importing the module
const mockSql = vi.fn();
vi.mock("@/lib/db", () => ({
  getDirectClient: () => mockSql,
}));

// Import after mocking
const { calculatePercentiles, METRIC_KEYS } = await import(
  "@/lib/percentiles/engine"
);

describe("calculatePercentiles", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns empty array when all metrics are null", async () => {
    const metrics: ParsedMetrics = {
      earnings_per_trip: null,
      earnings_per_km: null,
      earnings_per_hour: null,
      trips_per_hour: null,
      platform_commission_pct: null,
    };

    const results = await calculatePercentiles("cdmx", "uber", metrics);
    expect(results).toEqual([]);
    expect(mockSql).not.toHaveBeenCalled();
  });

  it("returns empty array when metrics object is empty", async () => {
    const results = await calculatePercentiles("cdmx", "uber", {});
    expect(results).toEqual([]);
  });

  it("skips null/undefined metrics and processes non-null ones", async () => {
    mockSql.mockResolvedValueOnce([
      { percentile: 72, sample_size: 1500, matched_city: "cdmx" },
    ]);

    const metrics: ParsedMetrics = {
      earnings_per_trip: 48.85,
      earnings_per_km: null,
      earnings_per_hour: undefined as unknown as null,
    };

    const results = await calculatePercentiles("cdmx", "uber", metrics);

    expect(results).toHaveLength(1);
    expect(results[0].metric).toBe("earnings_per_trip");
    expect(results[0].value).toBe(48.85);
    expect(results[0].percentile).toBe(72);
    expect(results[0].display_percentile).toBe(72);
    expect(results[0].sample_size).toBe(1500);
    expect(results[0].is_national_fallback).toBe(false);
  });

  it("inverts commission percentile for display", async () => {
    // First 4 metrics return null (not in our input)
    // Only platform_commission_pct is provided
    mockSql.mockResolvedValueOnce([
      { percentile: 30, sample_size: 1200, matched_city: "cdmx" },
    ]);

    const metrics: ParsedMetrics = {
      platform_commission_pct: 22.5,
    };

    const results = await calculatePercentiles("cdmx", "uber", metrics);

    expect(results).toHaveLength(1);
    expect(results[0].metric).toBe("platform_commission_pct");
    expect(results[0].percentile).toBe(30); // raw
    expect(results[0].display_percentile).toBe(70); // 100 - 30 = 70 (lower commission is better)
  });

  it("detects national fallback from matched_city", async () => {
    mockSql.mockResolvedValueOnce([
      { percentile: 55, sample_size: 5000, matched_city: "national" },
    ]);

    const metrics: ParsedMetrics = {
      earnings_per_trip: 40.0,
    };

    const results = await calculatePercentiles("leon", "didi", metrics);

    expect(results).toHaveLength(1);
    expect(results[0].is_national_fallback).toBe(true);
    expect(results[0].sample_size).toBe(5000);
  });

  it("handles SQL returning null percentile (no data)", async () => {
    mockSql.mockResolvedValueOnce([
      { percentile: null, sample_size: null, matched_city: null },
    ]);

    const metrics: ParsedMetrics = {
      earnings_per_trip: 40.0,
    };

    const results = await calculatePercentiles("unknown_city", "uber", metrics);
    expect(results).toEqual([]);
  });

  it("handles empty SQL result", async () => {
    mockSql.mockResolvedValueOnce([]);

    const metrics: ParsedMetrics = {
      earnings_per_trip: 40.0,
    };

    const results = await calculatePercentiles("cdmx", "uber", metrics);
    expect(results).toEqual([]);
  });

  it("processes all 5 metrics when all are provided", async () => {
    // Mock 5 sequential SQL calls
    for (let i = 0; i < 5; i++) {
      mockSql.mockResolvedValueOnce([
        {
          percentile: 50 + i * 5,
          sample_size: 1000,
          matched_city: "cdmx",
        },
      ]);
    }

    const metrics: ParsedMetrics = {
      earnings_per_trip: 45.0,
      earnings_per_km: 7.0,
      earnings_per_hour: 140.0,
      trips_per_hour: 3.2,
      platform_commission_pct: 25.0,
    };

    const results = await calculatePercentiles("cdmx", "uber", metrics);

    expect(results).toHaveLength(5);
    expect(mockSql).toHaveBeenCalledTimes(5);

    // Verify order matches METRIC_KEYS
    expect(results.map((r) => r.metric)).toEqual([...METRIC_KEYS]);

    // Verify commission is inverted
    const commission = results.find(
      (r) => r.metric === "platform_commission_pct",
    )!;
    expect(commission.display_percentile).toBe(100 - commission.percentile);

    // Verify non-commission metrics are NOT inverted
    const earnings = results.find(
      (r) => r.metric === "earnings_per_trip",
    )!;
    expect(earnings.display_percentile).toBe(earnings.percentile);
  });
});

describe("METRIC_KEYS", () => {
  it("contains exactly 5 metrics", () => {
    expect(METRIC_KEYS).toHaveLength(5);
  });

  it("includes the expected metrics", () => {
    expect(METRIC_KEYS).toContain("earnings_per_trip");
    expect(METRIC_KEYS).toContain("earnings_per_km");
    expect(METRIC_KEYS).toContain("earnings_per_hour");
    expect(METRIC_KEYS).toContain("trips_per_hour");
    expect(METRIC_KEYS).toContain("platform_commission_pct");
  });
});
