package mx.kompara.parsers.spec

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies the ECDSA-P256/SHA-256 signature on a [SignedSpecBundle] against the public key embedded
 * in this module (`resources/keys/spec-signing-public.pem`), then decodes the bundle (B-033).
 *
 * Why ECDSA P-256 (`SHA256withECDSA`) and not Ed25519: Ed25519 in `java.security` only arrived on
 * API 33, and Kompara's minSdk is 26 — ECDSA over the NIST P-256 curve is available on every target.
 *
 * Why verify the literal [SignedSpecBundle.payload] string: ECDSA signatures are non-deterministic
 * and JSON re-serialization is not byte-stable across the Node signer and the Kotlin client, so we
 * sign/verify the exact transmitted bytes and only parse the payload *after* the signature checks
 * out. A tampered payload, a signature made with the wrong key, or a malformed signature all fail
 * closed (return null) — the caller falls back to last-known-good / bundled specs.
 *
 * The signing key here is a committed *dev* keypair (see `backend/keys/dev/`). Real key management
 * (a rotated production key in a KMS, never committed) is tracked in techdebt before launch.
 */
class SpecBundleVerifier(
    publicKeyPem: String = loadEmbeddedPublicKeyPem(),
) {
    private val publicKey: PublicKey = parsePublicKey(publicKeyPem)

    /**
     * Verify [signed]'s signature and decode the inner [SpecBundle]. Returns null on any failure:
     * bad/forged signature, malformed base64, or undecodable payload JSON. Never throws.
     */
    fun verifyAndDecode(signed: SignedSpecBundle): SpecBundle? {
        if (!verify(signed)) return null
        return runCatching { SpecJson.json.decodeFromString(SpecBundle.serializer(), signed.payload) }
            .getOrNull()
    }

    /** True iff [signed.signature] is a valid signature over [signed.payload] for the embedded key. */
    fun verify(signed: SignedSpecBundle): Boolean = runCatching {
        val sigBytes = Base64.getDecoder().decode(signed.signature)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(signed.payload.toByteArray(Charsets.UTF_8))
        verifier.verify(sigBytes)
    }.getOrDefault(false)

    companion object {
        /** Read the embedded signing public key PEM from this module's resources. */
        fun loadEmbeddedPublicKeyPem(): String =
            SpecBundleVerifier::class.java.getResourceAsStream("/keys/spec-signing-public.pem")
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("embedded spec-signing public key resource is missing")

        private fun parsePublicKey(pem: String): PublicKey {
            val base64 = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "")
            val der = Base64.getDecoder().decode(base64)
            return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
        }
    }
}
