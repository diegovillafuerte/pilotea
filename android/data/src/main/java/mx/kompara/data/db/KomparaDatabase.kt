package mx.kompara.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.data.db.dao.CostProfileDao
import mx.kompara.data.db.dao.FiscalConfigDao
import mx.kompara.data.db.dao.FixtureReportDao
import mx.kompara.data.db.dao.OfferDao
import mx.kompara.data.db.dao.PopulationStatDao
import mx.kompara.data.db.dao.ShiftDao
import mx.kompara.data.db.dao.TelemetryCounterDao
import mx.kompara.data.db.dao.TripDao
import mx.kompara.data.db.entity.CostProfileEntity
import mx.kompara.data.db.entity.DailyAggregateEntity
import mx.kompara.data.db.entity.FiscalConfigEntity
import mx.kompara.data.db.entity.FixtureReportEntity
import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.db.entity.PopulationStatEntity
import mx.kompara.data.db.entity.ShiftEntity
import mx.kompara.data.db.entity.TelemetryCounterEntity
import mx.kompara.data.db.entity.TripEntity
import mx.kompara.data.db.entity.WeeklyAggregateEntity

/**
 * The on-device Room database — Kompara's primary store of offers, trips, shifts and the
 * driver cost profile.
 *
 * Schemas are exported to `data/schemas/` ([exportSchema] = true; the location is wired via
 * the `room.schemaLocation` KSP arg) so migrations can be diffed and a migration test harness
 * can validate them.
 */
@Database(
    entities = [
        OfferEntity::class,
        TripEntity::class,
        ShiftEntity::class,
        CostProfileEntity::class,
        TelemetryCounterEntity::class,
        FixtureReportEntity::class,
        WeeklyAggregateEntity::class,
        DailyAggregateEntity::class,
        PopulationStatEntity::class,
        FiscalConfigEntity::class,
    ],
    // v2 (B-043): adds weekly_aggregates.lastSyncedAt (consented sync watermark) + the
    // population_stats benchmark cache table. See KomparaMigrations.MIGRATION_1_2.
    // v3 (B-051): adds the fiscal_config cache table (IMSS-threshold remote config).
    // See KomparaMigrations.MIGRATION_2_3.
    // v4 (B-081): DATA-ONLY repair — purges phantom zero-value trips + uptime-dated (1970) rows the
    // pre-fix node path wrote. No schema change. See KomparaMigrations.MIGRATION_3_4.
    version = 4,
    exportSchema = true,
)
abstract class KomparaDatabase : RoomDatabase() {
    abstract fun offerDao(): OfferDao
    abstract fun tripDao(): TripDao
    abstract fun shiftDao(): ShiftDao
    abstract fun costProfileDao(): CostProfileDao
    abstract fun telemetryCounterDao(): TelemetryCounterDao
    abstract fun fixtureReportDao(): FixtureReportDao
    abstract fun aggregateDao(): AggregateDao
    abstract fun populationStatDao(): PopulationStatDao
    abstract fun fiscalConfigDao(): FiscalConfigDao

    companion object {
        const val NAME = "kompara.db"
    }
}
