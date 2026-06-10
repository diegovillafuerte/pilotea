/**
 * Build the currently-active {@link SpecBundle} from `parser_configs` rows (B-033).
 *
 * Semantics:
 *  - `specs` = every `active = true` row's spec (the newest spec_version per package wins on the
 *    client, which sorts by spec_version; we include all active rows so version ranges work).
 *  - `killSwitches` = packages that have row(s) but NO active row → the package is *known* but
 *    deliberately serving no spec right now ("actualizando soporte…"). Flipping a package's rows to
 *    `active = false` is therefore the kill switch.
 *  - `bundleVersion` = the maximum `spec_version` across all rows (active or not). Spec versions are
 *    monotonic per the table's unique index + our authoring convention, so this only ever increases
 *    as specs are revised or a kill toggles — which is exactly the monotonic guarantee the client
 *    enforces (it rejects a bundle whose version is ≤ its last-known-good).
 */

import { desc } from "drizzle-orm";
import { parserConfigs } from "../db/schema.js";
import type { Database } from "../db/client.js";
import type { SpecBundle, ParserSpecJson } from "./bundle.js";

export async function buildActiveBundle(db: Database): Promise<SpecBundle> {
  const rows = await db
    .select()
    .from(parserConfigs)
    .orderBy(desc(parserConfigs.specVersion));

  const activeSpecs: ParserSpecJson[] = [];
  const packagesWithRows = new Set<string>();
  const packagesWithActive = new Set<string>();
  let maxSpecVersion = 0;

  for (const row of rows) {
    packagesWithRows.add(row.targetPackage);
    if (row.specVersion > maxSpecVersion) maxSpecVersion = row.specVersion;
    if (row.active) {
      packagesWithActive.add(row.targetPackage);
      activeSpecs.push(row.spec as ParserSpecJson);
    }
  }

  const killSwitches: Record<string, boolean> = {};
  for (const pkg of packagesWithRows) {
    if (!packagesWithActive.has(pkg)) killSwitches[pkg] = true;
  }

  return {
    // bundleVersion 0 would never beat a client's null-then-applied state; floor at 1 so an
    // all-empty table still produces a valid, applicable bundle.
    bundleVersion: Math.max(1, maxSpecVersion),
    generatedAt: new Date().toISOString(),
    specs: activeSpecs,
    killSwitches,
  };
}
