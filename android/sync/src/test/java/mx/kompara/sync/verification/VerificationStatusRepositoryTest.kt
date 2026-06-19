package mx.kompara.sync.verification

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
 * [VerificationStatusRepository] tests (PR-E): fail-closed default (unverified) when empty, a first
 * fetch caching `verified`, [markVerified]/[reset], sticky-positive on a failed refresh, revocation on
 * a successful `false`, and TTL gating. Mirrors PaywallConfigRepositoryTest's harness.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VerificationStatusRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private lateinit var tempFile: File

    @Before fun setUp() {
        tempFile = File.createTempFile("verification_test", ".preferences_pb").also { it.delete() }
    }

    @After fun tearDown() {
        tempFile.delete()
    }

    private fun dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { tempFile })

    private fun fixedClock(epochMillis: Long): Clock =
        Clock.fixed(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)

    private fun meBody(verified: Boolean): String =
        """{"driver":{"id":"d1","phone":"+5215555555","tier":"free"},"verified":$verified}"""

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
        return ApiClient(http, "http://test.local", { "token" }, { "device-uuid" })
    }

    @Test
    fun `defaults to unverified when the cache is empty (fail closed)`() = runTest {
        val repo = VerificationStatusRepository(
            dataStore = dataStore(),
            api = api(intArrayOf(0)) { error("not called") },
            clock = fixedClock(0L),
        )
        assertFalse(repo.verified.first())
        assertFalse(repo.current())
    }

    @Test
    fun `first fetch caches a verified driver`() = runTest {
        val reqs = intArrayOf(0)
        val repo = VerificationStatusRepository(
            dataStore = dataStore(),
            api = api(reqs) { meBody(verified = true) to HttpStatusCode.OK },
            clock = fixedClock(10_000L),
        )
        assertEquals(VerificationStatusRepository.RefreshResult.FETCHED, repo.refresh())
        assertEquals(1, reqs[0])
        assertTrue(repo.verified.first())
    }

    @Test
    fun `markVerified flips the flag immediately with no network`() = runTest {
        val repo = VerificationStatusRepository(
            dataStore = dataStore(),
            api = api(intArrayOf(0)) { error("not called") },
            clock = fixedClock(0L),
        )
        repo.markVerified(repo.sessionGeneration())
        assertTrue(repo.verified.first())
    }

    @Test
    fun `reset clears a verified flag (logout - resale guard)`() = runTest {
        val repo = VerificationStatusRepository(
            dataStore = dataStore(),
            api = api(intArrayOf(0)) { error("not called") },
            clock = fixedClock(0L),
        )
        repo.markVerified(repo.sessionGeneration())
        assertTrue(repo.verified.first())
        repo.reset()
        assertFalse(repo.verified.first())
    }

    @Test
    fun `an import mark from a since-reset session is discarded (no cross-account leak)`() = runTest {
        val repo = VerificationStatusRepository(
            dataStore = dataStore(),
            api = api(intArrayOf(0)) { error("not called") },
            clock = fixedClock(0L),
        )
        val captured = repo.sessionGeneration() // import begins...
        repo.reset() // ...a logout / 401 lands mid-import (bumps the generation)
        repo.markVerified(captured) // ...import completes; the mark is for a session that's gone

        assertFalse(repo.verified.first()) // stale positive discarded
    }

    @Test
    fun `a stale refresh does not overwrite a concurrent import mark`() = runTest {
        val ds = dataStore()
        lateinit var repo: VerificationStatusRepository
        // The refresh's in-flight fetch is interleaved with an import marking verified: the engine
        // block marks (bumping the generation) before returning a pre-import `false`.
        val engine = MockEngine { _ ->
            repo.markVerified(repo.sessionGeneration())
            respond(meBody(verified = false), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
        }
        repo = VerificationStatusRepository(
            dataStore = ds,
            api = ApiClient(http, "http://test.local", { "token" }, { "device-uuid" }),
            clock = fixedClock(0L),
        )

        val result = repo.refresh(force = true)

        assertEquals(VerificationStatusRepository.RefreshResult.STALE, result)
        assertTrue(repo.verified.first()) // the import's true survives the racing refresh
    }

    @Test
    fun `a failed refresh keeps a previously-verified flag (sticky-positive)`() = runTest {
        val ds = dataStore()
        VerificationStatusRepository(
            dataStore = ds,
            api = api(intArrayOf(0)) { meBody(verified = true) to HttpStatusCode.OK },
            clock = fixedClock(0L),
        ).refresh()

        val fails = VerificationStatusRepository(
            dataStore = ds,
            api = api(intArrayOf(0)) { "" to HttpStatusCode.ServiceUnavailable },
            clock = fixedClock(100_000_000L),
        )
        assertEquals(VerificationStatusRepository.RefreshResult.FAILED, fails.refresh(force = true))
        assertTrue(fails.verified.first()) // a transient blip never re-locks a verified driver
    }

    @Test
    fun `a successful false downgrades a verified flag (revocation)`() = runTest {
        val ds = dataStore()
        VerificationStatusRepository(
            dataStore = ds,
            api = api(intArrayOf(0)) { meBody(verified = true) to HttpStatusCode.OK },
            clock = fixedClock(0L),
        ).refresh()

        val revoked = VerificationStatusRepository(
            dataStore = ds,
            api = api(intArrayOf(0)) { meBody(verified = false) to HttpStatusCode.OK },
            clock = fixedClock(100_000_000L),
        )
        assertEquals(VerificationStatusRepository.RefreshResult.FETCHED, revoked.refresh(force = true))
        assertFalse(revoked.verified.first())
    }

    @Test
    fun `a refresh whose fetch races a reset is discarded (STALE), the clear wins`() = runTest {
        val ds = dataStore()
        // The MockEngine block is suspending, so it can simulate a reset (logout / 401) landing WHILE
        // this /v1/me fetch is in flight — bumping the session generation before the response returns.
        val engine = MockEngine { _ ->
            VerificationStatusRepository.clearVerification(ds)
            respond(meBody(verified = true), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
        }
        val repo = VerificationStatusRepository(
            dataStore = ds,
            api = ApiClient(http, "http://test.local", { "token" }, { "device-uuid" }),
            clock = fixedClock(0L),
        )

        val result = repo.refresh(force = true)

        assertEquals(VerificationStatusRepository.RefreshResult.STALE, result)
        assertFalse(repo.verified.first()) // the racing clear wins — no stale account-A verified=true
    }

    @Test
    fun `refresh is a no-op within the TTL`() = runTest {
        val ds = dataStore()
        val reqs = intArrayOf(0)
        VerificationStatusRepository(
            dataStore = ds,
            api = api(reqs) { meBody(verified = true) to HttpStatusCode.OK },
            clock = fixedClock(1_000L),
        ).refresh()

        val again = VerificationStatusRepository(
            dataStore = ds,
            api = api(reqs) { meBody(verified = false) to HttpStatusCode.OK },
            clock = fixedClock(1_000L + VerificationStatusRepository.TTL_MS - 1),
        )
        assertEquals(VerificationStatusRepository.RefreshResult.FRESH_CACHE, again.refresh())
        assertEquals(1, reqs[0]) // still only the first fetch
    }
}
