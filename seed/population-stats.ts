/**
 * Seed population_stats with synthetic data for the 10 priority cities + national fallback.
 *
 * Run with: pnpm db:seed
 *
 * Idempotent — uses ON CONFLICT DO UPDATE, so it can be run multiple times safely.
 *
 * Data is based on reasonable estimates from:
 * - Public reports on ride-hailing earnings in Mexico
 * - INEGI transportation data
 * - Driver community forums and social media
 * - News articles on ride-hailing in Mexican cities
 */

import postgres from "postgres";

// ─── Configuration ────────────────────────────────────────────

const PLATFORMS = ["uber", "didi", "indrive"] as const;

const METRICS = [
  "earnings_per_trip",
  "earnings_per_km",
  "earnings_per_hour",
  "trips_per_hour",
  "platform_commission_pct",
] as const;

/**
 * Cities with their sample sizes.
 * Major cities (CDMX, Monterrey, Guadalajara) have larger sample sizes.
 * Smaller cities have fewer data points.
 */
const CITIES: Record<string, number> = {
  cdmx: 2000,
  monterrey: 1500,
  guadalajara: 1400,
  puebla: 800,
  toluca: 600,
  tijuana: 900,
  leon: 500,
  queretaro: 700,
  merida: 600,
  cancun: 700,
  national: 5000,
};

// ─── Synthetic data definitions ───────────────────────────────

/**
 * Base stats for each metric per platform (national averages in MXN).
 * These are realistic estimates for Mexican ride-hailing markets.
 *
 * earnings_per_trip: Average net earnings per completed trip
 * earnings_per_km: Net earnings per kilometer driven
 * earnings_per_hour: Net earnings per hour online
 * trips_per_hour: Average completed trips per hour online
 * platform_commission_pct: Platform take rate as percentage
 */
interface MetricProfile {
  p10: number;
  p25: number;
  p50: number;
  p75: number;
  p90: number;
  mean: number;
}

type PlatformMetrics = Record<string, MetricProfile>;

const NATIONAL_BASE: Record<string, PlatformMetrics> = {
  uber: {
    earnings_per_trip: {
      p10: 28.0,
      p25: 35.0,
      p50: 45.0,
      p75: 58.0,
      p90: 72.0,
      mean: 46.5,
    },
    earnings_per_km: {
      p10: 3.5,
      p25: 4.8,
      p50: 6.2,
      p75: 7.8,
      p90: 9.5,
      mean: 6.3,
    },
    earnings_per_hour: {
      p10: 85.0,
      p25: 110.0,
      p50: 145.0,
      p75: 185.0,
      p90: 230.0,
      mean: 150.0,
    },
    trips_per_hour: {
      p10: 1.5,
      p25: 2.0,
      p50: 2.8,
      p75: 3.5,
      p90: 4.2,
      mean: 2.8,
    },
    platform_commission_pct: {
      p10: 20.0,
      p25: 22.5,
      p50: 25.0,
      p75: 28.0,
      p90: 32.0,
      mean: 25.5,
    },
  },
  didi: {
    earnings_per_trip: {
      p10: 25.0,
      p25: 32.0,
      p50: 42.0,
      p75: 55.0,
      p90: 68.0,
      mean: 43.0,
    },
    earnings_per_km: {
      p10: 3.2,
      p25: 4.5,
      p50: 5.8,
      p75: 7.2,
      p90: 8.8,
      mean: 5.9,
    },
    earnings_per_hour: {
      p10: 78.0,
      p25: 100.0,
      p50: 135.0,
      p75: 172.0,
      p90: 215.0,
      mean: 140.0,
    },
    trips_per_hour: {
      p10: 1.4,
      p25: 1.9,
      p50: 2.6,
      p75: 3.3,
      p90: 4.0,
      mean: 2.6,
    },
    platform_commission_pct: {
      p10: 18.0,
      p25: 20.0,
      p50: 23.0,
      p75: 26.0,
      p90: 30.0,
      mean: 23.5,
    },
  },
  indrive: {
    earnings_per_trip: {
      p10: 22.0,
      p25: 30.0,
      p50: 40.0,
      p75: 52.0,
      p90: 65.0,
      mean: 41.0,
    },
    earnings_per_km: {
      p10: 3.0,
      p25: 4.2,
      p50: 5.5,
      p75: 7.0,
      p90: 8.5,
      mean: 5.6,
    },
    earnings_per_hour: {
      p10: 70.0,
      p25: 92.0,
      p50: 125.0,
      p75: 160.0,
      p90: 200.0,
      mean: 130.0,
    },
    trips_per_hour: {
      p10: 1.2,
      p25: 1.7,
      p50: 2.4,
      p75: 3.0,
      p90: 3.8,
      mean: 2.4,
    },
    platform_commission_pct: {
      p10: 10.0,
      p25: 12.0,
      p50: 15.0,
      p75: 18.0,
      p90: 22.0,
      mean: 15.5,
    },
  },
};

/**
 * City-level multipliers relative to national base.
 * Values > 1.0 mean higher than national average.
 *
 * CDMX and tourist cities (Cancun) tend to have higher earnings.
 * Smaller cities tend to have lower earnings but also lower commission negotiation.
 */
const CITY_MULTIPLIERS: Record<
  string,
  Record<string, number>
> = {
  cdmx: {
    earnings_per_trip: 1.15,
    earnings_per_km: 1.05,
    earnings_per_hour: 1.20,
    trips_per_hour: 1.10,
    platform_commission_pct: 1.02,
  },
  monterrey: {
    earnings_per_trip: 1.10,
    earnings_per_km: 1.08,
    earnings_per_hour: 1.12,
    trips_per_hour: 1.05,
    platform_commission_pct: 1.00,
  },
  guadalajara: {
    earnings_per_trip: 1.05,
    earnings_per_km: 1.03,
    earnings_per_hour: 1.08,
    trips_per_hour: 1.03,
    platform_commission_pct: 1.00,
  },
  puebla: {
    earnings_per_trip: 0.90,
    earnings_per_km: 0.92,
    earnings_per_hour: 0.88,
    trips_per_hour: 0.95,
    platform_commission_pct: 0.98,
  },
  toluca: {
    earnings_per_trip: 0.85,
    earnings_per_km: 0.88,
    earnings_per_hour: 0.82,
    trips_per_hour: 0.92,
    platform_commission_pct: 0.97,
  },
  tijuana: {
    earnings_per_trip: 1.08,
    earnings_per_km: 1.10,
    earnings_per_hour: 1.05,
    trips_per_hour: 0.98,
    platform_commission_pct: 1.01,
  },
  leon: {
    earnings_per_trip: 0.88,
    earnings_per_km: 0.90,
    earnings_per_hour: 0.85,
    trips_per_hour: 0.93,
    platform_commission_pct: 0.98,
  },
  queretaro: {
    earnings_per_trip: 0.95,
    earnings_per_km: 0.97,
    earnings_per_hour: 0.93,
    trips_per_hour: 0.97,
    platform_commission_pct: 0.99,
  },
  merida: {
    earnings_per_trip: 0.92,
    earnings_per_km: 0.94,
    earnings_per_hour: 0.90,
    trips_per_hour: 0.95,
    platform_commission_pct: 0.98,
  },
  cancun: {
    earnings_per_trip: 1.18,
    earnings_per_km: 1.12,
    earnings_per_hour: 1.15,
    trips_per_hour: 1.02,
    platform_commission_pct: 1.03,
  },
};

// ─── Helpers ──────────────────────────────────────────────────

function round2(n: number): string {
  return n.toFixed(2);
}

function applyMultiplier(base: MetricProfile, multiplier: number): MetricProfile {
  return {
    p10: base.p10 * multiplier,
    p25: base.p25 * multiplier,
    p50: base.p50 * multiplier,
    p75: base.p75 * multiplier,
    p90: base.p90 * multiplier,
    mean: base.mean * multiplier,
  };
}

// ─── Main seed function ───────────────────────────────────────

interface SeedRow {
  city: string;
  platform: string;
  metric_name: string;
  period: string;
  sample_size: number;
  p10: string;
  p25: string;
  p50: string;
  p75: string;
  p90: string;
  mean: string;
}

function generateRows(): SeedRow[] {
  const rows: SeedRow[] = [];

  for (const [city, sampleSize] of Object.entries(CITIES)) {
    for (const platform of PLATFORMS) {
      for (const metric of METRICS) {
        const baseProfile = NATIONAL_BASE[platform][metric];
        const multiplier =
          city === "national" ? 1.0 : (CITY_MULTIPLIERS[city]?.[metric] ?? 1.0);
        const profile = applyMultiplier(baseProfile, multiplier);

        // Vary sample size slightly per platform/metric to look realistic
        const platformFactor =
          platform === "uber" ? 1.0 : platform === "didi" ? 0.7 : 0.4;
        const adjustedSampleSize = Math.round(sampleSize * platformFactor);

        rows.push({
          city,
          platform,
          metric_name: metric,
          period: "current",
          sample_size: Math.max(adjustedSampleSize, 30), // Ensure minimum sample
          p10: round2(profile.p10),
          p25: round2(profile.p25),
          p50: round2(profile.p50),
          p75: round2(profile.p75),
          p90: round2(profile.p90),
          mean: round2(profile.mean),
        });
      }
    }
  }

  return rows;
}

async function seed() {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error(
      "DATABASE_URL environment variable is not set.\n" +
        "Set it to your Postgres connection string, e.g.:\n" +
        "  DATABASE_URL=postgres://user:pass@host:5432/pilotea pnpm db:seed",
    );
    process.exit(1);
  }

  const sql = postgres(databaseUrl, { max: 1 });

  try {
    const rows = generateRows();

    console.log(
      `Seeding population_stats with ${rows.length} rows ` +
        `(${Object.keys(CITIES).length} cities x ${PLATFORMS.length} platforms x ${METRICS.length} metrics)...`,
    );

    // Use a single transaction for atomicity
    await sql.begin(async (tx) => {
      for (const row of rows) {
        await tx`
          INSERT INTO population_stats (
            city, platform, metric_name, period,
            sample_size, p10, p25, p50, p75, p90, mean, updated_at
          ) VALUES (
            ${row.city}, ${row.platform}, ${row.metric_name}, ${row.period},
            ${row.sample_size}, ${row.p10}, ${row.p25}, ${row.p50},
            ${row.p75}, ${row.p90}, ${row.mean}, NOW()
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
      }
    });

    console.log(`Successfully seeded ${rows.length} rows.`);

    // Print summary
    const summary = await sql`
      SELECT city, COUNT(*) as metrics
      FROM population_stats
      WHERE period = 'current'
      GROUP BY city
      ORDER BY city
    `;
    console.log("\nSummary:");
    for (const row of summary) {
      console.log(`  ${row.city}: ${row.metrics} metric rows`);
    }
  } catch (error) {
    console.error("Seed failed:", error);
    process.exit(1);
  } finally {
    await sql.end();
  }
}

// Export for testing
export { generateRows, CITIES, PLATFORMS, METRICS, applyMultiplier };

// Run when executed directly (not imported for testing)
// tsx sets the module URL to the file path, so we check if this is the entry point
const isMainModule =
  typeof process !== "undefined" &&
  process.argv[1] &&
  (process.argv[1].endsWith("population-stats.ts") ||
    process.argv[1].endsWith("population-stats.js"));

if (isMainModule) {
  seed().catch((err) => {
    console.error("Unexpected error:", err);
    process.exit(1);
  });
}
