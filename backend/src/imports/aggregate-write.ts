/**
 * weekly_aggregates fetch → merge → write (PR-A).
 *
 * The DB-touching companion to the pure {@link mergeAggregate}. Both write paths
 * — POST /v1/imports (imported) and POST /v1/aggregates (captured) — funnel
 * through here so the canonical field-level coalesce (import-strategy §0.5/§6.1)
 * is applied identically: read the existing (driver, platform, week) row, run the
 * pure merge, then UPDATE it (or INSERT when none exists).
 *
 * Concurrency: the SELECT → merge → UPDATE/INSERT runs inside a transaction
 * guarded by a per-key `pg_advisory_xact_lock`, so two simultaneous writes for
 * the SAME (driver, platform, week) are serialized — neither racing on INSERT
 * nor erasing each other's merged fields.
 */

import { and, eq, sql } from "drizzle-orm";
import { weeklyAggregates } from "../db/schema.js";
import type { Database } from "../db/client.js";
import { mergeAggregate, type AggregateColumns } from "./aggregate-merge.js";

type AggregateRow = typeof weeklyAggregates.$inferSelect;

/** Project a stored row onto the mergeable column set. */
function toColumns(row: AggregateRow): AggregateColumns {
  return {
    netEarnings: row.netEarnings,
    grossEarnings: row.grossEarnings,
    totalTrips: row.totalTrips,
    totalKm: row.totalKm,
    hoursOnline: row.hoursOnline,
    earningsPerTrip: row.earningsPerTrip,
    earningsPerKm: row.earningsPerKm,
    earningsPerHour: row.earningsPerHour,
    tripsPerHour: row.tripsPerHour,
    platformCommissionPct: row.platformCommissionPct,
    source: row.source,
  };
}

export type AggregateWriteResult =
  | { kind: "rejected" }
  | { kind: "written"; row: AggregateRow };

/**
 * Merge `incoming` onto the existing weekly aggregate for `key` and persist.
 *
 * Returns `rejected` when there is no existing row AND the incoming contribution
 * carries no core earnings (a partial/commission-only write that would otherwise
 * create a junk zero row — see {@link mergeAggregate}).
 */
export async function writeMergedAggregate(
  db: Database,
  key: { driverId: string; platform: string; weekStart: string },
  incoming: AggregateColumns,
): Promise<AggregateWriteResult> {
  const { driverId, platform, weekStart } = key;

  return db.transaction(async (tx) => {
    // Serialize concurrent writers for the SAME (driver, platform, week): a
    // transaction-scoped advisory lock (auto-released at COMMIT/ROLLBACK) makes
    // the SELECT → merge → UPDATE/INSERT atomic per key, so two writers can
    // neither race on INSERT nor erase each other's merged fields.
    await tx.execute(
      sql`select pg_advisory_xact_lock(hashtext(${`${driverId}|${platform}|${weekStart}`}))`,
    );

    const [existing] = await tx
      .select()
      .from(weeklyAggregates)
      .where(
        and(
          eq(weeklyAggregates.driverId, driverId),
          eq(weeklyAggregates.platform, platform),
          eq(weeklyAggregates.weekStart, weekStart),
        ),
      )
      .limit(1);

    const merge = mergeAggregate(incoming, existing ? toColumns(existing) : null);
    if (merge.kind === "rejected") return { kind: "rejected" as const };

    if (existing) {
      const [row] = await tx
        .update(weeklyAggregates)
        .set({ ...merge.values, updatedAt: sql`now()` })
        .where(eq(weeklyAggregates.id, existing.id))
        .returning();
      return { kind: "written" as const, row: row! };
    }

    const [row] = await tx
      .insert(weeklyAggregates)
      .values({ driverId, platform, weekStart, ...merge.values })
      .returning();
    return { kind: "written" as const, row: row! };
  });
}
