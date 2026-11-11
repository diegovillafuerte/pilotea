package mx.kompara.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.kompara.data.db.entity.OfferEvaluation
import mx.kompara.data.settings.MonthlyIncentiveRepository
import mx.kompara.data.settings.RateBasis
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.YearMonth

/** Runs the real 9 → 10 migration on a v9 database with rows (the instrumented twin cannot run here). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V10MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val at = 1_790_000_000_000L

    @Test fun `v9 offers read back as NET with their frozen judgement intact and incentives start empty`() = runTest {
        val name = "offers-v9"; context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { sqlite ->
            val schema = JSONObject(File(System.getProperty("kompara.schemas"), "mx.kompara.data.db.KomparaDatabase/9.json").readText()).getJSONObject("database")
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                sqlite.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                val indices = e.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
            sqlite.execSQL("INSERT INTO offers (id,seenAt,platform,fareMxn,distanceKm,durationMin,pickupKm,surge,verdict,accepted,outcome,resolvedAt,sessionId,lastSeenAt,tripKm,pickupMin,tripMin,paymentBasis,inputProvenance,evaluation_evaluatedAt,evaluation_unit,evaluation_goal,evaluation_minimum,evaluation_costVersionId,evaluation_source,evaluation_paymentBasis,evaluation_contribution,evaluation_perKm,evaluation_perHour,evaluation_unavailableReason) VALUES (1,$at,'UBER',148,12,24,2,0,'GREEN',1,'ACCEPTED',${at + 1},'s1',$at,10,4,20,'DRIVER_PAYMENT','CAPTURED',$at,'IPK','8.00','6.00',3,'OCR','DRIVER_PAYMENT','113.9','9.49','284.75',NULL)")
            sqlite.execSQL("INSERT INTO offers (id,seenAt,platform,fareMxn,distanceKm,durationMin,surge,verdict,accepted,outcome,lastSeenAt,paymentBasis,inputProvenance) VALUES (2,$at,'DIDI',0,0,0,0,'RED',0,'EXPIRED',$at,'UNCONFIRMED','LEGACY_UNSPECIFIED')")
            sqlite.execSQL("INSERT INTO trips (id,offerId,startedAt,platform,grossMxn,distanceKm,durationMin,estimated,lifecycle,lastObservedAt,paymentBasis,inputProvenance) VALUES (1,1,${at + 1},'UBER',148,12,24,1,'IN_PROGRESS',${at + 1},'DRIVER_PAYMENT','CAPTURED')")
            sqlite.version = 9
        }
        fun open() = Room.databaseBuilder(context, KomparaDatabase::class.java, name).addMigrations(*KomparaMigrations.ALL).build()
        var db = open()
        try {
            val judged = db.offerDao().findById(1)!!
            assertEquals(OfferEvaluation(at, "IPK", "8.00", "6.00", 3, "OCR", "DRIVER_PAYMENT", "113.9", "9.49", "284.75", null, "NET"), judged.evaluation)
            assertEquals(RateBasis.NET, RateBasis.fromName(judged.evaluation!!.rateBasis))
            assertEquals("GREEN", judged.verdict); assertEquals("ACCEPTED", judged.outcome); assertEquals("s1", judged.sessionId)
            assertEquals(148.0, judged.fareMxn!!, 0.0); assertEquals(12.0, judged.distanceKm!!, 0.0); assertEquals(24.0, judged.durationMin!!, 0.0)
            assertEquals(10.0, judged.tripKm!!, 0.0); assertEquals(20.0, judged.tripMin!!, 0.0)
            val legacy = db.offerDao().findById(2)!!
            assertNull(legacy.evaluation); assertEquals("RED", legacy.verdict); assertEquals("LEGACY_UNSPECIFIED", legacy.inputProvenance)
            assertEquals(148.0, db.tripDao().findById(1)!!.grossMxn!!, 0.0)

            val incentives = MonthlyIncentiveRepository(db.monthlyIncentiveDao())
            val october = YearMonth.of(2026, 10)
            assertTrue(incentives.observe(october).first().isEmpty())
            incentives.set("uber", october, 1800, at); incentives.set("DIDI", october, 0, at)
            incentives.set("UBER", YearMonth.of(2026, 11), 500, at)
            incentives.set("UBER", october, 1900, at + 1) // Guardar again replaces
            db.close(); db = open()
            val reopened = MonthlyIncentiveRepository(db.monthlyIncentiveDao())
            assertEquals(mapOf("DIDI" to 0, "UBER" to 1900), reopened.observe(october).first())
            reopened.remove("DIDI", october)
            assertEquals(mapOf("UBER" to 1900), reopened.observe(october).first())
            assertEquals(mapOf("UBER" to 500), reopened.observe(YearMonth.of(2026, 11)).first())
            db.localDataDao().eraseHistory()
            assertTrue(reopened.observe(october).first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `a GROSS judgement survives a reopen`() = runTest {
        val name = "offers-gross"; context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, KomparaDatabase::class.java, name).build()
        var db = open()
        try {
            val evaluation = OfferEvaluation(at, "IPK", "11", "9", 1, "OCR", "DRIVER_PAYMENT", "113.9", "9.49", "284.75", null, RateBasis.GROSS.name)
            val id = db.offerDao().insert(mx.kompara.data.db.entity.OfferEntity(seenAt = at, platform = "UBER", fareMxn = 148.0,
                distanceKm = 12.0, durationMin = 24.0, sessionId = "g", verdict = "GREEN", evaluation = evaluation))
            db.close(); db = open()
            assertEquals(evaluation, db.offerDao().findById(id)!!.evaluation)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
