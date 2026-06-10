/**
 * Loads the parser-bundle signing key (B-033).
 *
 * Resolution order:
 *  1. `SPEC_SIGNING_PRIVATE_KEY` env (raw PEM) — how production should inject a KMS-managed key;
 *  2. `SPEC_SIGNING_PRIVATE_KEY_PATH` env (a PEM file path);
 *  3. the committed DEV key at `backend/keys/dev/spec-signing-private.pem` — convenient for local
 *     dev and CI ONLY. This key is intentionally committed and is NOT a secret; the matching public
 *     key is embedded in the shipped app. See techdebt: real key management before launch.
 *
 * The dev public key is also exported so the round-trip test can verify without re-reading files.
 */

import { createPrivateKey, createPublicKey, type KeyObject } from "node:crypto";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const devKeysDir = join(here, "..", "..", "keys", "dev");

export const DEV_PRIVATE_KEY_PATH = join(devKeysDir, "spec-signing-private.pem");
export const DEV_PUBLIC_KEY_PATH = join(devKeysDir, "spec-signing-public.pem");

/** Resolve the active signing private key (env-injected in prod; committed dev key otherwise). */
export function loadSigningPrivateKey(): KeyObject {
  const inlinePem = process.env.SPEC_SIGNING_PRIVATE_KEY;
  if (inlinePem && inlinePem.trim().length > 0) {
    return createPrivateKey(inlinePem);
  }
  const path = process.env.SPEC_SIGNING_PRIVATE_KEY_PATH ?? DEV_PRIVATE_KEY_PATH;
  return createPrivateKey(readFileSync(path, "utf8"));
}

/** The committed dev public key (for tests / signing-script verification output). */
export function loadDevPublicKey(): KeyObject {
  return createPublicKey(readFileSync(DEV_PUBLIC_KEY_PATH, "utf8"));
}
