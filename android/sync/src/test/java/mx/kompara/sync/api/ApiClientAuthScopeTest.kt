package mx.kompara.sync.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Token-scoped 401 invalidation (PR-E safety / codex review): a bearer-required call that 401s must
 * only clear the session when the request actually carried a token AND that token is still current.
 * Closes the race where an unconditional startup `/v1/me` call (B-056 grant merge / PR-E verification
 * refresh) fired while signed-out — or a stale call for a token replaced by a racing sign-in — would
 * otherwise wipe a freshly-written session. Each test also asserts the WIRE Authorization header, so the
 * sent token is provably the one the scoping decision compares (no TOCTOU between capture and send).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ApiClientAuthScopeTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** Captures the Authorization header each request carried (so a test can assert what went on the wire). */
    private fun client(
        token: TokenProvider,
        invalidator: SessionInvalidator,
        sentAuthHeaders: MutableList<String?>,
    ): ApiClient {
        val engine = MockEngine { req ->
            sentAuthHeaders += req.headers[HttpHeaders.Authorization]
            respondError(HttpStatusCode.Unauthorized)
        }
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
        }
        return ApiClient(http, "http://test.local", token, { "device" }, invalidator)
    }

    @Test
    fun `a no-token (signed-out) 401 does NOT invalidate and carries no auth header`() = runTest {
        var invalidations = 0
        val headers = mutableListOf<String?>()
        val api = client(TokenProvider { null }, SessionInvalidator { invalidations++ }, headers)

        assertThrows(ApiException::class.java) { runBlocking { api.getMeFull() } }

        assertNull(headers.single()) // no token sent
        assertEquals(0, invalidations) // a startup /v1/me before login must not wipe anything
    }

    @Test
    fun `a current-token 401 DOES invalidate (genuine expiry)`() = runTest {
        var invalidations = 0
        val headers = mutableListOf<String?>()
        val api = client(TokenProvider { "live-token" }, SessionInvalidator { invalidations++ }, headers)

        assertThrows(ApiException::class.java) { runBlocking { api.getMeFull() } }

        assertEquals("Bearer live-token", headers.single()) // the current token went on the wire
        assertEquals(1, invalidations) // it was rejected → re-auth
    }

    @Test
    fun `a stale 401 for a token replaced mid-flight does NOT invalidate the new session`() = runTest {
        var invalidations = 0
        val headers = mutableListOf<String?>()
        // First read (the captured 'sent' token, which bearer() now binds to) is the old "A"; every later
        // read — incl. ensureOkAuthed's current-token comparison — is the new "B", as if a racing sign-in
        // replaced the session while this request was in flight.
        var first = true
        val provider = TokenProvider { if (first) { first = false; "A" } else "B" }
        val api = client(provider, SessionInvalidator { invalidations++ }, headers)

        assertThrows(ApiException::class.java) { runBlocking { api.getMeFull() } }

        assertEquals("Bearer A", headers.single()) // the request carried the OLD token it was built with
        assertEquals(0, invalidations) // the 401 is for stale A, not the current B → must not wipe B
    }
}
