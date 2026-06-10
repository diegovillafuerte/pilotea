package mx.kompara.parsers.spec

import kotlinx.serialization.Serializable

/**
 * An over-the-air parser-config bundle (B-033). Carries every active [ParserSpec] plus per-package
 * kill switches, so a host-app UI change is fixed by shipping a new bundle rather than a Play
 * release. The whole point: drivers pick up the fix on the next refresh cycle (hours, not days).
 *
 * This is the *inner* payload — the thing that is signed. It is transmitted wrapped in a
 * [SignedSpecBundle] whose `payload` field is the exact JSON string of this object that was signed,
 * so the client verifies the literal bytes and there is no cross-platform canonicalization to get
 * wrong (the signer in `backend/scripts/sign-spec-bundle.ts` signs the same string).
 *
 * @property bundleVersion monotonic counter; the client rejects a fetched bundle whose version is
 *   not strictly greater than its last-known-good, so a rolled-back/replayed bundle can't downgrade
 *   a driver onto a stale (or maliciously old) spec.
 * @property generatedAt ISO-8601 timestamp the bundle was produced (audit/telemetry only).
 * @property specs every active spec, across all host packages and version ranges.
 * @property killSwitches package → disabled. A `true` entry removes that package's specs from the
 *   active set so a broken parser goes dark within one refresh cycle; the pipeline then emits
 *   [mx.kompara.parsers.spec] "disabled" state for an overlay to show "actualizando soporte…".
 */
@Serializable
data class SpecBundle(
    val bundleVersion: Int,
    val generatedAt: String,
    val specs: List<ParserSpec> = emptyList(),
    val killSwitches: Map<String, Boolean> = emptyMap(),
) {
    /** Packages explicitly disabled in this bundle (kill switch flipped on). */
    fun disabledPackages(): Set<String> =
        killSwitches.filterValues { it }.keys

    /** Specs that survive the kill switches — what a registry should actually load. */
    fun activeSpecs(): List<ParserSpec> {
        val disabled = disabledPackages()
        return specs.filterNot { it.targetPackage in disabled }
    }
}

/**
 * The signed wire envelope for a [SpecBundle]. [payload] is the canonical JSON string of the bundle
 * (the exact bytes that were signed); [signature] is a base64-encoded ECDSA-P256/SHA-256 signature
 * (DER) over those UTF-8 bytes, produced with the backend dev signing key and verified against the
 * public key embedded in this module ([SpecBundleVerifier]).
 *
 * Carrying the signed string verbatim (rather than re-serializing the parsed bundle) is deliberate:
 * ECDSA is non-deterministic and JSON re-serialization is not byte-stable across platforms, so we
 * verify the literal `payload` and only then parse it.
 */
@Serializable
data class SignedSpecBundle(
    val payload: String,
    val signature: String,
)
