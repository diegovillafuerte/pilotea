# Kompara backend

Thin, boring, managed backend for the Kompara Android app. Greenfield service
(per the pivot decision) that ports proven logic from the legacy web app:
consented weekly-aggregate sync, city benchmarks/percentiles, remote
parser-config hosting, and parser-health telemetry. Auth (WhatsApp magic links)
lands in B-042.

## Stack

- **Runtime:** Node 24+ (TypeScript, ESM)
- **HTTP:** [Hono](https://hono.dev/) — small, fully typed
- **DB:** Postgres via [Drizzle ORM](https://orm.drizzle.team/) + the `postgres` driver
  (mirrors the web stack for maximum port reuse)
- **Validation:** zod (`@hono/zod-validator`)
- **Tests:** Vitest + [`@electric-sql/pglite`](https://pglite.dev/) — an in-memory
  Postgres, so DB tests run with **zero infrastructure**
- **Deploy target:** Render (containerless Node service) — config deferred to launch

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
    middleware/auth.ts  # real bearer-session auth (B-042)
    routes/             # health, auth, me, devices, aggregates, benchmarks,
                        # parser-configs, telemetry, imports
    imports/            # Claude Vision import pipeline (ported from web MVP):
                        #   parsers/ (uber-pdf, uber/didi/indrive screenshot,
                        #   router), claude.ts (Vision client), utils.ts (sharp
                        #   normalization + completeness), storage.ts
                        #   (StorageAdapter: R2Storage | MemoryStorage), types.ts
    test/db.ts          # pglite test harness: migrate + seed
  migrations/           # drizzle-kit SQL + raw get_percentile() function
  seed/population-stats.ts  # synthetic seed (10 cities + national)
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
| POST | `/v1/aggregates` | upsert weekly aggregate (bearer required) |
| GET | `/v1/benchmarks?city=&platform=&period=` | population_stats breakpoints |
| GET | `/v1/parser-configs?package=&versionCode=` | active spec bundle |
| POST | `/v1/telemetry` | parser-health counter ingest |
| POST | `/v1/imports` | Claude Vision import (multipart; bearer required) |

### `POST /v1/imports`

Multipart upload of an Uber weekly PDF or an Uber/DiDi/InDrive screenshot,
parsed via Claude Vision (pipeline ported from the web MVP). Fields:

- `platform` — `uber` \| `didi` \| `indrive`
- `upload_type` — `pdf` \| `screenshot`
- `files[]` — ≤10 MB each, MIME `application/pdf`/`image/png`/`image/jpeg`/`image/webp`
  (DiDi screenshot requires **2** images: earnings screen + tablero)

The original(s) are stored via a `StorageAdapter` (R2 in prod, in-memory in
dev/CI) under `{driverId}/{importId}.{ext}`, parsed, validated (zod), and
upserted into `weekly_aggregates` with `source='imported'`. **Conflict rule —
captured beats imported:** a `weekly_aggregates` row whose `source='captured'`
(live Android capture) is never overwritten by an import; the import still
succeeds and links to the existing row. Imported-over-imported re-imports
overwrite (idempotent re-import of the same week). Returns
`{ import_id, metrics, data_completeness }`, or **422** with a Spanish error
(and `imports.status='failed'`) on a parse failure.

Files are private and processed server-side (distinct from the on-device
parser-config posture used by the Android capture engine). Requires
`ANTHROPIC_API_KEY` and, for durable storage, the `R2_*` env vars — see
`.env.example`. Without those, imports run with an in-memory store and the
real Anthropic call (so a key is still needed for a live call).

Secrets are provided via env only and are never committed.
