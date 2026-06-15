package mx.kompara.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Hand-written Room migrations for [KomparaDatabase].
 *
 * Each migration's SQL must produce a schema byte-identical to what Room generates for the target
 * version (the exported `data/schemas/<version>.json`), which the instrumented
 * [KomparaDatabaseMigrationTest] validates with `runMigrationsAndValidate`.
 */
object KomparaMigrations {

    /**
     * v1 → v2 (B-043 consented aggregate sync + benchmark cache):
     *  - adds the nullable `lastSyncedAt` watermark column to `weekly_aggregates` so the sync worker
     *    can select only dirty rows;
     *  - creates the `population_stats` benchmark cache table (the on-device twin of the backend's
     *    `population_stats`), keyed `(city, platform, metricName, period)`.
     *
     * Additive only — no existing data is rewritten or dropped, so this is a safe forward migration.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `weekly_aggregates` ADD COLUMN `lastSyncedAt` INTEGER")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `population_stats` (" +
                    "`city` TEXT NOT NULL, " +
                    "`platform` TEXT NOT NULL, " +
                    "`metricName` TEXT NOT NULL, " +
                    "`period` TEXT NOT NULL, " +
                    "`sampleSize` INTEGER NOT NULL, " +
                    "`p10` REAL NOT NULL, " +
                    "`p25` REAL NOT NULL, " +
                    "`p50` REAL NOT NULL, " +
                    "`p75` REAL NOT NULL, " +
                    "`p90` REAL NOT NULL, " +
                    "`mean` REAL NOT NULL, " +
                    "`isSynthetic` INTEGER NOT NULL, " +
                    "`fetchedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`city`, `platform`, `metricName`, `period`))",
            )
        }
    }

    /**
     * v2 → v3 (B-051 IMSS threshold tracker):
     *  - creates the `fiscal_config` cache table (the on-device twin of the backend's
     *    `fiscal_config`), keyed by `year`, holding the minimum-wage + IMSS-threshold values the
     *    Fiscal tab reads from remote config.
     *
     * Additive only — a brand-new table, nothing existing is rewritten or dropped — so this is a safe
     * forward migration.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `fiscal_config` (" +
                    "`year` INTEGER NOT NULL, " +
                    "`minimumWageDailyMxn` REAL NOT NULL, " +
                    "`imssMonthlyThresholdMxn` REAL NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "`fetchedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`year`))",
            )
        }
    }

    /**
     * v3 → v4 (B-081 ledger data repair — DATA-ONLY, no schema change):
     *
     * Cleans up rows an earlier build corrupted before the capture-clock and Uber-OCR-decimal fixes
     * landed. The code fixes (commit 46237f7) stop NEW corruption; this repairs the persisted history.
     *
     *  1. Drops any `trips`/`offers`/`shifts` row with an **uptime-artifact timestamp** — the node
     *     path stamped `SystemClock.uptimeMillis` (~243M ms ⇒ Jan 1970) instead of wall-clock. Any
     *     timestamp below [UPTIME_ARTIFACT_CUTOFF_MS] (2001-09-09, far above any uptime value and far
     *     below any real ≥2025 capture) is such an artifact; the boot epoch is unknown retroactively,
     *     so these can't be back-converted — they're dropped. This is the signature **every** observed
     *     corrupt row shares, including the 74 phantom zero-value trips the old node path wrote when it
     *     mis-classified Uber's (no-longer-node-readable) offer screens as trips. (An uptime-dated
     *     *open* shift would also over-count online hours in any bucket it overlaps, so removing them
     *     is a correctness fix, not just hygiene.)
     *
     *     **Why timestamp, not the zero-economics predicate** (DEVIATION from B-081 §1, per adversarial
     *     review): a legitimate recent "bare" trip (`estimated`, `offerId` null, zero economics — the
     *     OCR path still opens one when an offer frame is missed but the driver was genuinely on a
     *     trip) is byte-identical to a phantom EXCEPT for its real wall-clock timestamp, and it carries
     *     real online time / trip count. Deleting by the zero predicate would silently drop those; all
     *     real phantoms are uptime-dated, so the timestamp cutoff targets the corruption exactly.
     *  2. Drops any pre-2001 CAPTURED daily/weekly aggregate bucket that summarised those artifacts
     *     (ISO `yyyy-MM-dd` compares lexically). IMPORTED rows are never touched (B-045's contract).
     *
     * The current (trailing-window) CAPTURED aggregates are refreshed by the post-launch one-shot
     * [mx.kompara.metrics.rollup.RollupRecomputer] run KomparaApplication enqueues, plus the daily
     * periodic worker. Offer `id=3`'s first-frame OCR decimal-loss is left as-is: it is a single
     * declined offer (no trip, no ledger impact) and the parser fix prevents recurrence (B-081 §3).
     *
     * Idempotent (re-running deletes nothing more) and additive-safe (no schema change, only corrupt
     * rows removed), so it is a safe forward migration.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Uptime-artifact rows (~1970). This signature covers the 74 phantom trips too, and —
            // unlike the raw zero-economics predicate — never deletes a legitimate recent bare trip.
            db.execSQL("DELETE FROM `trips` WHERE `startedAt` < $UPTIME_ARTIFACT_CUTOFF_MS")
            db.execSQL("DELETE FROM `offers` WHERE `seenAt` < $UPTIME_ARTIFACT_CUTOFF_MS")
            db.execSQL("DELETE FROM `shifts` WHERE `startedAt` < $UPTIME_ARTIFACT_CUTOFF_MS")
            db.execSQL(
                "DELETE FROM `daily_aggregates` WHERE `day` < '2001-01-01' AND `source` = 'CAPTURED'",
            )
            db.execSQL(
                "DELETE FROM `weekly_aggregates` WHERE `weekStart` < '2001-01-01' " +
                    "AND `source` = 'CAPTURED'",
            )
        }
    }

    /**
     * Timestamps below this (epoch ms for 2001-09-09T01:46:40Z, i.e. 10^12) are `uptimeMillis`
     * artifacts from the pre-fix node path, not real captures (which are all ≥ 2025). Used by
     * [MIGRATION_3_4]; exposed for the migration test.
     */
    const val UPTIME_ARTIFACT_CUTOFF_MS: Long = 1_000_000_000_000L

    /** All migrations, in order — wired into the Room builder in DatabaseModule. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
