package mx.kompara.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import mx.kompara.data.accounting.*
import mx.kompara.data.db.entity.*
import mx.kompara.data.rollup.capturedIncomeRecords
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfferPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("America/Mexico_City")
    private val at = Instant.parse("2026-10-20T05:59:00Z").toEpochMilli() // local 19th, before midnight
    private val evaluation = OfferEvaluation(at, "IPK", "8.00", "6.00", 1, "OCR", "DRIVER_PAYMENT",
        "114.000000000000000000", "9.50", null, null)

    private fun offer(session: String, fare: Double? = 148.0) = OfferEntity(seenAt = at, platform = "UBER",
        fareMxn = fare, distanceKm = 12.0, durationMin = null, pickupKm = 0.0, tripKm = 12.0,
        sessionId = session, verdict = "GREEN", evaluation = evaluation)

    @Test fun `v5 migration validates nullable schema and keeps ambiguous zeros and verdict verbatim`() = runTest {
        val name = "offer-v5"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { sqlite ->
            val schema = JSONObject(File(System.getProperty("kompara.schemas"), "mx.kompara.data.db.KomparaDatabase/5.json").readText()).getJSONObject("database")
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            sqlite.execSQL("INSERT INTO offers (id,seenAt,platform,fareMxn,distanceKm,durationMin,surge,accepted,outcome,verdict) VALUES (1,$at,'UBER',0,0,0,0,0,'EXPIRED','RED')")
            sqlite.execSQL("INSERT INTO trips (id,startedAt,platform,grossMxn,distanceKm,durationMin,estimated) VALUES (1,$at,'UBER',0,0,0,1)")
            sqlite.version = 5
        }
        val db = Room.databaseBuilder(context, KomparaDatabase::class.java, name).addMigrations(*KomparaMigrations.ALL).build()
        try {
            val old = db.offerDao().findById(1)!!
            assertEquals(0.0, old.fareMxn!!, 0.0)
            assertEquals("RED", old.verdict)
            assertEquals("LEGACY_UNSPECIFIED", old.inputProvenance)
            assertEquals("UNCONFIRMED", old.paymentBasis)
            assertNull(old.sessionId); assertNull(old.evaluation); assertNull(old.tripKm)
            val trip = db.tripDao().findById(1)!!
            assertEquals("UNKNOWN", trip.lifecycle)
            assertEquals(0.0, trip.grossMxn!!, 0.0)
            assertFalse(trip.hasItemConfirmation)
            assertTrue(capturedIncomeRecords(listOf(trip), listOf(old), at + 1, zone).isEmpty())
            val id = db.offerDao().insert(offer("nullable", null).copy(distanceKm = null))
            assertTrue(id > 1)
            assertNull(db.offerDao().findById(id)!!.fareMxn)
            assertNull(db.offerDao().findById(id)!!.distanceKm)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `raw null zero frozen evaluation and joinable presentation survive reopening`() = runTest {
        val name = "offer-reopen"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, KomparaDatabase::class.java, name).build()
        var db = open()
        try {
            val id = db.offerDao().insert(offer("capture-a", null))
            assertEquals(-1L, db.offerDao().insert(offer("capture-a", 999.0)))
            db.offerPresentationDao().record(OfferPresentationEntity("capture-a", "SUPPRESSED", at, "NAVIGATION"))
            db.close(); db = open()
            val saved = db.offerDao().findById(id)!!
            assertNull(saved.fareMxn); assertNull(saved.durationMin)
            assertEquals(0.0, saved.pickupKm!!, 0.0)
            assertEquals(evaluation, saved.evaluation)
            assertEquals("SUPPRESSED", db.offerPresentationDao().seenBetween(at, at + 1).single().outcome)
            assertEquals(id, db.offerDao().forSession("capture-a")!!.id)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `no room query counts sessions in their offer day and removes recovered presentation`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, KomparaDatabase::class.java).build()
        try {
            val dao = db.offerPresentationDao()
            for (session in listOf("suppressed", "unread", "unsupported", "shown", "tomorrow")) {
                db.offerDao().insert(offer(session).copy(seenAt = if (session == "tomorrow") at + 1000 else at))
                dao.record(OfferPresentationEntity(session, when (session) {
                    "suppressed", "tomorrow" -> "SUPPRESSED"
                    "shown" -> "FULL"
                    else -> "UNSUPPORTED"
                }, at + 2000, "NAVIGATION")) // write time must not move yesterday's offer into today
            }
            assertEquals(1, dao.observeSuppressedCount(at, at + 1).first())
            dao.record(OfferPresentationEntity("suppressed", "COMPACT", at + 3000))
            assertEquals(0, dao.observeSuppressedCount(at, at + 1).first())
            assertEquals(1, dao.observeSuppressedCount(at + 1000, at + 1001).first())
        } finally { db.close() }
    }

    @Test fun `acceptance is atomic and idempotent cancellation affects only its trip`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, KomparaDatabase::class.java).build()
        try {
            val id = db.offerDao().insert(offer("a"))
            val id2 = db.offerDao().insert(offer("b").copy(seenAt = at + 10))
            fun trip(offer: Long) = TripEntity(offerId = offer, startedAt = at + 1, platform = "UBER",
                grossMxn = 148.0, distanceKm = 12.0, durationMin = null, lifecycle = "ACCEPTED_ESTIMATE")
            val first = db.tripDao().accept(trip(id))
            assertEquals(first, db.tripDao().accept(trip(id)))
            db.tripDao().accept(trip(id2))
            assertEquals("ACCEPTED", db.offerDao().findById(id)!!.outcome)
            assertTrue(db.tripDao().cancel(first, at + 100))
            assertFalse(db.tripDao().cancel(first, at + 101))
            assertEquals("CANCELLED", db.offerDao().findById(id)!!.outcome)
            assertEquals("ACCEPTED", db.offerDao().findById(id2)!!.outcome)
            val cancelled = db.tripDao().findById(first)!!
            assertEquals(148.0, cancelled.grossMxn!!, 0.0)
            assertEquals("USER", cancelled.cancellationSource)
            val income = capturedIncomeRecords(db.tripDao().startedBetween(at, at + 1000), db.offerDao().seenBetween(at, at + 1000), at + 1000, zone)
            assertEquals(1, income.size)
            assertEquals(1, income.single().inProgress)
        } finally { db.close() }
    }

    @Test fun `midnight start bucket includes one estimate and observed finish only changes count`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, KomparaDatabase::class.java).build()
        try {
            val id = db.offerDao().insert(offer("midnight"))
            val tripId = db.tripDao().accept(TripEntity(offerId = id, startedAt = at + 1, platform = "UBER",
                grossMxn = 148.0, distanceKm = 12.0, durationMin = null))
            val rows = db.offerDao().seenBetween(at, at + 1)
            val open = db.tripDao().findById(tripId)!!
            val before = capturedIncomeRecords(listOf(open, open), rows, at + 600_000, zone).single()
            db.tripDao().update(open.copy(endedAt = at + 1_440_000, lifecycle = "FINISHED"))
            val finished = db.tripDao().findById(tripId)!!
            val after = capturedIncomeRecords(listOf(finished), rows, at + 2_000_000, zone).single()
            assertEquals("2026-10-19", after.coverage.start.toString())
            assertEquals(before.payment, after.payment)
            assertEquals(1, before.inProgress); assertEquals(0, after.inProgress)
            assertEquals(MoneyLifecycle.TAKEN_ESTIMATE, after.lifecycle)
            assertFalse(finished.hasItemConfirmation)
            assertFalse(finished.copy(estimated = false, confirmationSource = "WEEKLY_IMPORT", confirmedAt = at,
                confirmationEvidenceId = "weekly-pdf").hasItemConfirmation)
        } finally { db.close() }
    }

    @Test fun `only a payment attributed to a finished trip can confirm its amount`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, KomparaDatabase::class.java).build()
        try {
            val offerId = db.offerDao().insert(offer("item"))
            val id = db.tripDao().accept(TripEntity(offerId = offerId, startedAt = at, platform = "UBER",
                grossMxn = 148.0, distanceKm = 12.0, durationMin = null))
            val evidence = ItemizedPaymentEvidence(id, "receipt:trip-123", 153.0, at + 100_000)
            assertFalse(db.tripDao().confirmItemPayment(evidence))
            db.tripDao().update(db.tripDao().findById(id)!!.copy(lifecycle = "FINISHED", endedAt = at + 60_000, lastObservedAt = at + 60_000))
            assertFalse(db.tripDao().findById(id)!!.hasItemConfirmation)
            assertTrue(db.tripDao().confirmItemPayment(evidence))
            val confirmed = db.tripDao().findById(id)!!
            assertTrue(confirmed.hasItemConfirmation)
            assertEquals(153.0, confirmed.grossMxn!!, 0.0)
            assertEquals(148.0, db.offerDao().findById(offerId)!!.fareMxn!!, 0.0)
            val record = capturedIncomeRecords(listOf(confirmed), listOf(db.offerDao().findById(offerId)!!), at + 200_000, zone).single()
            assertEquals(MoneyLifecycle.ITEM_CONFIRMED, record.lifecycle)
            assertEquals("receipt:trip-123", record.fieldSources[MoneyField.PAYMENT])
        } finally { db.close() }
    }

    @Test fun `saved cost identity wins over a later effective replacement`() {
        val date = java.time.LocalDate.parse("2026-10-19")
        val first = RunningCostVersion(1, 0, 0, RunningCostInputs(RunningCostMode.DIRECT, directPerKm = BigDecimal("2")), CostConfirmation.CONFIRMED)
        val second = first.copy(id = 2, effectiveFrom = at - 1, recordedAt = at + 1000,
            inputs = RunningCostInputs(RunningCostMode.DIRECT, directPerKm = BigDecimal("5")))
        val record = IncomeRecord("trip:1", "UBER", Coverage(date, date), MoneySource.CAPTURED_ESTIMATE,
            BigDecimal("148"), BigDecimal("12"), 1, startedAt = at, costVersionId = 1, costVersionLocked = true)
        val contribution = PeriodAccounting.contribution(record, CostSchedule(listOf(first, second)), zone)
        assertEquals(BigDecimal("124"), contribution.tripContribution)
        assertEquals(1L, contribution.runningCostVersion!!.id)
    }

    @Test fun `frozen missing cost cannot acquire an invented historical version`() {
        val record = IncomeRecord("offer:1", "UBER", Coverage(java.time.LocalDate.parse("2026-10-19"), java.time.LocalDate.parse("2026-10-19")),
            MoneySource.CAPTURED_ESTIMATE, BigDecimal("148"), BigDecimal("12"), 1, startedAt = at,
            costVersionLocked = true)
        val late = CostSchedule(listOf(RunningCostVersion(1, 0, at + 1000, RunningCostInputs.TYPICAL, CostConfirmation.CONFIRMED)))
        assertNull(PeriodAccounting.contribution(record, late, zone).tripContribution)
    }
}
