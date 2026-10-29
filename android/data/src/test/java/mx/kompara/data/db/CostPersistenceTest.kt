package mx.kompara.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import mx.kompara.data.accounting.*
import mx.kompara.data.settings.CarCostRepository
import mx.kompara.data.settings.CostProfileRepository
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CostPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `v4 migration validates schema preserves fixed evidence and marks unknown history`() = runTest {
        val name = "cost-v4"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { sqlite ->
            val schema = JSONObject(File(System.getProperty("kompara.schemas"), "mx.kompara.data.db.KomparaDatabase/4.json").readText()).getJSONObject("database")
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            sqlite.execSQL("INSERT INTO cost_profile VALUES (1, 1790000000000, 2.0, 0.8, 50.0, 200.0, 12.0, 24.5, 6)")
            sqlite.execSQL("INSERT INTO daily_aggregates (platform,day,source,netEarningsMxn,grossEarningsMxn,totalTrips,totalKm,hoursOnline,computedAt) VALUES ('UBER','2026-09-01','CAPTURED',80,100,1,10,1,1790000000000)")
            sqlite.version = 4
        }
        val db = Room.databaseBuilder(context, KomparaDatabase::class.java, name).addMigrations(*KomparaMigrations.ALL).build()
        try {
            val old = db.costProfileDao().get()!!
            assertEquals(50.0, old.insurancePerDayMxn, 0.0)
            assertEquals(200.0, old.rentPerDayMxn, 0.0)
            assertTrue(db.carCostDao().versions().isEmpty())
            val version = db.costProfileDao().versions().single().toDomain()
            assertEquals(1790000000000, version.effectiveFrom)
            assertEquals(CostConfirmation.LEGACY_CONFIRMED, version.confirmation)
            assertEquals(RunningCostMode.LEGACY, version.inputs.mode)
            assertEquals(2.8, version.inputs.costPerKm!!.toDouble(), 0.0)
            assertNull(CostSchedule(listOf(version)).runningAt(version.effectiveFrom - 1))
            val row = db.aggregateDao().capturedDay("2026-09-01").single()
            assertEquals(80.0, row.netEarningsMxn, 0.0)
            assertFalse(row.runningCostKnown)
            assertNull(row.contributionDecimal)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `typical confirmed gas electric direct and car removal survive database reopen`() = runTest {
        val name = "cost-roundtrip"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, KomparaDatabase::class.java, name).build()
        var db = open()
        try {
            var repo = CostProfileRepository(db.costProfileDao())
            val initial = repo.runningVersions().single()
            assertEquals(CostConfirmation.TYPICAL, initial.confirmation)
            assertNull(initial.confirmedAt)
            assertNull(repo.get()) // typical is not a personally saved legacy profile
            val at = initial.effectiveFrom + 1000
            repo.saveRunningCosts(RunningCostInputs.TYPICAL, at, at)
            val electric = RunningCostInputs(RunningCostMode.ELECTRIC, BigDecimal("0.87"),
                kwhPer100Km = BigDecimal("17.32"), pricePerKwh = BigDecimal("4.21"))
            repo.saveRunningCosts(electric, at + 1, at + 1)
            repo.saveRunningCosts(RunningCostInputs(RunningCostMode.DIRECT, directPerKm = BigDecimal.ZERO), at + 2, at + 2)
            val car = CarCostRepository(db.carCostDao())
            val day = LocalDate.parse("2026-10-19")
            car.save(BigDecimal("1500.13"), day, at)
            car.remove(day.plusDays(2), at + 2)
            db.close()
            db = open()
            repo = CostProfileRepository(db.costProfileDao())
            val versions = repo.runningVersions()
            assertEquals(4, versions.size)
            assertEquals(electric, versions[2].inputs)
            assertEquals(CostConfirmation.CONFIRMED, versions[1].confirmation)
            assertEquals(at, versions[1].confirmedAt)
            assertEquals(0, versions[3].inputs.costPerKm!!.signum())
            val schedule = CostSchedule(versions, CarCostRepository(db.carCostDao()).get())
            assertEquals(BigDecimal("1500.13"), schedule.carAt(day.plusDays(1))!!.weeklyMxn)
            assertNull(schedule.carAt(day.plusDays(2))!!.weeklyMxn)
            assertEquals(versions[1], schedule.runningAt(at))
            assertEquals(versions[1].inputs.costPerKm!!.toDouble(), repo.get(at)!!.let { it.fuelPerKmMxn + it.maintenancePerKmMxn }, 1e-12)
            assertEquals(0.0, repo.get(at + 2)!!.fuelPerKmMxn, 0.0)
            assertNull(repo.get(initial.effectiveFrom))
            val evZero = electric.copy(kwhPer100Km = BigDecimal.ZERO, wearPerKm = BigDecimal.ZERO,
                fuelPricePerLitre = BigDecimal("24.50"), kmPerLitre = BigDecimal("12"))
            repo.saveRunningCosts(evZero, at + 3, at + 3)
            val zero = repo.get(at + 3)!!
            assertEquals(0.0, zero.fuelPerKmMxn, 0.0)
            assertEquals(0.0, zero.gasPricePerLitreMxn, 0.0)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `invalid inputs never write an optimistic profile`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, KomparaDatabase::class.java).build()
        try {
            val repo = CostProfileRepository(db.costProfileDao())
            try {
                repo.saveRunningCosts(RunningCostInputs(RunningCostMode.GAS), 1, 1)
                fail("invalid cost accepted")
            } catch (_: IllegalArgumentException) { }
            assertTrue(db.costProfileDao().versions().isEmpty())
            assertNull(db.costProfileDao().get())
        } finally { db.close() }
    }
    @Test fun `explicit efficiency correction retains prices original judgements and earlier unknown coverage`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, KomparaDatabase::class.java).build()
        try {
            val repo = CostProfileRepository(db.costProfileDao())
            val first = repo.saveRunningCosts(RunningCostInputs.TYPICAL, 1000, 1000)
            val priced = RunningCostInputs.TYPICAL.copy(fuelPricePerLitre = BigDecimal("30"))
            val second = repo.saveRunningCosts(priced, 2000, 2000)
            repo.saveRunningCosts(priced.copy(kmPerLitre = BigDecimal("15")), 3000, 3000)
            assertEquals(BigDecimal("12"), CostSchedule(repo.runningVersions()).accountingVersion(first)!!.inputs.kmPerLitre)
            repo.saveRunningCosts(priced.copy(kmPerLitre = BigDecimal("16")), 4000, 4000, correctEfficiency = true)
            val rows = repo.runningVersions()
            val schedule = CostSchedule(rows)
            assertNull(schedule.runningAt(999))
            assertEquals(BigDecimal("12"), rows.single { it.id == first }.inputs.kmPerLitre)
            assertEquals(BigDecimal("24.50"), schedule.accountingVersion(first)!!.inputs.fuelPricePerLitre)
            assertEquals(BigDecimal("30"), schedule.accountingVersion(second)!!.inputs.fuelPricePerLitre)
            assertEquals(BigDecimal("16"), schedule.accountingVersion(first)!!.inputs.kmPerLitre)
            val day = LocalDate.parse("2026-10-19")
            val record = IncomeRecord("trip", "UBER", Coverage(day, day), MoneySource.CAPTURED_ESTIMATE,
                BigDecimal("100"), BigDecimal("10"), 1, costVersionId = first, costVersionLocked = true)
            val money = PeriodAccounting.contribution(record, schedule, java.time.ZoneId.of("UTC"))
            assertEquals(76.6875, money.tripContribution!!.toDouble(), 1e-12)
            // Second explicit correction addresses both original and already corrected snapshots.
            val correctedId = schedule.accountingVersion(first)!!.id
            repo.saveRunningCosts(priced.copy(kmPerLitre = BigDecimal("20")), 5000, 5000, correctEfficiency = true)
            assertEquals(BigDecimal("20"), CostSchedule(repo.runningVersions()).accountingVersion(correctedId)!!.inputs.kmPerLitre)
        } finally { db.close() }
    }

    @Test fun `car removal keeps elapsed charges and cancels scheduled reactivation`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, KomparaDatabase::class.java).build()
        try {
            val repo = CarCostRepository(db.carCostDao())
            val from = LocalDate.parse("2026-09-28")
            repo.save(BigDecimal("1500"), from, 1)
            repo.save(BigDecimal("2000"), from.plusDays(30), 2)
            repo.remove(from.plusDays(21), 3)
            val schedule = CostSchedule(emptyList(), repo.get())
            assertEquals(BigDecimal("1500"), schedule.carAt(from.plusDays(20))!!.weeklyMxn)
            assertNull(schedule.carAt(from.plusDays(21))!!.weeklyMxn)
            assertNull(schedule.carAt(from.plusDays(40))!!.weeklyMxn)
            assertEquals(4, repo.get().size)
            repo.save(BigDecimal.ZERO, from.plusDays(41), 4)
            assertEquals(BigDecimal.ZERO, CostSchedule(emptyList(), repo.get()).carAt(from.plusDays(41))!!.weeklyMxn)
        } finally { db.close() }
    }

}
