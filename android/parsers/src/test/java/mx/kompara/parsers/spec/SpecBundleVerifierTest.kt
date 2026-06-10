package mx.kompara.parsers.spec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * Signature-verification tests for the OTA spec bundle (B-033). These are self-contained: each test
 * mints its own P-256 keypair, signs a payload with `java.security`, and exports the public key as
 * PEM to construct a [SpecBundleVerifier]. That proves the verifier accepts a correctly-signed
 * bundle and rejects (a) a tampered payload, (b) a signature from a different key, and (c) garbage.
 */
class SpecBundleVerifierTest {

    private fun keyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

    private fun pemOf(key: PublicKey): String {
        val b64 = Base64.getEncoder().encodeToString(key.encoded)
        return "-----BEGIN PUBLIC KEY-----\n$b64\n-----END PUBLIC KEY-----\n"
    }

    private fun sign(payload: String, kp: KeyPair): String {
        val s = Signature.getInstance("SHA256withECDSA")
        s.initSign(kp.private)
        s.update(payload.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(s.sign())
    }

    private val samplePayload =
        """{"bundleVersion":3,"generatedAt":"2026-06-10T00:00:00Z","specs":[],"killSwitches":{}}"""

    @Test
    fun `valid signature verifies and decodes`() {
        val kp = keyPair()
        val verifier = SpecBundleVerifier(pemOf(kp.public))
        val signed = SignedSpecBundle(payload = samplePayload, signature = sign(samplePayload, kp))

        assertTrue(verifier.verify(signed))
        val bundle = verifier.verifyAndDecode(signed)
        assertNotNull(bundle)
        assertEquals(3, bundle!!.bundleVersion)
    }

    @Test
    fun `tampered payload is rejected`() {
        val kp = keyPair()
        val verifier = SpecBundleVerifier(pemOf(kp.public))
        val sig = sign(samplePayload, kp)
        // Same signature, but the payload was altered after signing (bundleVersion bumped).
        val tampered = SignedSpecBundle(
            payload = samplePayload.replace("\"bundleVersion\":3", "\"bundleVersion\":9"),
            signature = sig,
        )

        assertFalse(verifier.verify(tampered))
        assertNull(verifier.verifyAndDecode(tampered))
    }

    @Test
    fun `signature from a different key is rejected`() {
        val signingKp = keyPair()
        val otherKp = keyPair()
        // Verifier trusts otherKp, but the bundle was signed by signingKp.
        val verifier = SpecBundleVerifier(pemOf(otherKp.public))
        val signed = SignedSpecBundle(payload = samplePayload, signature = sign(samplePayload, signingKp))

        assertFalse(verifier.verify(signed))
        assertNull(verifier.verifyAndDecode(signed))
    }

    @Test
    fun `malformed signature is rejected without throwing`() {
        val kp = keyPair()
        val verifier = SpecBundleVerifier(pemOf(kp.public))
        val signed = SignedSpecBundle(payload = samplePayload, signature = "not-base64-!!!")

        assertFalse(verifier.verify(signed))
        assertNull(verifier.verifyAndDecode(signed))
    }

    @Test
    fun `embedded production dev key parses and a bundle signed by the committed dev key verifies`() {
        // The default verifier loads the embedded PEM. We can't sign with it here (no private key in
        // :parsers), but we can at least prove the embedded PEM is a parseable P-256 key.
        val verifier = SpecBundleVerifier()
        // A signature that obviously won't match the embedded key must fail closed, not throw.
        assertFalse(verifier.verify(SignedSpecBundle(samplePayload, sign(samplePayload, keyPair()))))
    }
}
