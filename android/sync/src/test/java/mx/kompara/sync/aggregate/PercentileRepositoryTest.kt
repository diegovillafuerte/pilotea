package mx.kompara.sync.aggregate

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mx.kompara.data.db.entity.PopulationStatEntity
import mx.kompara.data.model.City
import mx.kompara.data.model.PopulationStat
import mx.kompara.sync.api.ApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * [PercentileRepository] combination logic (B-046): mapping the driver's metric values against the
 * cached city + national breakpoints into [mx.kompara.metrics.percentile.PercentileResult]s, with the
 * fake-benchmark + fake-aggregate flows. The percentile arithmetic itself is parity-tested in
 * `:metrics`; here we cover the city/national split, skipping null metrics, and end-to-end reactivity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PercentileRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun stat(
        city: String,
        metric: String,
        sampleSize: Int,
        synthetic: Boolean = true,
        p10: Double = 27.0,
        p25: Double = 36.0,
        p50: Double = 45.0,
        p75: Double = 56.25,
        p90: Double = 67.5,
    ) = PopulationStat(
        city = city, platform = "uber", metric = metric, period = "current",
        sampleSize = sampleSize, p10 = p10, p25 = p25, p50 = p50, p75 = p75, p90 = p90,
        mean = p50 * 1.05, isSynthetic = synthetic,
    )

    /**
     * A [PercentileRepository] whose benchmarks flow is never read — these tests call [combine]
     * (pure) directly, so the backing repository just needs to exist.
     */
    private fun repo(): PercentileRepository {
        val engine = MockEngine { respond("", HttpStatusCode.OK) }
        val http = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        val api = ApiClient(http, "http://test.local", { null }, { "device" })
        return PercentileRepository(BenchmarksRepository(FakePopulationStatDao(), api, Clock.systemUTC()))
    }

    @Test
    fun `combines metric values against city and national breakpoints`() {
        val repo = repo()
        val stats = listOf(
            stat("cdmx", "earnings_per_trip", sampleSize = 1500, synthetic = false),
            stat("national", "earnings_per_trip", sampleSize = 5000),
        )

        val results = repo.combine(stats, mapOf("earnings_per_trip" to 45.0))

        assertEquals(1, results.size)
        val r = results.first()
        assertEquals("earnings_per_trip", r.metric)
        assertEquals(50, r.percentile) // v == p50
        assertFalse(r.isNationalFallback) // city has 1500 samples
        assertFalse(r.isSynthetic)
    }

    @Test
    fun `uses the national row when the city sample is too small`() {
        val repo = repo()
        val stats = listOf(
            stat("cdmx", "earnings_per_trip", sampleSize = 5), // below 20
            stat("national", "earnings_per_trip", sampleSize = 5000, synthetic = true),
        )

        val r = repo.combine(stats, mapOf("earnings_per_trip" to 45.0)).first()

        assertTrue(r.isNationalFallback)
        assertEquals(5000, r.sampleSize)
        assertTrue(r.isSynthetic)
    }

    @Test
    fun `skips metrics with a null value and metrics with no benchmark cell`() {
        val repo = repo()
        val stats = listOf(stat("national", "earnings_per_trip", sampleSize = 5000))

        val results = repo.combine(
            stats,
            linkedMapOf(
                "earnings_per_trip" to 45.0,   // has a cell -> included
                "earnings_per_km" to null,     // null -> skipped
                "trips_per_hour" to 2.5,        // no cell -> skipped
            ),
        )

        assertEquals(1, results.size)
        assertEquals("earnings_per_trip", results.first().metric)
    }

    @Test
    fun `returns empty when no benchmarks are cached`() {
        val repo = repo()
        assertTrue(repo.combine(emptyList(), mapOf("earnings_per_trip" to 45.0)).isEmpty())
    }

    @Test
    fun `inverts commission at display level`() {
        val repo = repo()
        val stats = listOf(
            stat(
                "national", "platform_commission_pct", sampleSize = 5000,
                p10 = 14.0, p25 = 17.0, p50 = 20.0, p75 = 23.6, p90 = 27.0,
            ),
        )

        val r = repo.combine(stats, mapOf("platform_commission_pct" to 14.0)).first()

        assertEquals(10, r.percentile) // raw: low value -> low percentile
        assertEquals(90, r.displayPercentile) // inverted: low commission is good
    }

    @Test
    fun `observe emits computed percentiles end-to-end over the cached benchmarks flow`() = runTest {
        val now = 10_000L
        val dao = FakePopulationStatDao().apply {
            rows += PopulationStatEntity(
                city = "cdmx", platform = "uber", metricName = "earnings_per_trip",
                period = "current", sampleSize = 1500,
                p10 = 27.0, p25 = 36.0, p50 = 45.0, p75 = 56.25, p90 = 67.5, mean = 47.25,
                isSynthetic = false, fetchedAt = now,
            )
        }
        val engine = MockEngine { respond("", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val http = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        val api = ApiClient(http, "http://test.local", { null }, { "device" })
        val benchmarks = BenchmarksRepository(dao, api, Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC))
        val repo = PercentileRepository(benchmarks)

        val results = repo.observe(City.CDMX, "uber", mapOf("earnings_per_trip" to 45.0)).first()

        assertEquals(1, results.size)
        assertEquals(50, results.first().percentile)
        assertNull(repo.observe(City.CDMX, "uber", emptyMap()).first().firstOrNull())
    }
}
