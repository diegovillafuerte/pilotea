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
    middleware/auth.ts  # stub bearer auth (real session lookup in B-042)
    routes/             # health, auth (stub), aggregates, benchmarks,
                        # parser-configs, telemetry
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
| POST | `/v1/aggregates` | upsert weekly aggregate (bearer required — stubbed) |
| GET | `/v1/benchmarks?city=&platform=&period=` | population_stats breakpoints |
| GET | `/v1/parser-configs?package=&versionCode=` | active spec bundle |
| POST | `/v1/telemetry` | parser-health counter ingest |
| `/v1/auth/*` | — | TODO(B-042): WhatsApp magic-link auth |

Secrets are provided via env only and are never committed.
