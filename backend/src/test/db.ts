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
 * Build a fresh migrated test DB. Pass `seed: true` to also load the synthetic
 * population_stats. Each call is an isolated in-memory database.
 */
export async function makeTestDb(opts: { seed?: boolean } = {}): Promise<TestDb> {
  const client = new PGlite();
  await client.waitReady;
  await applyMigrations(client);

  const db = drizzle(client, { schema }) as TestDb;
  if (opts.seed) {
    await seedPopulationStats(db);
  }
  return db;
}
