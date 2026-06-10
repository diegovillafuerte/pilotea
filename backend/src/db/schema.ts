/**
 * Kompara backend — Drizzle schema v1.
 *
 * Greenfield thin backend. Tables ported/adapted from the legacy web app
 * (src/lib/db/schema.ts) where the logic is proven, but trimmed to the
 * server's actual responsibilities: consented aggregate sync, benchmark
 * delivery, parser-config hosting, and telemetry ingestion.
 */

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
// One row per driver account. Anonymous-first product: an account is only
// required for sync/benchmarks/premium, so most fields are nullable.
export const drivers = pgTable("drivers", {
  id: uuid("id").primaryKey().defaultRandom(),
  phone: varchar("phone", { length: 20 }).notNull().unique(),
  name: varchar("name", { length: 100 }),
  city: varchar("city", { length: 100 }),
  platforms: text("platforms").array(),
  tier: varchar("tier", { length: 20 }).notNull().default("free"),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
});

// ─── sessions ──────────────────────────────────────────────────
// Server-side session records. Only the SHA-256 hash of the token is stored.
export const sessions = pgTable(
  "sessions",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    driverId: uuid("driver_id")
      .notNull()
      .references(() => drivers.id, { onDelete: "cascade" }),
    tokenHash: varchar("token_hash", { length: 64 }).notNull(),
    expiresAt: timestamp("expires_at", { withTimezone: true }).notNull(),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [index("idx_sessions_token_hash").on(table.tokenHash)],
);

// ─── weekly_aggregates ─────────────────────────────────────────
// One row per driver × platform × week. Holds the core earnings plus the
// 5 efficiency metrics. `source` distinguishes data captured live by the
// Android app from data imported via upload parsing.
export const weeklyAggregates = pgTable(
  "weekly_aggregates",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    driverId: uuid("driver_id")
      .notNull()
      .references(() => drivers.id, { onDelete: "cascade" }),
    platform: varchar("platform", { length: 20 }).notNull(),
    weekStart: date("week_start").notNull(),

    // Core earnings
    netEarnings: decimal("net_earnings", { precision: 10, scale: 2 }).notNull(),
    grossEarnings: decimal("gross_earnings", { precision: 10, scale: 2 }).notNull(),
    totalTrips: integer("total_trips").notNull(),

    // Raw inputs behind the efficiency metrics
    totalKm: decimal("total_km", { precision: 10, scale: 2 }),
    hoursOnline: decimal("hours_online", { precision: 10, scale: 2 }),

    // The 5 efficiency metrics (mirror population_stats metric_name keys)
    earningsPerTrip: decimal("earnings_per_trip", { precision: 10, scale: 2 }),
    earningsPerKm: decimal("earnings_per_km", { precision: 10, scale: 2 }),
    earningsPerHour: decimal("earnings_per_hour", { precision: 10, scale: 2 }),
    tripsPerHour: decimal("trips_per_hour", { precision: 10, scale: 2 }),
    platformCommissionPct: decimal("platform_commission_pct", { precision: 5, scale: 2 }),

    // captured = live by the Android capture engine; imported = upload parsing
    source: varchar("source", { length: 20 }).notNull().default("captured"),

    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    uniqueIndex("uq_weekly_aggregates_driver_platform_week").on(
      table.driverId,
      table.platform,
      table.weekStart,
    ),
    index("idx_weekly_aggregates_driver").on(table.driverId, table.weekStart),
  ],
);

// ─── population_stats ──────────────────────────────────────────
// Pre-computed percentile breakpoints per city × platform × metric × period.
// Seeded with synthetic data (is_synthetic = true) until real data accrues.
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
    isSynthetic: boolean("is_synthetic").notNull().default(false),
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

// ─── parser_configs ────────────────────────────────────────────
// Remote-hosted parser specs for the Android capture engine. A driver app
// fetches the active spec bundle for a given host package + version range.
export const parserConfigs = pgTable(
  "parser_configs",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    targetPackage: varchar("target_package", { length: 255 }).notNull(),
    versionRange: varchar("version_range", { length: 100 }).notNull(),
    specVersion: integer("spec_version").notNull(),
    spec: jsonb("spec").notNull(),
    active: boolean("active").notNull().default(true),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    uniqueIndex("uq_parser_configs_pkg_range_spec").on(
      table.targetPackage,
      table.versionRange,
      table.specVersion,
    ),
    index("idx_parser_configs_pkg_active").on(table.targetPackage, table.active),
  ],
);

// ─── telemetry_counters ────────────────────────────────────────
// Aggregate parser-health counters reported by the Android app. One row per
// host package × host version × spec version × day so we can spot regressions.
export const telemetryCounters = pgTable(
  "telemetry_counters",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    hostPackage: varchar("host_package", { length: 255 }).notNull(),
    hostVersion: varchar("host_version", { length: 100 }).notNull(),
    specVersion: integer("spec_version").notNull(),
    attempts: integer("attempts").notNull().default(0),
    successes: integer("successes").notNull().default(0),
    failures: integer("failures").notNull().default(0),
    day: date("day").notNull(),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    uniqueIndex("uq_telemetry_counters_pkg_version_spec_day").on(
      table.hostPackage,
      table.hostVersion,
      table.specVersion,
      table.day,
    ),
  ],
);
