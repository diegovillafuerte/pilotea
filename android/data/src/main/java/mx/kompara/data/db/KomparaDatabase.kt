package mx.kompara.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.data.db.dao.CostProfileDao
import mx.kompara.data.db.dao.FixtureReportDao
import mx.kompara.data.db.dao.OfferDao
import mx.kompara.data.db.dao.ShiftDao
import mx.kompara.data.db.dao.TelemetryCounterDao
import mx.kompara.data.db.dao.TripDao
import mx.kompara.data.db.entity.CostProfileEntity
import mx.kompara.data.db.entity.DailyAggregateEntity
import mx.kompara.data.db.entity.FixtureReportEntity
import mx.kompara.data.db.entity.OfferEntity
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
    ],
    version = 1,
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

    companion object {
        const val NAME = "kompara.db"
    }
}
