package mx.kompara.sync.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Token-scoped 401 invalidation (PR-E safety / codex review): a bearer-required call that 401s must
 * only clear the session when the request actually carried a token AND that token is still current.
 * Closes the race where an unconditional startup `/v1/me` call (B-056 grant merge / PR-E verification
 * refresh) fired while signed-out — or a stale call for a token replaced by a racing sign-in — would
 * otherwise wipe a freshly-written session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ApiClientAuthScopeTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun client(token: TokenProvider, invalidator: SessionInvalidator): ApiClient {
        val engine = MockEngine { respondError(HttpStatusCode.Unauthorized) }
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
        }
        return ApiClient(http, "http://test.local", token, { "device" }, invalidator)
    }

    @Test
    fun `a no-token (signed-out) 401 does NOT invalidate the session`() = runTest {
        var invalidations = 0
        val api = client(TokenProvider { null }, SessionInvalidator { invalidations++ })

        assertThrows(ApiException::class.java) { runBlocking { api.getMeFull() } }

        assertEquals(0, invalidations) // a startup /v1/me before login must not wipe anything
    }

    @Test
    fun `a current-token 401 DOES invalidate (genuine expiry)`() = runTest {
        var invalidations = 0
        val api = client(TokenProvider { "live-token" }, SessionInvalidator { invalidations++ })

        assertThrows(ApiException::class.java) { runBlocking { api.getMeFull() } }

        assertEquals(1, invalidations) // the current session's token was rejected → re-auth
    }

    @Test
    fun `a stale 401 for a token replaced mid-flight does NOT invalidate the new session`() = runTest {
        var invalidations = 0
        // First read (the captured 'sent' token) is the old "A"; every later read is the new "B" — as if
        // a racing sign-in / account switch replaced the session while this request was in flight.
        var first = true
        val provider = TokenProvider {
            if (first) { first = false; "A" } else "B"
        }
        val api = client(provider, SessionInvalidator { invalidations++ })

        assertThrows(ApiException::class.java) { runBlocking { api.getMeFull() } }

        assertEquals(0, invalidations) // must not wipe the newly-written "B" session
    }
}
