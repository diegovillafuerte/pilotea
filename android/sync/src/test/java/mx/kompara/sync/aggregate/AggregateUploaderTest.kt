package mx.kompara.sync.aggregate

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.data.db.entity.DailyAggregateEntity
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.sync.api.ApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** A fixed [Clock] at [epochMillis] for deterministic time in tests. */
private fun fixedClock(epochMillis: Long): Clock =
    Clock.fixed(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)

/**
 * [AggregateUploader] tests (B-043):
 *  - consent-off short-circuit (nothing read or sent);
 *  - signed-out short-circuit;
 *  - dirty-row selection (only never-synced / recomputed rows upload, both sources);
 *  - PAYLOAD SHAPE: the serialized HTTP body carries ONLY derived aggregate fields — no
 *    offer/trip-level data and no raw text (asserted against the actual bytes on the wire);
 *  - transient (5xx) failure leaves the row dirty + reports failed; permanent (4xx) stamps it synced.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AggregateUploaderTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun weeklyRow(
        platform: String = "UBER",
        weekStart: String = "2026-06-01",
        source: String = "CAPTURED",
        computedAt: Long = 100L,
        lastSyncedAt: Long? = null,
    ) = WeeklyAggregateEntity(
        platform = platform,
        weekStart = weekStart,
        source = source,
        netEarningsMxn = 1234.5,
        grossEarningsMxn = 1500.0,
        totalTrips = 42,
        totalKm = 210.0,
        hoursOnline = 30.0,
        earningsPerTrip = 29.39,
        earningsPerKm = 5.88,
        earningsPerHour = 41.15,
        tripsPerHour = 1.4,
        acceptanceRate = 0.66,
        computedAt = computedAt,
        lastSyncedAt = lastSyncedAt,
    )

    /** Build an ApiClient over a MockEngine that records the serialized body + url of each request. */
    private fun api(
        handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
        bodies: MutableList<String>,
        urls: MutableList<String> = mutableListOf(),
    ): ApiClient {
        val engine = MockEngine { req ->
            urls += req.url.toString()
            val bytes = (req.body as? OutgoingContent.ByteArrayContent)?.bytes() ?: ByteArray(0)
            bodies += bytes.decodeToString()
            handler(req)
        }
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
        }
        return ApiClient(http, "http://test.local", { "session-token" }, { "device-uuid" })
    }

    private fun MockRequestHandleScope.ok(): HttpResponseData = respond(
        content = "{\"aggregate\":{}}",
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    @Test
    fun `consent off short-circuits - nothing read or sent`() = runTest {
        val dao = FakeAggregateDao().apply { dirty += weeklyRow() }
        val bodies = mutableListOf<String>()
        val uploader = AggregateUploader(
            aggregateDao = dao,
            api = api({ ok() }, bodies),
            consent = { false },
            session = { true },
            clock = fixedClock(1_000L),
        )

        val outcome = uploader.sync()

        assertTrue(outcome.shortCircuited)
        assertEquals(0, outcome.uploaded)
        assertTrue(bodies.isEmpty())
        // dirtyForSync was never consulted (rows untouched, unsynced).
        assertTrue(dao.markedSynced.isEmpty())
    }

    @Test
    fun `signed out short-circuits even when consented`() = runTest {
        val dao = FakeAggregateDao().apply { dirty += weeklyRow() }
        val bodies = mutableListOf<String>()
        val uploader = AggregateUploader(
            aggregateDao = dao,
            api = api({ ok() }, bodies),
            consent = { true },
            session = { false },
            clock = fixedClock(1_000L),
        )

        val outcome = uploader.sync()

        assertTrue(outcome.shortCircuited)
        assertTrue(bodies.isEmpty())
    }

    @Test
    fun `uploads every dirty row across both sources and stamps them synced`() = runTest {
        val captured = weeklyRow(source = "CAPTURED")
        val imported = weeklyRow(source = "IMPORTED", weekStart = "2026-05-25")
        val dao = FakeAggregateDao().apply { dirty += captured; dirty += imported }
        val bodies = mutableListOf<String>()
        val urls = mutableListOf<String>()
        val uploader = AggregateUploader(
            aggregateDao = dao,
            api = api({ ok() }, bodies, urls),
            consent = { true },
            session = { true },
            clock = fixedClock(5_000L),
        )

        val outcome = uploader.sync()

        assertFalse(outcome.shortCircuited)
        assertEquals(2, outcome.uploaded)
        assertFalse(outcome.failed)
        assertEquals(2, bodies.size)
        // Both POSTed to /v1/aggregates.
        assertTrue(urls.all { it.endsWith("/v1/aggregates") })
        // Both sources stamped synced at the observed clock time. markSynced uses the row's OWN
        // (Room) source string ("CAPTURED"/"IMPORTED") since that's the DB primary-key value — only
        // the wire payload lower-cases it.
        assertEquals(2, dao.markedSynced.size)
        assertTrue(dao.markedSynced.any { it.source == "CAPTURED" && it.syncedAt == 5_000L })
        assertTrue(dao.markedSynced.any { it.source == "IMPORTED" && it.syncedAt == 5_000L })
    }

    @Test
    fun `payload contains only derived aggregates - no offer or trip or raw data`() = runTest {
        val dao = FakeAggregateDao().apply { dirty += weeklyRow() }
        val bodies = mutableListOf<String>()
        val uploader = AggregateUploader(
            aggregateDao = dao,
            api = api({ ok() }, bodies),
            consent = { true },
            session = { true },
            clock = fixedClock(1L),
        )

        uploader.sync()

        assertEquals(1, bodies.size)
        val body = bodies.first()
        // Present: the derived aggregate fields + platform/week/source.
        assertTrue(body.contains("\"platform\""))
        assertTrue(body.contains("\"weekStart\""))
        assertTrue(body.contains("\"netEarnings\""))
        assertTrue(body.contains("\"grossEarnings\""))
        assertTrue(body.contains("\"totalTrips\""))
        assertTrue(body.contains("\"earningsPerTrip\""))
        assertTrue(body.contains("\"source\""))
        // Platform + source are lower-cased to the backend enum.
        assertTrue(body.contains("\"platform\":\"uber\""))
        assertTrue(body.contains("\"source\":\"captured\""))
        // Decimals are 2dp strings (matches the backend decimalString coercion).
        assertTrue(body.contains("\"netEarnings\":\"1234.50\""))

        // ABSENT: anything offer/trip-level, raw, or otherwise un-derived. This is the
        // privacy invariant — raw capture data NEVER leaves the device.
        assertFalse(body.contains("offer"))
        assertFalse(body.contains("trip\"")) // no "trip"/"trips" array — totalTrips is an int
        assertFalse(body.contains("acceptanceRate")) // device-only metric, not in the wire DTO
        assertFalse(body.contains("rawText"))
        assertFalse(body.contains("snapshot"))
        assertFalse(body.contains("nodes"))
        assertFalse(body.contains("fare"))
        assertFalse(body.contains("pickup"))
        assertFalse(body.contains("destination"))
        assertFalse(body.contains("driverId")) // ownership is from the session, never the body
        assertFalse(body.contains("computedAt"))
        assertFalse(body.contains("lastSyncedAt"))
    }

    @Test
    fun `transient 5xx leaves the row dirty and reports failed`() = runTest {
        val dao = FakeAggregateDao().apply { dirty += weeklyRow() }
        val bodies = mutableListOf<String>()
        val uploader = AggregateUploader(
            aggregateDao = dao,
            api = api({ respondError(HttpStatusCode.ServiceUnavailable) }, bodies),
            consent = { true },
            session = { true },
            clock = fixedClock(1L),
        )

        val outcome = uploader.sync()

        assertTrue(outcome.failed)
        assertEquals(0, outcome.uploaded)
        // Not stamped synced — retried next run.
        assertTrue(dao.markedSynced.isEmpty())
    }

    @Test
    fun `permanent 4xx stamps the poison row synced so the queue does not wedge`() = runTest {
        val dao = FakeAggregateDao().apply { dirty += weeklyRow() }
        val bodies = mutableListOf<String>()
        val uploader = AggregateUploader(
            aggregateDao = dao,
            api = api({ respondError(HttpStatusCode.BadRequest) }, bodies),
            consent = { true },
            session = { true },
            clock = fixedClock(9L),
        )

        val outcome = uploader.sync()

        assertFalse(outcome.failed)
        assertEquals(0, outcome.uploaded)
        // Stamped synced (dropped from the dirty set) so one bad row can't block the rest.
        assertEquals(1, dao.markedSynced.size)
        assertEquals(9L, dao.markedSynced.first().syncedAt)
    }
}

// ─── Fakes ────────────────────────────────────────────────────────────────

internal data class MarkSynced(
    val platform: String,
    val weekStart: String,
    val source: String,
    val syncedAt: Long,
)

internal class FakeAggregateDao : AggregateDao {
    val dirty = mutableListOf<WeeklyAggregateEntity>()
    val markedSynced = mutableListOf<MarkSynced>()

    override suspend fun dirtyForSync(): List<WeeklyAggregateEntity> = dirty.toList()

    override suspend fun markSynced(
        platform: String,
        weekStart: String,
        source: String,
        syncedAt: Long,
    ) {
        markedSynced += MarkSynced(platform, weekStart, source, syncedAt)
    }

    // Unused by the uploader.
    override suspend fun upsertWeekly(rows: List<WeeklyAggregateEntity>) = error("unused")
    override suspend fun upsertDaily(rows: List<DailyAggregateEntity>) = error("unused")
    override fun observeWeekly() = error("unused")
    override fun observeDaily() = error("unused")
    override suspend fun capturedWeek(weekStart: String): List<WeeklyAggregateEntity> = error("unused")
    override suspend fun capturedDay(day: String): List<DailyAggregateEntity> = error("unused")
    override suspend fun allWeekly(): List<WeeklyAggregateEntity> = error("unused")
    override suspend fun deleteCapturedDay(day: String) = error("unused")
    override suspend fun deleteCapturedWeek(weekStart: String) = error("unused")
}
