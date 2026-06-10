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

    /** All migrations, in order — wired into the Room builder in DatabaseModule. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
