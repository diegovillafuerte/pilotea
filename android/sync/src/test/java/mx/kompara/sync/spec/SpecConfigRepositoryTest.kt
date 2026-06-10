package mx.kompara.sync.spec

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import mx.kompara.parsers.spec.BundledSpecs
import mx.kompara.parsers.spec.LoadedSpecs
import mx.kompara.parsers.spec.SpecBundleVerifier
import mx.kompara.parsers.spec.SpecJson
import mx.kompara.sync.api.ApiClient
import mx.kompara.sync.api.TokenProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Unit tests for [SpecConfigRepository] (B-033): the over-the-air parser-config client.
 *
 * Covers the acceptance criteria end-to-end with no Android dependency — a Ktor [MockEngine] fakes
 * the backend `/v1/parser-configs/bundle` endpoint, an in-memory [SpecConfigStore] stands in for
 * `filesDir`, and a test-minted P-256 keypair signs bundles so the real signature path runs.
 *
 *  - a validly-signed, newer bundle is applied (and its kill switches remove that platform's spec);
 *  - a non-monotonic bundleVersion is rejected (no downgrade / replay);
 *  - a failed/transport-error fetch falls back to last-known-good (offline drivers keep specs);
 *  - with no cache and no network, the bundled `:parsers` specs are served;
 *  - a tampered/wrong-key signature is refused (covered in :parsers SpecBundleVerifierTest too);
 *  - a debug-build `spec-override.json` wins, unverified.
 */
class SpecConfigRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val dispatcher = StandardTestDispatcher()

    // ─── test signing key (the verifier trusts this key's public half) ───
    private val signingKeyPair: KeyPair =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

    private fun pemOf(key: PublicKey): String =
        "-----BEGIN PUBLIC KEY-----\n${Base64.getEncoder().encodeToString(key.encoded)}\n-----END PUBLIC KEY-----\n"

    private fun verifier() = SpecBundleVerifier(pemOf(signingKeyPair.public))

    /** Sign a SpecBundle JSON [payload] with the test private key, returning the signed envelope JSON. */
    private fun signEnvelope(payload: String): String {
        val s = Signature.getInstance("SHA256withECDSA")
        s.initSign(signingKeyPair.private)
        s.update(payload.toByteArray(Charsets.UTF_8))
        val sig = Base64.getEncoder().encodeToString(s.sign())
        return """{"payload":${JsonPrimitive(payload)},"signature":"$sig"}"""
    }

    /** A minimal valid SpecBundle payload string at [version], optionally disabling Uber. */
    private fun bundlePayload(version: Int, killUber: Boolean = false): String {
        val uberSpec = SpecJson.encodeSpec(BundledSpecs.load().first { it.targetPackage == "com.ubercab.driver" })
        val kill = if (killUber) ""","killSwitches":{"com.ubercab.driver":true}""" else ""
        return """{"bundleVersion":$version,"generatedAt":"2026-06-10T00:00:00Z","specs":[$uberSpec]$kill}"""
    }

    // ─── in-memory store ───
    private class FakeStore(var cached: String? = null, var override: String? = null) : SpecConfigStore {
        override fun readCachedBundle(): String? = cached
        override fun writeCachedBundle(signedJson: String) { cached = signedJson }
        override fun readDevOverride(): String? = override
    }

    private fun apiServing(handlerBody: () -> Pair<String, HttpStatusCode>): ApiClient {
        val engine = MockEngine {
            val (body, status) = handlerBody()
            if (status.value >= 400) {
                respondError(status)
            } else {
                respond(
                    content = ByteReadChannel(body),
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
        }
        return ApiClient(http, "http://test.local", TokenProvider { null }, { "test-device-id" })
    }

    private fun repo(
        api: ApiClient,
        store: SpecConfigStore,
        isDebug: Boolean = false,
    ) = SpecConfigRepository(
        api = api,
        store = store,
        verifier = verifier(),
        bundledSpecs = { BundledSpecs.load() },
        ioDispatcher = dispatcher,
        isDebugBuild = isDebug,
    )

    @Test
    fun `bundled fallback when no cache and never refreshed`() = runTest(dispatcher) {
        val r = repo(apiServing { "" to HttpStatusCode.OK }, FakeStore())
        val loaded = r.specs.first()
        assertEquals(LoadedSpecs.Source.BUNDLED, loaded.source)
        // Both bundled specs available.
        assertEquals(BundledSpecs.load().size, loaded.registry.all().size)
    }

    @Test
    fun `valid newer bundle is fetched, verified, applied, and persisted`() = runTest(dispatcher) {
        val store = FakeStore()
        val r = repo(apiServing { signEnvelope(bundlePayload(version = 2)) to HttpStatusCode.OK }, store)

        val result = r.refresh()
        assertTrue(result is RefreshResult.Applied)
        val loaded = r.specs.first()
        assertEquals(LoadedSpecs.Source.REMOTE, loaded.source)
        assertEquals(2, loaded.bundleVersion)
        // Persisted as last-known-good.
        assertTrue(store.cached != null)
    }

    @Test
    fun `kill switch removes the disabled platform from the active registry`() = runTest(dispatcher) {
        val r = repo(apiServing { signEnvelope(bundlePayload(version = 3, killUber = true)) to HttpStatusCode.OK }, FakeStore())
        r.refresh()
        val loaded = r.specs.first()
        assertTrue("uber should be disabled", loaded.isDisabled("com.ubercab.driver"))
        assertTrue(loaded.registry.all().none { it.targetPackage == "com.ubercab.driver" })
    }

    @Test
    fun `non-monotonic bundleVersion is rejected`() = runTest(dispatcher) {
        // First apply v5, then serve v3 (older) — must be rejected, v5 retained.
        var payload = bundlePayload(version = 5)
        val r = repo(apiServing { signEnvelope(payload) to HttpStatusCode.OK }, FakeStore())
        r.refresh()
        assertEquals(5, r.specs.first().bundleVersion)

        payload = bundlePayload(version = 3)
        val rejected = r.refresh()
        assertTrue(rejected is RefreshResult.Rejected)
        assertEquals(5, r.specs.first().bundleVersion) // unchanged
    }

    @Test
    fun `bad fetch falls back to last-known-good`() = runTest(dispatcher) {
        // Seed a cached, validly-signed v4 bundle, then make the network 500.
        val store = FakeStore(cached = signEnvelope(bundlePayload(version = 4)))
        val r = repo(apiServing { "" to HttpStatusCode.InternalServerError }, store)

        // Initial state already loaded the cache as last-known-good.
        assertEquals(LoadedSpecs.Source.REMOTE, r.specs.first().source)
        assertEquals(4, r.specs.first().bundleVersion)

        val result = r.refresh()
        assertTrue(result is RefreshResult.Failed)
        // Still on the cached v4 — the failed fetch did not clobber it.
        assertEquals(4, r.specs.first().bundleVersion)
    }

    @Test
    fun `tampered cached bundle is discarded in favor of bundled specs`() = runTest(dispatcher) {
        // A cache whose payload was altered after signing fails verification on load. The payload
        // lives JSON-escaped inside the envelope ("\"bundleVersion\":9"), so tamper the escaped form.
        val good = signEnvelope(bundlePayload(version = 9))
        val tampered = good.replace("\\\"bundleVersion\\\":9", "\\\"bundleVersion\\\":99")
        assertTrue("tamper precondition: payload must actually change", tampered != good)
        val r = repo(apiServing { "" to HttpStatusCode.OK }, FakeStore(cached = tampered))
        assertEquals(LoadedSpecs.Source.BUNDLED, r.specs.first().source)
    }

    @Test
    fun `dev override wins in debug and is used unverified`() = runTest(dispatcher) {
        // Override is a raw SpecBundle (not signed) disabling Uber.
        val overrideBundle =
            """{"bundleVersion":1,"generatedAt":"x","specs":[],"killSwitches":{"com.ubercab.driver":true}}"""
        val r = repo(
            apiServing { signEnvelope(bundlePayload(version = 50)) to HttpStatusCode.OK },
            FakeStore(override = overrideBundle),
            isDebug = true,
        )
        // Even at startup the override is active.
        assertEquals(LoadedSpecs.Source.DEV_OVERRIDE, r.specs.first().source)
        // And refresh keeps it (network skipped entirely).
        val result = r.refresh()
        assertTrue(result is RefreshResult.Applied)
        assertEquals(LoadedSpecs.Source.DEV_OVERRIDE, r.specs.first().source)
        assertTrue(r.specs.first().isDisabled("com.ubercab.driver"))
    }

    @Test
    fun `dev override is ignored in release builds`() = runTest(dispatcher) {
        val overrideBundle = """{"bundleVersion":1,"generatedAt":"x","specs":[],"killSwitches":{}}"""
        val r = repo(apiServing { "" to HttpStatusCode.OK }, FakeStore(override = overrideBundle), isDebug = false)
        // No cache, override ignored (release) -> bundled.
        assertEquals(LoadedSpecs.Source.BUNDLED, r.specs.first().source)
    }

    @Test
    fun `wrong-key signature on a fetched bundle is refused`() = runTest(dispatcher) {
        // Sign with a DIFFERENT key than the verifier trusts.
        val otherKp = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val payload = bundlePayload(version = 2)
        val s = Signature.getInstance("SHA256withECDSA")
        s.initSign(otherKp.private)
        s.update(payload.toByteArray(Charsets.UTF_8))
        val badSig = Base64.getEncoder().encodeToString(s.sign())
        val envelope = """{"payload":${JsonPrimitive(payload)},"signature":"$badSig"}"""

        val r = repo(apiServing { envelope to HttpStatusCode.OK }, FakeStore())
        val result = r.refresh()
        assertTrue(result is RefreshResult.Failed)
        assertEquals(LoadedSpecs.Source.BUNDLED, r.specs.first().source)
        assertNull(r.specs.first().bundleVersion)
    }
}
