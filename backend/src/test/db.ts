/**
 * In-memory test database helper.
 *
 * Spins up an @electric-sql/pglite Postgres instance (no external server),
 * applies every committed migration in journal order — including the raw
 * get_percentile() function migration — and optionally seeds population_stats.
 *
 * This is what lets DB tests run with zero infrastructure in CI.
 */

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { PGlite } from "@electric-sql/pglite";
import { drizzle } from "drizzle-orm/pglite";
import * as schema from "../db/schema.js";
import { buildSeedRows } from "../../seed/population-stats.js";
import { buildParserSpecRows } from "../../seed/parser-specs.js";
import { buildFiscalConfigRows } from "../../seed/fiscal-config.js";
import { buildAppConfigRow } from "../../seed/app-config.js";

const here = dirname(fileURLToPath(import.meta.url));
const migrationsDir = join(here, "..", "..", "migrations");

interface JournalEntry {
  tag: string;
}

interface Journal {
  entries: JournalEntry[];
}

export type TestDb = ReturnType<typeof drizzle<typeof schema>> & {
  $client: PGlite;
};

/**
 * Apply migrations by reading the Drizzle journal and executing each migration
 * SQL file in order. We split on Drizzle's "--> statement-breakpoint" markers so
 * multi-statement schema migrations apply cleanly, while the plpgsql function
 * migration (a single statement with dollar-quoted body) runs as one chunk.
 */
async function applyMigrations(client: PGlite): Promise<void> {
  const journal: Journal = JSON.parse(
    readFileSync(join(migrationsDir, "meta", "_journal.json"), "utf8"),
  );

  for (const entry of journal.entries) {
    const sqlText = readFileSync(join(migrationsDir, `${entry.tag}.sql`), "utf8");
    const statements = sqlText
      .split("--> statement-breakpoint")
      .map((s) => s.trim())
      .filter((s) => s.length > 0);

    for (const statement of statements) {
      await client.exec(statement);
    }
  }
}

/**
 * Insert the synthetic population_stats rows using the SAME deterministic
 * generators as the shipped seed runner, so tests exercise the real data.
 */
async function seedPopulationStats(db: TestDb): Promise<void> {
  const rows = buildSeedRows();
  await db.insert(schema.populationStats).values(
    rows.map((r) => ({
      city: r.city,
      platform: r.platform,
      metricName: r.metric,
      period: r.period,
      sampleSize: r.sampleSize,
      p10: String(r.breakpoints.p10),
      p25: String(r.breakpoints.p25),
      p50: String(r.breakpoints.p50),
      p75: String(r.breakpoints.p75),
      p90: String(r.breakpoints.p90),
      mean: String(r.breakpoints.mean),
      isSynthetic: r.isSynthetic,
    })),
  );
}

/**
 * Insert the launch-day parser specs (uber + didi) into parser_configs using the SAME rows the
 * shipped `seed/parser-specs.ts` builds, so the OTA-bundle tests exercise the real seed.
 */
async function seedParserSpecs(db: TestDb): Promise<void> {
  const rows = buildParserSpecRows();
  await db.insert(schema.parserConfigs).values(
    rows.map((r) => ({
      targetPackage: r.targetPackage,
      versionRange: r.versionRange,
      specVersion: r.specVersion,
      spec: r.spec,
      active: true,
    })),
  );
}

/**
 * Insert the current-year fiscal_config row(s) using the SAME generator as the shipped seed runner
 * (B-051), so the fiscal-config tests exercise the real seed values.
 */
async function seedFiscalConfig(db: TestDb): Promise<void> {
  const rows = buildFiscalConfigRows();
  await db.insert(schema.fiscalConfig).values(
    rows.map((r) => ({
      year: r.year,
      minimumWageDailyMxn: String(r.minimumWageDailyMxn),
      imssMonthlyThresholdMxn: String(r.imssMonthlyThresholdMxn),
    })),
  );
}

/**
 * Insert the app_config singleton using the SAME generator as the shipped seed runner (B-050), so the
 * app-config kill-switch tests exercise the real seeded default (paywall ON).
 */
async function seedAppConfig(db: TestDb): Promise<void> {
  const row = buildAppConfigRow();
  await db.insert(schema.appConfig).values({ singleton: true, paywallEnabled: row.paywallEnabled });
}

/**
 * Build a fresh migrated test DB. Pass `seed: true` to also load the synthetic population_stats,
 * `seedSpecs: true` to load the launch-day parser_configs bundle, `seedFiscal: true` to load the
 * fiscal_config IMSS-threshold rows (B-051), and `seedApp: true` to load the app_config kill-switch
 * singleton (B-050). Each call is an isolated in-memory database.
 */
export async function makeTestDb(
  opts: { seed?: boolean; seedSpecs?: boolean; seedFiscal?: boolean; seedApp?: boolean } = {},
): Promise<TestDb> {
  const client = new PGlite();
  await client.waitReady;
  await applyMigrations(client);

  const db = drizzle(client, { schema }) as TestDb;
  if (opts.seed) {
    await seedPopulationStats(db);
  }
  if (opts.seedSpecs) {
    await seedParserSpecs(db);
  }
  if (opts.seedFiscal) {
    await seedFiscalConfig(db);
  }
  if (opts.seedApp) {
    await seedAppConfig(db);
  }
  return db;
}
