package mx.kompara.sync.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mx.kompara.sync.api.ApiClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * [PaywallConfigRepository] tests (B-050): the fail-safe default (gating ON) when empty/offline, a first
 * fetch caching the flag, the launch-promo flip (paywallEnabled=false), TTL gating, last-known on
 * failure, and `force`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaywallConfigRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private lateinit var tempFile: File

    @Before fun setUp() {
        tempFile = File.createTempFile("paywall_config_test", ".preferences_pb").also { it.delete() }
    }

    @After fun tearDown() {
        tempFile.delete()
    }

    private fun dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { tempFile })

    private fun fixedClock(epochMillis: Long): Clock =
        Clock.fixed(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)

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
    fun `defaults to gating ON when the cache is empty (offline, never fetched)`() = runTest {
        val repo = PaywallConfigRepository(
            dataStore = dataStore(),
            api = api(intArrayOf(0)) { error("not called") },
            clock = fixedClock(0L),
        )
        assertTrue(repo.paywallEnabled.first())
        assertTrue(repo.current())
    }

    @Test
    fun `first fetch caches the flag and exposes it`() = runTest {
        val reqs = intArrayOf(0)
        val repo = PaywallConfigRepository(
            dataStore = dataStore(),
            api = api(reqs) { """{"paywallEnabled":false}""" to HttpStatusCode.OK },
            clock = fixedClock(10_000L),
        )

        assertEquals(PaywallConfigRepository.RefreshResult.FETCHED, repo.refresh())
        assertEquals(1, reqs[0])
        assertFalse(repo.paywallEnabled.first()) // launch promo: everything unlocked
    }

    @Test
    fun `a fetch failure keeps the safe default (never auto-unlocks)`() = runTest {
        val repo = PaywallConfigRepository(
            dataStore = dataStore(),
            api = api(intArrayOf(0)) { "" to HttpStatusCode.InternalServerError },
            clock = fixedClock(0L),
        )
        assertEquals(PaywallConfigRepository.RefreshResult.FAILED, repo.refresh())
        assertTrue(repo.paywallEnabled.first()) // still gating ON
    }

    @Test
    fun `a fetch failure retains a previously cached flag`() = runTest {
        val ds = dataStore()
        // First, cache paywallEnabled=false successfully.
        val ok = PaywallConfigRepository(
            dataStore = ds,
            api = api(intArrayOf(0)) { """{"paywallEnabled":false}""" to HttpStatusCode.OK },
            clock = fixedClock(0L),
        )
        ok.refresh()
        assertFalse(ok.paywallEnabled.first())

        // A later forced refresh that fails must keep the cached false, not revert to the default true.
        val fails = PaywallConfigRepository(
            dataStore = ds,
            api = api(intArrayOf(0)) { "" to HttpStatusCode.ServiceUnavailable },
            clock = fixedClock(100_000_000L),
        )
        assertEquals(PaywallConfigRepository.RefreshResult.FAILED, fails.refresh(force = true))
        assertFalse(fails.paywallEnabled.first())
    }

    @Test
    fun `refresh is a no-op within the TTL`() = runTest {
        val ds = dataStore()
        val reqs = intArrayOf(0)
        val repo = PaywallConfigRepository(
            dataStore = ds,
            api = api(reqs) { """{"paywallEnabled":true}""" to HttpStatusCode.OK },
            clock = fixedClock(1_000L),
        )
        assertEquals(PaywallConfigRepository.RefreshResult.FETCHED, repo.refresh())

        // Same clock → within TTL → no second network call.
        val again = PaywallConfigRepository(
            dataStore = ds,
            api = api(reqs) { """{"paywallEnabled":false}""" to HttpStatusCode.OK },
            clock = fixedClock(1_000L + PaywallConfigRepository.TTL_MS - 1),
        )
        assertEquals(PaywallConfigRepository.RefreshResult.FRESH_CACHE, again.refresh())
        assertEquals(1, reqs[0]) // still only the first fetch
    }

    @Test
    fun `force ignores the TTL and re-fetches`() = runTest {
        val ds = dataStore()
        val reqs = intArrayOf(0)
        PaywallConfigRepository(
            dataStore = ds,
            api = api(reqs) { """{"paywallEnabled":true}""" to HttpStatusCode.OK },
            clock = fixedClock(1_000L),
        ).refresh()

        val forced = PaywallConfigRepository(
            dataStore = ds,
            api = api(reqs) { """{"paywallEnabled":false}""" to HttpStatusCode.OK },
            clock = fixedClock(1_500L), // within TTL
        )
        assertEquals(PaywallConfigRepository.RefreshResult.FETCHED, forced.refresh(force = true))
        assertEquals(2, reqs[0])
        assertFalse(forced.paywallEnabled.first())
    }
}
