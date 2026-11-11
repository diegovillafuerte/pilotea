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

    /** v5: retain the singleton as migration evidence; never infer a car amount/start date. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `running_cost_versions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `effectiveFrom` INTEGER NOT NULL, `recordedAt` INTEGER NOT NULL,
                `mode` TEXT NOT NULL, `confirmation` TEXT NOT NULL, `confirmedAt` INTEGER,
                `wearPerKm` TEXT NOT NULL, `fuelPricePerLitre` TEXT, `kmPerLitre` TEXT,
                `kwhPer100Km` TEXT, `pricePerKwh` TEXT, `directPerKm` TEXT)""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `car_cost_versions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `effectiveFrom` TEXT NOT NULL, `weeklyMxn` TEXT, `recordedAt` INTEGER NOT NULL)""")
            for (table in listOf("daily_aggregates", "weekly_aggregates")) {
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `runningCostKnown` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `runningExpenseDecimal` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `contributionDecimal` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `costVersionIds` TEXT")
            }
            // Only the saved edit time is known. Earlier history has no attributable version.
            // LEGACY preserves the old resolved fuel rule; it cannot prove gas vs EV mode.
            db.execSQL("""INSERT INTO running_cost_versions
                (effectiveFrom, recordedAt, mode, confirmation, confirmedAt, wearPerKm,
                 fuelPricePerLitre, kmPerLitre, kwhPer100Km, pricePerKwh, directPerKm)
                SELECT updatedAt, updatedAt, 'LEGACY', 'LEGACY_CONFIRMED', updatedAt,
                    CAST(maintenancePerKmMxn AS TEXT), CAST(gasPricePerLitreMxn AS TEXT),
                    CAST(rendimientoKmPerLitre AS TEXT), NULL, NULL,
                    CAST(CASE WHEN fuelPerKmMxn > 0 THEN fuelPerKmMxn
                        WHEN rendimientoKmPerLitre > 0 THEN gasPricePerLitreMxn / rendimientoKmPerLitre
                        ELSE fuelPerKmMxn END AS TEXT)
                FROM cost_profile WHERE id = 1""")
        }
    }

    /** v6 preserves legacy zeros/verdicts verbatim, without inventing missing fields or limits. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `offers_v6` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `seenAt` INTEGER NOT NULL, `platform` TEXT NOT NULL, `fareMxn` REAL, `distanceKm` REAL, `durationMin` REAL, `pickupKm` REAL, `surge` INTEGER NOT NULL, `verdict` TEXT, `accepted` INTEGER NOT NULL, `outcome` TEXT NOT NULL, `resolvedAt` INTEGER, `shiftId` INTEGER, `sessionId` TEXT, `lastSeenAt` INTEGER NOT NULL, `tripKm` REAL, `pickupMin` REAL, `tripMin` REAL, `paymentBasis` TEXT NOT NULL, `inputProvenance` TEXT NOT NULL, `evaluation_evaluatedAt` INTEGER, `evaluation_unit` TEXT, `evaluation_goal` TEXT, `evaluation_minimum` TEXT, `evaluation_costVersionId` INTEGER, `evaluation_source` TEXT, `evaluation_paymentBasis` TEXT, `evaluation_contribution` TEXT, `evaluation_perKm` TEXT, `evaluation_perHour` TEXT, `evaluation_unavailableReason` TEXT)""")
            db.execSQL("""INSERT INTO offers_v6 (`id`, `seenAt`, `platform`, `fareMxn`, `distanceKm`, `durationMin`, `pickupKm`, `surge`, `verdict`, `accepted`, `outcome`, `resolvedAt`, `shiftId`, `sessionId`, `lastSeenAt`, `tripKm`, `pickupMin`, `tripMin`, `paymentBasis`, `inputProvenance`, `evaluation_evaluatedAt`, `evaluation_unit`, `evaluation_goal`, `evaluation_minimum`, `evaluation_costVersionId`, `evaluation_source`, `evaluation_paymentBasis`, `evaluation_contribution`, `evaluation_perKm`, `evaluation_perHour`, `evaluation_unavailableReason`) SELECT `id`, `seenAt`, `platform`, `fareMxn`, `distanceKm`, `durationMin`, `pickupKm`, `surge`, `verdict`, `accepted`, `outcome`, `resolvedAt`, `shiftId`, NULL, seenAt, NULL, NULL, NULL, 'UNCONFIRMED', 'LEGACY_UNSPECIFIED', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL FROM offers""")
            db.execSQL("DROP TABLE `offers`")
            db.execSQL("ALTER TABLE `offers_v6` RENAME TO `offers`")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `trips_v6` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `offerId` INTEGER, `shiftId` INTEGER, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `platform` TEXT NOT NULL, `grossMxn` REAL, `distanceKm` REAL, `durationMin` REAL, `estimated` INTEGER NOT NULL, `lifecycle` TEXT NOT NULL, `lastObservedAt` INTEGER NOT NULL, `paymentBasis` TEXT NOT NULL, `inputProvenance` TEXT NOT NULL, `confirmationEvidenceId` TEXT, `confirmationSource` TEXT, `confirmedAt` INTEGER, `cancellationSource` TEXT)""")
            db.execSQL("""INSERT INTO trips_v6 (`id`, `offerId`, `shiftId`, `startedAt`, `endedAt`, `platform`, `grossMxn`, `distanceKm`, `durationMin`, `estimated`, `lifecycle`, `lastObservedAt`, `paymentBasis`, `inputProvenance`, `confirmationEvidenceId`, `confirmationSource`, `confirmedAt`, `cancellationSource`) SELECT `id`, `offerId`, `shiftId`, `startedAt`, `endedAt`, `platform`, `grossMxn`, `distanceKm`, `durationMin`, `estimated`, CASE WHEN offerId IS NULL OR (grossMxn = 0 AND distanceKm = 0 AND durationMin = 0) THEN 'UNKNOWN' WHEN endedAt IS NULL THEN 'IN_PROGRESS' ELSE 'FINISHED' END, COALESCE(endedAt, startedAt), 'UNCONFIRMED', 'LEGACY_UNSPECIFIED', NULL, NULL, NULL, NULL FROM trips""")
            db.execSQL("DROP TABLE `trips`")
            db.execSQL("ALTER TABLE `trips_v6` RENAME TO `trips`")
            db.execSQL("CREATE UNIQUE INDEX index_offers_sessionId ON offers(sessionId)")
            db.execSQL("CREATE TABLE IF NOT EXISTS offer_presentations (sessionId TEXT NOT NULL PRIMARY KEY, outcome TEXT NOT NULL, recordedAt INTEGER NOT NULL, cause TEXT)")
        }
    }

    /** All migrations, in order — wired into the Room builder in DatabaseModule. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE running_cost_versions ADD COLUMN correctsVersionId INTEGER")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS import_drafts (id TEXT NOT NULL PRIMARY KEY, platform TEXT NOT NULL, uploadType TEXT NOT NULL, createdAt INTEGER NOT NULL, consentAt INTEGER, previewJson TEXT, correctionJson TEXT, stage TEXT NOT NULL, error TEXT, parentId TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS import_files (draftId TEXT NOT NULL, position INTEGER NOT NULL, name TEXT NOT NULL, mime TEXT NOT NULL, bytes BLOB NOT NULL, PRIMARY KEY(draftId, position))")
            db.execSQL("CREATE TABLE IF NOT EXISTS import_statements (id TEXT NOT NULL PRIMARY KEY, platform TEXT NOT NULL, start TEXT NOT NULL, endInclusive TEXT NOT NULL, payment TEXT, trips INTEGER, km TEXT, hours TEXT, source TEXT NOT NULL, uploadType TEXT NOT NULL, savedAt INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS import_selections (platform TEXT NOT NULL, start TEXT NOT NULL, statementId TEXT NOT NULL, fields TEXT NOT NULL, PRIMARY KEY(platform, start))")
        }
    }

    /** Existing imports retain their original source and receive no invented confirmation. */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE import_statements ADD COLUMN certificationId TEXT")
            db.execSQL("ALTER TABLE import_statements ADD COLUMN certificationCity TEXT")
            db.execSQL("ALTER TABLE import_statements ADD COLUMN confirmedFields TEXT")
        }
    }

    /**
     * v10 (v4 launch): every judged offer so far was judged NET, so its frozen evaluation records
     * NET; a row without an evaluation stays all-null (Room reads it as no evaluation). Adds the
     * driver-typed `monthly_incentives` table. Nothing existing is rewritten otherwise.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `offers` ADD COLUMN `evaluation_rateBasis` TEXT")
            db.execSQL("UPDATE `offers` SET `evaluation_rateBasis` = 'NET' WHERE `evaluation_evaluatedAt` IS NOT NULL OR `evaluation_unit` IS NOT NULL")
            db.execSQL("CREATE TABLE IF NOT EXISTS `monthly_incentives` (`platform` TEXT NOT NULL, `month` TEXT NOT NULL, `amountMxn` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`platform`, `month`))")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
}
