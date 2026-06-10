package mx.kompara.sync.aggregate

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
import mx.kompara.data.db.dao.PopulationStatDao
import mx.kompara.data.db.entity.PopulationStatEntity
import mx.kompara.data.model.City
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
 * [BenchmarksRepository] tests (B-043): cache TTL gating, offline last-known-good fallback, city-change
 * invalidation, and that fetched benchmarks are cached + exposed as domain models for B-046.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BenchmarksRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** A benchmarks response body for a city × platform with one earnings_per_trip cell. */
    private fun benchmarksJson(city: String, platform: String, p50: String, synthetic: Boolean) = """
        {"city":"$city","platform":"$platform","period":"current","stats":[
          {"city":"$city","platform":"$platform","metricName":"earnings_per_trip","period":"current",
           "sampleSize":1500,"p10":"27","p25":"36","p50":"$p50","p75":"56.25","p90":"67.5","mean":"47.25",
           "isSynthetic":$synthetic}
        ]}
    """.trimIndent()

    /**
     * ApiClient over a MockEngine that counts requests and replies per-handler. The handler receives
     * the requested `city` query param so a test can answer city vs `national` differently; counts are
     * tallied per-city so existing assertions can ignore the (now always-fetched) `national` cell by
     * asserting on the city's own count.
     */
    private fun api(
        requestCount: IntArray,
        handler: (city: String) -> Pair<String, HttpStatusCode>,
    ): ApiClient {
        val engine = MockEngine { request ->
            requestCount[0]++
            val city = request.url.parameters["city"].orEmpty()
            val (body, status) = handler(city)
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

    /** Empty (200) benchmarks body for a city — used to answer the `national` fetch a test ignores. */
    private fun emptyBenchmarks(city: String, platform: String) =
        """{"city":"$city","platform":"$platform","period":"current","stats":[]}"""

    private fun cachedRow(
        city: String,
        platform: String,
        p50: Double,
        fetchedAt: Long,
        synthetic: Boolean = true,
    ) = PopulationStatEntity(
        city = city,
        platform = platform,
        metricName = "earnings_per_trip",
        period = "current",
        sampleSize = 1500,
        p10 = 27.0, p25 = 36.0, p50 = p50, p75 = 56.25, p90 = 67.5, mean = 47.25,
        isSynthetic = synthetic,
        fetchedAt = fetchedAt,
    )

    @Test
    fun `first fetch caches benchmarks and exposes them as domain models`() = runTest {
        val dao = FakePopulationStatDao()
        val reqs = intArrayOf(0)
        val repo = BenchmarksRepository(
            dao = dao,
            api = api(reqs) { city ->
                if (city == "national") emptyBenchmarks(city, "uber") to HttpStatusCode.OK
                else benchmarksJson(city, "uber", "45", false) to HttpStatusCode.OK
            },
            clock = fixedClock(10_000L),
        )

        val result = repo.refresh(City.CDMX, platforms = listOf("uber"))

        assertEquals(BenchmarksRepository.RefreshResult.FETCHED, result)
        assertEquals(2, reqs[0]) // cdmx + national
        val stats = repo.observe(City.CDMX).first()
        assertEquals(1, stats.size)
        assertEquals("earnings_per_trip", stats.first().metric)
        assertEquals(45.0, stats.first().p50, 0.001)
        assertFalse(stats.first().isSynthetic) // folded real cell flows through
    }

    @Test
    fun `within the TTL the cache is served and no network call is made`() = runTest {
        val now = 1_000_000L
        val dao = FakePopulationStatDao().apply {
            // Both the city cell AND the national cell cached 1 hour ago — well within the 24h TTL.
            rows += cachedRow("cdmx", "uber", 45.0, fetchedAt = now - 60 * 60 * 1000)
            rows += cachedRow("national", "uber", 45.0, fetchedAt = now - 60 * 60 * 1000)
        }
        val reqs = intArrayOf(0)
        val repo = BenchmarksRepository(
            dao = dao,
            api = api(reqs) { error("should not fetch within TTL") },
            clock = fixedClock(now),
        )

        val result = repo.refresh(City.CDMX, platforms = listOf("uber"))

        assertEquals(BenchmarksRepository.RefreshResult.FRESH_CACHE, result)
        assertEquals(0, reqs[0]) // no fetch — neither city nor national
    }

    @Test
    fun `stale cache triggers a re-fetch past the TTL`() = runTest {
        val now = 100_000_000L
        val dao = FakePopulationStatDao().apply {
            // City + national cached 25 hours ago — past the 24h TTL.
            rows += cachedRow("cdmx", "uber", 40.0, fetchedAt = now - 25L * 60 * 60 * 1000)
            rows += cachedRow("national", "uber", 40.0, fetchedAt = now - 25L * 60 * 60 * 1000)
        }
        val reqs = intArrayOf(0)
        val repo = BenchmarksRepository(
            dao = dao,
            api = api(reqs) { city -> benchmarksJson(city, "uber", "45", false) to HttpStatusCode.OK },
            clock = fixedClock(now),
        )

        val result = repo.refresh(City.CDMX, platforms = listOf("uber"))

        assertEquals(BenchmarksRepository.RefreshResult.FETCHED, result)
        assertEquals(2, reqs[0]) // cdmx + national both stale
        // New value overwrote the stale one.
        assertEquals(45.0, repo.observe(City.CDMX).first().first().p50, 0.001)
    }

    @Test
    fun `national fallback row is cached and survives a city change`() = runTest {
        val now = 200_000_000L
        val dao = FakePopulationStatDao()
        val reqs = intArrayOf(0)
        val repo = BenchmarksRepository(
            dao = dao,
            api = api(reqs) { city ->
                val p50 = if (city == "national") "45" else "49"
                benchmarksJson(city, "uber", p50, true) to HttpStatusCode.OK
            },
            clock = fixedClock(now),
        )

        repo.refresh(City.CDMX, platforms = listOf("uber"))
        // The percentile read shape sees BOTH cdmx and national for the platform.
        val withNational = repo.observeForPercentiles(City.CDMX, "uber").first()
        assertTrue(withNational.any { it.city == "national" })
        assertTrue(withNational.any { it.city == "cdmx" })

        // Driver switches to Monterrey within the TTL: cdmx is pruned but national is retained.
        repo.refresh(City.MONTERREY, platforms = listOf("uber"))
        assertTrue(dao.rows.none { it.city == "cdmx" })
        assertTrue(dao.rows.any { it.city == "national" })
    }

    @Test
    fun `failed fetch keeps the last-known-good cache (offline tolerance)`() = runTest {
        val now = 100_000_000L
        val dao = FakePopulationStatDao().apply {
            // Stale (forces a fetch attempt) but present last-known-good for both cells.
            rows += cachedRow("cdmx", "uber", 40.0, fetchedAt = now - 25L * 60 * 60 * 1000)
            rows += cachedRow("national", "uber", 40.0, fetchedAt = now - 25L * 60 * 60 * 1000)
        }
        val reqs = intArrayOf(0)
        val repo = BenchmarksRepository(
            dao = dao,
            api = api(reqs) { "" to HttpStatusCode.ServiceUnavailable },
            clock = fixedClock(now),
        )

        val result = repo.refresh(City.CDMX, platforms = listOf("uber"))

        assertEquals(BenchmarksRepository.RefreshResult.FAILED, result)
        assertEquals(2, reqs[0]) // attempted both city + national
        // The stale-but-present cache is retained — benchmarks still available offline.
        val stats = repo.observe(City.CDMX).first()
        assertEquals(1, stats.size)
        assertEquals(40.0, stats.first().p50, 0.001)
    }

    @Test
    fun `changing city invalidates the previous city's cache`() = runTest {
        val now = 50_000_000L
        val dao = FakePopulationStatDao().apply {
            // A fresh cdmx cell from a prior session.
            rows += cachedRow("cdmx", "uber", 45.0, fetchedAt = now - 1000)
        }
        val reqs = intArrayOf(0)
        val repo = BenchmarksRepository(
            dao = dao,
            api = api(reqs) { city -> benchmarksJson(city, "uber", "49", false) to HttpStatusCode.OK },
            clock = fixedClock(now),
        )

        // Driver switched to Monterrey.
        repo.refresh(City.MONTERREY, platforms = listOf("uber"))

        // The cdmx rows are gone (other-city purge); only monterrey remains.
        assertTrue(dao.rows.none { it.city == "cdmx" })
        assertTrue(dao.rows.any { it.city == "monterrey" })
        // Observing cdmx now yields nothing; monterrey yields the fetched cell.
        assertTrue(repo.observe(City.CDMX).first().isEmpty())
        assertEquals(49.0, repo.observe(City.MONTERREY).first().first().p50, 0.001)
    }

    @Test
    fun `force ignores a fresh cache and re-fetches`() = runTest {
        val now = 1_000_000L
        val dao = FakePopulationStatDao().apply {
            rows += cachedRow("cdmx", "uber", 45.0, fetchedAt = now - 1000) // fresh
            rows += cachedRow("national", "uber", 45.0, fetchedAt = now - 1000) // fresh
        }
        val reqs = intArrayOf(0)
        val repo = BenchmarksRepository(
            dao = dao,
            api = api(reqs) { city -> benchmarksJson(city, "uber", "50", false) to HttpStatusCode.OK },
            clock = fixedClock(now),
        )

        val result = repo.refresh(City.CDMX, platforms = listOf("uber"), force = true)

        assertEquals(BenchmarksRepository.RefreshResult.FETCHED, result)
        assertEquals(2, reqs[0]) // force re-fetches both
        assertEquals(50.0, repo.observe(City.CDMX).first().first().p50, 0.001)
    }
}

// ─── Fake ─────────────────────────────────────────────────────────────────

internal class FakePopulationStatDao : PopulationStatDao {
    val rows = mutableListOf<PopulationStatEntity>()
    private val changes = MutableStateFlow(0)

    private fun keyOf(r: PopulationStatEntity) = "${r.city}|${r.platform}|${r.metricName}|${r.period}"

    override suspend fun upsert(rows: List<PopulationStatEntity>) {
        for (r in rows) {
            this.rows.removeAll { keyOf(it) == keyOf(r) }
            this.rows += r
        }
        changes.value++
    }

    override fun observeForCity(city: String): Flow<List<PopulationStatEntity>> =
        changes.map { rows.filter { it.city == city } }

    override fun observeForCityPlatform(
        city: String,
        platform: String,
    ): Flow<List<PopulationStatEntity>> =
        changes.map { rows.filter { it.city == city && it.platform == platform } }

    override suspend fun forCityPlatform(
        city: String,
        platform: String,
    ): List<PopulationStatEntity> = rows.filter { it.city == city && it.platform == platform }

    override fun observeForCityOrNational(
        city: String,
        platform: String,
    ): Flow<List<PopulationStatEntity>> =
        changes.map {
            rows.filter { it.platform == platform && (it.city == city || it.city == "national") }
        }

    override suspend fun latestFetchedAt(city: String, platform: String): Long? =
        rows.filter { it.city == city && it.platform == platform }.maxOfOrNull { it.fetchedAt }

    override suspend fun deleteOtherCities(city: String) {
        // Mirrors the real query: prune everything that is neither the active city nor `national`.
        val removed = rows.removeAll { it.city != city && it.city != "national" }
        if (removed) changes.value++
    }

    override suspend fun clear() {
        rows.clear()
        changes.value++
    }
}
