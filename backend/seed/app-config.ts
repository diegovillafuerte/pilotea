/**
 * Seed the app_config singleton (B-050).
 *
 * app_config carries app-wide runtime flags. Today the only field is
 * `paywallEnabled` — the remote kill switch for premium gating. It seeds TRUE so
 * a fresh database has gating ON by default; an operator flips it to FALSE
 * (PATCH /v1/config/app) to run a "launch promo" where every premium surface is
 * unlocked for everyone with no app release.
 *
 * Idempotent: a single row keyed on `singleton = TRUE`, upserted via
 * ON CONFLICT (singleton) DO UPDATE.
 *
 * Usage: DATABASE_URL=postgres://... pnpm tsx seed/app-config.ts
 */

import postgres from "postgres";

/** The seeded app_config row. */
export interface AppConfigSeedRow {
  paywallEnabled: boolean;
}

/**
 * The canonical seed row. Exported so the test DB harness loads the exact same
 * value the shipped seed runner writes (paywall ON at launch).
 */
export function buildAppConfigRow(): AppConfigSeedRow {
  return { paywallEnabled: true };
}

async function seed(): Promise<void> {
  const url = process.env.DATABASE_URL;
  if (!url) throw new Error("DATABASE_URL is not set.");

  const sql = postgres(url, { max: 1 });
  const row = buildAppConfigRow();

  await sql`
    INSERT INTO app_config (singleton, paywall_enabled, updated_at)
    VALUES (TRUE, ${row.paywallEnabled}, NOW())
    ON CONFLICT (singleton)
    DO UPDATE SET
      paywall_enabled = EXCLUDED.paywall_enabled,
      updated_at = NOW()
  `;

  console.log(`Done. app_config seeded (paywallEnabled=${row.paywallEnabled}).`);
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
