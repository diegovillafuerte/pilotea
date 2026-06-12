package mx.kompara.sync.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mx.kompara.sync.api.ApiClient
import mx.kompara.sync.api.ApiException
import mx.kompara.sync.api.TokenProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlin.io.path.createTempDirectory

/**
 * Unit tests for the `:sync` auth layer, driven by a Ktor [MockEngine] fake HTTP
 * transport and a real file-backed preferences [DataStore] in a temp dir.
 *
 * Covers: OTP request/verify happy path, wrong-code error surfacing, session
 * token + profile persistence (sessionState transitions), anonymous device-id
 * stability across calls, the deviceId sent on verify, and logout clearing the
 * session while retaining the device id.
 */
class AuthRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var dataStore: DataStore<Preferences>

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** Records every request the client made, for assertions. */
    private val recorded = mutableListOf<HttpRequestData>()

    @Before
    fun setUp() {
        tempDir = createTempDirectory(prefix = "kompara-auth-test").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    /** Build an ApiClient over a MockEngine whose handler is supplied per test. */
    private fun buildApi(
        tokenProvider: TokenProvider,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): ApiClient {
        val engine = MockEngine { req ->
            recorded += req
            handler(req)
        }
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
        }
        return ApiClient(http, "http://test.local", tokenProvider, { "test-device-id" })
    }

    private fun newDataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir, "auth-${UUID.randomUUID()}.preferences_pb") },
        )

    private fun MockRequestHandleScope.jsonResponse(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData =
        respond(
            content = ByteReadChannel(body),
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

    @Test
    fun `verifyOtp persists token and flips sessionState to authenticated`() = runTest {
        dataStore = newDataStore()
        val tokenProvider = TokenProvider { dataStore.data.first()[
            androidx.datastore.preferences.core.stringPreferencesKey("session_token")
        ] }
        val api = buildApi(tokenProvider) { req ->
            when {
                req.url.encodedPath.endsWith("/v1/devices/register") -> jsonResponse("""{"ok":true}""")
                req.url.encodedPath.endsWith("/v1/auth/otp/request") -> jsonResponse("""{"ok":true}""")
                req.url.encodedPath.endsWith("/v1/auth/otp/verify") -> jsonResponse(
                    """{"token":"abc123def456","driver":{"id":"d-1","phone":"+5215512345678","tier":"free"}}""",
                )
                else -> jsonResponse("""{"error":"unexpected"}""", HttpStatusCode.NotFound)
            }
        }
        val repo = AuthRepository(dataStore, api, json)

        // Starts anonymous.
        assertEquals(SessionState.Anonymous, repo.sessionState.first())

        repo.requestOtp("+5215512345678")
        val driver = repo.verifyOtp("+5215512345678", "123456")

        assertEquals("d-1", driver.id)
        val state = repo.sessionState.first()
        assertTrue(state is SessionState.Authenticated)
        assertEquals("d-1", (state as SessionState.Authenticated).driver.id)
        assertEquals("abc123def456", repo.currentToken())
    }

    @Test
    fun `verifyOtp sends the persisted anonymous deviceId`() = runTest {
        dataStore = newDataStore()
        val api = buildApi({ null }) { req ->
            when {
                req.url.encodedPath.endsWith("/v1/devices/register") -> jsonResponse("""{"ok":true}""")
                req.url.encodedPath.endsWith("/v1/auth/otp/verify") -> jsonResponse(
                    """{"token":"tok","driver":{"id":"d-1","phone":"+521","tier":"free"}}""",
                )
                else -> jsonResponse("""{"ok":true}""")
            }
        }
        val repo = AuthRepository(dataStore, api, json)

        val deviceId = repo.deviceId()
        repo.verifyOtp("+521", "123456")

        val verifyReq = recorded.last { it.url.encodedPath.endsWith("/v1/auth/otp/verify") }
        val sentBody = (verifyReq.body as io.ktor.http.content.TextContent).text
        assertTrue("verify body should carry the deviceId", sentBody.contains(deviceId))
    }

    @Test
    fun `deviceId is stable across calls and persists`() = runTest {
        dataStore = newDataStore()
        val api = buildApi({ null }) { jsonResponse("""{"ok":true}""") }
        val repo = AuthRepository(dataStore, api, json)

        val first = repo.deviceId()
        val second = repo.deviceId()
        assertEquals(first, second)
        // A fresh repository over the SAME datastore sees the same id.
        val repo2 = AuthRepository(dataStore, api, json)
        assertEquals(first, repo2.deviceId())
        // It is a valid UUID.
        assertNotNull(UUID.fromString(first))
    }

    @Test
    fun `deviceId registers the device anonymously on first generation`() = runTest {
        dataStore = newDataStore()
        val api = buildApi({ null }) { jsonResponse("""{"ok":true}""") }
        val repo = AuthRepository(dataStore, api, json)

        repo.deviceId()

        val registerReqs = recorded.filter { it.url.encodedPath.endsWith("/v1/devices/register") }
        assertEquals(1, registerReqs.size)
    }

    @Test
    fun `verifyOtp surfaces a 401 wrong code as ApiException and stays anonymous`() = runTest {
        dataStore = newDataStore()
        val api = buildApi({ null }) { req ->
            when {
                req.url.encodedPath.endsWith("/v1/auth/otp/verify") -> jsonResponse(
                    """{"error":"Invalid or expired code"}""",
                    HttpStatusCode.Unauthorized,
                )
                else -> jsonResponse("""{"ok":true}""")
            }
        }
        val repo = AuthRepository(dataStore, api, json)

        try {
            repo.verifyOtp("+521", "000000")
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }
        assertEquals(SessionState.Anonymous, repo.sessionState.first())
        assertNull(repo.currentToken())
    }

    @Test
    fun `logout clears the session but keeps the device id`() = runTest {
        dataStore = newDataStore()
        val tokenProvider = TokenProvider { dataStore.data.first()[
            androidx.datastore.preferences.core.stringPreferencesKey("session_token")
        ] }
        val api = buildApi(tokenProvider) { req ->
            when {
                req.url.encodedPath.endsWith("/v1/auth/otp/verify") -> jsonResponse(
                    """{"token":"tok","driver":{"id":"d-1","phone":"+521","tier":"free"}}""",
                )
                else -> jsonResponse("""{"ok":true}""")
            }
        }
        val repo = AuthRepository(dataStore, api, json)

        val deviceId = repo.deviceId()
        repo.verifyOtp("+521", "123456")
        assertTrue(repo.sessionState.first() is SessionState.Authenticated)

        repo.logout()

        assertEquals(SessionState.Anonymous, repo.sessionState.first())
        assertNull(repo.currentToken())
        // Device identity is retained across logout.
        assertEquals(deviceId, repo.deviceId())
        // The logout request carried the bearer token.
        val logoutReq = recorded.last { it.url.encodedPath.endsWith("/v1/auth/logout") }
        assertEquals("Bearer tok", logoutReq.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `deleteAccount calls DELETE me and clears the session, keeping the device id`() = runTest {
        dataStore = newDataStore()
        val tokenProvider = TokenProvider { dataStore.data.first()[
            androidx.datastore.preferences.core.stringPreferencesKey("session_token")
        ] }
        val api = buildApi(tokenProvider) { req ->
            when {
                req.url.encodedPath.endsWith("/v1/auth/otp/verify") -> jsonResponse(
                    """{"token":"tok","driver":{"id":"d-1","phone":"+521","tier":"free"}}""",
                )
                req.url.encodedPath.endsWith("/v1/me") -> jsonResponse("""{"ok":true}""")
                else -> jsonResponse("""{"ok":true}""")
            }
        }
        val repo = AuthRepository(dataStore, api, json)

        val deviceId = repo.deviceId()
        repo.verifyOtp("+521", "123456")
        assertTrue(repo.sessionState.first() is SessionState.Authenticated)

        repo.deleteAccount()

        assertEquals(SessionState.Anonymous, repo.sessionState.first())
        assertNull(repo.currentToken())
        assertEquals(deviceId, repo.deviceId())
        // The delete request hit DELETE /v1/me with the bearer token.
        val delReq = recorded.last { it.url.encodedPath.endsWith("/v1/me") }
        assertEquals("DELETE", delReq.method.value)
        assertEquals("Bearer tok", delReq.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `a 401 on a bearer call clears the session via the invalidator`() = runTest {
        dataStore = newDataStore()
        val tokenKey = androidx.datastore.preferences.core.stringPreferencesKey("session_token")
        val tokenProvider = TokenProvider { dataStore.data.first()[tokenKey] }
        // The invalidator mirrors the DI provider: drop the token + driver keys.
        val invalidator = mx.kompara.sync.api.SessionInvalidator {
            dataStore.edit { prefs ->
                prefs.remove(tokenKey)
                prefs.remove(androidx.datastore.preferences.core.stringPreferencesKey("driver_profile_json"))
            }
        }
        val engine = MockEngine { req ->
            recorded += req
            when {
                req.url.encodedPath.endsWith("/v1/auth/otp/verify") -> jsonResponse(
                    """{"token":"tok","driver":{"id":"d-1","phone":"+521","tier":"free"}}""",
                )
                // The expired-session case: a valid-looking bearer call comes back 401.
                req.url.encodedPath.endsWith("/v1/me") -> jsonResponse(
                    """{"error":"Invalid or expired session"}""",
                    HttpStatusCode.Unauthorized,
                )
                else -> jsonResponse("""{"ok":true}""")
            }
        }
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
        }
        val api = ApiClient(http, "http://test.local", tokenProvider, { "dev" }, invalidator)
        val repo = AuthRepository(dataStore, api, json)

        repo.verifyOtp("+521", "123456")
        assertTrue(repo.sessionState.first() is SessionState.Authenticated)

        // A profile refresh now 401s (session expired); the invalidator clears local auth.
        try {
            repo.refreshProfile()
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
        }
        assertEquals(SessionState.Anonymous, repo.sessionState.first())
        assertNull(repo.currentToken())
    }

    @Test
    fun `updateProfile sends PATCH and re-caches the returned driver`() = runTest {
        dataStore = newDataStore()
        val tokenProvider = TokenProvider { dataStore.data.first()[
            androidx.datastore.preferences.core.stringPreferencesKey("session_token")
        ] }
        val api = buildApi(tokenProvider) { req ->
            when {
                req.url.encodedPath.endsWith("/v1/auth/otp/verify") -> jsonResponse(
                    """{"token":"tok","driver":{"id":"d-1","phone":"+521","tier":"free"}}""",
                )
                req.url.encodedPath.endsWith("/v1/me") -> jsonResponse(
                    """{"driver":{"id":"d-1","phone":"+521","name":"Ana","city":"cdmx","platforms":["uber"],"tier":"free"}}""",
                )
                else -> jsonResponse("""{"ok":true}""")
            }
        }
        val repo = AuthRepository(dataStore, api, json)
        repo.verifyOtp("+521", "123456")

        val updated = repo.updateProfile(name = "Ana", city = "cdmx", platforms = listOf("uber"))
        assertEquals("Ana", updated.name)
        assertEquals("cdmx", updated.city)

        val state = repo.sessionState.first()
        assertTrue(state is SessionState.Authenticated)
        assertEquals("Ana", (state as SessionState.Authenticated).driver.name)
    }
}
