package mx.kompara.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Instrumented migration test harness for [KomparaDatabase].
 *
 * Runs on a device/emulator (out of scope for CI unit tests). It loads the v1 schema exported to
 * `data/schemas/` and verifies the database opens — the seam where future `Migration` objects get
 * asserted as new versions land. Add `helper.runMigrationsAndValidate(...)` calls per migration.
 */
class KomparaDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KomparaDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrate_createsVersion1Schema() {
        helper.createDatabase(TEST_DB, 1).close()
    }

    /**
     * v1 → v2 (B-043): adds `weekly_aggregates.lastSyncedAt` and the `population_stats` cache table.
     * Validates the hand-written [KomparaMigrations.MIGRATION_1_2] produces the exported v2 schema.
     */
    @Test
    @Throws(IOException::class)
    fun migrate_1_to_2_addsSyncWatermarkAndBenchmarkCache() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 2, true, KomparaMigrations.MIGRATION_1_2)
    }

    /**
     * v2 → v3 (B-051): adds the `fiscal_config` cache table. Validates the hand-written
     * [KomparaMigrations.MIGRATION_2_3] produces the exported v3 schema.
     */
    @Test
    @Throws(IOException::class)
    fun migrate_2_to_3_addsFiscalConfigCache() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            KomparaMigrations.MIGRATION_1_2,
            KomparaMigrations.MIGRATION_2_3,
        )
    }

    /**
     * v3 → v4 (B-081 ledger data repair): validates [KomparaMigrations.MIGRATION_3_4] keeps the v4
     * schema (it is data-only) AND purges only the corrupt rows. Crucially it asserts the regression
     * guard from the adversarial review: a **legitimate recent zero-value bare trip** (real timestamp,
     * no offer, zero economics — real online time the OCR path recorded) MUST survive, while the
     * uptime-dated (pre-2001) trip/offer/shift and pre-2001 CAPTURED aggregates go and IMPORTED rows
     * stay.
     */
    @Test
    @Throws(IOException::class)
    fun migrate_3_to_4_purgesUptimeArtifactsButKeepsLegitBareTrips() {
        val uptime = 243_000_000L // ~Jan 1970, an uptimeMillis artifact (< UPTIME_ARTIFACT_CUTOFF_MS)
        val real = 1_750_000_000_000L // ~2025, a genuine wall-clock capture

        helper.createDatabase(TEST_DB, 3).apply {
            // A) phantom zero-value bare trip, UPTIME-dated → purged.
            execSQL(
                "INSERT INTO trips (offerId, shiftId, startedAt, endedAt, platform, grossMxn, " +
                    "distanceKm, durationMin, estimated) VALUES " +
                    "(NULL, NULL, $uptime, $uptime, 'UBER', 0.0, 0.0, 0.0, 1)",
            )
            // B) LEGITIMATE recent zero-value bare trip (real timestamp, no offer captured) → KEPT.
            //    Byte-identical to a phantom except its wall-clock startedAt; carries real online time.
            execSQL(
                "INSERT INTO trips (offerId, shiftId, startedAt, endedAt, platform, grossMxn, " +
                    "distanceKm, durationMin, estimated) VALUES " +
                    "(NULL, NULL, $real, ${real + 600_000L}, 'DIDI', 0.0, 0.0, 0.0, 1)",
            )
            // C) a real recent trip with economics → KEPT.
            execSQL(
                "INSERT INTO trips (offerId, shiftId, startedAt, endedAt, platform, grossMxn, " +
                    "distanceKm, durationMin, estimated) VALUES " +
                    "(1, 1, $real, ${real + 1_200_000L}, 'UBER', 90.0, 10.0, 20.0, 1)",
            )
            // D) uptime-dated trip with economics → purged (the timestamp clause, not a zero predicate).
            execSQL(
                "INSERT INTO trips (offerId, shiftId, startedAt, endedAt, platform, grossMxn, " +
                    "distanceKm, durationMin, estimated) VALUES " +
                    "(NULL, NULL, $uptime, $uptime, 'DIDI', 50.0, 5.0, 10.0, 1)",
            )
            // offers: an uptime one (purged) + a real one (kept).
            execSQL(
                "INSERT INTO offers (seenAt, platform, fareMxn, distanceKm, durationMin, surge, " +
                    "accepted, outcome) VALUES ($uptime, 'UBER', 50.0, 5.0, 10.0, 0, 0, 'EXPIRED')",
            )
            execSQL(
                "INSERT INTO offers (seenAt, platform, fareMxn, distanceKm, durationMin, surge, " +
                    "accepted, outcome) VALUES ($real, 'UBER', 90.0, 10.0, 20.0, 0, 1, 'ACCEPTED')",
            )
            // shifts: an uptime one (purged) + a real one (kept).
            execSQL(
                "INSERT INTO shifts (startedAt, endedAt, lastEventAt, tripCount, grossMxn, netMxn, " +
                    "distanceKm) VALUES ($uptime, $uptime, $uptime, 0, 0.0, 0.0, 0.0)",
            )
            execSQL(
                "INSERT INTO shifts (startedAt, endedAt, lastEventAt, tripCount, grossMxn, netMxn, " +
                    "distanceKm) VALUES ($real, ${real + 1_200_000L}, ${real + 1_200_000L}, 1, 90.0, 80.0, 10.0)",
            )
            // daily aggregates: pre-2001 CAPTURED (purged), pre-2001 IMPORTED (kept), real CAPTURED (kept).
            execSQL(
                "INSERT INTO daily_aggregates (platform, day, source, netEarningsMxn, grossEarningsMxn, " +
                    "totalTrips, totalKm, hoursOnline, computedAt) VALUES " +
                    "('UBER', '1970-01-03', 'CAPTURED', 0.0, 0.0, 1, 0.0, 0.0, $real)",
            )
            execSQL(
                "INSERT INTO daily_aggregates (platform, day, source, netEarningsMxn, grossEarningsMxn, " +
                    "totalTrips, totalKm, hoursOnline, computedAt) VALUES " +
                    "('UBER', '1970-01-03', 'IMPORTED', 100.0, 120.0, 3, 30.0, 2.0, $real)",
            )
            execSQL(
                "INSERT INTO daily_aggregates (platform, day, source, netEarningsMxn, grossEarningsMxn, " +
                    "totalTrips, totalKm, hoursOnline, computedAt) VALUES " +
                    "('UBER', '2025-06-15', 'CAPTURED', 80.0, 90.0, 1, 10.0, 0.5, $real)",
            )
            // weekly aggregates: pre-2001 CAPTURED (purged), pre-2001 IMPORTED (kept), real CAPTURED (kept).
            execSQL(
                "INSERT INTO weekly_aggregates (platform, weekStart, source, netEarningsMxn, " +
                    "grossEarningsMxn, totalTrips, totalKm, hoursOnline, computedAt) VALUES " +
                    "('UBER', '1969-12-29', 'CAPTURED', 0.0, 0.0, 1, 0.0, 0.0, $real)",
            )
            execSQL(
                "INSERT INTO weekly_aggregates (platform, weekStart, source, netEarningsMxn, " +
                    "grossEarningsMxn, totalTrips, totalKm, hoursOnline, computedAt) VALUES " +
                    "('UBER', '1969-12-29', 'IMPORTED', 500.0, 600.0, 12, 120.0, 9.0, $real)",
            )
            execSQL(
                "INSERT INTO weekly_aggregates (platform, weekStart, source, netEarningsMxn, " +
                    "grossEarningsMxn, totalTrips, totalKm, hoursOnline, computedAt) VALUES " +
                    "('UBER', '2025-06-09', 'CAPTURED', 80.0, 90.0, 1, 10.0, 0.5, $real)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, KomparaMigrations.MIGRATION_3_4)

        // Trips: the legit recent bare trip (B) and the real economics trip (C) survive; A & D go.
        assertEquals(2, count(db, "SELECT COUNT(*) FROM trips"))
        assertEquals(0, count(db, "SELECT COUNT(*) FROM trips WHERE startedAt < ${KomparaMigrations.UPTIME_ARTIFACT_CUTOFF_MS}"))
        assertEquals("legit recent zero-value bare trip survives", 1, count(db, "SELECT COUNT(*) FROM trips WHERE grossMxn = 0"))
        assertEquals(90.0, scalarDouble(db, "SELECT SUM(grossMxn) FROM trips"), 1e-9)
        // Uptime offer/shift gone; real ones kept.
        assertEquals(1, count(db, "SELECT COUNT(*) FROM offers"))
        assertEquals(1, count(db, "SELECT COUNT(*) FROM shifts"))
        // Pre-2001 CAPTURED buckets gone (daily + weekly); IMPORTED + real CAPTURED stay.
        assertEquals(0, count(db, "SELECT COUNT(*) FROM daily_aggregates WHERE day < '2001-01-01' AND source = 'CAPTURED'"))
        assertEquals(2, count(db, "SELECT COUNT(*) FROM daily_aggregates"))
        assertEquals(0, count(db, "SELECT COUNT(*) FROM weekly_aggregates WHERE weekStart < '2001-01-01' AND source = 'CAPTURED'"))
        assertEquals(2, count(db, "SELECT COUNT(*) FROM weekly_aggregates"))
        db.close()
    }

    @Test
    fun migrate_4_to_5_preservesLegacyCostsWithoutInventingCarHistory() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL("INSERT INTO cost_profile VALUES (1, 1790000000000, 2.0, 0.8, 50.0, 200.0, 12.0, 24.5, 6)")
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, KomparaMigrations.MIGRATION_4_5)
        assertEquals(1, count(db, "SELECT COUNT(*) FROM running_cost_versions WHERE mode = 'LEGACY' AND confirmation = 'LEGACY_CONFIRMED' AND effectiveFrom = 1790000000000"))
        assertEquals(0, count(db, "SELECT COUNT(*) FROM car_cost_versions"))
        assertEquals(250.0, scalarDouble(db, "SELECT insurancePerDayMxn + rentPerDayMxn FROM cost_profile"), 0.0)
        db.close()
    }

    @Test
    fun migrate_1_to_7_validatesCompleteUpgradeChain() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 8, true, *KomparaMigrations.ALL).close()
    }

    @Test
    fun migrate_5_to_6_preservesLegacyZeroAndVerdictWithoutInventingLimits() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL("INSERT INTO offers (id,seenAt,platform,fareMxn,distanceKm,durationMin,surge,accepted,outcome,verdict) VALUES (1,1790000000000,'UBER',0,0,0,0,0,'EXPIRED','RED')")
            execSQL("INSERT INTO trips (id,startedAt,platform,grossMxn,distanceKm,durationMin,estimated) VALUES (1,1790000000000,'UBER',0,0,0,1)")
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, KomparaMigrations.MIGRATION_5_6)
        assertEquals(1, count(db, "SELECT COUNT(*) FROM offers WHERE fareMxn = 0 AND verdict = 'RED' AND evaluation_goal IS NULL AND inputProvenance = 'LEGACY_UNSPECIFIED'"))
        assertEquals(1, count(db, "SELECT COUNT(*) FROM trips WHERE grossMxn = 0 AND lifecycle = 'UNKNOWN' AND confirmationEvidenceId IS NULL"))
        db.close()
    }

    @Test
    fun migrate_6_to_7_keepsOriginalCostsWithoutInventingCorrections() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL("INSERT INTO running_cost_versions (id,effectiveFrom,recordedAt,mode,confirmation,wearPerKm,kmPerLitre,fuelPricePerLitre) VALUES (1,1790000000000,1790000000000,'GAS','TYPICAL','0.80','12','24.50')")
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, KomparaMigrations.MIGRATION_6_7)
        assertEquals(1, count(db, "SELECT COUNT(*) FROM running_cost_versions WHERE id=1 AND correctsVersionId IS NULL AND kmPerLitre='12'"))
        db.close()
    }

    private fun count(db: SupportSQLiteDatabase, sql: String): Int =
        db.query(sql).use { it.moveToFirst(); it.getInt(0) }

    private fun scalarDouble(db: SupportSQLiteDatabase, sql: String): Double =
        db.query(sql).use { it.moveToFirst(); it.getDouble(0) }

    companion object {
        private const val TEST_DB = "kompara-migration-test"
    }
    @Test fun migrate7To8_preservesLegacyAndCreatesEmptyDraftTables() {
        helper.createDatabase(TEST_DB, 7).apply {
            execSQL("INSERT INTO weekly_aggregates (platform,weekStart,source,netEarningsMxn,grossEarningsMxn,totalTrips,totalKm,hoursOnline,computedAt) VALUES ('UBER','2026-09-14','IMPORTED',5000,5340,29,0,26,100)")
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 8, true, KomparaMigrations.MIGRATION_7_8)
        assertEquals(1, count(db, "SELECT COUNT(*) FROM weekly_aggregates WHERE source='IMPORTED' AND grossEarningsMxn=5340"))
        assertEquals(0, count(db, "SELECT COUNT(*) FROM import_statements"))
        assertEquals(0, count(db, "SELECT COUNT(*) FROM import_drafts"))
        db.close()
    }

}
