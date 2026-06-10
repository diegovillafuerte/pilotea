package mx.kompara.sync.fiscal

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mx.kompara.data.db.dao.FiscalConfigDao
import mx.kompara.data.db.entity.FiscalConfigEntity
import mx.kompara.data.settings.FiscalDefaults
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
 * [FiscalConfigRepository] tests (B-051): default fallback when empty/offline, first fetch caches +
 * exposes the values, TTL gating, last-known-good on failure, and `force`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FiscalConfigRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun configJson(year: Int, threshold: Double, wage: Double) = """
        {"imssMonthlyThresholdMxn":$threshold,"minimumWageDailyMxn":$wage,"year":$year,
         "updatedAt":"2026-01-01T00:00:00.000Z"}
    """.trimIndent()

    private fun api(
        requestCount: IntArray,
        handler: () -> Pair<String, HttpStatusCode>,
    ): ApiClient {
        val engine = MockEngine {
            requestCount[0]++
            val (body, status) = handler()
            if (status == HttpStatusCode.OK) {
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respondError(status)
            }
        }
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
        }
        return ApiClient(http, "http://test.local", { null }, { "device-uuid" })
    }

    @Test
    fun `observe falls back to the compile-time default when the cache is empty`() = runTest {
        val repo = FiscalConfigRepository(
            dao = FakeFiscalConfigDao(),
            api = api(intArrayOf(0)) { error("not called") },
            clock = fixedClock(0L),
        )

        val config = repo.observe().first()
        assertTrue(config.isDefault)
        assertEquals(FiscalDefaults.DEFAULT_YEAR, config.year)
        assertEquals(FiscalDefaults.DEFAULT_IMSS_MONTHLY_THRESHOLD_MXN, config.imssMonthlyThresholdMxn, 0.001)
        assertEquals(FiscalDefaults.DEFAULT_MINIMUM_WAGE_DAILY_MXN, config.minimumWageDailyMxn, 0.001)
    }

    @Test
    fun `current resolves to the default when empty`() = runTest {
        val repo = FiscalConfigRepository(
            dao = FakeFiscalConfigDao(),
            api = api(intArrayOf(0)) { error("not called") },
            clock = fixedClock(0L),
        )
        assertTrue(repo.current().isDefault)
    }

    @Test
    fun `first fetch caches the config and exposes it as a non-default value`() = runTest {
        val dao = FakeFiscalConfigDao()
        val reqs = intArrayOf(0)
        val repo = FiscalConfigRepository(
            dao = dao,
            api = api(reqs) { configJson(2027, 9000.0, 300.0) to HttpStatusCode.OK },
            clock = fixedClock(10_000L),
        )

        val result = repo.refresh()

        assertEquals(FiscalConfigRepository.RefreshResult.FETCHED, result)
        assertEquals(1, reqs[0])
        val config = repo.observe().first()
        assertFalse(config.isDefault)
        assertEquals(2027, config.year)
        assertEquals(9000.0, config.imssMonthlyThresholdMxn, 0.001)
        assertEquals(300.0, config.minimumWageDailyMxn, 0.001)
    }

    @Test
    fun `within the TTL the cache is served and no network call is made`() = runTest {
        val now = 1_000_000L
        val dao = FakeFiscalConfigDao().apply {
            row = FiscalConfigEntity(2026, 278.8, 8364.0, updatedAt = 0, fetchedAt = now - 60 * 60 * 1000)
        }
        val reqs = intArrayOf(0)
        val repo = FiscalConfigRepository(
            dao = dao,
            api = api(reqs) { error("should not fetch within TTL") },
            clock = fixedClock(now),
        )

        assertEquals(FiscalConfigRepository.RefreshResult.FRESH_CACHE, repo.refresh())
        assertEquals(0, reqs[0])
    }

    @Test
    fun `stale cache re-fetches past the TTL`() = runTest {
        val now = 100_000_000L
        val dao = FakeFiscalConfigDao().apply {
            row = FiscalConfigEntity(2026, 270.0, 8100.0, updatedAt = 0, fetchedAt = now - 25L * 60 * 60 * 1000)
        }
        val reqs = intArrayOf(0)
        val repo = FiscalConfigRepository(
            dao = dao,
            api = api(reqs) { configJson(2026, 8364.0, 278.8) to HttpStatusCode.OK },
            clock = fixedClock(now),
        )

        assertEquals(FiscalConfigRepository.RefreshResult.FETCHED, repo.refresh())
        assertEquals(1, reqs[0])
        assertEquals(8364.0, repo.observe().first().imssMonthlyThresholdMxn, 0.001)
    }

    @Test
    fun `failed fetch keeps the last-known-good cache`() = runTest {
        val now = 100_000_000L
        val dao = FakeFiscalConfigDao().apply {
            row = FiscalConfigEntity(2026, 278.8, 8364.0, updatedAt = 0, fetchedAt = now - 25L * 60 * 60 * 1000)
        }
        val reqs = intArrayOf(0)
        val repo = FiscalConfigRepository(
            dao = dao,
            api = api(reqs) { "" to HttpStatusCode.ServiceUnavailable },
            clock = fixedClock(now),
        )

        assertEquals(FiscalConfigRepository.RefreshResult.FAILED, repo.refresh())
        assertEquals(1, reqs[0])
        val config = repo.observe().first()
        assertFalse(config.isDefault) // the stale-but-present cache is retained
        assertEquals(8364.0, config.imssMonthlyThresholdMxn, 0.001)
    }

    @Test
    fun `a 404 (nothing seeded) leaves the default in place`() = runTest {
        val dao = FakeFiscalConfigDao()
        val repo = FiscalConfigRepository(
            dao = dao,
            api = api(intArrayOf(0)) { "" to HttpStatusCode.NotFound },
            clock = fixedClock(0L),
        )

        assertEquals(FiscalConfigRepository.RefreshResult.FAILED, repo.refresh())
        assertTrue(repo.observe().first().isDefault)
    }

    @Test
    fun `force ignores a fresh cache and re-fetches`() = runTest {
        val now = 1_000_000L
        val dao = FakeFiscalConfigDao().apply {
            row = FiscalConfigEntity(2026, 278.8, 8364.0, updatedAt = 0, fetchedAt = now - 1000)
        }
        val reqs = intArrayOf(0)
        val repo = FiscalConfigRepository(
            dao = dao,
            api = api(reqs) { configJson(2027, 9000.0, 300.0) to HttpStatusCode.OK },
            clock = fixedClock(now),
        )

        assertEquals(FiscalConfigRepository.RefreshResult.FETCHED, repo.refresh(force = true))
        assertEquals(1, reqs[0])
        assertEquals(2027, repo.observe().first().year)
    }
}

// ─── Fake ─────────────────────────────────────────────────────────────────

internal class FakeFiscalConfigDao : FiscalConfigDao {
    var row: FiscalConfigEntity? = null
    private val changes = MutableStateFlow(0)

    override suspend fun upsert(row: FiscalConfigEntity) {
        // The repo only ever keeps the latest; mirror "highest year wins" trivially by replacing.
        if (this.row == null || row.year >= this.row!!.year) this.row = row
        changes.value++
    }

    override suspend fun latest(): FiscalConfigEntity? = row

    override fun observeLatest(): Flow<FiscalConfigEntity?> = changes.map { row }
}
