package mx.kompara.sync.referral

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mx.kompara.sync.aggregate.SessionGate
import mx.kompara.sync.api.ApiClient
import mx.kompara.sync.api.DeviceIdProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ReferralRepository] tests (B-056): getMine mapping, redeem happy path + device-id forwarding,
 * Spanish error mapping for each abuse rule (the backend's exact strings), the local empty-code guard,
 * and the signed-out gate. Runs against a Ktor MockEngine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReferralRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val deviceId = "device-uuid-123"

    private fun api(
        requests: MutableList<HttpRequestData> = mutableListOf(),
        handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): ApiClient {
        val engine = MockEngine { req ->
            requests += req
            handler(req)
        }
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
        }
        return ApiClient(http, "http://test.local", { "session-token" }, { deviceId })
    }

    private fun MockRequestHandleScope.okJson(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun MockRequestHandleScope.errorJson(status: HttpStatusCode, message: String) = respond(
        content = """{"error":${json.encodeToString(message)}}""",
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun repo(
        api: ApiClient,
        signedIn: Boolean = true,
    ) = ReferralRepository(
        api = api,
        session = SessionGate { signedIn },
        deviceIdProvider = DeviceIdProvider { deviceId },
    )

    @Test
    fun `getMine maps the code and stats`() = runTest {
        val body = """
            {"code":"ABCD2345","redemptionsCount":3,"premiumDaysEarned":42,
             "premiumUntilMillis":1900000000000}
        """.trimIndent()
        val result = repo(api { okJson(body) }).getMine()

        assertTrue(result is ReferralResult.Success)
        val value = (result as ReferralResult.Success).value
        assertEquals("ABCD2345", value.code)
        assertEquals(3, value.redemptionsCount)
        assertEquals(42, value.premiumDaysEarned)
        assertEquals(1_900_000_000_000L, value.premiumUntilMillis)
    }

    @Test
    fun `redeem forwards the device id and maps the grant`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val body = """
            {"grantedDaysRedeemer":14,"grantedDaysReferrer":14,"premiumUntilMillis":1900000000000}
        """.trimIndent()
        val result = repo(api(requests) { okJson(body) }).redeem("ABCD2345")

        assertTrue(result is ReferralResult.Success)
        val value = (result as ReferralResult.Success).value
        assertEquals(14, value.grantedDaysRedeemer)
        assertEquals(14, value.grantedDaysReferrer)

        // The redeem request body carries the trimmed code + the device id.
        val sent = (requests.single().body as io.ktor.http.content.TextContent).text
        assertTrue(sent.contains("\"code\":\"ABCD2345\""))
        assertTrue(sent.contains("\"deviceId\":\"$deviceId\""))
    }

    @Test
    fun `redeem trims surrounding whitespace before sending`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val body = """{"grantedDaysRedeemer":14,"grantedDaysReferrer":14}"""
        repo(api(requests) { okJson(body) }).redeem("   ABCD2345  ")

        val sent = (requests.single().body as io.ktor.http.content.TextContent).text
        assertTrue(sent.contains("\"code\":\"ABCD2345\""))
    }

    @Test
    fun `redeem maps each backend Spanish error and its status`() = runTest {
        val cases = listOf(
            HttpStatusCode.NotFound to "Ese código no existe. Revisa que lo hayas escrito bien.",
            HttpStatusCode.BadRequest to "No puedes usar tu propio código de invitación.",
            HttpStatusCode.Conflict to "Ya canjeaste un código de invitación antes. Solo se puede una vez.",
            HttpStatusCode.Forbidden to
                "Los códigos de invitación son solo para conductores nuevos (cuentas de menos de 30 días).",
            HttpStatusCode.Conflict to "Este dispositivo ya canjeó un código de invitación.",
        )
        for ((status, message) in cases) {
            val result = repo(api { errorJson(status, message) }).redeem("ABCD2345")
            assertTrue(result is ReferralResult.Failure)
            val failure = result as ReferralResult.Failure
            assertEquals(message, failure.message)
            assertEquals(status.value, failure.status)
        }
    }

    @Test
    fun `redeem with a blank code fails locally without a network call`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val result = repo(api(requests) { okJson("{}") }).redeem("   ")

        assertTrue(result is ReferralResult.Failure)
        assertNull((result as ReferralResult.Failure).status) // local, no HTTP status
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `getMine maps a transport failure to a generic Spanish message`() = runTest {
        val result = repo(api { throw java.io.IOException("boom") }).getMine()
        assertTrue(result is ReferralResult.Failure)
        val failure = result as ReferralResult.Failure
        assertNull(failure.status)
        assertTrue(failure.message.contains("conexión"))
    }

    @Test
    fun `isSignedIn reflects the session gate`() = runTest {
        assertTrue(repo(api { okJson("{}") }, signedIn = true).isSignedIn())
        assertTrue(!repo(api { okJson("{}") }, signedIn = false).isSignedIn())
    }
}
