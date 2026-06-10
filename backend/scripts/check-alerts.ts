#!/usr/bin/env tsx
/**
 * Parser-breakage alert checker (B-034) — cron entry point.
 *
 * Connects to the production database (DATABASE_URL), runs the same
 * computeAlerts() the GET /v1/telemetry/alerts endpoint uses, prints a short
 * report, and exits NON-ZERO when any (host_package, host_version) pair is
 * flagged — so a cron wrapper can fire an email/Telegram on a non-zero exit.
 *
 * Usage (cron):
 *   tsx backend/scripts/check-alerts.ts
 *   # exit 0 = healthy, exit 1 = at least one host/version breakage flagged
 *
 * TODO(techdebt TD-B034): wire email/Telegram delivery. For now this prints to
 * stdout and signals via exit code; the cron job's own mailer handles delivery.
 */

import { getDb } from "../src/db/client.js";
import { computeAlerts, ALERT_WINDOW_HOURS } from "../src/telemetry/alerts.js";

async function main(): Promise<number> {
  const db = getDb();
  const stats = await computeAlerts(db);
  const flagged = stats.filter((s) => s.flagged);

  const fmtPct = (r: number) => `${(r * 100).toFixed(1)}%`;

  console.log(`[check-alerts] window=${ALERT_WINDOW_HOURS}h pairs=${stats.length}`);
  for (const s of stats) {
    const marker = s.flagged ? "ALERT" : "ok   ";
    console.log(
      `[${marker}] ${s.hostPackage}@${s.hostVersion} ` +
        `attempts=${s.attempts} failures=${s.failures} rate=${fmtPct(s.failureRate)}`,
    );
  }

  if (flagged.length > 0) {
    console.error(
      `[check-alerts] ${flagged.length} breakage(s) flagged — investigate parser specs.`,
    );
    return 1;
  }

  console.log("[check-alerts] no breakages flagged.");
  return 0;
}

main()
  .then((code) => process.exit(code))
  .catch((err) => {
    console.error("[check-alerts] failed:", err);
    process.exit(2);
  });
