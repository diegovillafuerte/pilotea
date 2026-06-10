/**
 * Sign a parser-config bundle (B-033).
 *
 * Two modes:
 *
 *   pnpm tsx scripts/sign-spec-bundle.ts --gen-key
 *     (Re)generate the ECDSA-P256 dev keypair into backend/keys/dev/. This was run ONCE to mint the
 *     committed dev keypair; re-running it rotates the dev key (you must then re-embed the new
 *     public key in android/parsers/src/main/resources/keys/spec-signing-public.pem). The dev
 *     PRIVATE key is committed on purpose — it is NOT a secret. Real key management (a KMS-held
 *     production key, never committed) is tracked in techdebt before launch.
 *
 *   pnpm tsx scripts/sign-spec-bundle.ts [--out path]
 *     Build the active bundle and print/write its SIGNED envelope. The bundle is built from the
 *     `parser_configs` rows when DATABASE_URL is set; otherwise from the vendored launch-day seed
 *     specs (so the script works with zero infra). Signs with loadSigningPrivateKey() (env-injected
 *     key in prod, committed dev key otherwise). This is the same envelope the
 *     `GET /v1/parser-configs/bundle` endpoint returns — handy for static/CDN hosting (a techdebt
 *     alternative to the live endpoint).
 */

import { generateKeyPairSync } from "node:crypto";
import { mkdirSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { signBundle, verifyBundle, type SpecBundle } from "../src/spec/bundle.js";
import { loadSigningPrivateKey, loadDevPublicKey, DEV_PRIVATE_KEY_PATH, DEV_PUBLIC_KEY_PATH } from "../src/spec/signing-key.js";
import { buildParserSpecRows } from "../seed/parser-specs.js";

const here = dirname(fileURLToPath(import.meta.url));

function genKey(): void {
  const { publicKey, privateKey } = generateKeyPairSync("ec", { namedCurve: "P-256" });
  mkdirSync(join(here, "..", "keys", "dev"), { recursive: true });
  writeFileSync(DEV_PRIVATE_KEY_PATH, privateKey.export({ type: "pkcs8", format: "pem" }) as string);
  writeFileSync(DEV_PUBLIC_KEY_PATH, publicKey.export({ type: "spki", format: "pem" }) as string);
  // eslint-disable-next-line no-console
  console.log(
    `Wrote dev keypair to backend/keys/dev/.\n` +
      `Re-embed the PUBLIC key in:\n` +
      `  android/parsers/src/main/resources/keys/spec-signing-public.pem`,
  );
}

/** Build the active bundle from the DB (if DATABASE_URL set) or from the vendored seed specs. */
async function buildBundle(): Promise<SpecBundle> {
  if (process.env.DATABASE_URL) {
    // Dynamic imports so the no-DB path needs no postgres connection.
    const { getDb } = await import("../src/db/client.js");
    const { buildActiveBundle } = await import("../src/spec/active-bundle.js");
    return buildActiveBundle(getDb());
  }
  const rows = buildParserSpecRows();
  return {
    bundleVersion: Math.max(1, ...rows.map((r) => r.specVersion)),
    generatedAt: new Date().toISOString(),
    specs: rows.map((r) => r.spec),
    killSwitches: {},
  };
}

async function main(): Promise<void> {
  const args = process.argv.slice(2);
  if (args.includes("--gen-key")) {
    genKey();
    return;
  }

  const bundle = await buildBundle();
  const signed = signBundle(bundle, loadSigningPrivateKey());

  // Self-check: the signature we just produced must verify against the dev public key.
  const verified = verifyBundle(signed, loadDevPublicKey());
  if (!verified) {
    throw new Error("signing self-check failed: signature does not verify against the dev public key");
  }

  const outIdx = args.indexOf("--out");
  if (outIdx >= 0 && args[outIdx + 1]) {
    writeFileSync(args[outIdx + 1]!, JSON.stringify(signed, null, 2));
    // eslint-disable-next-line no-console
    console.log(`Wrote signed bundle v${bundle.bundleVersion} to ${args[outIdx + 1]}`);
  } else {
    // eslint-disable-next-line no-console
    console.log(JSON.stringify(signed, null, 2));
  }
}

main().catch((err) => {
  // eslint-disable-next-line no-console
  console.error(err);
  process.exit(1);
});
