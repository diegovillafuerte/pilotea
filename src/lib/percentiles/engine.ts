import { getDirectClient } from "../db";

// ─── Types ────────────────────────────────────────────────────

export interface PercentileResult {
  metric: string;
  value: number;
  percentile: number; // 1-99 (raw from SQL function)
  display_percentile: number; // 1-99 (inverted for commission)
  sample_size: number;
  is_national_fallback: boolean;
}

/**
 * Parsed metrics from a weekly data upload.
 * Keys match the metric_name values in population_stats.
 */
export type ParsedMetrics = Partial<
  Record<(typeof METRIC_KEYS)[number], number | null>
>;

// ─── Constants ────────────────────────────────────────────────

/** The 5 efficiency metrics we compute percentiles for */
export const METRIC_KEYS = [
  "earnings_per_trip",
  "earnings_per_km",
  "earnings_per_hour",
  "trips_per_hour",
  "platform_commission_pct",
] as const;

/**
 * Metrics where lower values are better for the driver.
 * The display_percentile is inverted: 100 - raw_percentile.
 */
const INVERTED_METRICS = new Set<string>(["platform_commission_pct"]);

/** Minimum sample size to use city-level data (below this, fall back to national) */
const MIN_SAMPLE_SIZE = 20;

// ─── Engine ───────────────────────────────────────────────────

/**
 * Calculate percentiles for a driver's metrics against the population.
 *
 * For each non-null metric, calls the `get_percentile` SQL function
 * which does linear interpolation against population_stats breakpoints.
 * Falls back to national data when city sample_size < 20.
 *
 * Commission percentage is inverted for display (lower = better).
 */
export async function calculatePercentiles(
  city: string,
  platform: string,
  metrics: ParsedMetrics,
): Promise<PercentileResult[]> {
  const sql = getDirectClient();
  const results: PercentileResult[] = [];

  for (const key of METRIC_KEYS) {
    const value = metrics[key];
    if (value == null) continue;

    // Call the SQL function and retrieve metadata about which row was used
    const rows = await sql`
      WITH city_stats AS (
        SELECT sample_size, city as matched_city
        FROM population_stats
        WHERE city = ${city}
          AND platform = ${platform}
          AND metric_name = ${key}
          AND period = 'current'
      ),
      nat_stats AS (
        SELECT sample_size, 'national' as matched_city
        FROM population_stats
        WHERE city = 'national'
          AND platform = ${platform}
          AND metric_name = ${key}
          AND period = 'current'
      ),
      chosen AS (
        SELECT
          CASE
            WHEN cs.sample_size IS NOT NULL AND cs.sample_size >= ${MIN_SAMPLE_SIZE}
            THEN cs.sample_size
            ELSE ns.sample_size
          END as sample_size,
          CASE
            WHEN cs.sample_size IS NOT NULL AND cs.sample_size >= ${MIN_SAMPLE_SIZE}
            THEN cs.matched_city
            ELSE COALESCE(ns.matched_city, 'national')
          END as matched_city
        FROM (SELECT 1) dummy
        LEFT JOIN city_stats cs ON true
        LEFT JOIN nat_stats ns ON true
      )
      SELECT
        get_percentile(${city}, ${platform}, ${key}, ${value}::decimal) as percentile,
        c.sample_size,
        c.matched_city
      FROM chosen c
    `;

    const row = rows[0];
    if (row?.percentile == null) continue;

    const rawPercentile = Number(row.percentile);
    const isInverted = INVERTED_METRICS.has(key);

    results.push({
      metric: key,
      value,
      percentile: rawPercentile,
      display_percentile: isInverted ? 100 - rawPercentile : rawPercentile,
      sample_size: Number(row.sample_size) || 0,
      is_national_fallback: row.matched_city === "national",
    });
  }

  return results;
}
