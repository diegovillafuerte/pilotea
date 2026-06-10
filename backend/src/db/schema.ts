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
  // Anonymous-first: the install's device UUID is recorded so locally-captured
  // data can be merged into the account at first OTP verification. Nullable
  // because accounts created from a fresh phone (no prior anonymous use) won't
  // have one. Unique so a device maps to at most one account.
  anonymousDeviceId: uuid("anonymous_device_id").unique(),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
});

// ─── devices ───────────────────────────────────────────────────
// Anonymous-first install identities. A device row is created at first run so
// the reader works with zero signup (telemetry / parser-config / future local
// rollups key off it). When the driver later creates an account via OTP, the
// device's data is attached to the new driver row.
export const devices = pgTable("devices", {
  id: uuid("id").primaryKey().defaultRandom(),
  deviceId: uuid("device_id").notNull().unique(),
  // Set once the device is claimed by an account (OTP verify with deviceId).
  driverId: uuid("driver_id").references(() => drivers.id, { onDelete: "set null" }),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  lastSeenAt: timestamp("last_seen_at", { withTimezone: true }).notNull().defaultNow(),
});

// ─── otp_codes ─────────────────────────────────────────────────
// Short-lived WhatsApp OTP challenges. Only the SHA-256 hash of the 6-digit
// code is stored (never the plaintext). `attempts` caps brute force on a single
// code; the per-phone request rate limit is enforced in SQL by counting rows
// in the trailing window (no infra dependency). Expired/used rows are harmless
// to leave around; verification filters on expiresAt and consumedAt.
export const otpCodes = pgTable(
  "otp_codes",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    phone: varchar("phone", { length: 20 }).notNull(),
    codeHash: varchar("code_hash", { length: 64 }).notNull(),
    expiresAt: timestamp("expires_at", { withTimezone: true }).notNull(),
    attempts: integer("attempts").notNull().default(0),
    consumedAt: timestamp("consumed_at", { withTimezone: true }),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    // Rate-limit lookups and verification both query by phone, newest first.
    index("idx_otp_codes_phone_created").on(table.phone, table.createdAt),
  ],
);

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

// ─── imports ───────────────────────────────────────────────────
// One row per upload import attempt (Uber PDF, Uber/DiDi/InDrive screenshots).
// Ported from the legacy web app's `uploads` table. The original file(s) live in
// object storage (key scheme `{driverId}/{importId}.{ext}`); the parsed Claude
// Vision payload is kept for audit/debugging. On a successful parse the row links
// to the upserted weekly_aggregates row via `weeklyAggregateId`.
export const imports = pgTable(
  "imports",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    driverId: uuid("driver_id")
      .notNull()
      .references(() => drivers.id, { onDelete: "cascade" }),
    platform: varchar("platform", { length: 20 }).notNull(),
    uploadType: varchar("upload_type", { length: 20 }).notNull(),
    // Comma-joined storage key(s): single file → "{driverId}/{importId}.{ext}";
    // multi-file (DiDi) → "{driverId}/{importId}_0.{ext},{driverId}/{importId}_1.{ext}".
    fileKey: text("file_key").notNull(),
    // pending → parsed (success) | failed (parse error). Mirrors the web flow,
    // collapsed to the states the backend actually transitions through.
    status: varchar("status", { length: 20 }).notNull().default("pending"),
    errorMessage: text("error_message"),
    // Full Claude Vision extraction (raw_extraction) for audit/debugging.
    parsedPayload: jsonb("parsed_payload"),
    weeklyAggregateId: uuid("weekly_aggregate_id").references(() => weeklyAggregates.id, {
      onDelete: "set null",
    }),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [index("idx_imports_driver_created").on(table.driverId, table.createdAt)],
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

// ─── fixture_reports ───────────────────────────────────────────
// Driver-submitted "Reportar tarjeta no leída" reports: a PII-scrubbed
// accessibility snapshot the on-device SnapshotScrubber failed to parse, sent
// with explicit per-report consent so it can feed the parser-spec corpus
// (B-028). Device-authed (anonymous deviceId), rate-limited per device, and
// capped at 50 KB. The `snapshot` jsonb holds ONLY the scrubbed structural
// shape (packageName, versionCode, masked node texts/bounds/viewIds) — no raw
// passenger names, plates, phones, or addresses survive the on-device scrub.
export const fixtureReports = pgTable(
  "fixture_reports",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    // The reporting device (anonymous-first). References devices.device_id via
    // app logic (validated at the route) rather than an FK, so a report from an
    // un-registered device is rejected with a 401 instead of an FK error.
    deviceId: uuid("device_id").notNull(),
    hostPackage: varchar("host_package", { length: 255 }).notNull(),
    hostVersion: varchar("host_version", { length: 100 }),
    specVersion: integer("spec_version"),
    // Why the on-device parser produced no card (NO_SPEC / NOT_AN_OFFER / other).
    reason: varchar("reason", { length: 40 }),
    // The scrubbed ParserSnapshot shape. Structural only; PII already masked.
    snapshot: jsonb("snapshot").notNull(),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    // Rate-limit lookups count a device's reports in the trailing window.
    index("idx_fixture_reports_device_created").on(table.deviceId, table.createdAt),
  ],
);

// ─── subscriptions ─────────────────────────────────────────────
// Play Billing subscription state per driver (B-049). One row per purchase
// token (a token is the stable id of a single subscription purchase across its
// renewals). The client posts the token + its observed state after a
// purchase/restore (POST /v1/subscriptions/sync); Real-time Developer
// Notifications (POST /v1/rtdn) drive subsequent status transitions.
//
// `status` mirrors the lifecycle the app gates on:
//   active   — purchased/renewing (incl. trial); entitled
//   canceled — auto-renew off but still within the paid period; entitled
//   grace    — renewal payment failing, grace window; entitled
//   hold     — past grace (account hold); NOT entitled
//   expired  — ended/revoked; NOT entitled
//
// SECURITY: server-side verification against the Google Play Developer API and
// signed RTDN/Pub-Sub validation are NOT yet implemented (StubVerifier trusts
// the client). Tracked as a LAUNCH BLOCKER in techdebt.
export const subscriptions = pgTable(
  "subscriptions",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    driverId: uuid("driver_id")
      .notNull()
      .references(() => drivers.id, { onDelete: "cascade" }),
    // The Play purchase token — the unique, stable handle for a subscription.
    purchaseToken: text("purchase_token").notNull().unique(),
    productId: varchar("product_id", { length: 100 }).notNull(),
    status: varchar("status", { length: 20 }).notNull().default("active"),
    trial: boolean("trial").notNull().default(false),
    expiresAt: timestamp("expires_at", { withTimezone: true }),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [index("idx_subscriptions_driver").on(table.driverId)],
);

// ─── fiscal_config ─────────────────────────────────────────────
// Remote-config for the IMSS threshold tracker (B-051). The 2025 Mexican
// platform-work reform ties IMSS social-security coverage to earning ≥ 1 monthly
// minimum wage per platform per calendar month. Both the daily minimum wage and
// the derived monthly threshold are indexed yearly (CONASAMI sets the minimum
// wage each December for the following year), so the Android app reads them from
// here (GET /v1/config/fiscal) rather than baking them into a release — a new
// year's values are an admin PATCH, not an app update.
//
// One row per fiscal `year`. The app fetches the latest (highest year) and falls
// back to bundled defaults when offline/empty, so it never blocks the UI.
export const fiscalConfig = pgTable("fiscal_config", {
  id: uuid("id").primaryKey().defaultRandom(),
  // The fiscal year these values apply to (e.g. 2026). Unique so a year is
  // upserted, never duplicated.
  year: integer("year").notNull().unique(),
  // General-zone daily minimum wage in MXN (CONASAMI "salario mínimo general").
  minimumWageDailyMxn: decimal("minimum_wage_daily_mxn", { precision: 10, scale: 2 }).notNull(),
  // The IMSS coverage threshold = one monthly minimum wage in MXN. Stored
  // explicitly (rather than derived) because the reform's reporting figure
  // (~$8,364) is what counsel/IMSS publish, and the daily→monthly factor can
  // shift; keeping it explicit lets an admin set the authoritative number.
  imssMonthlyThresholdMxn: decimal("imss_monthly_threshold_mxn", {
    precision: 10,
    scale: 2,
  }).notNull(),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
});
