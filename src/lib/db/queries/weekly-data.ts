import { db } from "@/lib/db";
import { weeklyData } from "@/lib/db/schema";
import { eq, and, gte, lte, desc } from "drizzle-orm";
import { sql } from "drizzle-orm";

interface UpsertWeeklyDataInput {
  driverId: string;
  platform: string;
  weekStart: string;
  netEarnings: string;
  grossEarnings: string;
  totalTrips: number;
  earningsPerTrip?: string | null;
  earningsPerKm?: string | null;
  earningsPerHour?: string | null;
  tripsPerHour?: string | null;
  platformCommissionPct?: string | null;
  totalKm?: string | null;
  hoursOnline?: string | null;
  platformCommission?: string | null;
  taxes?: string | null;
  incentives?: string | null;
  tips?: string | null;
  surgeEarnings?: string | null;
  waitTimeEarnings?: string | null;
  activeDays?: number | null;
  peakDayEarnings?: string | null;
  peakDayName?: string | null;
  cashAmount?: string | null;
  cardAmount?: string | null;
  rewards?: string | null;
  dataCompleteness?: string | null;
  rawExtraction?: Record<string, unknown> | null;
  uploadId?: string | null;
}

/**
 * Upsert weekly data for a driver. On conflict (driver_id + platform + week_start),
 * overwrites the existing record (idempotent re-upload).
 */
export async function upsertWeeklyData(data: UpsertWeeklyDataInput) {
  const values = {
    driverId: data.driverId,
    platform: data.platform,
    weekStart: data.weekStart,
    netEarnings: data.netEarnings,
    grossEarnings: data.grossEarnings,
    totalTrips: data.totalTrips,
    earningsPerTrip: data.earningsPerTrip ?? null,
    earningsPerKm: data.earningsPerKm ?? null,
    earningsPerHour: data.earningsPerHour ?? null,
    tripsPerHour: data.tripsPerHour ?? null,
    platformCommissionPct: data.platformCommissionPct ?? null,
    totalKm: data.totalKm ?? null,
    hoursOnline: data.hoursOnline ?? null,
    platformCommission: data.platformCommission ?? null,
    taxes: data.taxes ?? null,
    incentives: data.incentives ?? null,
    tips: data.tips ?? null,
    surgeEarnings: data.surgeEarnings ?? null,
    waitTimeEarnings: data.waitTimeEarnings ?? null,
    activeDays: data.activeDays ?? null,
    peakDayEarnings: data.peakDayEarnings ?? null,
    peakDayName: data.peakDayName ?? null,
    cashAmount: data.cashAmount ?? null,
    cardAmount: data.cardAmount ?? null,
    rewards: data.rewards ?? null,
    dataCompleteness: data.dataCompleteness ?? null,
    rawExtraction: data.rawExtraction ?? null,
    uploadId: data.uploadId ?? null,
  };

  const [result] = await db
    .insert(weeklyData)
    .values(values)
    .onConflictDoUpdate({
      target: [weeklyData.driverId, weeklyData.platform, weeklyData.weekStart],
      set: {
        netEarnings: sql`excluded.net_earnings`,
        grossEarnings: sql`excluded.gross_earnings`,
        totalTrips: sql`excluded.total_trips`,
        earningsPerTrip: sql`excluded.earnings_per_trip`,
        earningsPerKm: sql`excluded.earnings_per_km`,
        earningsPerHour: sql`excluded.earnings_per_hour`,
        tripsPerHour: sql`excluded.trips_per_hour`,
        platformCommissionPct: sql`excluded.platform_commission_pct`,
        totalKm: sql`excluded.total_km`,
        hoursOnline: sql`excluded.hours_online`,
        platformCommission: sql`excluded.platform_commission`,
        taxes: sql`excluded.taxes`,
        incentives: sql`excluded.incentives`,
        tips: sql`excluded.tips`,
        surgeEarnings: sql`excluded.surge_earnings`,
        waitTimeEarnings: sql`excluded.wait_time_earnings`,
        activeDays: sql`excluded.active_days`,
        peakDayEarnings: sql`excluded.peak_day_earnings`,
        peakDayName: sql`excluded.peak_day_name`,
        cashAmount: sql`excluded.cash_amount`,
        cardAmount: sql`excluded.card_amount`,
        rewards: sql`excluded.rewards`,
        dataCompleteness: sql`excluded.data_completeness`,
        rawExtraction: sql`excluded.raw_extraction`,
        uploadId: sql`excluded.upload_id`,
      },
    })
    .returning();

  return result;
}

/**
 * Get the latest weekly data record for each platform for a given driver.
 */
export async function getLatestWeeklyData(driverId: string) {
  return db
    .select()
    .from(weeklyData)
    .where(eq(weeklyData.driverId, driverId))
    .orderBy(desc(weeklyData.weekStart))
    .limit(10);
}

/**
 * Get weekly data for a driver within a date range.
 */
export async function getWeeklyDataByPeriod(
  driverId: string,
  startDate: string,
  endDate: string,
) {
  return db
    .select()
    .from(weeklyData)
    .where(
      and(
        eq(weeklyData.driverId, driverId),
        gte(weeklyData.weekStart, startDate),
        lte(weeklyData.weekStart, endDate),
      ),
    )
    .orderBy(desc(weeklyData.weekStart));
}
