import { db } from "@/lib/db";
import { populationStats } from "@/lib/db/schema";
import { and, eq } from "drizzle-orm";

// ─── Types ────────────────────────────────────────────────────
export interface PercentileResult {
  metric: string;
  value: number;
  percentile: number; // 1–99
  city_sample_size: number;
  is_national_fallback: boolean;
}

/**
 * Metrics for which percentiles are calculated.
 * These correspond to columns in weekly_data and metric_name values in population_stats.
 */
export const PERCENTILE_METRICS = [
  "earnings_per_trip",
  "earnings_per_km",
  "earnings_per_hour",
  "trips_per_hour",
  "platform_commission_pct",
] as const;

export type PercentileMetric = (typeof PERCENTILE_METRICS)[number];

/** Metrics where lower is better for the driver (inverted for display). */
const INVERTED_METRICS: ReadonlySet<string> = new Set([
  "platform_commission_pct",
]);

/** Minimum sample size before falling back to national data. */
const MIN_SAMPLE_SIZE = 20;

// ─── Core calculation ─────────────────────────────────────────

/**
 * Calculate percentile (1-99) for a given value using linear interpolation
 * against pre-computed population stats breakpoints.
 *
 * This is a pure TypeScript implementation of the same logic as the
 * get_percentile SQL function, enabling unit testing without a database.
 */
export function computePercentile(
  value: number,
  p10: number,
  p25: number,
  p50: number,
  p75: number,
  p90: number,
): number {
  let raw: number;

  if (value <= p10) {
    raw = p10 === 0 ? 0 : (value / p10) * 10;
  } else if (value <= p25) {
    const range = p25 - p10;
    raw = range === 0 ? 10 : 10 + ((value - p10) / range) * 15;
  } else if (value <= p50) {
    const range = p50 - p25;
    raw = range === 0 ? 25 : 25 + ((value - p25) / range) * 25;
  } else if (value <= p75) {
    const range = p75 - p50;
    raw = range === 0 ? 50 : 50 + ((value - p50) / range) * 25;
  } else if (value <= p90) {
    const range = p90 - p75;
    raw = range === 0 ? 75 : 75 + ((value - p75) / range) * 15;
  } else {
    const tail = p90 * 0.5;
    const extra = tail === 0 ? 0 : ((value - p90) / tail) * 10;
    raw = 90 + Math.min(9, extra);
  }

  return Math.max(1, Math.min(99, Math.round(raw)));
}

/**
 * Fetch population stats for a metric, falling back to national if city
 * sample is too small.
 */
async function getStats(city: string, platform: string, metric: string) {
  // Try city-specific first
  const [cityRow] = await db
    .select()
    .from(populationStats)
    .where(
      and(
        eq(populationStats.city, city),
        eq(populationStats.platform, platform),
        eq(populationStats.metricName, metric),
        eq(populationStats.period, "current"),
      ),
    )
    .limit(1);

  if (cityRow && cityRow.sampleSize >= MIN_SAMPLE_SIZE) {
    return { stats: cityRow, isNationalFallback: false };
  }

  // Fallback to national
  const [nationalRow] = await db
    .select()
    .from(populationStats)
    .where(
      and(
        eq(populationStats.city, "national"),
        eq(populationStats.platform, platform),
        eq(populationStats.metricName, metric),
        eq(populationStats.period, "current"),
      ),
    )
    .limit(1);

  if (nationalRow) {
    return { stats: nationalRow, isNationalFallback: true };
  }

  return null;
}

/**
 * Calculate percentiles for a driver's metrics against population data.
 *
 * For each non-null metric, looks up the population breakpoints for the
 * driver's city/platform and computes a percentile (1-99).
 *
 * Commission percentage is inverted: lower commission = higher percentile
 * for display (lower commission is better for the driver).
 */
export async function calculatePercentiles(
  city: string,
  platform: string,
  metrics: Record<string, number | null | undefined>,
): Promise<PercentileResult[]> {
  const results: PercentileResult[] = [];

  for (const metric of PERCENTILE_METRICS) {
    const value = metrics[metric];
    if (value == null) continue;

    const result = await getStats(city, platform, metric);
    if (!result) continue;

    const { stats, isNationalFallback } = result;

    const p10 = Number(stats.p10);
    const p25 = Number(stats.p25);
    const p50 = Number(stats.p50);
    const p75 = Number(stats.p75);
    const p90 = Number(stats.p90);

    let percentile = computePercentile(value, p10, p25, p50, p75, p90);

    // Invert commission percentile: lower commission = better for driver
    if (INVERTED_METRICS.has(metric)) {
      percentile = 100 - percentile;
      // Re-clamp after inversion
      percentile = Math.max(1, Math.min(99, percentile));
    }

    results.push({
      metric,
      value,
      percentile,
      city_sample_size: stats.sampleSize,
      is_national_fallback: isNationalFallback,
    });
  }

  return results;
}
