import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth/middleware";
import { db, getDirectClient } from "@/lib/db";
import { drivers, weeklyData } from "@/lib/db/schema";
import { eq, desc, and } from "drizzle-orm";
import {
  calculatePercentiles,
  METRIC_KEYS,
  type ParsedMetrics,
} from "@/lib/percentiles/engine";
import { generateRecommendations } from "@/lib/recommendations/engine";

/**
 * GET /api/dashboard
 *
 * Returns the authenticated driver's current week metrics, percentile rankings
 * per platform, totals across platforms, and personalized recommendations.
 */
export async function GET() {
  const auth = await requireAuth();
  if (auth instanceof NextResponse) return auth;
  const { driverId } = auth;

  // Fetch driver info
  const [driver] = await db
    .select()
    .from(drivers)
    .where(eq(drivers.id, driverId))
    .limit(1);

  if (!driver) {
    return NextResponse.json(
      { ok: false, error: "Conductor no encontrado" },
      { status: 404 },
    );
  }

  // Find the most recent week_start for this driver
  const sql = getDirectClient();
  const latestWeekRows = await sql`
    SELECT DISTINCT week_start
    FROM weekly_data
    WHERE driver_id = ${driverId}
    ORDER BY week_start DESC
    LIMIT 1
  `;

  // Empty state: no uploads yet
  if (latestWeekRows.length === 0) {
    return NextResponse.json({
      driver: {
        name: driver.name,
        city: driver.city,
        tier: driver.tier,
        streak_weeks: driver.streakWeeks,
        platforms: driver.platforms ?? [],
      },
      current_week: null,
      recommendations: [],
    });
  }

  const weekStart = latestWeekRows[0].week_start;

  // Fetch all weekly_data rows for this driver and week
  const weekRows = await db
    .select()
    .from(weeklyData)
    .where(
      and(eq(weeklyData.driverId, driverId), eq(weeklyData.weekStart, weekStart)),
    )
    .orderBy(desc(weeklyData.createdAt));

  // Build per-platform data with percentiles
  const platformsData: Record<
    string,
    {
      metrics: ParsedMetrics;
      percentiles: Awaited<ReturnType<typeof calculatePercentiles>>;
      data_completeness: number;
    }
  > = {};

  let totalNetEarnings = 0;
  let totalTrips = 0;
  const crossPlatformMetrics: Record<string, ParsedMetrics> = {};

  for (const row of weekRows) {
    const metrics: ParsedMetrics = {};
    for (const key of METRIC_KEYS) {
      const schemaKey = key
        .replace(/_([a-z])/g, (_, c: string) => c.toUpperCase()) as keyof typeof row;
      const val = row[schemaKey];
      metrics[key] = val != null ? Number(val) : null;
    }

    const city = driver.city ?? "national";
    const percentiles = await calculatePercentiles(city, row.platform, metrics);

    platformsData[row.platform] = {
      metrics,
      percentiles,
      data_completeness: row.dataCompleteness
        ? Number(row.dataCompleteness)
        : 1,
    };

    totalNetEarnings += Number(row.netEarnings);
    totalTrips += row.totalTrips;
    crossPlatformMetrics[row.platform] = metrics;
  }

  // Generate recommendations using the first platform's data as primary
  const primaryPlatform = weekRows[0]?.platform;
  const primaryMetrics = primaryPlatform
    ? crossPlatformMetrics[primaryPlatform]
    : {};
  const primaryPercentiles = primaryPlatform
    ? platformsData[primaryPlatform]?.percentiles ?? []
    : [];

  const recommendations = generateRecommendations(
    primaryMetrics,
    primaryPercentiles,
    Object.keys(crossPlatformMetrics).length >= 2
      ? crossPlatformMetrics
      : undefined,
    {
      streak_weeks: driver.streakWeeks,
      tier: driver.tier,
    },
  );

  // Add data completeness recommendation if needed
  for (const [platform, data] of Object.entries(platformsData)) {
    if (data.data_completeness < 0.7) {
      const platformName =
        platform.charAt(0).toUpperCase() + platform.slice(1);
      recommendations.push({
        type: "info",
        message: `Sube el PDF semanal de ${platformName} para datos mas completos.`,
        priority: 4,
      });
      break; // Only one completeness recommendation
    }
  }

  return NextResponse.json({
    driver: {
      name: driver.name,
      city: driver.city,
      tier: driver.tier,
      streak_weeks: driver.streakWeeks,
      platforms: driver.platforms ?? [],
    },
    current_week: {
      week_start: weekStart,
      platforms: platformsData,
      totals: {
        net_earnings: totalNetEarnings,
        total_trips: totalTrips,
      },
    },
    recommendations: recommendations.slice(0, 3),
  });
}
