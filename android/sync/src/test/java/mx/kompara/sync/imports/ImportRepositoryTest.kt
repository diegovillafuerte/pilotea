package mx.kompara.sync.imports

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.data.db.entity.AggregateSource
import mx.kompara.data.db.entity.DailyAggregateEntity
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.sync.aggregate.SessionGate
import mx.kompara.sync.api.ApiClient
import mx.kompara.sync.api.ApiException
import mx.kompara.sync.api.ImportFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * [ImportRepository] tests (B-045): dry-run vs real upload, backend error mapping (Spanish strings),
 * local Room upsert on confirm, captured-protection, idempotent re-import, and the per-platform
 * file-count guard. Runs against a Ktor MockEngine + an in-memory [FakeImportDao].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun fixedClock(millis: Long): Clock =
        Clock.fixed(Instant.ofEpochMilli(millis), ZoneOffset.UTC)

    private fun pdf(name: String = "report.pdf") =
        ImportFile(fileName = name, mimeType = "application/pdf", bytes = byteArrayOf(1, 2, 3, 4))

    private fun png(name: String) =
        ImportFile(fileName = name, mimeType = "image/png", bytes = byteArrayOf(5, 6, 7, 8))

    /** Build an ApiClient over a MockEngine that records each request's URL. */
    private fun api(
        urls: MutableList<String> = mutableListOf(),
        handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): ApiClient {
        val engine = MockEngine { req ->
            urls += req.url.toString()
            handler(req)
        }
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
        }
        return ApiClient(http, "http://test.local", { "session-token" }, { "device-uuid" })
    }

    private fun MockRequestHandleScope.okJson(body: String): HttpResponseData = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun MockRequestHandleScope.errorJson(
        status: HttpStatusCode,
        message: String,
    ): HttpResponseData = respond(
        content = """{"error":${json.encodeToString(message)}}""",
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun repo(
        api: ApiClient,
        dao: AggregateDao = FakeImportDao(),
        signedIn: Boolean = true,
        clockMillis: Long = 1_000L,
    ) = ImportRepository(
        api = api,
        aggregateDao = dao,
        session = SessionGate { signedIn },
        clock = fixedClock(clockMillis),
    )

    private val previewBody = """
        {
          "import_id": null,
          "metrics": {
            "week_start": "2025-03-24",
            "net_earnings": 3850.5,
            "gross_earnings": 5200.0,
            "total_trips": 72,
            "hours_online": 45.5,
            "earnings_per_trip": 53.48,
            "earnings_per_hour": 84.63,
            "trips_per_hour": 1.58,
            "platform_commission_pct": 20.19
          },
          "data_completeness": 0.95,
          "dry_run": true
        }
    """.trimIndent()

    private val confirmBody = """
        {
          "import_id": "imp-123",
          "metrics": {
            "week_start": "2025-03-24",
            "net_earnings": 3850.5,
            "gross_earnings": 5200.0,
            "total_trips": 72,
            "hours_online": 45.5,
            "earnings_per_trip": 53.48,
            "earnings_per_hour": 84.63,
            "trips_per_hour": 1.58,
            "platform_commission_pct": 20.19
          },
          "data_completeness": 0.95,
          "dry_run": false
        }
    """.trimIndent()

    // ─── Dry-run preview ──────────────────────────────────────────────────────

    @Test
    fun `preview adds dry_run query param, maps metrics, and writes NOTHING locally`() = runTest {
        val urls = mutableListOf<String>()
        val dao = FakeImportDao()
        val repository = repo(api(urls) { okJson(previewBody) }, dao)

        val result = repository.preview("uber", "pdf", listOf(pdf()))

        assertTrue(result.dryRun)
        assertNull(result.importId)
        assertEquals("2025-03-24", result.metrics.weekStart)
        assertEquals(3850.5, result.metrics.netEarnings!!, 0.001)
        assertEquals(72, result.metrics.totalTrips)
        assertEquals(0.95, result.dataCompleteness, 0.001)
        // dry_run flag is on the wire.
        assertTrue(urls.single().contains("dry_run=true"))
        // Preview must never persist.
        assertTrue(dao.upserts.isEmpty())
    }

    // ─── Confirmed import + local upsert ──────────────────────────────────────

    @Test
    fun `confirm uploads without dry_run and upserts one IMPORTED weekly row`() = runTest {
        val urls = mutableListOf<String>()
        val dao = FakeImportDao()
        val repository = repo(api(urls) { okJson(confirmBody) }, dao, clockMillis = 7_000L)

        val result = repository.confirm("uber", "pdf", listOf(pdf()))

        assertEquals("imp-123", result.importId)
        assertTrue(!result.dryRun)
        assertTrue(!urls.single().contains("dry_run"))

        // Exactly one local row, source=IMPORTED, platform stored as the enum name UBER.
        assertEquals(1, dao.rows.size)
        val row = dao.rows.single()
        assertEquals(AggregateSource.IMPORTED.name, row.source)
        assertEquals("UBER", row.platform)
        assertEquals("2025-03-24", row.weekStart)
        assertEquals(3850.5, row.netEarningsMxn, 0.001)
        assertEquals(72, row.totalTrips)
        assertEquals(7_000L, row.computedAt)
        assertNull(row.acceptanceRate)
        assertNull(row.lastSyncedAt)
    }

    @Test
    fun `confirm maps didi platform name and lands the imported row`() = runTest {
        val dao = FakeImportDao()
        val didiBody = confirmBody.replace("imp-123", "imp-didi")
        val repository = repo(api { okJson(didiBody) }, dao)

        repository.confirm("didi", "screenshot", listOf(png("earnings.png"), png("tablero.png")))

        assertEquals("DIDI", dao.rows.single().platform)
    }

    // ─── Captured-protection ──────────────────────────────────────────────────

    @Test
    fun `confirm never overwrites a CAPTURED row - imported lands as a distinct key`() = runTest {
        val dao = FakeImportDao().apply {
            // Pre-seed a live-captured row for the SAME platform/week.
            rows += capturedRow(platform = "UBER", weekStart = "2025-03-24", net = 9999.0)
        }
        val repository = repo(api { okJson(confirmBody) }, dao)

        repository.confirm("uber", "pdf", listOf(pdf()))

        // Both rows coexist (different source in the composite key); captured untouched.
        assertEquals(2, dao.rows.size)
        val captured = dao.rows.single { it.source == AggregateSource.CAPTURED.name }
        val imported = dao.rows.single { it.source == AggregateSource.IMPORTED.name }
        assertEquals(9999.0, captured.netEarningsMxn, 0.001) // captured preserved
        assertEquals(3850.5, imported.netEarningsMxn, 0.001) // imported written
    }

    // ─── Idempotent re-import ─────────────────────────────────────────────────

    @Test
    fun `re-importing the same week leaves exactly one IMPORTED row (latest wins)`() = runTest {
        val dao = FakeImportDao()
        val repository = repo(api { okJson(confirmBody) }, dao)

        repository.confirm("uber", "pdf", listOf(pdf()))
        // Second import of the same week with different numbers.
        val updated = confirmBody.replace("3850.5", "4000.0").replace("72", "80")
        val repository2 = repo(api { okJson(updated) }, dao)
        repository2.confirm("uber", "pdf", listOf(pdf()))

        // REPLACE upsert on (platform, weekStart, IMPORTED) keeps one row.
        val imported = dao.rows.filter { it.source == AggregateSource.IMPORTED.name }
        assertEquals(1, imported.size)
        assertEquals(4000.0, imported.single().netEarningsMxn, 0.001)
        assertEquals(80, imported.single().totalTrips)
    }

    // ─── Error mapping ────────────────────────────────────────────────────────

    @Test
    fun `parse failure surfaces the exact backend Spanish error as an ApiException`() = runTest {
        val message = "No pudimos leer tus datos. Asegurate que el screenshot sea claro y completo."
        val dao = FakeImportDao()
        val repository = repo(api { errorJson(HttpStatusCode.UnprocessableEntity, message) }, dao)

        val ex = assertThrows(ApiException::class.java) {
            kotlinx.coroutines.runBlocking { repository.confirm("uber", "pdf", listOf(pdf())) }
        }
        assertEquals(422, ex.status)
        assertEquals(message, ex.message)
        // Nothing persisted on failure.
        assertTrue(dao.upserts.isEmpty())
    }

    @Test
    fun `signed-out upload surfaces the 401 from the backend`() = runTest {
        val dao = FakeImportDao()
        // isSignedIn() is the UI's gate; the repository still forwards the call, and a 401 comes back.
        val repository = repo(
            api { errorJson(HttpStatusCode.Unauthorized, "No autorizado") },
            dao,
            signedIn = false,
        )

        assertTrue(!repository.isSignedIn())
        val ex = assertThrows(ApiException::class.java) {
            kotlinx.coroutines.runBlocking { repository.confirm("uber", "pdf", listOf(pdf())) }
        }
        assertEquals(401, ex.status)
        assertTrue(dao.upserts.isEmpty())
    }

    // ─── File-count validation ────────────────────────────────────────────────

    @Test
    fun `didi requires exactly two files - one is rejected before any network call`() = runTest {
        val urls = mutableListOf<String>()
        val dao = FakeImportDao()
        val repository = repo(api(urls) { okJson(confirmBody) }, dao)

        val ex = assertThrows(ApiException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository.confirm("didi", "screenshot", listOf(png("only.png")))
            }
        }
        assertTrue(ex.message.contains("DiDi requiere 2 capturas"))
        // Short-circuited: never hit the network, never persisted.
        assertTrue(urls.isEmpty())
        assertTrue(dao.upserts.isEmpty())
    }

    @Test
    fun `single-file platforms reject a multi-file upload before any network call`() = runTest {
        val urls = mutableListOf<String>()
        val repository = repo(api(urls) { okJson(confirmBody) })

        assertThrows(ApiException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository.preview("uber", "pdf", listOf(pdf("a.pdf"), pdf("b.pdf")))
            }
        }
        assertTrue(urls.isEmpty())
    }

    @Test
    fun `didi preview accepts exactly two files`() = runTest {
        val urls = mutableListOf<String>()
        val repository = repo(api(urls) { okJson(previewBody) })

        val result = repository.preview(
            "didi",
            "screenshot",
            listOf(png("earnings.png"), png("tablero.png")),
        )

        assertTrue(result.dryRun)
        assertTrue(urls.single().contains("dry_run=true"))
    }
}

// ─── Fakes ────────────────────────────────────────────────────────────────────

private fun capturedRow(platform: String, weekStart: String, net: Double) = WeeklyAggregateEntity(
    platform = platform,
    weekStart = weekStart,
    source = AggregateSource.CAPTURED.name,
    netEarningsMxn = net,
    grossEarningsMxn = net + 500.0,
    totalTrips = 200,
    totalKm = 800.0,
    hoursOnline = 50.0,
    earningsPerTrip = net / 200,
    earningsPerKm = net / 800,
    earningsPerHour = net / 50,
    tripsPerHour = 4.0,
    acceptanceRate = 0.7,
    computedAt = 1L,
    lastSyncedAt = null,
)

/**
 * In-memory [AggregateDao] modelling Room's REPLACE-on-(platform, weekStart, source) upsert so the
 * repository's local-backfill + captured-protection + idempotency can be asserted without a DB.
 */
private class FakeImportDao : AggregateDao {
    val rows = mutableListOf<WeeklyAggregateEntity>()
    /** Every upsert call's payload, in order — lets tests assert "nothing was written". */
    val upserts = mutableListOf<List<WeeklyAggregateEntity>>()

    override suspend fun upsertWeekly(rows: List<WeeklyAggregateEntity>) {
        upserts += rows
        for (row in rows) {
            // REPLACE on the composite primary key.
            this.rows.removeAll {
                it.platform == row.platform && it.weekStart == row.weekStart && it.source == row.source
            }
            this.rows += row
        }
    }

    override suspend fun upsertDaily(rows: List<DailyAggregateEntity>) = error("unused")
    override fun observeWeekly() = error("unused")
    override fun observeDaily() = error("unused")
    override suspend fun capturedWeek(weekStart: String): List<WeeklyAggregateEntity> = error("unused")
    override suspend fun capturedDay(day: String): List<DailyAggregateEntity> = error("unused")
    override suspend fun allWeekly(): List<WeeklyAggregateEntity> = error("unused")
    override suspend fun deleteCapturedDay(day: String) = error("unused")
    override suspend fun deleteCapturedWeek(weekStart: String) = error("unused")
    override suspend fun dirtyForSync(): List<WeeklyAggregateEntity> = error("unused")
    override suspend fun markSynced(platform: String, weekStart: String, source: String, syncedAt: Long) =
        error("unused")
}
