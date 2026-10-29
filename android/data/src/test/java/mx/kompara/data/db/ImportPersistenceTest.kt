package mx.kompara.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import mx.kompara.data.imports.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Test fun `v7 to v8 keeps legacy imports unknown and preserves captured money`() = runTest {
        val name = "imports-v7"; context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { sqlite ->
            val schema = JSONObject(File(System.getProperty("kompara.schemas"), "mx.kompara.data.db.KomparaDatabase/7.json").readText()).getJSONObject("database")
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                sqlite.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                val indices = e.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
            for (source in listOf("IMPORTED", "CAPTURED")) sqlite.execSQL("INSERT INTO weekly_aggregates (platform,weekStart,source,netEarningsMxn,grossEarningsMxn,totalTrips,totalKm,hoursOnline,computedAt) VALUES ('UBER','2026-09-14','$source',5000,5340,29,0,26,100)")
            sqlite.version = 7
        }
        val db = Room.databaseBuilder(context, KomparaDatabase::class.java, name).addMigrations(*KomparaMigrations.ALL).build()
        try {
            assertEquals(2, db.aggregateDao().allWeekly().size)
            assertTrue(db.importDao().statements().isEmpty()); assertTrue(db.importDao().selections().isEmpty())
            assertNull(db.importDao().pending())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun `draft original parse correction selected files and field choices survive database reopen`() = runTest {
        val name = "imports-reopen"; context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, KomparaDatabase::class.java, name).build()
        var db = open()
        val draft = ImportDraftEntity("fingerprint", "uber", "pdf", 100, 101, "original", "correction", "REVIEW")
        val statement = ImportStatementEntity(draft.id, "UBER", "2026-09-14", "2026-09-20", "5340.125", null, null, "0", "USER_CORRECTED_IMPORT", "pdf", 102)
        try {
            db.importDao().putDraft(draft)
            db.importDao().putFiles(listOf(ImportFileEntity(draft.id, 0, "week.pdf", "application/pdf", byteArrayOf(1, 2))))
            db.importDao().putStatement(statement)
            db.importDao().putSelection(ImportSelectionEntity("UBER", statement.start, draft.id, "PAYMENT,HOURS"))
            db.close(); db = open()
            assertEquals(draft, db.importDao().pending())
            assertArrayEquals(byteArrayOf(1, 2), db.importDao().files(draft.id).single().bytes)
            assertEquals(statement, db.importDao().statements().single()); assertNull(statement.income().km)
            assertFalse(statement.income().tripsKnown); assertEquals(0, statement.income().minutes!!.signum())
            assertEquals(setOf(mx.kompara.data.accounting.MoneyField.PAYMENT, mx.kompara.data.accounting.MoneyField.HOURS), db.importDao().selections().single().domain().fields)
            db.localDataDao().eraseHistory()
            assertNull(db.importDao().pending()); assertTrue(db.importDao().files(draft.id).isEmpty())
            assertTrue(db.importDao().statements().isEmpty()); assertTrue(db.importDao().selections().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
