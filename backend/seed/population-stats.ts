/**
 * Seed population_stats with synthetic data for 10 priority cities + national
 * fallback. Ported from the legacy web app (seed/population-stats.ts) and
 * adapted to the greenfield backend schema (adds the is_synthetic flag).
 *
 * Values are reasonable estimates for Mexican ride-hail drivers in MXN, based
 * on public reports, news articles, and driver community data.
 *
 * Idempotent: uses ON CONFLICT ... DO UPDATE to upsert.
 *
 * Usage: DATABASE_URL=postgres://... pnpm db:seed
 *
 * The generators below (sample sizes, city/platform modifiers, breakpoint
 * spreads) are an EXACT port of the web logic so the seeded distributions —
 * and therefore the percentile parity test — match the proven implementation.
 */

import postgres from "postgres";

// ─── Configuration ────────────────────────────────────────────

export const CITIES = [
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
  "national",
] as const;

// "all" is a synthetic COMBINED population across every platform, so a driver's
// blended (cross-app) value can be ranked against all drivers — not just one
// app's population. The real-data fold produces the same `all` bucket (see
// jobs/fold-population-stats.ts). It is a pseudo-platform: never a real wire
// platform, only a benchmark bucket the client queries with platform=all.
export const PLATFORMS = ["uber", "didi", "indrive", "all"] as const;

export const METRICS = [
  "earnings_per_trip",
  "earnings_per_km",
  "earnings_per_hour",
  "trips_per_hour",
  "platform_commission_pct",
  // Weekly net earnings total — the Comparar table's "Ganancia neta" row needs a
  // population to rank against (B-085). Synthetic until the fold accrues real data.
  "net_earnings",
] as const;

// ─── Types ────────────────────────────────────────────────────

export type MetricName = (typeof METRICS)[number];
export type CityKey = (typeof CITIES)[number];
export type PlatformKey = (typeof PLATFORMS)[number];

export interface PercentileBreakpoints {
  p10: number;
  p25: number;
  p50: number;
  p75: number;
  p90: number;
  mean: number;
}

// ─── Sample sizes by city tier ────────────────────────────────

export function sampleSize(city: CityKey, platform: PlatformKey): number {
  // Major cities have more data
  const cityTier: Record<CityKey, "major" | "medium" | "small" | "national"> = {
    cdmx: "major",
    monterrey: "major",
    guadalajara: "major",
    puebla: "medium",
    toluca: "medium",
    tijuana: "medium",
    leon: "small",
    queretaro: "small",
    merida: "small",
    cancun: "small",
    national: "national",
  };

  const baseSizes: Record<string, number> = {
    major: 1500,
    medium: 600,
    small: 200,
    national: 5000,
  };

  // Platform market share adjustments
  const platformMultiplier: Record<PlatformKey, number> = {
    uber: 1.0,
    didi: 0.7,
    indrive: 0.4,
    all: 2.1, // ~ the sum of the three platforms' shares (combined population)
  };

  const base = baseSizes[cityTier[city]]!;
  // Add some variation so not every city has the same number
  const variation = ((city.length * 37 + platform.length * 13) % 200) - 100;
  return Math.max(50, Math.round((base + variation) * platformMultiplier[platform]));
}

// ─── Synthetic data generators ────────────────────────────────

interface CityModifiers {
  earningsMultiplier: number; // Higher in expensive cities
  commissionBase: number; // Platform commission median %
}

const CITY_MODIFIERS: Record<CityKey, CityModifiers> = {
  cdmx: { earningsMultiplier: 1.15, commissionBase: 25 },
  monterrey: { earningsMultiplier: 1.1, commissionBase: 25 },
  guadalajara: { earningsMultiplier: 1.05, commissionBase: 25 },
  puebla: { earningsMultiplier: 0.9, commissionBase: 25 },
  toluca: { earningsMultiplier: 0.88, commissionBase: 25 },
  tijuana: { earningsMultiplier: 1.12, commissionBase: 24 },
  leon: { earningsMultiplier: 0.85, commissionBase: 25 },
  queretaro: { earningsMultiplier: 0.95, commissionBase: 25 },
  merida: { earningsMultiplier: 0.92, commissionBase: 25 },
  cancun: { earningsMultiplier: 1.2, commissionBase: 24 }, // Tourism premium
  national: { earningsMultiplier: 1.0, commissionBase: 25 },
};

const PLATFORM_MODIFIERS: Record<
  PlatformKey,
  { earningsMultiplier: number; commissionShift: number }
> = {
  uber: { earningsMultiplier: 1.0, commissionShift: 0 },
  didi: { earningsMultiplier: 0.92, commissionShift: -2 }, // DiDi slightly lower earnings, lower commission
  indrive: { earningsMultiplier: 0.85, commissionShift: -5 }, // InDrive lower earnings, much lower commission (driver sets price)
  // Combined population: share-weighted blend of the three (rates land near a
  // single platform; net total skews higher because multi-app drivers stack weeks).
  all: { earningsMultiplier: 0.95, commissionShift: -1.6 },
};

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

export function generateBreakpoints(
  median: number,
  spread: "tight" | "normal" | "wide",
): PercentileBreakpoints {
  // Spread controls how much variation around the median
  const spreadFactors = {
    tight: { p10: 0.7, p25: 0.85, p75: 1.18, p90: 1.35 },
    normal: { p10: 0.6, p25: 0.8, p75: 1.25, p90: 1.5 },
    wide: { p10: 0.5, p25: 0.72, p75: 1.35, p90: 1.7 },
  };

  const f = spreadFactors[spread];
  const p10 = round2(median * f.p10);
  const p25 = round2(median * f.p25);
  const p50 = round2(median);
  const p75 = round2(median * f.p75);
  const p90 = round2(median * f.p90);
  // Mean is slightly above median (right-skewed distribution typical of earnings)
  const mean = round2(median * 1.05);

  return { p10, p25, p50, p75, p90, mean };
}

export function generateMetrics(
  city: CityKey,
  platform: PlatformKey,
): Record<MetricName, PercentileBreakpoints> {
  const cm = CITY_MODIFIERS[city];
  const pm = PLATFORM_MODIFIERS[platform];
  const mult = cm.earningsMultiplier * pm.earningsMultiplier;

  return {
    // Earnings per trip: national median ~$45 MXN
    earnings_per_trip: generateBreakpoints(45 * mult, "normal"),

    // Earnings per km: national median ~$7 MXN
    earnings_per_km: generateBreakpoints(7 * mult, "normal"),

    // Earnings per hour: national median ~$140 MXN
    earnings_per_hour: generateBreakpoints(140 * mult, "wide"),

    // Trips per hour: national median ~3.2
    // Less affected by city earnings, more by traffic/density
    trips_per_hour: generateBreakpoints(
      3.2 * (city === "cdmx" ? 0.85 : city === "cancun" ? 1.1 : 1.0),
      "tight",
    ),

    // Platform commission %: median ~25%, varies by platform
    platform_commission_pct: generateBreakpoints(cm.commissionBase + pm.commissionShift, "tight"),

    // Weekly net earnings total: national median ~$2,800 MXN. The "all" bucket
    // (combined drivers, many multi-app) skews higher via its 2.1× sample + the
    // earnings multiplier; wide spread (hours-driven, the most variable metric).
    net_earnings: generateBreakpoints(2800 * mult, "wide"),
  };
}

// ─── Shared row builder (used by seed + tests) ────────────────

export interface SeedRow {
  city: CityKey;
  platform: PlatformKey;
  metric: MetricName;
  period: "current";
  sampleSize: number;
  breakpoints: PercentileBreakpoints;
  isSynthetic: true;
}

/**
 * Compute every population_stats row deterministically. Reused by the seed
 * runner and the test harness so the data under test is identical to what
 * ships.
 */
export function buildSeedRows(): SeedRow[] {
  const rows: SeedRow[] = [];
  for (const city of CITIES) {
    for (const platform of PLATFORMS) {
      const metrics = generateMetrics(city, platform);
      const size = sampleSize(city, platform);
      for (const metric of METRICS) {
        rows.push({
          city,
          platform,
          metric,
          period: "current",
          sampleSize: size,
          breakpoints: metrics[metric],
          isSynthetic: true,
        });
      }
    }
  }
  return rows;
}

// ─── Seed execution ───────────────────────────────────────────

async function seed() {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error(
      "DATABASE_URL environment variable is required.\n" +
        "Set it to your Postgres connection string, e.g.\n" +
        "  DATABASE_URL=postgres://user:pass@host:5432/kompara pnpm db:seed",
    );
    process.exit(1);
  }

  const sql = postgres(databaseUrl, { max: 1 });
  const rows = buildSeedRows();

  console.log("Seeding population_stats...");
  for (const r of rows) {
    const bp = r.breakpoints;
    await sql`
      INSERT INTO population_stats (
        city, platform, metric_name, period, sample_size,
        p10, p25, p50, p75, p90, mean, is_synthetic, updated_at
      ) VALUES (
        ${r.city}, ${r.platform}, ${r.metric}, ${r.period}, ${r.sampleSize},
        ${bp.p10}, ${bp.p25}, ${bp.p50}, ${bp.p75}, ${bp.p90}, ${bp.mean}, ${r.isSynthetic},
        NOW()
      )
      ON CONFLICT (city, platform, metric_name, period)
      DO UPDATE SET
        sample_size = EXCLUDED.sample_size,
        p10 = EXCLUDED.p10,
        p25 = EXCLUDED.p25,
        p50 = EXCLUDED.p50,
        p75 = EXCLUDED.p75,
        p90 = EXCLUDED.p90,
        mean = EXCLUDED.mean,
        is_synthetic = EXCLUDED.is_synthetic,
        updated_at = NOW()
    `;
  }

  console.log(
    `Done. ${rows.length} rows upserted across ${CITIES.length} cities ` +
      `(${CITIES.length} cities x ${PLATFORMS.length} platforms x ${METRICS.length} metrics).`,
  );

  await sql.end();
}

// Only run when invoked directly (not when imported by tests).
const invokedDirectly =
  process.argv[1] !== undefined && import.meta.url === `file://${process.argv[1]}`;

if (invokedDirectly) {
  seed().catch((err) => {
    console.error("Seed failed:", err);
    process.exit(1);
  });
}
