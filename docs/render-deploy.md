# Render Deployment Guide

## Quick Start

1. Push `render.yaml` to `main`
2. In Render Dashboard: **New** > **Blueprint** > select this repo
3. Render auto-creates the web service + Postgres database
4. Set the environment variables listed below
5. Deploy triggers automatically on push to `main`

## Environment Variables

Set these in the Render Dashboard under your web service's **Environment** tab:

| Variable | Required | Description |
|---|---|---|
| `DATABASE_URL` | Auto | Injected by Render from the linked Postgres instance |
| `NODE_ENV` | Auto | Set to `production` in render.yaml |
| `R2_ENDPOINT` | Yes | Cloudflare R2 S3-compatible endpoint URL |
| `R2_ACCESS_KEY_ID` | Yes | R2 API token access key |
| `R2_SECRET_ACCESS_KEY` | Yes | R2 API token secret key |
| `R2_BUCKET_NAME` | Yes | R2 bucket name for file uploads |
| `ANTHROPIC_API_KEY` | Yes | Claude API key for vision/parsing |
| `TWILIO_ACCOUNT_SID` | Yes | Twilio account SID |
| `TWILIO_AUTH_TOKEN` | Yes | Twilio auth token |
| `TWILIO_WHATSAPP_FROM` | No | WhatsApp sender number (default: sandbox number) |
| `TWILIO_TEMPLATE_SID` | No | Twilio message template SID |
| `MAGIC_LINK_BASE_URL` | Yes | Base URL for login links (e.g. `https://kompara.onrender.com`) |

## Health Check

- **Path:** `GET /api/health`
- **Response:** `{ status, timestamp, version, db }`
- `status`: `"ok"` (all systems) or `"degraded"` (DB unreachable)
- `version`: short git commit hash (from `RENDER_GIT_COMMIT`) or `"dev"`
- Render uses this for zero-downtime deploys — new instances must pass health check before old ones are removed

## Build & Start

```bash
# Build (Render runs this)
pnpm install && pnpm build

# Start (Render runs this)
pnpm start
```

## Database Migrations

After deploy, run migrations manually via Render Shell or a one-off job:

```bash
pnpm db:migrate
```

## Zero-Downtime Deploys

Render's health check integration ensures zero-downtime:
1. New instance starts and runs `pnpm start`
2. Render hits `/api/health` until it returns 200
3. Traffic shifts to the new instance
4. Old instance is terminated
