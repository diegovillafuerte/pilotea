import { NextRequest, NextResponse } from "next/server";
import { getDirectClient } from "@/lib/db";

/**
 * POST /api/cron/update-stats
 *
 * Recalculates population_stats from real weekly_data.
 * Protected by CRON_SECRET in the Authorization header.
 *
 * Intended to be called by an external cron service (e.g., Render Cron Job)
 * on a weekly schedule (Sunday night).
 *
 * Currently a placeholder — the SQL aggregation logic is defined but
 * will only produce meaningful results once enough real data accumulates.
 */

const METRICS = [
  "earnings_per_trip",
  "earnings_per_km",
  "earnings_per_hour",
  "trips_per_hour",
  "platform_commission_pct",
] as const;

export async function POST(request: NextRequest) {
  // ── Auth check ──────────────────────────────────────────────
  const cronSecret = process.env.CRON_SECRET;
  if (!cronSecret) {
    return NextResponse.json(
      { error: "CRON_SECRET not configured" },
      { status: 500 },
    );
  }

  const authHeader = request.headers.get("authorization");
  if (authHeader !== `Bearer ${cronSecret}`) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  // ── Recalculate population stats ────────────────────────────
  const sql = getDirectClient();

  try {
    let totalUpdated = 0;

    for (const metric of METRICS) {
      const result = await sql`
        INSERT INTO population_stats (
          city, platform, metric_name, period, sample_size,
          p10, p25, p50, p75, p90, mean, updated_at
        )
        SELECT
          d.city,
          wd.platform,
          ${metric} as metric_name,
          'current' as period,
          COUNT(*) as sample_size,
          PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY wd.${sql(metric)}),
          PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY wd.${sql(metric)}),
          PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY wd.${sql(metric)}),
          PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY wd.${sql(metric)}),
          PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY wd.${sql(metric)}),
          AVG(wd.${sql(metric)}),
          NOW()
        FROM weekly_data wd
        JOIN drivers d ON d.id = wd.driver_id
        WHERE wd.week_start >= CURRENT_DATE - INTERVAL '4 weeks'
          AND wd.${sql(metric)} IS NOT NULL
        GROUP BY d.city, wd.platform
        HAVING COUNT(*) >= 20
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

      totalUpdated += result.count;
    }

    return NextResponse.json({
      ok: true,
      metrics_processed: METRICS.length,
      rows_upserted: totalUpdated,
      timestamp: new Date().toISOString(),
    });
  } catch (error) {
    console.error("Failed to update population stats:", error);
    return NextResponse.json(
      { error: "Internal server error" },
      { status: 500 },
    );
  }
}
