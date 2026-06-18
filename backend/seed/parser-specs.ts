/**
 * Seed `parser_configs` with the initial OTA bundle: the Uber MX (B-029), DiDi MX (B-030) and
 * inDrive MX (B-035) parser specs. These mirror the JSON bundled in the Android `:parsers` module
 * exactly — vendored here under `seed/specs/` so the served bundle and the on-device fallback start
 * identical.
 *
 * Idempotent: upserts on the (target_package, version_range, spec_version) unique index.
 *
 * Usage: DATABASE_URL=postgres://... pnpm tsx seed/parser-specs.ts
 */

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import postgres from "postgres";

const here = dirname(fileURLToPath(import.meta.url));
const specsDir = join(here, "specs");

interface VersionRange {
  min?: number | null;
  max?: number | null;
}

export interface SeedSpec {
  targetPackage: string;
  versionRange: string;
  specVersion: number;
  spec: Record<string, unknown>;
}

/** Render a ParserSpec's versionCodeRange to the `version_range` column string, e.g. "1-" / "1-100". */
function renderRange(range: VersionRange | undefined): string {
  const min = range?.min ?? "";
  const max = range?.max ?? "";
  return `${min}-${max}`;
}

/** Read a vendored spec JSON file and shape it into a parser_configs row. */
function loadSpec(file: string): SeedSpec {
  const raw = JSON.parse(readFileSync(join(specsDir, file), "utf8")) as Record<string, unknown>;
  return {
    targetPackage: raw.targetPackage as string,
    versionRange: renderRange(raw.versionCodeRange as VersionRange | undefined),
    specVersion: (raw.specVersion as number) ?? 1,
    spec: raw,
  };
}

/** The launch-day specs that make up the initial signed bundle. */
export function buildParserSpecRows(): SeedSpec[] {
  return [loadSpec("uber-driver.json"), loadSpec("didi-mx.json"), loadSpec("indrive-mx.json")];
}

async function main(): Promise<void> {
  const url = process.env.DATABASE_URL;
  if (!url) throw new Error("DATABASE_URL is not set");

  const sql = postgres(url, { max: 1 });
  const rows = buildParserSpecRows();
  for (const r of rows) {
    await sql`
      INSERT INTO parser_configs (target_package, version_range, spec_version, spec, active)
      VALUES (${r.targetPackage}, ${r.versionRange}, ${r.specVersion}, ${sql.json(r.spec as never)}, true)
      ON CONFLICT (target_package, version_range, spec_version)
      DO UPDATE SET spec = EXCLUDED.spec, active = true, updated_at = now()
    `;
  }
  await sql.end();
  console.log(`Seeded ${rows.length} parser specs into parser_configs.`);
}

// Run only when executed directly (not when imported by the test).
if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((err) => {
    console.error(err);
    process.exit(1);
  });
}
