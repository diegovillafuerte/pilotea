import {
  pgTable,
  uuid,
  varchar,
  text,
  boolean,
  integer,
  decimal,
  timestamp,
  date,
  jsonb,
  uniqueIndex,
  index,
} from "drizzle-orm/pg-core";

// ─── drivers ───────────────────────────────────────────────────
export const drivers = pgTable("drivers", {
  id: uuid("id").primaryKey().defaultRandom(),
  phone: varchar("phone", { length: 20 }).notNull().unique(),
  name: varchar("name", { length: 100 }),
  city: varchar("city", { length: 100 }),
  platforms: text("platforms").array(),
  tier: varchar("tier", { length: 20 }).notNull().default("free"),
  onboardingCompleted: boolean("onboarding_completed").notNull().default(false),
  streakWeeks: integer("streak_weeks").notNull().default(0),
  lastUploadAt: timestamp("last_upload_at", { withTimezone: true }),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
});

// ─── magic_links ───────────────────────────────────────────────
export const magicLinks = pgTable(
  "magic_links",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    phone: varchar("phone", { length: 20 }).notNull(),
    token: varchar("token", { length: 64 }).notNull().unique(),
    expiresAt: timestamp("expires_at", { withTimezone: true }).notNull(),
    usedAt: timestamp("used_at", { withTimezone: true }),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [index("idx_magic_links_token").on(table.token)],
);

// ─── sessions ──────────────────────────────────────────────────
export const sessions = pgTable(
  "sessions",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    driverId: uuid("driver_id")
      .notNull()
      .references(() => drivers.id),
    tokenHash: varchar("token_hash", { length: 64 }).notNull(),
    expiresAt: timestamp("expires_at", { withTimezone: true }).notNull(),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [index("idx_sessions_token_hash").on(table.tokenHash)],
);

// ─── uploads ───────────────────────────────────────────────────
export const uploads = pgTable(
  "uploads",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    driverId: uuid("driver_id")
      .notNull()
      .references(() => drivers.id),
    platform: varchar("platform", { length: 20 }).notNull(),
    uploadType: varchar("upload_type", { length: 20 }).notNull(),
    fileKey: text("file_key").notNull(),
    status: varchar("status", { length: 20 }).notNull().default("pending"),
    errorMessage: text("error_message"),
    parsedData: jsonb("parsed_data"),
    weeklyDataId: uuid("weekly_data_id"),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [index("idx_uploads_driver_status").on(table.driverId, table.status)],
);

// ─── weekly_data ───────────────────────────────────────────────
export const weeklyData = pgTable(
  "weekly_data",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    driverId: uuid("driver_id")
      .notNull()
      .references(() => drivers.id),
    platform: varchar("platform", { length: 20 }).notNull(),
    weekStart: date("week_start").notNull(),

    // Core earnings
    netEarnings: decimal("net_earnings", { precision: 10, scale: 2 }).notNull(),
    grossEarnings: decimal("gross_earnings", { precision: 10, scale: 2 }).notNull(),
    totalTrips: integer("total_trips").notNull(),

    // Efficiency metrics
    earningsPerTrip: decimal("earnings_per_trip", { precision: 10, scale: 2 }),
    earningsPerKm: decimal("earnings_per_km", { precision: 10, scale: 2 }),
    earningsPerHour: decimal("earnings_per_hour", { precision: 10, scale: 2 }),
    tripsPerHour: decimal("trips_per_hour", { precision: 10, scale: 2 }),
    platformCommissionPct: decimal("platform_commission_pct", { precision: 5, scale: 2 }),

    // Raw inputs
    totalKm: decimal("total_km", { precision: 10, scale: 2 }),
    hoursOnline: decimal("hours_online", { precision: 10, scale: 2 }),
    platformCommission: decimal("platform_commission", { precision: 10, scale: 2 }),
    taxes: decimal("taxes", { precision: 10, scale: 2 }),
    incentives: decimal("incentives", { precision: 10, scale: 2 }),
    tips: decimal("tips", { precision: 10, scale: 2 }),
    surgeEarnings: decimal("surge_earnings", { precision: 10, scale: 2 }),
    waitTimeEarnings: decimal("wait_time_earnings", { precision: 10, scale: 2 }),
    activeDays: integer("active_days"),
    peakDayEarnings: decimal("peak_day_earnings", { precision: 10, scale: 2 }),
    peakDayName: varchar("peak_day_name", { length: 20 }),
    cashAmount: decimal("cash_amount", { precision: 10, scale: 2 }),
    cardAmount: decimal("card_amount", { precision: 10, scale: 2 }),
    rewards: decimal("rewards", { precision: 10, scale: 2 }),

    // Metadata
    dataCompleteness: decimal("data_completeness", { precision: 3, scale: 2 }),
    rawExtraction: jsonb("raw_extraction"),
    uploadId: uuid("upload_id").references(() => uploads.id),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    uniqueIndex("uq_weekly_data_driver_platform_week").on(
      table.driverId,
      table.platform,
      table.weekStart,
    ),
    index("idx_weekly_data_driver").on(table.driverId, table.weekStart),
    index("idx_weekly_data_city_platform").on(table.platform, table.weekStart),
  ],
);

// ─── population_stats ──────────────────────────────────────────
export const populationStats = pgTable(
  "population_stats",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    city: varchar("city", { length: 100 }).notNull(),
    platform: varchar("platform", { length: 20 }).notNull(),
    metricName: varchar("metric_name", { length: 50 }).notNull(),
    period: varchar("period", { length: 20 }).notNull(),
    sampleSize: integer("sample_size").notNull(),
    p10: decimal("p10", { precision: 10, scale: 2 }).notNull(),
    p25: decimal("p25", { precision: 10, scale: 2 }).notNull(),
    p50: decimal("p50", { precision: 10, scale: 2 }).notNull(),
    p75: decimal("p75", { precision: 10, scale: 2 }).notNull(),
    p90: decimal("p90", { precision: 10, scale: 2 }).notNull(),
    mean: decimal("mean", { precision: 10, scale: 2 }).notNull(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    uniqueIndex("uq_population_stats_city_platform_metric_period").on(
      table.city,
      table.platform,
      table.metricName,
      table.period,
    ),
  ],
);

// ─── subscriptions ─────────────────────────────────────────────
export const subscriptions = pgTable("subscriptions", {
  id: uuid("id").primaryKey().defaultRandom(),
  driverId: uuid("driver_id")
    .notNull()
    .references(() => drivers.id),
  tier: varchar("tier", { length: 20 }).notNull(),
  status: varchar("status", { length: 20 }).notNull().default("active"),
  amount: decimal("amount", { precision: 10, scale: 2 }).notNull(),
  startedAt: timestamp("started_at", { withTimezone: true }).notNull(),
  expiresAt: timestamp("expires_at", { withTimezone: true }).notNull(),
  paymentReference: varchar("payment_reference", { length: 255 }),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});
