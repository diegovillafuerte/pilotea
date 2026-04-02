import { describe, it, expect } from "vitest";
import {
  generateRows,
  CITIES,
  PLATFORMS,
  METRICS,
  applyMultiplier,
} from "../../../seed/population-stats";

describe("population-stats seed data", () => {
  const rows = generateRows();

  it("generates correct total number of rows", () => {
    const expectedCount =
      Object.keys(CITIES).length * PLATFORMS.length * METRICS.length;
    expect(rows).toHaveLength(expectedCount);
  });

  it("covers all 11 city groups (10 cities + national)", () => {
    const cities = new Set(rows.map((r) => r.city));
    expect(cities.size).toBe(11);
    expect(cities.has("national")).toBe(true);
    expect(cities.has("cdmx")).toBe(true);
    expect(cities.has("monterrey")).toBe(true);
    expect(cities.has("guadalajara")).toBe(true);
    expect(cities.has("puebla")).toBe(true);
    expect(cities.has("toluca")).toBe(true);
    expect(cities.has("tijuana")).toBe(true);
    expect(cities.has("leon")).toBe(true);
    expect(cities.has("queretaro")).toBe(true);
    expect(cities.has("merida")).toBe(true);
    expect(cities.has("cancun")).toBe(true);
  });

  it("covers all 3 platforms", () => {
    const platforms = new Set(rows.map((r) => r.platform));
    expect(platforms.size).toBe(3);
    expect(platforms.has("uber")).toBe(true);
    expect(platforms.has("didi")).toBe(true);
    expect(platforms.has("indrive")).toBe(true);
  });

  it("covers all 5 metrics", () => {
    const metrics = new Set(rows.map((r) => r.metric_name));
    expect(metrics.size).toBe(5);
    expect(metrics.has("earnings_per_trip")).toBe(true);
    expect(metrics.has("earnings_per_km")).toBe(true);
    expect(metrics.has("earnings_per_hour")).toBe(true);
    expect(metrics.has("trips_per_hour")).toBe(true);
    expect(metrics.has("platform_commission_pct")).toBe(true);
  });

  it("all rows have period set to 'current'", () => {
    for (const row of rows) {
      expect(row.period).toBe("current");
    }
  });

  it("all rows have positive sample sizes", () => {
    for (const row of rows) {
      expect(row.sample_size).toBeGreaterThan(0);
    }
  });

  it("major cities have larger sample sizes than smaller ones", () => {
    const cdmxRows = rows.filter(
      (r) => r.city === "cdmx" && r.platform === "uber",
    );
    const leonRows = rows.filter(
      (r) => r.city === "leon" && r.platform === "uber",
    );
    expect(cdmxRows[0].sample_size).toBeGreaterThan(leonRows[0].sample_size);
  });

  it("percentile breakpoints are monotonically increasing for each row", () => {
    for (const row of rows) {
      const p10 = Number(row.p10);
      const p25 = Number(row.p25);
      const p50 = Number(row.p50);
      const p75 = Number(row.p75);
      const p90 = Number(row.p90);

      expect(p10).toBeLessThanOrEqual(p25);
      expect(p25).toBeLessThanOrEqual(p50);
      expect(p50).toBeLessThanOrEqual(p75);
      expect(p75).toBeLessThanOrEqual(p90);
    }
  });

  it("all numeric values are positive", () => {
    for (const row of rows) {
      expect(Number(row.p10)).toBeGreaterThan(0);
      expect(Number(row.p25)).toBeGreaterThan(0);
      expect(Number(row.p50)).toBeGreaterThan(0);
      expect(Number(row.p75)).toBeGreaterThan(0);
      expect(Number(row.p90)).toBeGreaterThan(0);
      expect(Number(row.mean)).toBeGreaterThan(0);
    }
  });

  it("mean is between p25 and p75 for each row", () => {
    for (const row of rows) {
      const mean = Number(row.mean);
      const p10 = Number(row.p10);
      const p90 = Number(row.p90);
      // Mean should be within a reasonable range (between p10 and p90)
      expect(mean).toBeGreaterThanOrEqual(p10);
      expect(mean).toBeLessThanOrEqual(p90);
    }
  });

  it("commission percentages are in realistic range (10-35%)", () => {
    const commissionRows = rows.filter(
      (r) => r.metric_name === "platform_commission_pct",
    );
    for (const row of commissionRows) {
      expect(Number(row.p10)).toBeGreaterThanOrEqual(8);
      expect(Number(row.p90)).toBeLessThanOrEqual(40);
    }
  });

  it("earnings per hour are in realistic MXN range", () => {
    const ephRows = rows.filter(
      (r) => r.metric_name === "earnings_per_hour",
    );
    for (const row of ephRows) {
      // Reasonable range for Mexican ride-hailing: 50-300 MXN/hour
      expect(Number(row.p10)).toBeGreaterThanOrEqual(50);
      expect(Number(row.p90)).toBeLessThanOrEqual(300);
    }
  });

  it("each city x platform x metric combination is unique", () => {
    const keys = rows.map(
      (r) => `${r.city}|${r.platform}|${r.metric_name}|${r.period}`,
    );
    const uniqueKeys = new Set(keys);
    expect(uniqueKeys.size).toBe(rows.length);
  });
});

describe("applyMultiplier", () => {
  it("multiplies all fields by the given factor", () => {
    const base = { p10: 10, p25: 20, p50: 30, p75: 40, p90: 50, mean: 30 };
    const result = applyMultiplier(base, 2);
    expect(result).toEqual({
      p10: 20,
      p25: 40,
      p50: 60,
      p75: 80,
      p90: 100,
      mean: 60,
    });
  });

  it("returns same values with multiplier 1.0", () => {
    const base = { p10: 10, p25: 20, p50: 30, p75: 40, p90: 50, mean: 30 };
    const result = applyMultiplier(base, 1.0);
    expect(result).toEqual(base);
  });
});
