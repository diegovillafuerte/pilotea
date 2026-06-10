/**
 * Breakage alerting over the telemetry_counters table.
 *
 * The product goal (B-034): know when Uber/DiDi ship a UI change that breaks our
 * parser BEFORE drivers complain. We never see screen content — only the
 * privacy-safe attempt/success/failure counters keyed by host package × host
 * version × spec version × day. A new host version that our spec can't read
 * shows up as a failure-rate spike for that (host_package, host_version) pair.
 *
 * computeAlerts aggregates the trailing window per (host_package, host_version),
 * computes failure rate = failures / attempts, and flags a pair when it crosses
 * the threshold with enough attempts to be meaningful (avoids alerting on a
 * single device's two failed reads).
 */

import { gte, sql } from "drizzle-orm";
import { telemetryCounters } from "../db/schema.js";
import type { Database } from "../db/client.js";

/** Default alerting window: trailing 48 hours. */
export const ALERT_WINDOW_HOURS = 48;
/** Flag a pair whose failure rate exceeds this fraction. */
export const ALERT_FAILURE_RATE = 0.2;
/** Minimum attempts in the window for a pair to be eligible (statistical floor). */
export const ALERT_MIN_ATTEMPTS = 50;

export interface HostStat {
  hostPackage: string;
  hostVersion: string;
  attempts: number;
  successes: number;
  failures: number;
  failureRate: number;
  flagged: boolean;
}

export interface AlertOptions {
  windowHours?: number;
  failureRate?: number;
  minAttempts?: number;
  /** Override "now" for deterministic tests. */
  now?: Date;
}

/**
 * Aggregate the trailing-window counters per (host_package, host_version) and
 * mark each pair flagged when failure rate > threshold AND attempts ≥ floor.
 * Pure over its DB input; the route and the cron script both call it.
 */
export async function computeAlerts(
  db: Database,
  opts: AlertOptions = {},
): Promise<HostStat[]> {
  const windowHours = opts.windowHours ?? ALERT_WINDOW_HOURS;
  const failureRate = opts.failureRate ?? ALERT_FAILURE_RATE;
  const minAttempts = opts.minAttempts ?? ALERT_MIN_ATTEMPTS;
  const now = opts.now ?? new Date();

  // telemetry_counters.day is a DATE; compare against the window's start date.
  const windowStart = new Date(now.getTime() - windowHours * 60 * 60 * 1000);
  const windowStartDay = windowStart.toISOString().slice(0, 10);

  const rows = await db
    .select({
      hostPackage: telemetryCounters.hostPackage,
      hostVersion: telemetryCounters.hostVersion,
      attempts: sql<number>`coalesce(sum(${telemetryCounters.attempts}), 0)::int`,
      successes: sql<number>`coalesce(sum(${telemetryCounters.successes}), 0)::int`,
      failures: sql<number>`coalesce(sum(${telemetryCounters.failures}), 0)::int`,
    })
    .from(telemetryCounters)
    .where(gte(telemetryCounters.day, windowStartDay))
    .groupBy(telemetryCounters.hostPackage, telemetryCounters.hostVersion);

  return rows
    .map((r) => {
      const attempts = r.attempts ?? 0;
      const failures = r.failures ?? 0;
      const rate = attempts > 0 ? failures / attempts : 0;
      const flagged = attempts >= minAttempts && rate > failureRate;
      return {
        hostPackage: r.hostPackage,
        hostVersion: r.hostVersion,
        attempts,
        successes: r.successes ?? 0,
        failures,
        failureRate: rate,
        flagged,
      };
    })
    .sort((a, b) => b.failureRate - a.failureRate);
}
