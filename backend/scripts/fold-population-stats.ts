/**
 * Cron-able fold-in runner (B-043).
 *
 * Recomputes benchmark percentiles from accrued real `weekly_aggregates` and
 * flips cells off their synthetic seed once they cross the sample threshold.
 * Intended to run on a schedule (e.g. nightly cron) against the production DB:
 *
 *   DATABASE_URL=postgres://... pnpm fold-stats
 *   # optional rolling window override (default 8 weeks):
 *   DATABASE_URL=postgres://... pnpm fold-stats --weeks 12
 *
 * It connects with a dedicated single-connection postgres pool, runs the fold,
 * prints a summary, and exits. Safe to run repeatedly: the fold upserts and is
 * idempotent for a fixed window of data.
 */

import postgres from "postgres";
import { drizzle } from "drizzle-orm/postgres-js";
import * as schema from "../src/db/schema.js";
import { foldPopulationStats, DEFAULT_WINDOW_WEEKS } from "../src/jobs/fold-population-stats.js";

function parseWeeks(argv: string[]): number {
  const idx = argv.indexOf("--weeks");
  if (idx === -1) return DEFAULT_WINDOW_WEEKS;
  const raw = argv[idx + 1];
  const n = Number(raw);
  if (!Number.isInteger(n) || n <= 0) {
    console.error(`--weeks must be a positive integer (got: ${raw ?? "<missing>"})`);
    process.exit(1);
  }
  return n;
}

async function main() {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error(
      "DATABASE_URL environment variable is required.\n" +
        "  DATABASE_URL=postgres://user:pass@host:5432/kompara pnpm fold-stats",
    );
    process.exit(1);
  }

  const windowWeeks = parseWeeks(process.argv.slice(2));
  const sql = postgres(databaseUrl, { max: 1 });
  const db = drizzle(sql, { schema });

  console.log(`Folding real aggregates into population_stats (window=${windowWeeks}w)...`);
  const result = await foldPopulationStats(db, { windowWeeks });

  console.log(
    `Done. windowStart=${result.windowStart} ` +
      `realCellsRecomputed=${result.foldedReal.length} ` +
      `syntheticCellsRemaining=${result.keptSynthetic}`,
  );
  for (const cell of result.foldedReal) {
    console.log(`  real: ${cell.city}/${cell.platform}/${cell.metric} (n=${cell.sampleSize})`);
  }

  await sql.end();
}

main().catch((err) => {
  console.error("Fold failed:", err);
  process.exit(1);
});
