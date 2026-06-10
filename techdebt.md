# Tech Debt

Conscious deferrals. Each entry: date, severity, context, why deferred, when to fix.

## 2026-06-10 | Low | Room schema v1 regenerated in place for new cost-profile columns (B-032)

**Context:** B-032 added `rendimientoKmPerLitre`, `gasPricePerLitreMxn` and `workDaysPerWeek` to `CostProfileEntity` so the metrics engine can derive fuel $/km and reason about per-shift break-even. Rather than bumping to schema v2 + a `Migration`, the exported `data/schemas/.../1.json` was regenerated in place (the new columns are additive with defaults, and the entity's identity hash changed).
**Why deferred:** Schema v1 has not shipped to any user (no production DB exists yet), so there is nothing to migrate from — a clean v1 is correct and avoids a no-op migration. The instrumented `KomparaDatabaseMigrationTest` still validates that v1 opens.
**When to fix:** Once the app ships its first build with this schema, any further `CostProfileEntity` change MUST bump the DB version and add a real `Migration` (no more in-place edits to `1.json`).

## 2026-06-10 | Low | DefaultThresholds hand-ported from web seed, not generated at build time (B-032)

**Context:** `DefaultThresholds` hard-codes the p50 earnings_per_km / earnings_per_hour per city/platform, computed from `seed/population-stats.ts`'s deterministic generator. It is not auto-generated from the seed, so a seed change won't propagate.
**Why deferred:** The two repos don't share a build; a codegen step crossing the web/Android boundary is out of scope for this task. Values were computed from the seed formula (not eyeballed) and a comment documents the source + formula.
**When to fix:** If the seed's earnings model changes materially, re-run the formula and update the table (or wire a small codegen step) — guarded by `DefaultThresholdsTest`.

**Context:** All `src/lib/` modules contain only TODO placeholder exports.
**Why deferred:** B-001 establishes the folder structure per the tech design. Each module will be implemented by its own task in the S-001 story.
**When to fix:** During their respective implementation tasks (B-002 through B-008).

## 2026-04-02 | Low | No test coverage for any feature

**Context:** `tests/e2e/upload-flow.spec.ts` is a TODO placeholder. No unit or integration tests exist.
**Why deferred:** No feature code to test yet. Tests will be written alongside feature implementation.
**When to fix:** As each feature is implemented in subsequent tasks.

## 2026-04-02 | Low | PWA icons missing

**Context:** `src/app/manifest.ts` references `/icons/icon-192.png` and `/icons/icon-512.png` but `public/icons/` only has `.gitkeep`.
**Why deferred:** Icons require design work. The manifest structure is correct for when icons are provided.
**When to fix:** During the UI/design implementation phase.

## 2026-04-02 | Low | ARCHITECTURE.md not yet created

**Context:** `CLAUDE.md` references `ARCHITECTURE.md` as TBD. The folder structure follows `docs/technical-design.md` section 4 but no standalone architecture doc exists yet.
**Why deferred:** Architecture will be documented once the initial features are implemented and patterns solidify.
**When to fix:** After the first 2-3 feature tasks are completed and the architecture patterns are established.

## 2026-04-02 | Low | ESLint does not enforce import boundaries

**Context:** ESLint config only extends `next/core-web-vitals` and `next/typescript`. No custom import boundary rules are configured.
**Why deferred:** No layer boundaries to enforce yet -- all modules are placeholders. Rules will be added as the architecture materializes.
**When to fix:** When the first cross-module imports appear (likely during auth or parser implementation).

## TD-001: Magic link tokens stored in plaintext
- **Date:** 2026-04-02
- **Severity:** medium
- **Context:** The `magic_links.token` column stores the raw magic link token. If the database is compromised, unexpired tokens could be used to hijack accounts. The `sessions` table already stores hashed tokens (`token_hash`), showing the correct pattern.
- **Why deferred:** The tech design spec (section 5.1) explicitly defines `magic_links.token` as `VARCHAR(64) UNIQUE`. Task B-002 requires the schema to match the spec exactly. Changing this would be a spec deviation.
- **When to fix:** Before launch. Change `token` to `token_hash`, store SHA-256 of the token, and compare hashes during magic link validation. Update the auth implementation (B-003 or equivalent) to hash tokens before storage and comparison.

## TD-003: Upload route coerces null metrics to zero for NOT NULL DB columns
- **Date:** 2026-04-02
- **Severity:** medium
- **Context:** `src/app/api/uploads/route.ts` lines 216-218 coerce null `net_earnings`/`gross_earnings` to `"0"` and null `total_trips` to `0` before inserting into the `weekly_data` table. These columns are `NOT NULL` in the schema. For the Uber PDF parser this is rarely an issue (those fields are almost always present), but the screenshot parser (`uber-screenshot.ts`) intentionally returns `total_trips: null` since that data is not available from a pie chart view. This creates weekly data rows with `total_trips = 0` which is misleading (zero trips vs. unknown trips).
- **Why deferred:** The upload route is pre-existing code from B-006. Fixing it requires either making these DB columns nullable (schema migration) or adding parser-specific handling in the upload route. Both are out of scope for B-011 (screenshot parser).
- **When to fix:** Before launch. Either make `total_trips`, `net_earnings`, `gross_earnings` nullable in the schema, or add logic in the upload route to distinguish "zero" from "unknown/null". Consider adding a `source_type` column to `weekly_data` to track whether data came from a full PDF or limited screenshot.

## TD-002: get_percentile edge case with zero-valued distributions
- **Date:** 2026-04-02
- **Severity:** low
- **Context:** The `get_percentile` SQL function uses `NULLIF(..., 0)` to avoid division by zero, but when a percentile bucket boundary is zero, the branch evaluates to NULL and PostgreSQL's `GREATEST`/`LEAST` ignore NULLs, potentially returning incorrect percentile values for zero-heavy metrics (e.g., tips, rewards).
- **Why deferred:** The function is copied verbatim from the tech design (section 5.2). The edge case only manifests with degenerate population data where bucket boundaries are zero, which is unlikely with real-world earnings data.
- **When to fix:** When implementing the percentiles engine (B-005 or equivalent). Add COALESCE guards around each interpolation branch to handle zero denominators gracefully.

## TD-004: Accessibility capture needs on-device validation (package IDs, latency, Doze)
- **Date:** 2026-06-10
- **Severity:** high
- **Context:** `:capture` (B-027) ships the read-only `KomparaAccessibilityService` + `EventPipeline` against assumptions that cannot be verified without a device/emulator (instrumented tests were out of scope for this task). Three things are unverified: (1) the target package IDs `com.ubercab.driver` (Uber Driver) and `com.sdu.didi.gsui` (DiDi Conductor) hard-coded in `KomparaAccessibilityService` and `res/xml/kompara_accessibility_service.xml` — these are best-known production MX IDs but the apps may rename/split packages by region; (2) the snapshot-latency target (<50 ms from event to delivered `ScreenSnapshot` on mid-range hardware) — the 80 ms debounce window in `EventPipeline.DEFAULT_DEBOUNCE_MS` plus flatten time has only been validated in virtual time, not on real node trees; (3) service survival through screen-off/Doze during an active shift, including Android 16 background/foreground behavior changes flagged in android-technical-design.md §6 (likely requires battery-optimization whitelisting and possibly a foreground service companion).
- **Why deferred:** No emulator/device available in CI; instrumented tests are explicitly out of scope for B-027. The logic is unit-tested with Robolectric + fakes (traversal/depth/recycling, debounce coalescing, state flow), but the framework-binding and timing characteristics can only be confirmed on hardware.
- **When to fix:** Before launch / during the first on-device QA pass. (a) Verify both package IDs against installed driver apps and add any regional variants to `packageNames`; (b) profile event→snapshot latency on a mid-range device and tune `DEFAULT_DEBOUNCE_MS` to hit <50 ms; (c) test a full shift with screen-off/Doze, add the user to battery-optimization exemptions, and validate Android 16 behavior; (d) add an instrumented test with a stub host activity proving the event→snapshot flow end-to-end (B-027 acceptance item #6, deferred).

## 2026-06-10 | Medium | Backend deploy config (Render service) deferred to launch

- **Context:** B-041 scaffolded the greenfield `backend/` service (Hono + Drizzle, Postgres). The chosen deploy target is Render (containerless Node service), but no Render service definition, managed-Postgres provisioning, or production env wiring exists yet for the backend. `backend/.env.example` documents the required vars (`DATABASE_URL`, `PORT`).
- **Why deferred:** B-041 is the scaffold task — schema, API skeleton, percentile port, and CI. Standing up the hosted service and database is a launch-phase ops task, not part of the thin scaffold.
- **When to fix:** At launch. Add the Render service definition, provision managed Postgres, run `pnpm db:migrate` + `pnpm db:seed` against it, and wire the backend health endpoint into `/canary`.

## 2026-06-10 | RESOLVED (B-042) | Backend auth stubbed (bearer-presence only) until B-042

- **Context:** `backend/src/middleware/auth.ts` only checked that a bearer token was *present* on protected routes; it did not resolve the token to a session/driver, and `backend/src/routes/auth.ts` was an empty placeholder.
- **Resolution (B-042):** `requireBearer(db)` now resolves the bearer token to a `driverId` via a SHA-256 hash lookup against the `sessions` table (`backend/src/auth/sessions.ts`). WhatsApp OTP request/verify, session creation (256-bit token, hash-at-rest, 30-day expiry), `GET/PATCH /v1/me`, and logout are implemented.
- **Follow-up (RESOLVED 2026-06-10):** `POST /v1/aggregates` no longer trusts a client-supplied `driverId` (IDOR). `driverId` was removed from the input schema (any body value is stripped/ignored) and the handler derives ownership from `c.get("driverId")` — the authenticated session. Covered by tests in `backend/src/app.test.ts`.

## TD-006: Parser spec engine has no real-device fixture corpus yet
- **Date:** 2026-06-10
- **Severity:** medium
- **Context:** B-028 built the declarative parser spec engine, normalizers, PII scrubber, and a JSON fixture regression harness in `:parsers`. The only spec/corpus present is the fictitious `com.kompara.demo` (4 hand-crafted fixtures under `android/parsers/src/test/resources/fixtures/com.kompara.demo/`) which proves the harness works end-to-end. There are no real Uber or DiDi specs, and no fixtures captured from real offer screens, so the engine is unproven against actual host-app node trees and es-MX rendering quirks.
- **Why deferred:** Capturing real fixtures requires the AccessibilityService snapshot capture (`:capture`, wired to `:parsers` in B-029) and real driver devices. B-028 is the architecture; B-029/B-030 record sanitized real snapshots and author the production Uber/DiDi specs.
- **When to fix:** B-029 (Uber spec + capture wiring + `ScreenSnapshot.toParserSnapshot()` adapter) and B-030 (DiDi spec). Each adds a spec under `android/parsers/src/test/resources/specs/` and a fixture corpus under `.../fixtures/<package>/`, then points a parameterized harness class at it (mirror `DemoFixtureHarnessTest`). Run all fixtures through `SnapshotScrubber` before committing them.

## TD-007: :parsers ↔ :capture not yet wired (no ScreenSnapshot → ParserSnapshot edge)
- **Date:** 2026-06-10
- **Severity:** low
- **Context:** `:parsers` deliberately defines its own framework-free `ParserSnapshot`/`ParserNode` mirror of `:capture`'s `ScreenSnapshot`/`SnapshotNode` (built in parallel) and does NOT depend on `:capture`, to keep the engine pure-JVM unit-testable. The `Rect` → `RectBox` conversion lives in `android/parsers/.../snapshot/SnapshotMapper.kt`, but the actual `ScreenSnapshot.toParserSnapshot()` extension (which would import `:capture`) is only documented there, not implemented.
- **Why deferred:** Adding the inter-module dependency edge and the adapter is B-029's job; doing it in B-028 would couple the two modules before `:capture`'s types are finalized.
- **When to fix:** B-029. Add `:capture`→`:parsers` is already present; implement the adapter in `:capture` (which depends on `:parsers`) delegating to `Rect.toRectBox()`, and feed `OfferCard` into the `OfferPipeline`/`ParsedOffer` flow.

## TD-008: Account UI screens (login/profile) deferred from B-042
- **Date:** 2026-06-10
- **Severity:** medium
- **Context:** B-042 implemented WhatsApp OTP auth end-to-end on the backend and the Android client *layer* in `:sync` (`AuthRepository`, `ApiClient`, `SessionState`, DI), but **no UI screens**. There is no "crear cuenta" phone→OTP flow screen, no profile (nombre/ciudad/plataformas) editor, and no logout/device-management UI. The repository exposes everything those screens need (`requestOtp`, `verifyOtp`, `sessionState: Flow<SessionState>`, `updateProfile`, `logout`), but nothing in `:app`/`:ui` consumes it yet.
- **Why deferred:** The orchestrator split B-042 to land the backend + client layer while a sibling agent builds navigation UI in parallel; touching `:app`/`:ui` would conflict. The 10-city list + "otras" picker and platform multi-select (acceptance criterion 4) are UI concerns that belong with the account screens.
- **When to fix:** The follow-up account-UI task. Build the OTP entry + verify screens and the profile editor in `:ui`, wire them to `AuthRepository` via a ViewModel, gate them behind the sync/premium entry points, and add the 10-city + platform pickers. Verify the <60s happy-path and resend/fallback acceptance criteria on-device.

## TD-009: Session token + device id stored in plaintext DataStore
- **Date:** 2026-06-10
- **Severity:** high
- **Context:** `AuthRepository` (`android/sync/.../auth/AuthRepository.kt`) persists the raw 256-bit session token and the anonymous device UUID in an unencrypted preferences DataStore (`kompara_auth`, provided by `android/sync/.../di/ApiModule.kt`). On a rooted device or via a device backup, the token could be lifted and replayed for up to 30 days (the server session lifetime). The backend correctly stores only the SHA-256 hash, but the client holds the raw token at rest.
- **Why deferred:** Plaintext DataStore is the simplest correct persistence and unblocks the auth flow + tests. Hardening (Android Keystore-wrapped encryption or EncryptedSharedPreferences / DataStore with a Keystore-derived key) is an isolated change that does not affect the API surface.
- **When to fix:** Before launch. Wrap the token (and ideally the device id) with a Keystore-backed key — e.g. encrypt the value before `dataStore.edit` and decrypt on read, or migrate the token to `EncryptedSharedPreferences`. Keep `AuthRepository`'s public surface unchanged so callers/tests are unaffected.

## TD-010: No real Twilio credentials — WhatsApp OTP delivery unverified end-to-end
- **Date:** 2026-06-10
- **Severity:** medium
- **Context:** The backend's `MessageSender` has a real `TwilioWhatsAppSender` (raw Twilio Messages REST call, `backend/src/auth/message-sender.ts`) but it has never run against live Twilio — this machine has no `TWILIO_ACCOUNT_SID`/`TWILIO_AUTH_TOKEN`, so `senderFromEnv()` falls back to `DevLogSender` (logs the OTP) in dev/CI, and tests use a fake sender. The es_MX message body is hand-written, not a Twilio-approved WhatsApp template, so production sends may be rejected for being out of the 24h session window without an approved template.
- **Why deferred:** No credentials available; the interface + fallback let the whole flow be built and tested without them. Provisioning Twilio, getting a WhatsApp sender number, and submitting a template for approval is an ops/account task.
- **When to fix:** Before launch. Provision Twilio WhatsApp (sender + approved `auth_otp` es_MX template), set the `TWILIO_*` env vars in the deploy environment, switch `TwilioWhatsAppSender` to send via the approved template (contentSid + variables) rather than a free-form body, and run one real end-to-end OTP delivery test.
