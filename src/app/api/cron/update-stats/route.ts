import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";
import { sql } from "drizzle-orm";
import { PERCENTILE_METRICS } from "@/lib/percentiles/engine";

/**
 * POST /api/cron/update-stats
 *
 * Recalculates population_stats from real weekly_data.
 * Protected by CRON_SECRET in the Authorization header.
 *
 * Intended to run weekly (e.g., Sunday night via Render Cron Job).
 * For each city x platform x metric with >= 20 data points in the last 4 weeks,
 * upserts the percentile breakpoints into population_stats.
 */
export async function POST(request: NextRequest) {
  // Verify cron secret
  const authHeader = request.headers.get("authorization");
  const cronSecret = process.env.CRON_SECRET;

  if (!cronSecret) {
    return NextResponse.json(
      { error: "CRON_SECRET not configured" },
      { status: 500 },
    );
  }

  if (authHeader !== `Bearer ${cronSecret}`) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  try {
    let totalUpdated = 0;

    for (const metric of PERCENTILE_METRICS) {
      // City-level aggregation (joins with drivers to get city)
      const cityResult = await db.execute(sql`
        INSERT INTO population_stats (
          city, platform, metric_name, period,
          sample_size, p10, p25, p50, p75, p90, mean, updated_at
        )
        SELECT
          d.city,
          wd.platform,
          ${metric} as metric_name,
          'current' as period,
          COUNT(*) as sample_size,
          PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY wd.${sql.raw(metric)}),
          PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY wd.${sql.raw(metric)}),
          PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY wd.${sql.raw(metric)}),
          PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY wd.${sql.raw(metric)}),
          PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY wd.${sql.raw(metric)}),
          AVG(wd.${sql.raw(metric)}),
          NOW()
        FROM weekly_data wd
        JOIN drivers d ON d.id = wd.driver_id
        WHERE wd.week_start >= CURRENT_DATE - INTERVAL '4 weeks'
          AND wd.${sql.raw(metric)} IS NOT NULL
        GROUP BY d.city, wd.platform
        HAVING COUNT(*) >= 20
        ON CONFLICT (city, platform, metric_name, period)
        DO UPDATE SET
          sample_size = EXCLUDED.sample_size,
          p10 = EXCLUDED.p10, p25 = EXCLUDED.p25, p50 = EXCLUDED.p50,
          p75 = EXCLUDED.p75, p90 = EXCLUDED.p90, mean = EXCLUDED.mean,
          updated_at = NOW()
      `);

      totalUpdated += Array.isArray(cityResult) ? cityResult.length : 0;

      // National aggregation (no city join needed)
      await db.execute(sql`
        INSERT INTO population_stats (
          city, platform, metric_name, period,
          sample_size, p10, p25, p50, p75, p90, mean, updated_at
        )
        SELECT
          'national' as city,
          wd.platform,
          ${metric} as metric_name,
          'current' as period,
          COUNT(*) as sample_size,
          PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY wd.${sql.raw(metric)}),
          PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY wd.${sql.raw(metric)}),
          PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY wd.${sql.raw(metric)}),
          PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY wd.${sql.raw(metric)}),
          PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY wd.${sql.raw(metric)}),
          AVG(wd.${sql.raw(metric)}),
          NOW()
        FROM weekly_data wd
        WHERE wd.week_start >= CURRENT_DATE - INTERVAL '4 weeks'
          AND wd.${sql.raw(metric)} IS NOT NULL
        GROUP BY wd.platform
        HAVING COUNT(*) >= 20
        ON CONFLICT (city, platform, metric_name, period)
        DO UPDATE SET
          sample_size = EXCLUDED.sample_size,
          p10 = EXCLUDED.p10, p25 = EXCLUDED.p25, p50 = EXCLUDED.p50,
          p75 = EXCLUDED.p75, p90 = EXCLUDED.p90, mean = EXCLUDED.mean,
          updated_at = NOW()
      `);
    }

    return NextResponse.json({
      success: true,
      message: `Population stats updated. ${totalUpdated} city-platform-metric combinations refreshed.`,
      timestamp: new Date().toISOString(),
    });
  } catch (error) {
    console.error("Failed to update population stats:", error);
    return NextResponse.json(
      {
        error: "Failed to update population stats",
        details: error instanceof Error ? error.message : "Unknown error",
      },
      { status: 500 },
    );
  }
}
