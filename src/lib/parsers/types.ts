import { z } from "zod";

// ─── ParseInput ───────────────────────────────────────────────
export interface ParseInput {
  files: Buffer[]; // 1 file for Uber/InDrive, 2 for DiDi
  mimeType: string;
}

// ─── ParsedMetrics ────────────────────────────────────────────
export interface ParsedMetrics {
  week_start: string; // ISO date (Monday)
  net_earnings: number | null;
  gross_earnings: number | null;
  total_trips: number | null;
  earnings_per_trip: number | null;
  earnings_per_km: number | null;
  earnings_per_hour: number | null;
  trips_per_hour: number | null;
  platform_commission_pct: number | null;
  total_km: number | null;
  hours_online: number | null;
  platform_commission: number | null;
  taxes: number | null;
  incentives: number | null;
  tips: number | null;
  surge_earnings: number | null;
  wait_time_earnings: number | null;
  active_days: number | null;
  peak_day_earnings: number | null;
  peak_day_name: string | null;
  cash_amount: number | null;
  card_amount: number | null;
  rewards: number | null;
}

// ─── ParseResult ──────────────────────────────────────────────
export interface ParseResult {
  success: boolean;
  metrics: ParsedMetrics | null;
  raw_extraction: Record<string, unknown>; // Full Claude response
  data_completeness: number; // 0.0–1.0
  error?: string; // Spanish error message
}

// ─── Platform and upload type enums ───────────────────────────
export type Platform = "uber" | "didi" | "indrive";
export type UploadType = "pdf" | "screenshot";

// ─── Zod schema for Claude Vision extraction response ─────────
// This validates the raw JSON that Claude returns from the PDF
export const uberPdfExtractionSchema = z.object({
  week_start: z.string(),
  net_earnings: z.number().nullable(),
  gross_earnings: z.number().nullable(),
  total_trips: z.number().int().nullable(),
  hours_online: z.number().nullable(),
  platform_commission: z.number().nullable(),
  platform_commission_pct: z.number().nullable(),
  taxes: z.number().nullable(),
  incentives: z.number().nullable(),
  tips: z.number().nullable(),
  surge_earnings: z.number().nullable(),
  wait_time_earnings: z.number().nullable(),
  active_days: z.number().int().nullable(),
  peak_day_earnings: z.number().nullable(),
  peak_day_name: z.string().nullable(),
  cash_amount: z.number().nullable(),
  card_amount: z.number().nullable(),
  rewards: z.number().nullable(),
});

export type UberPdfExtraction = z.infer<typeof uberPdfExtractionSchema>;

// ─── Zod schema for Uber screenshot (pie chart) extraction ───
// Only a subset of fields are available from the pie chart view
export const uberScreenshotExtractionSchema = z.object({
  week_start: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "week_start must be a valid ISO date (YYYY-MM-DD)").nullable(),
  net_earnings: z.number().nullable(),
  gross_earnings: z.number().nullable(),
  platform_commission: z.number().nullable(),
  platform_commission_pct: z.number().nullable(),
  taxes: z.number().nullable(),
  incentives: z.number().nullable(),
  tips: z.number().nullable(),
});

export type UberScreenshotExtraction = z.infer<typeof uberScreenshotExtractionSchema>;

// ─── Zod schema for DiDi screenshot extraction ──────────────
// DiDi provides 2 screens: earnings + tablero (dashboard)
export const didiScreenshotExtractionSchema = z.object({
  week_start: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "week_start must be a valid ISO date (YYYY-MM-DD)").nullable(),
  net_earnings: z.number().nullable(),
  gross_earnings: z.number().nullable(),
  total_trips: z.number().int().nullable(),
  earnings_per_km: z.number().nullable(),
  earnings_per_trip: z.number().nullable(),
  earnings_per_hour: z.number().nullable(),
  cash_amount: z.number().nullable(),
  card_amount: z.number().nullable(),
  taxes: z.number().nullable(),
  rewards: z.number().nullable(),
});

export type DidiScreenshotExtraction = z.infer<typeof didiScreenshotExtractionSchema>;

// ─── All metrics fields (for completeness calculation) ────────
export const ALL_METRICS_FIELDS: (keyof ParsedMetrics)[] = [
  "week_start",
  "net_earnings",
  "gross_earnings",
  "total_trips",
  "earnings_per_trip",
  "earnings_per_km",
  "earnings_per_hour",
  "trips_per_hour",
  "platform_commission_pct",
  "total_km",
  "hours_online",
  "platform_commission",
  "taxes",
  "incentives",
  "tips",
  "surge_earnings",
  "wait_time_earnings",
  "active_days",
  "peak_day_earnings",
  "peak_day_name",
  "cash_amount",
  "card_amount",
  "rewards",
];
