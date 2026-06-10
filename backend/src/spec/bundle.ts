/**
 * Signed parser-config bundle (B-033) — the server side of the OTA spec delivery contract shared
 * with the Android `:parsers` module (`SpecBundle` / `SignedSpecBundle` / `SpecBundleVerifier`).
 *
 * A {@link SpecBundle} carries every active parser spec plus per-package kill switches. It is
 * transmitted wrapped in a {@link SignedSpecBundle} whose `payload` is the EXACT JSON string that
 * was signed, and `signature` is a base64 ECDSA-P256/SHA-256 (DER) signature over those UTF-8 bytes.
 *
 * Why ship the signed string verbatim rather than re-serializing on the client: ECDSA is
 * non-deterministic and JSON re-serialization is not byte-stable across Node and Kotlin, so both
 * sides operate on the literal `payload`. The client verifies, THEN parses.
 *
 * Why ECDSA P-256 (not Ed25519): the Android client's minSdk is 26 and `java.security` only gained
 * Ed25519 on API 33; P-256 (`SHA256withECDSA`) is available on every target.
 */

import { createSign, createVerify, createPublicKey, type KeyObject } from "node:crypto";

/** A single parser spec as stored in `parser_configs.spec` (an opaque ParserSpec JSON object). */
export type ParserSpecJson = Record<string, unknown>;

/** The inner, signed payload. Field order here defines the canonical serialization. */
export interface SpecBundle {
  bundleVersion: number;
  generatedAt: string;
  specs: ParserSpecJson[];
  killSwitches: Record<string, boolean>;
}

/** The signed wire envelope returned by `GET /v1/parser-configs/bundle`. */
export interface SignedSpecBundle {
  payload: string;
  signature: string;
}

/**
 * Serialize a bundle to its canonical JSON string (the bytes that get signed). Plain
 * `JSON.stringify` with a fixed key order — the client never re-serializes this, it parses it, so
 * the only requirement is that it is valid JSON the Kotlin `SpecBundle` deserializer accepts.
 */
export function canonicalize(bundle: SpecBundle): string {
  return JSON.stringify({
    bundleVersion: bundle.bundleVersion,
    generatedAt: bundle.generatedAt,
    specs: bundle.specs,
    killSwitches: bundle.killSwitches,
  });
}

/** Sign a bundle with the dev/private signing key, returning the wire envelope. */
export function signBundle(bundle: SpecBundle, privateKey: KeyObject): SignedSpecBundle {
  const payload = canonicalize(bundle);
  const signer = createSign("SHA256");
  signer.update(Buffer.from(payload, "utf8"));
  signer.end();
  const signature = signer.sign(privateKey).toString("base64");
  return { payload, signature };
}

/**
 * Verify a signed envelope against [publicKey] and return the parsed bundle, or null on any
 * failure (mirrors the client's fail-closed `SpecBundleVerifier.verifyAndDecode`). Used by the
 * round-trip test and as a server-side self-check.
 */
export function verifyBundle(
  signed: SignedSpecBundle,
  publicKey: KeyObject,
): SpecBundle | null {
  try {
    const verifier = createVerify("SHA256");
    verifier.update(Buffer.from(signed.payload, "utf8"));
    verifier.end();
    const ok = verifier.verify(publicKey, Buffer.from(signed.signature, "base64"));
    if (!ok) return null;
    return JSON.parse(signed.payload) as SpecBundle;
  } catch {
    return null;
  }
}

/** Parse a PEM public key string into a KeyObject (for the round-trip test). */
export function publicKeyFromPem(pem: string): KeyObject {
  return createPublicKey(pem);
}
