/**
 * Seed population_stats with synthetic data for 10 priority cities + national fallback.
 *
 * Values are reasonable estimates for Mexican ride-hail drivers in MXN,
 * based on public reports, news articles, and driver community data.
 *
 * Idempotent: uses ON CONFLICT ... DO UPDATE to upsert.
 *
 * Usage: pnpm db:seed
 */

import postgres from "postgres";

// ─── Configuration ────────────────────────────────────────────

const CITIES = [
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

const PLATFORMS = ["uber", "didi", "indrive"] as const;

const METRICS = [
  "earnings_per_trip",
  "earnings_per_km",
  "earnings_per_hour",
  "trips_per_hour",
  "platform_commission_pct",
] as const;

// ─── Types ────────────────────────────────────────────────────

type MetricName = (typeof METRICS)[number];
type CityKey = (typeof CITIES)[number];
type PlatformKey = (typeof PLATFORMS)[number];

interface PercentileBreakpoints {
  p10: number;
  p25: number;
  p50: number;
  p75: number;
  p90: number;
  mean: number;
}

interface CityPlatformProfile {
  sample_size: number;
  metrics: Record<MetricName, PercentileBreakpoints>;
}

// ─── Sample sizes by city tier ────────────────────────────────

function sampleSize(city: CityKey, platform: PlatformKey): number {
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
  };

  const base = baseSizes[cityTier[city]];
  // Add some variation so not every city has the same number
  const variation = ((city.length * 37 + platform.length * 13) % 200) - 100;
  return Math.max(
    50,
    Math.round((base + variation) * platformMultiplier[platform]),
  );
}

// ─── Synthetic data generators ────────────────────────────────

/**
 * Base national median values (MXN per week basis):
 * - earnings_per_trip: ~$45 MXN median
 * - earnings_per_km: ~$7 MXN median
 * - earnings_per_hour: ~$140 MXN median
 * - trips_per_hour: ~$3.2 median
 * - platform_commission_pct: ~25% median
 */

interface CityModifiers {
  earningsMultiplier: number; // Higher in expensive cities
  commissionBase: number; // Platform commission median %
}

const CITY_MODIFIERS: Record<CityKey, CityModifiers> = {
  cdmx: { earningsMultiplier: 1.15, commissionBase: 25 },
  monterrey: { earningsMultiplier: 1.10, commissionBase: 25 },
  guadalajara: { earningsMultiplier: 1.05, commissionBase: 25 },
  puebla: { earningsMultiplier: 0.90, commissionBase: 25 },
  toluca: { earningsMultiplier: 0.88, commissionBase: 25 },
  tijuana: { earningsMultiplier: 1.12, commissionBase: 24 },
  leon: { earningsMultiplier: 0.85, commissionBase: 25 },
  queretaro: { earningsMultiplier: 0.95, commissionBase: 25 },
  merida: { earningsMultiplier: 0.92, commissionBase: 25 },
  cancun: { earningsMultiplier: 1.20, commissionBase: 24 }, // Tourism premium
  national: { earningsMultiplier: 1.00, commissionBase: 25 },
};

const PLATFORM_MODIFIERS: Record<
  PlatformKey,
  { earningsMultiplier: number; commissionShift: number }
> = {
  uber: { earningsMultiplier: 1.0, commissionShift: 0 },
  didi: { earningsMultiplier: 0.92, commissionShift: -2 }, // DiDi slightly lower earnings, lower commission
  indrive: { earningsMultiplier: 0.85, commissionShift: -5 }, // InDrive lower earnings, much lower commission (driver sets price)
};

function generateBreakpoints(
  median: number,
  spread: "tight" | "normal" | "wide",
): PercentileBreakpoints {
  // Spread controls how much variation around the median
  const spreadFactors = {
    tight: { p10: 0.70, p25: 0.85, p75: 1.18, p90: 1.35 },
    normal: { p10: 0.60, p25: 0.80, p75: 1.25, p90: 1.50 },
    wide: { p10: 0.50, p25: 0.72, p75: 1.35, p90: 1.70 },
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

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

function generateMetrics(
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
    platform_commission_pct: generateBreakpoints(
      cm.commissionBase + pm.commissionShift,
      "tight",
    ),
  };
}

// ─── Seed execution ───────────────────────────────────────────

async function seed() {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error(
      "DATABASE_URL environment variable is required.\n" +
        "Set it to your Postgres connection string, e.g.\n" +
        "  DATABASE_URL=postgres://user:pass@host:5432/pilotea pnpm db:seed",
    );
    process.exit(1);
  }

  const sql = postgres(databaseUrl, { max: 1 });

  console.log("Seeding population_stats...");
  let rowCount = 0;

  for (const city of CITIES) {
    for (const platform of PLATFORMS) {
      const metrics = generateMetrics(city, platform);
      const size = sampleSize(city, platform);

      for (const metric of METRICS) {
        const bp = metrics[metric];

        await sql`
          INSERT INTO population_stats (
            city, platform, metric_name, period, sample_size,
            p10, p25, p50, p75, p90, mean, updated_at
          ) VALUES (
            ${city}, ${platform}, ${metric}, 'current', ${size},
            ${bp.p10}, ${bp.p25}, ${bp.p50}, ${bp.p75}, ${bp.p90}, ${bp.mean},
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
            updated_at = NOW()
        `;
        rowCount++;
      }
    }
    console.log(`  ${city}: ${PLATFORMS.length * METRICS.length} rows`);
  }

  console.log(`\nDone. ${rowCount} rows upserted across ${CITIES.length} cities.`);
  console.log(
    `Coverage: ${CITIES.length} cities x ${PLATFORMS.length} platforms x ${METRICS.length} metrics = ${CITIES.length * PLATFORMS.length * METRICS.length} rows`,
  );

  await sql.end();
}

seed().catch((err) => {
  console.error("Seed failed:", err);
  process.exit(1);
});
