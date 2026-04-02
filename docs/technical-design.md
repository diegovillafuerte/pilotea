# Kompara — Technical Design Document

> **Version:** 1.0
> **Date:** 2026-04-02
> **Status:** Draft — pending review
> **Audience:** Engineering team (including AI-assisted development)

---

## 1. Overview

Kompara is a mobile-first PWA for ride-hailing drivers in Mexico. Drivers upload screenshots or PDFs of their weekly earnings from Uber, DiDi, and InDrive. The app uses Claude Vision to extract structured data, calculates efficiency metrics, and benchmarks the driver against others in their city using percentiles.

**Core loop:** Upload screenshot → AI extracts data → See dashboard with percentiles → Get actionable recommendations.

**Business model:** Freemium. Free tier shows single-platform dashboard. Pro ($59 MXN/mo) adds recommendations and history. Multi ($99 MXN/mo) adds cross-platform comparison.

For full business context, see `docs/project-context.md`.

---

## 2. Architecture

```
┌─────────────────────────────────────────────────────┐
│                    Render                             │
│                                                       │
│  ┌──────────────────────────────────────────────┐    │
│  │         Next.js App (Web Service)             │    │
│  │                                                │    │
│  │  ┌─────────────┐    ┌──────────────────┐      │    │
│  │  │  React UI    │    │  API Routes       │      │    │
│  │  │  (App Router │    │  /api/auth/*      │      │    │
│  │  │   SSR + CSR) │    │  /api/uploads/*   │      │    │
│  │  │              │    │  /api/dashboard/*  │      │    │
│  │  └─────────────┘    │  /api/compare/*    │      │    │
│  │                      │  /api/fiscal/*     │      │    │
│  │                      └────────┬───────────┘      │    │
│  └───────────────────────────────┼──────────────┘    │
│                                  │                    │
│  ┌───────────────────────────────▼──────────────┐    │
│  │           Render Postgres                     │    │
│  │  drivers | uploads | weekly_data              │    │
│  │  population_stats | subscriptions             │    │
│  │  magic_links | sessions                       │    │
│  └───────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
        │                           │
        ▼                           ▼
┌──────────────┐          ┌──────────────────┐
│ Cloudflare R2 │          │  External APIs    │
│ (file storage)│          │                    │
│ screenshots/  │          │  - Claude API      │
│ pdfs          │          │    (Vision/OCR)    │
└──────────────┘          │  - Twilio WhatsApp │
                           │    (auth + notifs) │
                           └──────────────────┘
```

**Key decision: monolith.** Next.js handles both the React frontend and API routes in a single deployable unit. This is the right call for a solo/small team — one repo, one deploy, one service to monitor. The API routes are regular Node.js code with full access to the database and external services.

---

## 3. Tech Stack

| Layer | Technology | Why |
|-------|-----------|-----|
| **Framework** | Next.js 15 (App Router) | SSR, API routes, PWA-capable, single deploy |
| **Language** | TypeScript (strict) | Type safety end-to-end, parsers already in TS |
| **UI** | React 19 + Tailwind CSS 4 | Utility-first, fast iteration, mobile-first |
| **Database** | Render Postgres | Managed, same platform as compute |
| **ORM** | Drizzle ORM | Type-safe, lightweight, good migration story |
| **File storage** | Cloudflare R2 | S3-compatible, cheap, no egress fees |
| **AI/OCR** | Claude Sonnet API (vision) | Understands context, not just OCR — handles messy screenshots |
| **Auth** | Custom WhatsApp magic links | Drivers don't use email; WhatsApp is natural |
| **WhatsApp** | Twilio WhatsApp Business API | Magic links + weekly notifications |
| **JWT** | jose | Lightweight, standards-compliant JWT library |
| **Validation** | Zod | Runtime validation for API inputs and parser outputs |
| **PDF generation** | @react-pdf/renderer | Fiscal report PDFs |
| **Image generation** | Satori + resvg-js | Shareable "Tu Mes" cards |
| **Testing** | Vitest + Playwright | Unit/integration + e2e |
| **Hosting** | Render (Web Service) | Simple deploys, managed infra |

### Package overview

```json
{
  "dependencies": {
    "next": "^15",
    "react": "^19",
    "react-dom": "^19",
    "@anthropic-ai/sdk": "^0.39",
    "drizzle-orm": "^0.38",
    "postgres": "^3.4",
    "@aws-sdk/client-s3": "^3",
    "twilio": "^5",
    "jose": "^5",
    "zod": "^3",
    "tailwindcss": "^4",
    "swr": "^2"
  },
  "devDependencies": {
    "drizzle-kit": "^0.30",
    "typescript": "^5.7",
    "vitest": "^3",
    "@playwright/test": "^1.50",
    "eslint": "^9",
    "prettier": "^3"
  }
}
```

---

## 4. Project Structure

```
kompara/
├── src/
│   ├── app/                          # Next.js App Router
│   │   ├── (auth)/                   # Public auth pages
│   │   │   ├── login/page.tsx        # Phone number input
│   │   │   └── verify/page.tsx       # Magic link landing
│   │   ├── (app)/                    # Authenticated app (shared layout with bottom nav)
│   │   │   ├── layout.tsx            # Bottom nav: Dashboard | Comparar | Subir | Fiscal | Tips
│   │   │   ├── dashboard/page.tsx
│   │   │   ├── compare/page.tsx
│   │   │   ├── upload/page.tsx
│   │   │   ├── fiscal/page.tsx
│   │   │   └── tips/page.tsx
│   │   ├── onboarding/page.tsx       # Post-auth: name, city, platforms
│   │   ├── api/                      # API route handlers
│   │   │   ├── auth/
│   │   │   │   ├── login/route.ts    # POST — send magic link
│   │   │   │   ├── verify/route.ts   # GET — validate token, create session
│   │   │   │   └── logout/route.ts   # POST — destroy session
│   │   │   ├── uploads/
│   │   │   │   └── route.ts          # POST — upload + parse
│   │   │   ├── dashboard/
│   │   │   │   └── route.ts          # GET — metrics + percentiles
│   │   │   ├── compare/
│   │   │   │   └── route.ts          # GET — cross-platform (paywall)
│   │   │   ├── fiscal/
│   │   │   │   ├── route.ts          # GET — fiscal data
│   │   │   │   └── pdf/route.ts      # GET — generate PDF
│   │   │   └── share/
│   │   │       └── card/route.ts     # GET — generate shareable image
│   │   ├── layout.tsx                # Root layout
│   │   ├── page.tsx                  # Splash / landing
│   │   └── manifest.ts              # PWA manifest
│   ├── lib/                          # Shared library code
│   │   ├── db/
│   │   │   ├── index.ts              # Drizzle client + connection
│   │   │   ├── schema.ts             # Drizzle table definitions
│   │   │   └── queries/              # Reusable query functions
│   │   │       ├── drivers.ts
│   │   │       ├── uploads.ts
│   │   │       ├── weekly-data.ts
│   │   │       └── population-stats.ts
│   │   ├── parsers/
│   │   │   ├── index.ts              # Router: detect platform → call parser
│   │   │   ├── uber-pdf.ts
│   │   │   ├── uber-screenshot.ts
│   │   │   ├── didi-screenshot.ts
│   │   │   ├── indrive-screenshot.ts
│   │   │   └── types.ts              # ParseResult, ParsedMetrics types
│   │   ├── auth/
│   │   │   ├── session.ts            # Create/verify/destroy sessions
│   │   │   ├── magic-link.ts         # Generate/validate magic links
│   │   │   └── middleware.ts         # Auth middleware for API routes
│   │   ├── storage/
│   │   │   └── r2.ts                 # R2 upload/download/presign
│   │   ├── whatsapp/
│   │   │   └── client.ts             # Twilio WhatsApp: send magic link, notifications
│   │   ├── percentiles/
│   │   │   └── engine.ts             # Calculate percentiles from population_stats
│   │   └── constants.ts              # Cities, platforms, metric definitions
│   ├── components/
│   │   ├── ui/                       # Generic: Button, Card, Badge, Input, etc.
│   │   ├── dashboard/                # MetricCard, PercentileBar, RecommendationCard
│   │   ├── upload/                   # PlatformSelector, FileDropzone, ProcessingSteps
│   │   ├── compare/                  # CompareBar, PlatformCompareCard
│   │   └── layout/                   # BottomNav, Header, PaywallGate
│   └── hooks/
│       ├── use-session.ts            # Client-side session state
│       └── use-upload.ts             # Upload flow state machine
├── migrations/                       # Drizzle SQL migrations
│   └── 0001_initial.sql
├── seed/                             # Seed data for population_stats
│   └── population-stats.ts
├── public/
│   ├── icons/                        # PWA icons (192, 512)
│   └── screenshots/                  # Example upload screenshots (for instructions)
├── tests/
│   ├── unit/
│   │   └── parsers/                  # Parser tests with fixture images
│   ├── integration/
│   │   └── api/                      # API route tests
│   └── e2e/
│       └── upload-flow.spec.ts
├── drizzle.config.ts
├── next.config.ts
├── tailwind.config.ts
├── tsconfig.json
├── package.json
└── .env.example
```

---

## 5. Database Schema

### 5.1 Tables

#### `drivers`
The user profile. Created on first successful auth.

| Column | Type | Notes |
|--------|------|-------|
| id | UUID (PK) | `gen_random_uuid()` |
| phone | VARCHAR(20) | UNIQUE, NOT NULL. E.164 format: `+5215512345678` |
| name | VARCHAR(100) | Nullable — set during onboarding |
| city | VARCHAR(100) | Nullable — set during onboarding. Matches `constants.ts` city keys |
| platforms | TEXT[] | `['uber', 'didi', 'indrive']` — set during onboarding |
| tier | VARCHAR(20) | `'free'` / `'pro'` / `'multi'`. Default `'free'` |
| onboarding_completed | BOOLEAN | Default false. True after name + city + platforms set |
| streak_weeks | INTEGER | Consecutive weeks with at least one upload. Default 0 |
| last_upload_at | TIMESTAMPTZ | Nullable |
| created_at | TIMESTAMPTZ | |
| updated_at | TIMESTAMPTZ | |

#### `magic_links`
Short-lived tokens for WhatsApp authentication.

| Column | Type | Notes |
|--------|------|-------|
| id | UUID (PK) | |
| phone | VARCHAR(20) | NOT NULL |
| token | VARCHAR(64) | UNIQUE. Cryptographically random |
| expires_at | TIMESTAMPTZ | Created_at + 15 minutes |
| used_at | TIMESTAMPTZ | Nullable. Set when verified |
| created_at | TIMESTAMPTZ | |

Index: `idx_magic_links_token` on `token`.
Cleanup: cron job deletes rows where `created_at < now() - interval '24 hours'`.

#### `sessions`
Server-side sessions. Each row maps to an HTTP-only session cookie.

| Column | Type | Notes |
|--------|------|-------|
| id | UUID (PK) | |
| driver_id | UUID (FK → drivers) | |
| token_hash | VARCHAR(64) | SHA-256 hash of the session token. The raw token lives only in the cookie |
| expires_at | TIMESTAMPTZ | Created_at + 30 days |
| created_at | TIMESTAMPTZ | |

Index: `idx_sessions_token_hash` on `token_hash`.
Why hash? If the DB leaks, session tokens can't be used directly.

#### `uploads`
One row per file uploaded by a driver.

| Column | Type | Notes |
|--------|------|-------|
| id | UUID (PK) | |
| driver_id | UUID (FK → drivers) | |
| platform | VARCHAR(20) | `'uber'` / `'didi'` / `'indrive'` |
| upload_type | VARCHAR(20) | `'pdf'` / `'screenshot'` |
| file_key | TEXT | R2 object key: `{driver_id}/{upload_id}.{ext}` |
| status | VARCHAR(20) | `'pending'` / `'processing'` / `'parsed'` / `'failed'` |
| error_message | TEXT | Nullable. Human-readable error in Spanish |
| parsed_data | JSONB | Raw Claude extraction result |
| weekly_data_id | UUID (FK → weekly_data) | Nullable. Set after successful parse |
| created_at | TIMESTAMPTZ | |

Index: `idx_uploads_driver_status` on `(driver_id, status)`.

#### `weekly_data`
The main analytics table. One row per driver × platform × week.

| Column | Type | Notes |
|--------|------|-------|
| id | UUID (PK) | |
| driver_id | UUID (FK → drivers) | |
| platform | VARCHAR(20) | |
| week_start | DATE | Monday of the earnings week |
| **Core earnings** | | |
| net_earnings | DECIMAL(10,2) | After platform commission |
| gross_earnings | DECIMAL(10,2) | Before commission |
| total_trips | INTEGER | |
| **Efficiency metrics** (the 5 key comparables) | | |
| earnings_per_trip | DECIMAL(10,2) | net_earnings / total_trips |
| earnings_per_km | DECIMAL(10,2) | net_earnings / total_km. Null if platform doesn't report km |
| earnings_per_hour | DECIMAL(10,2) | net_earnings / hours_online. Null if platform doesn't report hours |
| trips_per_hour | DECIMAL(10,2) | total_trips / hours_online |
| platform_commission_pct | DECIMAL(5,2) | As percentage (e.g., 25.00 = 25%) |
| **Raw inputs** | | |
| total_km | DECIMAL(10,2) | Null for Uber (never reports km) |
| hours_online | DECIMAL(10,2) | Null for InDrive (never reports hours) |
| platform_commission | DECIMAL(10,2) | Absolute amount. Null for DiDi (doesn't report) |
| taxes | DECIMAL(10,2) | |
| incentives | DECIMAL(10,2) | |
| tips | DECIMAL(10,2) | |
| surge_earnings | DECIMAL(10,2) | |
| wait_time_earnings | DECIMAL(10,2) | |
| active_days | INTEGER | |
| peak_day_earnings | DECIMAL(10,2) | |
| peak_day_name | VARCHAR(20) | e.g., "Viernes" |
| cash_amount | DECIMAL(10,2) | DiDi splits cash/card |
| card_amount | DECIMAL(10,2) | |
| rewards | DECIMAL(10,2) | DiDi rewards |
| **Metadata** | | |
| data_completeness | DECIMAL(3,2) | 0.00–1.00 score |
| raw_extraction | JSONB | Full Claude response for debugging |
| upload_id | UUID (FK → uploads) | |
| created_at | TIMESTAMPTZ | |

Constraint: `UNIQUE(driver_id, platform, week_start)` — upsert semantics. If a driver re-uploads the same week, we overwrite.

Indexes:
- `idx_weekly_data_driver` on `(driver_id, week_start DESC)`
- `idx_weekly_data_city_platform` on `(platform, week_start)` — for population aggregation queries

#### `population_stats`
Pre-computed percentile breakpoints per city × platform × metric. Updated periodically as real data accumulates (initially seeded with synthetic data).

| Column | Type | Notes |
|--------|------|-------|
| id | UUID (PK) | |
| city | VARCHAR(100) | City key or `'national'` for fallback |
| platform | VARCHAR(20) | |
| metric_name | VARCHAR(50) | One of the 5 efficiency metrics |
| period | VARCHAR(20) | `'current'` (rolling) or `'2026-W14'` (specific week) |
| sample_size | INTEGER | Number of data points behind these stats |
| p10 | DECIMAL(10,2) | |
| p25 | DECIMAL(10,2) | |
| p50 | DECIMAL(10,2) | |
| p75 | DECIMAL(10,2) | |
| p90 | DECIMAL(10,2) | |
| mean | DECIMAL(10,2) | |
| updated_at | TIMESTAMPTZ | |

Constraint: `UNIQUE(city, platform, metric_name, period)`.

#### `subscriptions`
Payment and tier management.

| Column | Type | Notes |
|--------|------|-------|
| id | UUID (PK) | |
| driver_id | UUID (FK → drivers) | |
| tier | VARCHAR(20) | `'pro'` / `'multi'` |
| status | VARCHAR(20) | `'active'` / `'cancelled'` / `'expired'` |
| amount | DECIMAL(10,2) | In MXN |
| started_at | TIMESTAMPTZ | |
| expires_at | TIMESTAMPTZ | |
| payment_reference | VARCHAR(255) | Stripe/Mercado Pago reference |
| created_at | TIMESTAMPTZ | |

### 5.2 SQL Functions

#### `get_percentile(city, platform, metric, value) → INTEGER`

Calculates a driver's percentile (1–99) for a given metric using linear interpolation against `population_stats`. Falls back to `'national'` if city sample_size < 20.

```sql
CREATE OR REPLACE FUNCTION get_percentile(
  p_city VARCHAR,
  p_platform VARCHAR,
  p_metric VARCHAR,
  p_value DECIMAL
) RETURNS INTEGER AS $$
DECLARE
  stats RECORD;
BEGIN
  SELECT * INTO stats FROM population_stats
  WHERE city = p_city AND platform = p_platform
    AND metric_name = p_metric AND period = 'current';

  IF stats IS NULL OR stats.sample_size < 20 THEN
    SELECT * INTO stats FROM population_stats
    WHERE city = 'national' AND platform = p_platform
      AND metric_name = p_metric AND period = 'current';
  END IF;

  IF stats IS NULL THEN RETURN NULL; END IF;

  RETURN GREATEST(1, LEAST(99,
    CASE
      WHEN p_value <= stats.p10 THEN
        ROUND((p_value / NULLIF(stats.p10, 0)) * 10)
      WHEN p_value <= stats.p25 THEN
        10 + ROUND(((p_value - stats.p10) / NULLIF(stats.p25 - stats.p10, 0)) * 15)
      WHEN p_value <= stats.p50 THEN
        25 + ROUND(((p_value - stats.p25) / NULLIF(stats.p50 - stats.p25, 0)) * 25)
      WHEN p_value <= stats.p75 THEN
        50 + ROUND(((p_value - stats.p50) / NULLIF(stats.p75 - stats.p50, 0)) * 25)
      WHEN p_value <= stats.p90 THEN
        75 + ROUND(((p_value - stats.p75) / NULLIF(stats.p90 - stats.p75, 0)) * 15)
      ELSE
        90 + LEAST(9, ROUND(((p_value - stats.p90) / NULLIF(stats.p90 * 0.5, 0)) * 10))
    END
  ));
END;
$$ LANGUAGE plpgsql STABLE;
```

### 5.3 Migrations

Managed by Drizzle Kit. Migrations are SQL files in `migrations/`. Run via:

```bash
pnpm db:generate   # Generate migration from schema changes
pnpm db:migrate    # Apply pending migrations
pnpm db:seed       # Seed population_stats with synthetic data
```

---

## 6. Authentication — WhatsApp Magic Links

### Why magic links via WhatsApp?
- Target users (ride-hailing drivers in Mexico) live in WhatsApp — it's their primary communication tool
- Many don't have email accounts or check them regularly
- No passwords to remember or manage
- Frictionless: tap phone → tap link in WhatsApp → you're in

### Flow

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│  Login    │     │  Server   │     │  Twilio   │     │ WhatsApp │
│  Screen   │     │           │     │           │     │          │
└────┬─────┘     └─────┬─────┘     └─────┬─────┘     └────┬─────┘
     │                  │                  │                 │
     │ POST /api/auth   │                  │                 │
     │ {phone}          │                  │                 │
     ├─────────────────►│                  │                 │
     │                  │                  │                 │
     │                  │ generate token   │                 │
     │                  │ store in DB      │                 │
     │                  │                  │                 │
     │                  │ send WhatsApp    │                 │
     │                  ├─────────────────►│                 │
     │                  │                  │  deliver msg    │
     │                  │                  ├────────────────►│
     │  "Check your     │                  │                 │
     │   WhatsApp"      │                  │                 │
     │◄─────────────────┤                  │                 │
     │                  │                  │                 │
     │                  │                  │    User taps    │
     │                  │                  │    the link     │
     │  GET /api/auth/verify?token=xxx     │                 │
     ├─────────────────►│                  │                 │
     │                  │                  │                 │
     │                  │ validate token   │                 │
     │                  │ mark as used     │                 │
     │                  │ find/create      │                 │
     │                  │  driver record   │                 │
     │                  │ create session   │                 │
     │                  │                  │                 │
     │  Set-Cookie +    │                  │                 │
     │  redirect to     │                  │                 │
     │  /dashboard or   │                  │                 │
     │  /onboarding     │                  │                 │
     │◄─────────────────┤                  │                 │
```

### Implementation details

**Token generation:**
```typescript
// lib/auth/magic-link.ts
import { randomBytes } from 'crypto';

function generateToken(): string {
  return randomBytes(32).toString('hex'); // 64-char hex string
}
```

**Rate limiting:** Max 3 magic links per phone per 15-minute window. Prevents abuse and WhatsApp spam.

**Token expiry:** 15 minutes. Single-use (set `used_at` on verification).

**Session creation:** On successful verification:
1. Find existing driver by phone, or create new one
2. Generate session token (random 32 bytes)
3. Store SHA-256 hash in `sessions` table
4. Set HTTP-only, Secure, SameSite=Lax cookie: `kompara_session={token}`
5. Cookie expiry: 30 days (same as session)
6. If driver's `onboarding_completed` is false → redirect to `/onboarding`
7. Otherwise → redirect to `/dashboard`

**Session verification (middleware):**
```typescript
// middleware.ts (Next.js middleware)
// Runs on every request to /(app)/* routes
// 1. Read kompara_session cookie
// 2. Hash it
// 3. Look up in sessions table (cached for 5 min)
// 4. If valid → attach driver_id to request
// 5. If invalid/expired → redirect to /login
```

**WhatsApp message template** (must be pre-approved by Meta):
```
Template name: auth_magic_link
Language: es_MX

Body:
"Hola {{1}}! Entra a Kompara con este link:

{{2}}

Expira en 15 minutos. Si no solicitaste esto, ignora este mensaje."
```

Twilio WhatsApp Business account is already configured and active. Template must be submitted for approval via the Twilio Console before first deploy.

**Logout:** Delete session from DB, clear cookie.

---

## 7. File Storage — Cloudflare R2

### Configuration

R2 is S3-compatible. We use `@aws-sdk/client-s3` with R2's endpoint.

```typescript
// lib/storage/r2.ts
import { S3Client } from '@aws-sdk/client-s3';

const r2 = new S3Client({
  region: 'auto',
  endpoint: process.env.R2_ENDPOINT,        // https://<account_id>.r2.cloudflarestorage.com
  credentials: {
    accessKeyId: process.env.R2_ACCESS_KEY_ID,
    secretAccessKey: process.env.R2_SECRET_ACCESS_KEY,
  },
});
```

### Bucket structure

Bucket name: `kompara-uploads`

Object key format: `{driver_id}/{upload_id}.{ext}`

Example: `a1b2c3d4/e5f6g7h8.pdf`

### Operations

| Operation | When |
|-----------|------|
| `PutObject` | User uploads a file |
| `GetObject` | Re-processing a failed parse, admin debugging |
| `DeleteObject` | User requests data deletion (ARCO compliance) |

No public access. Files are only accessed server-side by the parsing pipeline.

### File validation

Before upload:
- Max file size: **10 MB** (screenshots are typically 200KB–2MB, PDFs up to 5MB)
- Allowed MIME types: `image/png`, `image/jpeg`, `image/webp`, `application/pdf`
- Validate on both client (fast feedback) and server (security)

---

## 8. Upload & Parsing Pipeline

This is the core of the product. A driver uploads one or more files, and the system extracts structured earnings data.

### Upload flow

```
User selects file(s)
    │
    ▼
Client validates (size, type)
    │
    ▼
POST /api/uploads
  FormData: { platform, upload_type, files[] }
    │
    ▼
Server: auth check (session middleware)
    │
    ▼
Server: validate inputs (Zod)
    │
    ▼
Upload file(s) to R2
    │
    ▼
Create `uploads` record (status: 'processing')
    │
    ▼
Route to correct parser based on platform + upload_type
    │
    ▼
Parser: send image/PDF to Claude Vision API
    │
    ▼
Parser: validate Claude's JSON response (Zod)
    │
    ▼
Parser: calculate derived metrics
    │
    ▼
Upsert into `weekly_data`
    │
    ▼
Calculate percentiles (call get_percentile for each metric)
    │
    ▼
Update `uploads` record (status: 'parsed', link to weekly_data)
    │
    ▼
Update driver streak + last_upload_at
    │
    ▼
Return response: { metrics, percentiles, data_completeness, recommendations }
```

### Synchronous processing

For MVP, parsing happens synchronously in the API route. The user sees a loading screen with animated steps ("Leyendo archivos...", "Extrayendo datos con IA...", "Calculando metricas...", "Comparando con conductores").

Claude API calls typically take 3–10 seconds. Total upload-to-result: ~5–15 seconds. This is acceptable for MVP.

**Future optimization:** If parsing latency becomes an issue (>15s), move to async with a job queue. Store upload as `pending`, process in background, push result to client via SSE or polling.

### Parser architecture

Each parser is a function with this signature:

```typescript
// lib/parsers/types.ts
interface ParseInput {
  files: Buffer[];           // 1 file for Uber/InDrive, 2 for DiDi
  mimeType: string;
}

interface ParseResult {
  success: boolean;
  metrics: ParsedMetrics | null;
  raw_extraction: Record<string, unknown>;  // Full Claude response
  data_completeness: number;                // 0.0–1.0
  error?: string;                           // Spanish error message
}

interface ParsedMetrics {
  week_start: string;              // ISO date (Monday)
  net_earnings: number | null;
  gross_earnings: number | null;
  total_trips: number | null;
  earnings_per_trip: number | null;
  earnings_per_km: number | null;
  earnings_per_hour: number | null;
  trips_per_hour: number | null;
  platform_commission_pct: number | null;
  total_km: number | null;
  hours_online: number | null;
  platform_commission: number | null;
  taxes: number | null;
  incentives: number | null;
  tips: number | null;
  surge_earnings: number | null;
  wait_time_earnings: number | null;
  active_days: number | null;
  peak_day_earnings: number | null;
  peak_day_name: string | null;
  cash_amount: number | null;
  card_amount: number | null;
  rewards: number | null;
}
```

### Parser details

#### Uber PDF (`uber-pdf.ts`)
- **Input:** 1 PDF file (weekly earnings report from drivers.uber.com)
- **Claude prompt:** Describes the PDF layout, asks for specific fields in JSON
- **Extracts:** ~20 fields. Most complete source
- **Derives:** `earnings_per_trip`, `earnings_per_hour`, `trips_per_hour` from raw values
- **Cannot extract:** kilometers (Uber never reports km)
- **Expected `data_completeness`:** ~0.95

#### Uber Screenshot (`uber-screenshot.ts`)
- **Input:** 1 image (pie chart "Desglose de la tarifa")
- **Claude prompt:** Describes the pie chart layout
- **Extracts:** earnings, commission %, taxes, incentives
- **Cannot extract:** trips, km, hours
- **Expected `data_completeness`:** ~0.40

#### DiDi Screenshots (`didi-screenshot.ts`)
- **Input:** 2 images (earnings screen + dashboard/tablero)
- **Claude prompt:** Describes both screens, cross-references data
- **Extracts:** earnings, trips, $/km (DiDi is the only platform reporting $/km directly), $/trip, $/hour, cash/card split
- **Derives:** hours (estimated from earnings ÷ $/hour)
- **Cannot extract:** explicit commission (DiDi doesn't break it out)
- **Expected `data_completeness`:** ~0.85

#### InDrive Screenshot (`indrive-screenshot.ts`)
- **Input:** 1 image
- **Extracts:** earnings, trips, km, $/km, gross fares, service fee
- **Derives:** commission (gross - net) / gross
- **Cannot extract:** hours online (InDrive never reports hours)
- **Expected `data_completeness`:** ~0.70

### Claude API call pattern

```typescript
import Anthropic from '@anthropic-ai/sdk';

const anthropic = new Anthropic();

async function parseWithClaude(
  systemPrompt: string,
  images: { data: string; mediaType: string }[]
): Promise<Record<string, unknown>> {
  const response = await anthropic.messages.create({
    model: 'claude-sonnet-4-20250514',
    max_tokens: 2000,
    system: systemPrompt,
    messages: [{
      role: 'user',
      content: [
        ...images.map(img => ({
          type: 'image' as const,
          source: {
            type: 'base64' as const,
            media_type: img.mediaType,
            data: img.data,
          },
        })),
        {
          type: 'text',
          content: 'Extrae los datos de ganancias de esta imagen. Responde SOLO con JSON válido.',
        },
      ],
    }],
  });

  // Parse JSON from Claude's response
  const text = response.content[0].type === 'text' ? response.content[0].text : '';
  return JSON.parse(text);
}
```

### Null handling

Each metric can be `null` if the platform doesn't provide the data needed to compute it. The system never fabricates data. When a metric is null:
- Dashboard shows "No disponible" with an explanation (e.g., "InDrive no reporta horas en linea")
- Percentile comparison is skipped for that metric
- `data_completeness` reflects the proportion of non-null metrics

### Data availability matrix

| Metric | Uber PDF | Uber Screenshot | DiDi | InDrive |
|--------|----------|-----------------|------|---------|
| $/viaje | Yes | No | Yes | Yes |
| $/km | **No** (never) | No | **Yes** (native) | Yes |
| $/hora | Yes | No | Yes (estimated) | **No** (never) |
| viajes/hora | Yes | No | Yes (estimated) | **No** (never) |
| comision % | Yes | Yes | **No** (never) | Yes (derived) |

---

## 9. Percentile Engine

### How it works

When a driver uploads data, each of their efficiency metrics is compared against `population_stats` for their city and platform. The result is a percentile (1–99) indicating where they rank.

Example: "Le ganas al 72% de los conductores de Uber en CDMX en $/viaje."

### Calculation

Uses the SQL function `get_percentile()` (see section 5.2). Called once per non-null metric after parsing:

```typescript
// lib/percentiles/engine.ts
interface PercentileResult {
  metric: string;
  value: number;
  percentile: number;        // 1–99
  city_sample_size: number;
  is_national_fallback: boolean;
}

async function calculatePercentiles(
  city: string,
  platform: string,
  metrics: ParsedMetrics
): Promise<PercentileResult[]> {
  const metricKeys = [
    'earnings_per_trip',
    'earnings_per_km',
    'earnings_per_hour',
    'trips_per_hour',
    'platform_commission_pct',  // Note: lower is better for commission
  ];

  const results: PercentileResult[] = [];
  for (const key of metricKeys) {
    const value = metrics[key];
    if (value == null) continue;

    const row = await db.execute(sql`
      SELECT get_percentile(${city}, ${platform}, ${key}, ${value}) as percentile
    `);
    // ... build result
  }
  return results;
}
```

**Special case — commission %:** Lower commission is better for the driver, so the percentile is inverted (100 - raw_percentile) for display. "Tu comision es menor que el 78% de los conductores" = good.

### Population stats: initial seeding

Before we have real user data, `population_stats` is seeded with synthetic data based on:
- Public reports on ride-hailing earnings in Mexico (INEGI, news articles, driver community posts)
- Reasonable estimates for major cities
- National averages as fallback

Seeded cities (10 priority): CDMX, Monterrey, Guadalajara, Puebla, Toluca, Tijuana, Leon, Queretaro, Merida, Cancun + `national` fallback.

**Transparency:** When using synthetic data, the UI shows: "Basado en estimados de mercado. Se actualiza con mas datos reales."

### Population stats: updating with real data

As real upload data accumulates, a scheduled job recalculates `population_stats`:

```sql
-- Runs weekly (e.g., Sunday night)
-- For each city × platform × metric with >= 20 data points in the last 4 weeks:
INSERT INTO population_stats (city, platform, metric_name, period, sample_size, p10, p25, p50, p75, p90, mean, updated_at)
SELECT
  d.city,
  wd.platform,
  'earnings_per_trip' as metric_name,
  'current' as period,
  COUNT(*) as sample_size,
  PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY wd.earnings_per_trip),
  PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY wd.earnings_per_trip),
  PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY wd.earnings_per_trip),
  PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY wd.earnings_per_trip),
  PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY wd.earnings_per_trip),
  AVG(wd.earnings_per_trip),
  NOW()
FROM weekly_data wd
JOIN drivers d ON d.id = wd.driver_id
WHERE wd.week_start >= CURRENT_DATE - INTERVAL '4 weeks'
  AND wd.earnings_per_trip IS NOT NULL
GROUP BY d.city, wd.platform
HAVING COUNT(*) >= 20
ON CONFLICT (city, platform, metric_name, period)
DO UPDATE SET
  sample_size = EXCLUDED.sample_size,
  p10 = EXCLUDED.p10, p25 = EXCLUDED.p25, p50 = EXCLUDED.p50,
  p75 = EXCLUDED.p75, p90 = EXCLUDED.p90, mean = EXCLUDED.mean,
  updated_at = NOW();
-- Repeat for each of the 5 metrics
```

This runs as a Render Cron Job or a Next.js API route triggered by an external cron (e.g., Render Cron Job hitting `POST /api/cron/update-stats` with a secret key).

---

## 10. API Reference

All API routes are Next.js Route Handlers in `src/app/api/`.

### Auth

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/login` | Public | Send magic link. Body: `{ phone: "+5215512345678" }` |
| GET | `/api/auth/verify` | Public | Validate magic link. Query: `?token=xxx`. Sets session cookie, redirects |
| POST | `/api/auth/logout` | Session | Destroy session, clear cookie |

**POST /api/auth/login — Response:**
```json
{
  "ok": true,
  "message": "Te enviamos un link por WhatsApp. Tocalo para entrar."
}
```

**Error cases:**
- Invalid phone format → 400
- Rate limited (>3 per 15 min) → 429, `"Demasiados intentos. Espera unos minutos."`

### Uploads

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/uploads` | Session | Upload + parse. FormData: `platform`, `upload_type`, `files[]` |
| GET | `/api/uploads` | Session | List driver's uploads. Query: `?limit=20&offset=0` |

**POST /api/uploads — Response (success):**
```json
{
  "ok": true,
  "upload_id": "uuid",
  "weekly_data_id": "uuid",
  "metrics": {
    "week_start": "2026-03-24",
    "net_earnings": 4250.00,
    "total_trips": 87,
    "earnings_per_trip": 48.85,
    "earnings_per_km": null,
    "earnings_per_hour": 152.50,
    "trips_per_hour": 3.12,
    "platform_commission_pct": 25.30,
    "...": "..."
  },
  "percentiles": [
    { "metric": "earnings_per_trip", "value": 48.85, "percentile": 62, "sample_size": 1247 },
    { "metric": "earnings_per_hour", "value": 152.50, "percentile": 78, "sample_size": 1247 },
    { "metric": "platform_commission_pct", "value": 25.30, "percentile": 55, "sample_size": 1247 }
  ],
  "data_completeness": 0.95,
  "recommendations": [
    {
      "type": "positive",
      "message": "Tu ingreso por hora esta en el top 22% de conductores de Uber en CDMX."
    },
    {
      "type": "warning",
      "message": "Tu comision esta semana fue 25.3%, arriba del promedio de 22.1%. Revisa si tuviste viajes cancelados."
    }
  ]
}
```

**Error cases:**
- No files → 400
- File too large → 413
- Invalid platform/type → 400
- Claude parsing failed → 422, `"No pudimos leer tus datos. Asegurate que el screenshot sea claro y completo."`

### Dashboard

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/dashboard` | Session | Current week metrics + percentiles per platform |
| GET | `/api/dashboard/history` | Session | Historical weekly data. Query: `?weeks=12` |

**GET /api/dashboard — Response:**
```json
{
  "driver": {
    "name": "Carlos",
    "city": "CDMX",
    "tier": "free",
    "streak_weeks": 4,
    "platforms": ["uber", "didi"]
  },
  "current_week": {
    "week_start": "2026-03-24",
    "platforms": {
      "uber": {
        "metrics": { "...": "ParsedMetrics" },
        "percentiles": [ "...PercentileResult[]" ],
        "data_completeness": 0.95
      },
      "didi": {
        "metrics": { "...": "ParsedMetrics" },
        "percentiles": [ "...PercentileResult[]" ],
        "data_completeness": 0.85
      }
    },
    "totals": {
      "net_earnings": 7800.00,
      "total_trips": 156
    }
  },
  "recommendations": ["..."]
}
```

### Compare (paywall: Multi tier)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/compare` | Session + Multi | Cross-platform comparison for current week |

Returns side-by-side metrics for all platforms the driver uses, with a "winner" per metric.

### Fiscal

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/fiscal` | Session | Fiscal summary for a month. Query: `?month=2026-03` |
| GET | `/api/fiscal/pdf` | Session | Download PDF report. Query: `?month=2026-03` |

### Share

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/share/card` | Session | Generate shareable "Tu Mes" image. Query: `?month=2026-03` |

### Cron (internal)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/cron/update-stats` | Secret key | Recalculate population_stats from real data |
| POST | `/api/cron/cleanup-magic-links` | Secret key | Delete expired magic links |
| POST | `/api/cron/weekly-reminder` | Secret key | Send weekly WhatsApp reminder to active drivers |

Protected by a shared secret in the `Authorization` header, not session auth.

---

## 11. Frontend Architecture

### Mobile-first PWA

The primary user is on a smartphone (Android, limited data). Every design decision prioritizes:
1. **Small screen** — 360px–414px width
2. **Low bandwidth** — minimize JS bundle, lazy load images
3. **Touch targets** — minimum 44px tap targets
4. **Spanish only** (for now)

### Navigation

Bottom tab bar with 5 tabs (always visible in authenticated layout):

| Tab | Icon | Route | Auth required |
|-----|------|-------|---------------|
| Dashboard | chart icon | `/dashboard` | Yes |
| Comparar | vs icon | `/compare` | Yes (Multi paywall) |
| Subir | camera icon (prominent) | `/upload` | Yes |
| Fiscal | receipt icon | `/fiscal` | Yes |
| Tips | lightbulb icon | `/tips` | Yes |

The "Subir" tab is visually emphasized (larger, colored) as the primary action.

### Key screens

#### Splash (`/`)
- Logo + tagline
- Auto-redirect to `/dashboard` if session exists, `/login` if not
- 2 seconds max

#### Login (`/login`)
- Phone number input with country code picker (default +52 Mexico)
- "Enviar link por WhatsApp" button
- After submit: "Revisa tu WhatsApp" waiting screen

#### Onboarding (`/onboarding`)
- Step 1: Name (text input)
- Step 2: City (searchable dropdown, 40 cities)
- Step 3: Platforms (multi-select: Uber, DiDi, InDrive)
- Privacy badge: "Tus datos, tu control"
- Progressive — each step is one screen

#### Upload (`/upload`)
- Platform tabs (only shows platforms the driver selected)
- Per platform: instructions with example screenshots, file picker
- Upload types: Uber offers "PDF (recomendado)" and "Screenshot (limitado)"
- During processing: animated step indicator (4 steps, ~10 seconds)
- On success: redirect to dashboard with new data highlighted

#### Dashboard (`/dashboard`)
- Header: total earnings this week, streak badge
- Metric cards (6 cards, scrollable)
- Percentile visualization: 20 person icons, highlighted position
- Recommendations section (2–3 cards)
- Data completeness indicator if <100%

#### Compare (`/compare`)
- Paywall gate if tier != multi
- Side-by-side bars per metric (e.g., Uber $/hora vs DiDi $/hora)
- Winner indicator per metric
- Commission comparison

#### Fiscal (`/fiscal`)
- Month selector
- Earnings breakdown by platform
- Tax-relevant totals
- "Descargar PDF" button → generates report for accountant

#### Tips (`/tips`)
- Personalized recommendations based on data
- Generic best practices
- Upsell to Pro/Multi if free tier

### Client state management

- **Server Components** for data fetching where possible (dashboard, fiscal)
- **SWR** for client-side data fetching with caching (upload results, history)
- **React state** for UI-only state (form inputs, modals, tabs)
- No global state library needed at this scale

### Paywall enforcement

The `PaywallGate` component wraps paid features:

```typescript
// components/layout/PaywallGate.tsx
function PaywallGate({ requiredTier, children }: { requiredTier: 'pro' | 'multi', children: ReactNode }) {
  const { driver } = useSession();
  const tierRank = { free: 0, pro: 1, multi: 2 };

  if (tierRank[driver.tier] >= tierRank[requiredTier]) {
    return children;
  }

  return <UpgradePrompt tier={requiredTier} />;
}
```

Paywall is **also enforced server-side** in API routes. Client-side is just UX — the API is the real gate.

---

## 12. PWA Configuration

### Web manifest (`src/app/manifest.ts`)

```typescript
export default function manifest() {
  return {
    name: 'Kompara',
    short_name: 'Kompara',
    description: 'Compara tus ganancias entre plataformas',
    start_url: '/dashboard',
    display: 'standalone',
    background_color: '#ffffff',
    theme_color: '#000000',    // TBD — brand color
    orientation: 'portrait',
    icons: [
      { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
      { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
    ],
  };
}
```

### Service worker

Using `@ducanh2912/next-pwa` for service worker generation. Configuration:

- **Cache strategy:** Network-first for API routes, cache-first for static assets
- **Offline:** Show "Sin conexion" page. App requires internet for core functionality (uploading, parsing)
- **Install prompt:** Show "Agregar a inicio" banner after second visit

---

## 13. Infrastructure & Deployment

### Render services

| Service | Type | Plan | Notes |
|---------|------|------|-------|
| `kompara-web` | Web Service | Starter ($7/mo) | Next.js app. Auto-deploy from `main` branch |
| `kompara-db` | PostgreSQL | Starter ($7/mo) | 1 GB RAM, 1 GB storage. Scale as needed |
| `kompara-cron-stats` | Cron Job | Free | Weekly: `POST /api/cron/update-stats`. Sunday 03:00 UTC |
| `kompara-cron-cleanup` | Cron Job | Free | Daily: `POST /api/cron/cleanup-magic-links`. 04:00 UTC |
| `kompara-cron-reminder` | Cron Job | Free | Weekly: `POST /api/cron/weekly-reminder`. Monday 14:00 UTC (8am CDMX) |

### Cloudflare R2

| Resource | Details |
|----------|---------|
| Bucket | `kompara-uploads` |
| Region | Auto (or `wnam` for US West — close to Mexico) |
| Access | Private — no public access |
| Lifecycle | Delete objects older than 90 days (originals aren't needed after parsing) |

### Deploy process

```
git push main
    │
    ▼
Render detects push
    │
    ▼
Build: pnpm install && pnpm build
    │
    ▼
Health check: GET /api/health returns 200
    │
    ▼
Traffic switches to new deploy (zero-downtime)
```

### Build settings on Render

- **Build command:** `pnpm install && pnpm build`
- **Start command:** `pnpm start`
- **Node version:** 22 (set in `.node-version`)
- **Auto-deploy:** Yes, from `main` branch
- **Health check path:** `/api/health`

---

## 14. Environment Variables

```bash
# .env.example

# Database (Render Postgres — internal connection string)
DATABASE_URL=postgresql://user:pass@host:5432/kompara

# Cloudflare R2
R2_ENDPOINT=https://<account_id>.r2.cloudflarestorage.com
R2_ACCESS_KEY_ID=xxx
R2_SECRET_ACCESS_KEY=xxx
R2_BUCKET_NAME=kompara-uploads

# Claude API
ANTHROPIC_API_KEY=sk-ant-xxx

# Twilio (WhatsApp)
TWILIO_ACCOUNT_SID=ACxxx
TWILIO_AUTH_TOKEN=xxx
TWILIO_WHATSAPP_FROM=whatsapp:+14155238886  # Twilio sandbox or approved number

# Auth
SESSION_SECRET=xxx            # Random 64-char hex, used for cookie signing
MAGIC_LINK_BASE_URL=https://kompara.app  # Or Render URL for staging

# Cron (internal API auth)
CRON_SECRET=xxx               # Shared secret for cron job endpoints

# App
NEXT_PUBLIC_APP_URL=https://kompara.app
NODE_ENV=production
```

All secrets are set in Render's environment variable dashboard — never committed to git.

---

## 15. Development Phases

### Phase 1 — MVP: Upload → Parse → Dashboard (single platform)

**Goal:** A driver can sign up, upload one Uber PDF, and see their metrics with percentiles.

**Scope:**
- WhatsApp magic link auth
- Onboarding (name, city, platform selection)
- Uber PDF parser (richest data source, most common)
- Upload flow with processing animation
- Dashboard with metrics and percentile visualization
- Population stats seeded with synthetic data for 10 cities
- R2 file storage
- Deploy to Render

**Out of scope for Phase 1:** DiDi/InDrive parsers, compare screen, fiscal reports, shareable cards, push notifications, subscriptions.

### Phase 2 — Multi-platform: All parsers + comparison

**Goal:** Support all 4 upload types. If a driver uses multiple platforms, they can compare.

**Scope:**
- Uber screenshot parser
- DiDi screenshot parser (2 images)
- InDrive screenshot parser
- Upload flow supports multi-platform (tabs per platform)
- Compare screen (side-by-side metrics with winner indicator)
- Recommendations engine (basic: "DiDi te paga X% mas por hora")
- Streak counter

### Phase 3 — Monetization + Fiscal

**Goal:** Introduce the paywall and fiscal reports.

**Scope:**
- Subscription system (Pro $59/mo, Multi $99/mo)
- Payment integration (Stripe Mexico or Mercado Pago)
- Paywall enforcement (client + server)
- Compare screen behind Multi paywall
- Fiscal report (monthly breakdown by platform)
- PDF generation for accountant
- Upgrade prompts in free tier

### Phase 4 — Growth features

**Goal:** Viral loops and retention.

**Scope:**
- "Tu Mes" shareable card (image generation)
- WhatsApp share button
- Weekly WhatsApp reminder ("Como te fue? Sube tus datos")
- Tips screen with personalized + generic recommendations
- Pro-tier upsell in tips
- Notifications for streak milestones

---

## 16. Testing Strategy

### Unit tests (Vitest)

Focus on the riskiest logic:

- **Parsers:** Test each parser with fixture images/PDFs. Verify extracted metrics match expected values. Test edge cases (blurry image → graceful failure, unexpected format → clear error).
- **Percentile engine:** Test interpolation logic, null handling, national fallback.
- **Auth:** Token generation, session creation/validation, expiry handling.
- **Derived metrics:** Earnings per trip/km/hour calculations, null propagation.

### Integration tests (Vitest + test DB)

- **API routes:** Test full request/response cycle with a test Postgres instance.
- **Upload pipeline:** End-to-end from file upload to weekly_data insertion (mock Claude API response to avoid costs).

### E2E tests (Playwright) — Phase 2+

- Login flow (mock WhatsApp, verify redirect)
- Upload flow (select platform, upload file, see dashboard update)
- Paywall (free user can't access compare)

### Test data

- Fixture images: real screenshots with personal data redacted
- Fixture PDFs: sample Uber PDF with realistic data
- Mock Claude responses: saved JSON from real Claude calls, used in tests to avoid API costs

---

## 17. Monitoring & Error Handling

### Error handling strategy

All errors shown to drivers are in **Spanish** and are **actionable**:

| Scenario | Error message |
|----------|--------------|
| Claude can't parse image | "No pudimos leer tus datos. Asegurate que el screenshot sea claro y completo." |
| Wrong platform selected | "Esto parece un screenshot de DiDi, no de Uber. Selecciona la plataforma correcta." |
| File too large | "El archivo es muy grande. El maximo es 10 MB." |
| Upload timeout | "Se tardo mucho en procesar. Intentalo de nuevo." |
| Server error | "Algo salio mal. Intentalo de nuevo en unos minutos." |

### Logging

- **Structured JSON logs** to stdout (Render captures these)
- Log every upload: driver_id, platform, upload_type, status, processing_time_ms, data_completeness
- Log every Claude API call: model, tokens_used, latency_ms
- Log auth events: login_attempt, login_success, session_created, session_expired
- **Never log:** raw file contents, phone numbers (redact to last 4 digits), session tokens

### Monitoring (future)

- Render built-in metrics (CPU, memory, response times)
- Custom dashboard for: uploads/day, parse success rate, avg processing time, active drivers
- Alert on: parse failure rate > 20%, API error rate > 5%, Claude API errors

### Health check

```typescript
// src/app/api/health/route.ts
export async function GET() {
  // Check DB connection
  // Check R2 connectivity
  // Return { status: 'ok', timestamp, version }
}
```

---

## 18. Security Considerations

### Data protection (LFPDPPP compliance)

- **Privacy notice** before collecting any data (onboarding screen)
- **Explicit consent** for processing financial screenshots
- **ARCO rights:** API endpoint for data export and deletion
- **Data minimization:** Delete original files from R2 after 90 days. Parsed data (numbers only) is retained.
- **No PII in logs:** Phone numbers redacted, no file contents logged

### Auth security

- Magic link tokens: 32 bytes of `crypto.randomBytes` (256 bits of entropy)
- Session tokens: hashed (SHA-256) before storage — DB leak doesn't compromise sessions
- HTTP-only, Secure, SameSite=Lax cookies — no XSS access to tokens
- Rate limiting on login: 3 attempts per phone per 15 minutes
- Magic links expire in 15 minutes, single-use

### API security

- All mutating endpoints verify session
- Driver can only access their own data (enforced in every query with `WHERE driver_id = ?`)
- File upload validation: size limit, MIME type whitelist
- Cron endpoints protected by shared secret
- Input validation with Zod on every API route

### Infrastructure

- Render's managed Postgres: encrypted at rest, encrypted in transit
- R2: private bucket, no public access
- Environment variables: never in code, managed in Render dashboard
- HTTPS everywhere (Render provides automatic TLS)

---

## 19. Open Decisions

| Decision | Options | Status |
|----------|---------|--------|
| Final app name | Jale, Mero, Rifa (using "Kompara" as placeholder) | Pending IMPI verification |
| Domain | kompara.app or final name domain | Pending name decision |
| Payment processor | Stripe Mexico vs Mercado Pago | Decide in Phase 3 |
| Brand colors / visual identity | TBD | Needs design work |
| Weekly reminder copy | Exact WhatsApp message text | Decide before Phase 4 |
| Population stats refresh | Weekly cron vs real-time vs hybrid | Start with weekly cron, optimize later |
| Async parsing | Move to job queue if sync parsing >15s | Monitor in Phase 1, decide in Phase 2 |
| DiDi commission estimation | Back-calculate from gross vs net, or skip | Implement in Phase 2, validate with real data |

---

## Appendix A: City List (40 cities)

The following cities are supported at launch. Each has a key used in the database and display name shown to users.

| Key | Display name |
|-----|-------------|
| cdmx | Valle de Mexico (CDMX + Edomex) |
| monterrey | Monterrey |
| guadalajara | Guadalajara |
| puebla | Puebla-Tlaxcala |
| toluca | Toluca |
| tijuana | Tijuana |
| leon | Leon |
| queretaro | Queretaro |
| merida | Merida |
| cancun | Cancun |
| aguascalientes | Aguascalientes |
| chihuahua | Chihuahua |
| saltillo | Saltillo |
| hermosillo | Hermosillo |
| morelia | Morelia |
| culiacan | Culiacan |
| veracruz | Veracruz |
| villahermosa | Villahermosa |
| tuxtla | Tuxtla Gutierrez |
| oaxaca | Oaxaca |
| tampico | Tampico |
| reynosa | Reynosa |
| san_luis | San Luis Potosi |
| mexicali | Mexicali |
| cuernavaca | Cuernavaca |
| pachuca | Pachuca |
| xalapa | Xalapa |
| celaya | Celaya |
| irapuato | Irapuato |
| mazatlan | Mazatlan |
| durango | Durango |
| zacatecas | Zacatecas |
| campeche | Campeche |
| colima | Colima |
| tepic | Tepic |
| la_paz | La Paz |
| chetumal | Chetumal |
| coatzacoalcos | Coatzacoalcos |
| playa_del_carmen | Playa del Carmen |
| los_cabos | Los Cabos |

## Appendix B: Recommendation Engine (Phase 2+)

Recommendations are generated after each upload by comparing the driver's metrics against population stats and across platforms.

### Rule-based recommendations

| Condition | Recommendation | Type |
|-----------|---------------|------|
| Driver's $/hour is >p75 | "Tu ingreso por hora esta en el top {100-percentile}% en {city}." | positive |
| Driver's commission >p75 | "Tu comision esta semana fue {pct}%, arriba del promedio de {p50}%. Revisa viajes cancelados." | warning |
| Platform A $/hour > Platform B by >15% | "{PlatformA} te paga {diff}% mas por hora que {PlatformB}. Priorizalo en horas pico." | actionable |
| Driver's $/trip <p25 | "Tu ingreso por viaje esta bajo. Considera rechazar viajes cortos en horas de alta demanda." | actionable |
| Streak >= 4 weeks | "Llevas {n} semanas consecutivas subiendo datos!" | positive |
| data_completeness < 0.7 | "Sube el PDF semanal de Uber para datos mas completos." | info |

### Implementation

Recommendations are computed server-side in the upload API response and the dashboard API. They are **not stored** — they're derived fresh from current data. This keeps them always up-to-date and avoids stale advice.

```typescript
// lib/recommendations/engine.ts
interface Recommendation {
  type: 'positive' | 'warning' | 'actionable' | 'info';
  message: string;
  priority: number;  // 1 = highest
}

function generateRecommendations(
  metrics: ParsedMetrics,
  percentiles: PercentileResult[],
  crossPlatform?: Record<string, ParsedMetrics>,
  driver?: { streak_weeks: number; tier: string }
): Recommendation[];
```

Max 3 recommendations shown at a time, sorted by priority.
