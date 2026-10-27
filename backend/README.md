# Kompara backend

Thin, boring, managed backend for the Kompara Android app. Greenfield service
(per the pivot decision) that ports proven logic from the legacy web app:
consented weekly-aggregate sync, city benchmarks/percentiles, remote
parser-config hosting, and parser-health telemetry. Auth is WhatsApp OTP
(B-042).

## Stack

- **Runtime:** Node 24+ (TypeScript, ESM)
- **HTTP:** [Hono](https://hono.dev/) — small, fully typed
- **DB:** Postgres via [Drizzle ORM](https://orm.drizzle.team/) + the `postgres` driver
  (mirrors the web stack for maximum port reuse)
- **Validation:** zod (`@hono/zod-validator`)
- **Tests:** Vitest + [`@electric-sql/pglite`](https://pglite.dev/) — an in-memory
  Postgres, so DB tests run with **zero infrastructure**
- **Deploy target:** Render (containerless Node service) — the `kompara-backend`
  web service + `kompara-backend-db` Postgres in the repo-root `render.yaml`

## Layout

```
backend/
  src/
    index.ts            # server entrypoint (boots Hono on PORT)
    app.ts              # createApp(db) — wires routes; testable factory
    db/
      schema.ts         # Drizzle schema v1
      client.ts         # runtime Postgres client (lazy)
      percentile.test.ts# percentile parity test (acceptance criterion)
    auth/               # WhatsApp OTP, sessions, token hashing, Twilio sender
    middleware/
      auth.ts           # real bearer-session auth (B-042)
      device-auth.ts    # X-Device-Id auth for anonymous-data endpoints
    routes/             # health, auth, me, devices, aggregates, benchmarks,
                        # parser-configs, telemetry, fixture-reports, imports,
                        # subscriptions, admin, fiscal-config, app-config,
                        # referrals
    imports/            # Claude Vision import pipeline (ported from web MVP):
                        #   parsers/ (uber-pdf, uber/didi/indrive screenshot,
                        #   router), claude.ts (Vision client), utils.ts (sharp
                        #   normalization + completeness), storage.ts
                        #   (StorageAdapter: R2Storage | MemoryStorage), types.ts
                        #   aggregate-merge.ts (pure field-level merge rules)
                        #   aggregate-write.ts (locked DB read-merge-write)
    jobs/               # population_stats fold-in from real aggregates (B-043)
    referrals/          # referral-code generation + premium-day grants (B-056)
    spec/               # signed OTA parser-config bundle + signing key (B-033)
    subscriptions/      # Play verification seam (stub, B-049), effectiveTier
    telemetry/          # parser-breakage alerts from telemetry counters (B-034)
    test/db.ts          # pglite test harness: migrate + seed
  migrations/           # drizzle-kit SQL + raw get_percentile() function
  seed/
    population-stats.ts # synthetic seed (10 cities + national)
    parser-specs.ts     # parser_configs: initial OTA bundle (uber/didi/indrive)
    specs/              # vendored parser-spec JSON loaded by parser-specs.ts
    fiscal-config.ts    # fiscal_config: current-year IMSS threshold (B-051)
    app-config.ts       # app_config singleton, paywall kill switch ON (B-050)
  scripts/              # CLI entry points (run with tsx):
                        #   sign-spec-bundle.ts: sign the OTA bundle (B-033)
                        #   fold-population-stats.ts: benchmark fold-in (B-043)
                        #   check-alerts.ts: parser-breakage alert cron (B-034)
                        #   dev-pglite.ts: dev server on ephemeral pglite
  keys/dev/             # committed dev spec-signing keypair (TD-014)
```

## Setup

```bash
pnpm install
cp .env.example .env   # set DATABASE_URL, PORT
```

## Commands

```bash
pnpm dev          # watch-mode dev server (tsx)
pnpm start        # run the server
pnpm build        # tsc -> dist/
pnpm typecheck    # tsc --noEmit
pnpm test         # vitest (pglite, no DB server needed)
pnpm db:generate  # drizzle-kit generate (schema -> SQL migration)
pnpm db:migrate   # apply migrations to DATABASE_URL
pnpm db:seed      # seed population_stats (needs DATABASE_URL)
pnpm db:seed:specs # seed parser_configs with the launch-day uber+didi+indrive bundle (needs DATABASE_URL)
pnpm db:seed:fiscal # seed fiscal_config with the current-year IMSS threshold (B-051; needs DATABASE_URL)
pnpm db:seed:app  # seed app_config with the paywall kill switch ON (B-050; needs DATABASE_URL)
pnpm sign-bundle  # build + sign the active parser-config bundle (DB if DATABASE_URL set, else seed)
```

## Migrations

`migrations/` holds drizzle-kit-generated SQL plus one hand-written migration
(`0001_get_percentile_function.sql`) that installs the `get_percentile()`
plpgsql function ported verbatim from `docs/technical-design.md` §5.2. The test
harness (`src/test/db.ts`) applies every migration in journal order against
pglite, so the function and schema are exercised exactly as they ship.

## API (v1)

| Method | Path | Notes |
|---|---|---|
| GET | `/health` | liveness |
| `/v1/auth/*` | — | WhatsApp OTP auth (request/verify/logout) |
| GET/PATCH | `/v1/me` | current driver profile (bearer required) |
| DELETE | `/v1/me` | hard-delete the account; child rows cascade, device rows are kept unlinked (bearer required; B-069) |
| POST | `/v1/devices/register` | anonymous install registration (no account; merged at OTP verify) |
| POST | `/v1/aggregates` | upsert weekly aggregate (bearer required) |
| GET | `/v1/benchmarks?city=&platform=&period=` | population_stats breakpoints |
| GET | `/v1/parser-configs?package=&versionCode=` | active spec rows for a package (legacy list) |
| GET | `/v1/parser-configs/bundle` | active SIGNED OTA bundle (specs + kill switches) — see below |
| POST | `/v1/telemetry` | parser-health counter ingest |
| GET | `/v1/telemetry/alerts` | parser-breakage report: recent failure rate per host version (ADMIN_TOKEN) |
| POST | `/v1/fixture-reports` | consented, scrubbed snapshot of a card the parser couldn't read (X-Device-Id; ≤50 KB, ≤10 per 24h) |
| POST | `/v1/imports` | Claude Vision import (multipart; bearer required) |
| POST | `/v1/subscriptions/sync` | record a Play purchase token + state, stub-verified (bearer required; B-049) |
| POST | `/v1/rtdn` | Play RTDN webhook stub; no Pub/Sub verification yet (launch blocker) |
| POST | `/v1/admin/fold-stats?windowWeeks=` | recompute benchmark percentiles from real aggregates (ADMIN_TOKEN; B-043) |
| GET | `/v1/config/fiscal` | latest year's IMSS threshold + daily minimum wage (B-051) |
| PATCH | `/v1/config/fiscal` | upsert one year's fiscal values (ADMIN_TOKEN) |
| GET | `/v1/config/app` | `{ paywallEnabled }` paywall kill switch; `true` if unseeded (B-050) |
| PATCH | `/v1/config/app` | set `paywallEnabled`; `false` = launch promo, all unlocked (ADMIN_TOKEN) |
| GET | `/v1/referrals/mine` | own referral code + stats (bearer; auto-creates code) |
| POST | `/v1/referrals/redeem` | redeem a referral/partner code (bearer) — see below |
| POST | `/v1/admin/partners` | mint a partner code (ADMIN_TOKEN) |
| GET | `/v1/admin/partners` | partner attribution export (ADMIN_TOKEN) |

### `POST /v1/imports`

Multipart upload of an Uber weekly PDF or an Uber/DiDi/InDrive screenshot,
parsed via Claude Vision (pipeline ported from the web MVP). Fields:

- `platform` — `uber` \| `didi` \| `indrive`
- `upload_type` — `pdf` \| `screenshot`
- `files[]` — ≤10 MB each, MIME `application/pdf`/`image/png`/`image/jpeg`/`image/webp`
  (DiDi screenshot requires **2** images: earnings screen + tablero)

The original(s) are stored via a `StorageAdapter` (R2 in prod, in-memory in
dev/CI) under `{driverId}/{importId}.{ext}`, parsed, validated (zod), and
merged into the driver's `weekly_aggregates` row for that platform and week
(created if absent). **Conflict rule — field-level coalesce**
(`src/imports/aggregate-merge.ts`): a field the import carries overwrites the
stored value, and a field it lacks (null, e.g. Uber km/hours) keeps its prior
captured or imported value. One exception: an import with net earnings and a
positive earnings-per-km figure but no km total (DiDi) gets its km derived as
net / earnings-per-km before the merge (TD-035), so that km overwrites too.
The derived ratios (earnings per trip/km/hour, trips per hour) are recomputed
from the merged raw fields. A row that combines a captured and an imported
contribution becomes `source='mixed'`; an import-only row stays `'imported'`.
The captured sync (`POST /v1/aggregates`) writes through the same merge
(`src/imports/aggregate-write.ts`), so whichever source writes last wins only
for the fields it carries. Returns
`{ import_id, metrics, data_completeness }`, or **422** with a Spanish error
(and `imports.status='failed'`) on a parse failure or when the import would
create a new row with no core earnings (net, gross and trips all missing).

Files are private and processed server-side (distinct from the on-device
parser-config posture used by the Android capture engine). Requires
`ANTHROPIC_API_KEY` and, for durable storage, the `R2_*` env vars — see
`.env.example`. Without those, imports run with an in-memory store and the
real Anthropic call (so a key is still needed for a live call).

Secrets are provided via env only and are never committed.

### Referrals & partners (B-056)

Two growth loops over one set of tables (`referral_codes`, `referral_redemptions`,
`premium_grants`) plus a `drivers.premium_until` timestamp:

- **Driver-to-driver referrals.** `GET /v1/referrals/mine` returns the driver's
  code (auto-minted on first call, idempotent) plus `redemptions_count` and
  `premium_days_earned`. `POST /v1/referrals/redeem { code, deviceId }` grants
  **14 days** of premium to BOTH the referrer and the redeemer.
- **Ruta Rentable Partners.** `POST /v1/admin/partners { name }` (ADMIN_TOKEN)
  mints a driver-less `type=partner` code for a driver-influencer; redeeming it
  grants **14 days** to the **redeemer only** (the influencer is paid manually
  off the attribution export). `GET /v1/admin/partners` returns per-code
  redemption counts (last 30 days + all-time) as export-ready JSON.

**Abuse guardrails on redemption** (each returns a specific Spanish error):
unknown code (404), self-referral (400), already redeemed once (409), redeeming
account ≥ 30 days old (403, new-driver requirement), device already used for a
redemption (409, fraud heuristic). The account/device caps are also enforced by
unique constraints so a race can't double-grant.

**Effective tier.** A driver is `premium` iff an entitled Play subscription is
active **OR** `premium_until > now()` (see `effectiveTier` in
`src/subscriptions/status.ts`). `GET /v1/me` returns `premiumUntilMillis` and
re-derives `driver.tier` on every read, so a referral/partner grant unlocks
premium with **no Play purchase** and the denormalized `drivers.tier` column
never blocks entitlement. Grants **stack**: each grant extends `premium_until`
by its days from `max(now, current premium_until)`, so two 14-day grants → 28
days.

K-factor dashboards and partner payout tooling are manual for v1 (the attribution
JSON is the export) — see `techdebt.md`.

### `GET /v1/parser-configs/bundle` — signed OTA parser config (B-033)

Returns the active **signed** parser-config bundle the Android capture engine
fetches so a host-app UI fix or a kill switch reaches drivers in hours, not a
Play release. Response shape:

```json
{ "payload": "<canonical JSON of the bundle>", "signature": "<base64 ECDSA-P256/SHA-256>" }
```

`payload` is the exact JSON string that was signed — the client verifies those
literal bytes against the public key embedded in the app, then parses it (ECDSA
is non-deterministic and JSON re-serialization isn't byte-stable, so we never
re-serialize). The decoded bundle is `{ bundleVersion, generatedAt, specs[],
killSwitches{} }`:

- `specs` — every `active=true` `parser_configs` row's spec.
- `killSwitches` — packages that have row(s) but **no active row**; flipping a
  package's rows to `active=false` is the kill switch (the client drops that
  platform's spec and surfaces a "support updating" state).
- `bundleVersion` — `max(spec_version)` across rows (monotonic); the client
  rejects any bundle whose version is ≤ its last-known-good, blocking
  downgrade/replay.

Signing uses an ECDSA **P-256** key (`SHA256withECDSA`) — Ed25519 isn't
available on Android < API 33 and minSdk is 26. The key is resolved by
`src/spec/signing-key.ts`: `SPEC_SIGNING_PRIVATE_KEY` (raw PEM) or
`SPEC_SIGNING_PRIVATE_KEY_PATH` env in prod, else the **committed dev key** at
`keys/dev/` (NOT a secret — see techdebt TD-014 for the KMS/launch plan). The
matching public key is embedded in `android/parsers/.../keys/spec-signing-public.pem`.

Seed the launch-day bundle with `pnpm db:seed:specs`; emit a signed bundle to
stdout or a file with `pnpm sign-bundle [--out file.json]` (the static-hosting
alternative — see TD-015). `pnpm sign-bundle --gen-key` rotates the dev keypair.
