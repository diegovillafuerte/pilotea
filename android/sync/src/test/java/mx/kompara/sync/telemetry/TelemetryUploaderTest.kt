package mx.kompara.sync.telemetry

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mx.kompara.data.db.dao.FixtureReportDao
import mx.kompara.data.db.dao.TelemetryCounterDao
import mx.kompara.data.db.entity.FixtureReportEntity
import mx.kompara.data.db.entity.TelemetryCounterEntity
import mx.kompara.parsers.snapshot.ParserNode
import mx.kompara.parsers.snapshot.ParserSnapshot
import mx.kompara.sync.api.ApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TelemetryUploader] tests (B-034): consent-off short-circuit, flush/ack deletion, transient-vs-
 * permanent failure handling, and the no-screen-content-in-payload property asserted against the
 * ACTUAL serialized HTTP body of every request the uploader sends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryUploaderTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun uberCounter(attempts: Long = 10, successes: Long = 7, failures: Long = 3) =
        TelemetryCounterEntity(
            hostPackage = "com.ubercab.driver",
            hostVersionCode = 500L,
            specVersion = 7,
            variant = TelemetryCounterEntity.TOTAL,
            dayUtc = "2026-06-10",
            attempts = attempts,
            successes = successes,
            failures = failures,
        )

    /** A scrubbed-but-let's-be-paranoid snapshot; we still assert nothing recognizable leaks. */
    private fun reportRow(id: Long = 1L) = FixtureReportEntity(
        id = id,
        hostPackage = "com.ubercab.driver",
        hostVersionCode = 500L,
        specVersion = 7,
        reason = "NOT_AN_OFFER",
        snapshotJson = json.encodeToString(
            ParserSnapshot.serializer(),
            ParserSnapshot(
                packageName = "com.ubercab.driver",
                timestampMs = 1L,
                versionCode = 500L,
                nodes = listOf(
                    ParserNode(text = "«name»", viewId = "passenger", depth = 0, index = 0),
                    ParserNode(text = "Viaje", viewId = "title", depth = 1, index = 0),
                ),
            ),
        ),
        consented = true,
        createdAt = 100L,
    )

    /** Build an ApiClient over a MockEngine recording the serialized body of each request. */
    private fun api(
        handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
        recorder: MutableList<String>,
    ): ApiClient {
        val engine = MockEngine { req ->
            val bytes = (req.body as? OutgoingContent.ByteArrayContent)?.bytes() ?: ByteArray(0)
            recorder += bytes.decodeToString()
            handler(req)
        }
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
        }
        return ApiClient(http, "http://test.local", { null }, { "device-uuid" })
    }

    private fun MockRequestHandleScope.ok(): HttpResponseData = respond(
        content = "{\"ok\":true}",
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    @Test
    fun `consent off short-circuits counter upload but still sends fixture reports`() = runTest {
        val counters = FakeCounterDao().apply { put(uberCounter()) }
        val fixtures = FakeFixtureDao().apply { add(reportRow()) }
        val bodies = mutableListOf<String>()
        val uploader = TelemetryUploader(
            counterDao = counters,
            fixtureDao = fixtures,
            api = api({ ok() }, bodies),
            consent = { false },
            json = json,
        )

        val outcome = uploader.flush()

        assertEquals(0, outcome.countersUploaded)
        assertEquals(1, outcome.fixturesUploaded)
        assertFalse(outcome.failed)
        // Counter row untouched on device; fixture row deleted on ack.
        assertEquals(1, counters.rows.size)
        assertTrue(fixtures.rows.isEmpty())
        // Only the fixture-report request was sent.
        assertEquals(1, bodies.size)
    }

    @Test
    fun `flush uploads counters and deletes them on ack`() = runTest {
        val counters = FakeCounterDao().apply { put(uberCounter()) }
        val fixtures = FakeFixtureDao()
        val bodies = mutableListOf<String>()
        val uploader = TelemetryUploader(
            counterDao = counters,
            fixtureDao = fixtures,
            api = api({ ok() }, bodies),
            consent = { true },
            json = json,
        )

        val outcome = uploader.flush()

        assertEquals(1, outcome.countersUploaded)
        assertFalse(outcome.failed)
        assertTrue(counters.rows.isEmpty())
    }

    @Test
    fun `transient failure leaves the queue intact and reports failed`() = runTest {
        val counters = FakeCounterDao().apply { put(uberCounter()) }
        val fixtures = FakeFixtureDao()
        val bodies = mutableListOf<String>()
        val uploader = TelemetryUploader(
            counterDao = counters,
            fixtureDao = fixtures,
            api = api({ respondError(HttpStatusCode.ServiceUnavailable) }, bodies),
            consent = { true },
            json = json,
        )

        val outcome = uploader.flush()

        assertTrue(outcome.failed)
        assertEquals(0, outcome.countersUploaded)
        // Not deleted — a 5xx is retried next run.
        assertEquals(1, counters.rows.size)
    }

    @Test
    fun `permanent 4xx failure drops the poison counter so the queue does not wedge`() = runTest {
        val counters = FakeCounterDao().apply { put(uberCounter()) }
        val fixtures = FakeFixtureDao()
        val bodies = mutableListOf<String>()
        val uploader = TelemetryUploader(
            counterDao = counters,
            fixtureDao = fixtures,
            api = api({ respondError(HttpStatusCode.BadRequest) }, bodies),
            consent = { true },
            json = json,
        )

        val outcome = uploader.flush()

        assertFalse(outcome.failed)
        assertEquals(0, outcome.countersUploaded)
        assertTrue(counters.rows.isEmpty()) // dropped, not stuck
    }

    @Test
    fun `no request body ever contains node text - only counters and masked structure`() = runTest {
        val counters = FakeCounterDao().apply { put(uberCounter()) }
        val fixtures = FakeFixtureDao().apply { add(reportRow()) }
        val bodies = mutableListOf<String>()
        val uploader = TelemetryUploader(
            counterDao = counters,
            fixtureDao = fixtures,
            api = api({ ok() }, bodies),
            consent = { true },
            json = json,
        )

        uploader.flush()

        assertEquals(2, bodies.size) // one counter POST + one fixture POST
        val counterBody = bodies.first { it.contains("\"attempts\"") }
        // Counter payload must be integers + identifiers only — no node text field at all.
        assertFalse(counterBody.contains("\"text\""))
        assertFalse(counterBody.contains("Viaje"))
        assertTrue(counterBody.contains("\"hostPackage\""))
        assertTrue(counterBody.contains("\"attempts\":10"))

        // Fixture payload carries scrubbed structure; the only "text" present is the mask, never
        // a recognizable passenger token.
        val fixtureBody = bodies.first { it.contains("\"snapshot\"") }
        assertTrue(fixtureBody.contains("«name»"))
        assertFalse(fixtureBody.contains("Juan"))
        assertFalse(fixtureBody.contains("Pérez"))
    }

    @Test
    fun `non-consented fixture row is dropped without upload (defensive)`() = runTest {
        val counters = FakeCounterDao()
        val fixtures = FakeFixtureDao().apply { add(reportRow().copy(consented = false)) }
        val bodies = mutableListOf<String>()
        val uploader = TelemetryUploader(
            counterDao = counters,
            fixtureDao = fixtures,
            api = api({ ok() }, bodies),
            consent = { true },
            json = json,
        )

        val outcome = uploader.flush()

        assertEquals(0, outcome.fixturesUploaded)
        assertTrue(fixtures.rows.isEmpty())
        assertTrue(bodies.isEmpty())
    }
}

// ─── Fakes ────────────────────────────────────────────────────────────────

private class FakeCounterDao : TelemetryCounterDao {
    val rows = mutableListOf<TelemetryCounterEntity>()
    fun put(row: TelemetryCounterEntity) { rows += row }

    override suspend fun increment(
        hostPackage: String, hostVersionCode: Long, specVersion: Int, variant: String,
        dayUtc: String, attempts: Long, successes: Long, failures: Long,
    ) = error("unused")

    override suspend fun insertIgnore(row: TelemetryCounterEntity) = error("unused")
    override suspend fun addDeltas(
        hostPackage: String, hostVersionCode: Long, specVersion: Int, variant: String,
        dayUtc: String, attempts: Long, successes: Long, failures: Long,
    ) = error("unused")

    override suspend fun all(): List<TelemetryCounterEntity> = rows.toList()
    override suspend fun count(): Int = rows.size

    override suspend fun deleteIfUnchanged(
        hostPackage: String, hostVersionCode: Long, specVersion: Int, variant: String,
        dayUtc: String, attempts: Long, successes: Long, failures: Long,
    ): Int {
        val removed = rows.removeAll {
            it.hostPackage == hostPackage && it.hostVersionCode == hostVersionCode &&
                it.specVersion == specVersion && it.variant == variant && it.dayUtc == dayUtc &&
                it.attempts == attempts && it.successes == successes && it.failures == failures
        }
        return if (removed) 1 else 0
    }
}

private class FakeFixtureDao : FixtureReportDao {
    val rows = mutableListOf<FixtureReportEntity>()
    fun add(row: FixtureReportEntity) { rows += row }

    override suspend fun insert(report: FixtureReportEntity): Long {
        rows += report; return report.id
    }
    override suspend fun oldest(limit: Int): List<FixtureReportEntity> =
        rows.sortedBy { it.createdAt }.take(limit)
    override suspend fun count(): Int = rows.size
    override suspend fun delete(report: FixtureReportEntity) { rows.removeAll { it.id == report.id } }
    override suspend fun deleteById(id: Long) { rows.removeAll { it.id == id } }
}
