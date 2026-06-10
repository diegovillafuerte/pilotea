/**
 * Seed fiscal_config with the IMSS-threshold values for the current fiscal year
 * (B-051).
 *
 * Background — 2025 Mexican platform-work reform: a driver who earns ≥ 1 monthly
 * minimum wage per platform in a calendar month is entitled to IMSS
 * social-security coverage. The minimum wage is set yearly by CONASAMI (Comisión
 * Nacional de los Salarios Mínimos), published each December for the following
 * year, so these figures MUST be refreshed annually (operator PATCH, not an app
 * release).
 *
 * 2026 values (research-grade defaults — see techdebt; verify against the
 * official CONASAMI resolution before relying on them):
 *  - General-zone daily minimum wage: MXN $278.80/day.
 *  - IMSS monthly threshold: MXN $8,364.00 (the reform-reporting "1 salario
 *    mínimo mensual" figure; $278.80 × 30 = $8,364.00).
 *
 * Idempotent: uses ON CONFLICT (year) DO UPDATE to upsert.
 *
 * Usage: DATABASE_URL=postgres://... pnpm tsx seed/fiscal-config.ts
 */

import postgres from "postgres";

/** A single fiscal-config row to upsert. */
export interface FiscalConfigSeedRow {
  year: number;
  minimumWageDailyMxn: number;
  imssMonthlyThresholdMxn: number;
}

/**
 * The canonical seed rows. Exported so the test DB harness can load the exact
 * same values the shipped seed runner writes.
 */
export function buildFiscalConfigRows(): FiscalConfigSeedRow[] {
  return [
    {
      year: 2026,
      // CONASAMI general-zone daily minimum wage (research-grade default).
      minimumWageDailyMxn: 278.8,
      // One monthly minimum wage = IMSS coverage threshold per platform/month.
      imssMonthlyThresholdMxn: 8364.0,
    },
  ];
}

async function seed(): Promise<void> {
  const url = process.env.DATABASE_URL;
  if (!url) throw new Error("DATABASE_URL is not set.");

  const sql = postgres(url, { max: 1 });
  const rows = buildFiscalConfigRows();

  for (const r of rows) {
    await sql`
      INSERT INTO fiscal_config (year, minimum_wage_daily_mxn, imss_monthly_threshold_mxn, updated_at)
      VALUES (${r.year}, ${r.minimumWageDailyMxn}, ${r.imssMonthlyThresholdMxn}, NOW())
      ON CONFLICT (year)
      DO UPDATE SET
        minimum_wage_daily_mxn = EXCLUDED.minimum_wage_daily_mxn,
        imss_monthly_threshold_mxn = EXCLUDED.imss_monthly_threshold_mxn,
        updated_at = NOW()
    `;
  }

  console.log(`Done. ${rows.length} fiscal_config row(s) upserted.`);
  await sql.end();
}

// Only run when invoked directly (not when imported by tests).
const invokedDirectly =
  process.argv[1] !== undefined && import.meta.url === `file://${process.argv[1]}`;

if (invokedDirectly) {
  seed().catch((err) => {
    console.error("Seed failed:", err);
    process.exit(1);
  });
}
