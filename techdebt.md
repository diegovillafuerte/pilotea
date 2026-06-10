# Tech Debt

Conscious deferrals. Each entry: date, severity, context, why deferred, when to fix.

## 2026-06-10 | LAUNCH BLOCKER | No server-side Play subscription verification — StubVerifier trusts the client (B-049)

**Context:** `POST /v1/subscriptions/sync` records whatever purchase token + status the client posts. The injected `PlayVerifier` (`backend/src/subscriptions/verifier.ts`) is wired to `StubVerifier`, which echoes the client's claim verbatim and logs a WARNING. There is NO call to the Google Play Developer API (`purchases.subscriptionsv2.get`) to confirm the token, its real state, trial status, or expiry. A malicious client could self-grant premium by POSTing a fabricated token with `status: "active"`.
**Why deferred:** Real verification needs a Google Play service-account credential (JSON key + Play Console linkage) that isn't provisioned in this environment, and there is no Play environment/device to obtain a genuine token. The interface (`PlayVerifier`) and the entire sync/RTDN/entitlement flow are built and tested against the stub so the real client drops in without route changes.
**When to fix:** BEFORE LAUNCH. Implement a `GooglePlayVerifier implements PlayVerifier` that authenticates with the service account and calls the Play Developer API to resolve the authoritative subscription state by `purchaseToken`; have `subscriptionsRoutes`/`/v1/me` use it instead of `StubVerifier`. Until then premium is effectively unauthenticated.

## 2026-06-10 | LAUNCH BLOCKER | RTDN webhook has no Pub/Sub signature / push-endpoint verification (B-049)

**Context:** `POST /v1/rtdn` (`backend/src/routes/subscriptions.ts`) validates only the Pub/Sub envelope shape and the base64-decoded `DeveloperNotification` JSON, then updates `subscriptions.status` by token (and re-derives the driver tier) trusting the decoded `status`. It does NOT verify the request actually came from Google Cloud Pub/Sub (no OIDC JWT / push-endpoint auth-token check) and does NOT re-query Play to confirm the new state. Anyone who can reach the endpoint can flip a known token's status (incl. forcing `active`).
**Why deferred:** Same missing service-account/Pub/Sub setup as the verifier; no Play environment to receive real RTDNs. The endpoint is a tested stub so the wiring (token → status → tier) is proven.
**When to fix:** BEFORE LAUNCH. Verify the Pub/Sub push request's OIDC token (audience + Google signer), and on each notification re-verify the token against the Play Developer API rather than trusting the payload's status. Guard the route with that auth before exposing it publicly.

## 2026-06-10 | LAUNCH BLOCKER | Play Billing purchase lifecycle never validated against a real store/device (B-049, internal track B-053)

**Context:** The Android `:billing` module (`BillingClientFacade` + `PlayBillingClientImpl` over Play Billing 9.0.0, `EntitlementRepository`, `EntitlementStore`) is verified entirely with `FakeBillingClient` on the JVM/Robolectric: purchase→PREMIUM, trial flag, restore-on-reinstall, acknowledge-within-flow, pending→active, grace/hold mapping, offline last-known fallback, account-linking via `obfuscatedAccountId`. `PlayBillingClientImpl` itself is unit-untested (it only translates Play callbacks to our suspend/Flow contract). No real Play Console product (`kompara_premium` / base plan `monthly` / offer `trial-7d`), no test-track purchase, no real grace/hold transition, and no on-device acknowledgement were exercised. Client-side grace-vs-active and the trial flag are intentionally conservative — they rely on the backend (RTDN) for authoritative state, which is itself stubbed (above).
**Why deferred:** No Play environment, signed build, or device/emulator-with-Play in this environment. Everything testable was put behind interfaces with fakes per the task contract.
**When to fix:** BEFORE LAUNCH, on the internal test track (B-053): configure the product/base-plan/offer in Play Console; validate the full lifecycle (card-free trial → active → cancel → restore) including grace period and account hold; confirm prices come only from `ProductDetails` at runtime; confirm entitlement is consistent across reinstall + device change (account-linked); and confirm `PlayBillingClientImpl`'s `Purchase.isSuspended`/grace mapping and the free-trial pricing-phase detection match real Play responses.

## 2026-06-10 | Medium | Verdict overlay window behaviour validated only by unit tests, not on device (B-031)

**Context:** The `:overlay` verdict chip (`OverlayController` + `VerdictChipUi`, `TYPE_ACCESSIBILITY_OVERLAY` attached from `KomparaAccessibilityService`) is verified by JVM/Robolectric unit tests of every piece of logic: OfferCard→TripOffer mapping, verdict→chip state, position clamping + bottom safe-zone math, the show/hide state machine with the 500 ms grace (virtual time), threshold-sheet persistence, and the manual `OverlayLifecycleOwner` state walk. No emulator/device was available, so the actual window behaviour was never exercised.
**Why deferred:** No device/emulator in the build environment; compilation + logic tests are the agreed bar for this task. The real-window concerns can only be confirmed on hardware.
**When to fix:** Before launch, on a physical device with the accessibility service enabled, validate: (1) end-to-end latency card→verdict < 150 ms with the live parser; (2) touch pass-through — taps over the host app (especially the Accept button below the 25% bottom safe zone) never hit the chip (FLAG_NOT_FOCUSABLE + WRAP_CONTENT bounds); (3) real `TYPE_ACCESSIBILITY_OVERLAY` add/remove/updateViewLayout across orientation + IME changes and service restarts with no window leak; (4) drag/snap feel and persisted position restore. Also confirm the chip-footprint fallback px (`DEFAULT_CHIP_WIDTH_PX`/`HEIGHT_PX`) match the measured Compose size, and that the cost-profile/threshold snapshots (warmed in `render()`, one offer behind on cold start) are acceptable or should be pre-warmed at service connect.

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

## TD-006: Parser spec engine fixtures are synthetic, not captured from real devices
- **Date:** 2026-06-10 (updated 2026-06-10 by B-029)
- **Severity:** medium
- **Context:** B-028 built the declarative parser spec engine, normalizers, PII scrubber, and a JSON fixture regression harness in `:parsers`. **B-029** added the production Uber Driver MX spec (`android/parsers/src/main/resources/specs/uber-driver.json`, package `com.ubercab.driver`, open version range) and a 16-fixture corpus under `android/parsers/src/test/resources/fixtures/com.ubercab.driver/` covering every variant (standard, surge, exclusive, trip_radar, multi_stop, reservation) and edge cases (thousands separators, meters pickup, `1 h 5 min` durations, missing rating, long sanitized addresses, native `MX$X.XX/km` badge). **However, the Uber corpus is SYNTHETIC** — hand-authored from the publicly documented Uber Driver MX offer-card layout because no physical device was available in the build environment, NOT captured from a real device. So the spec is proven against a *model* of the UI, not the real es-MX node trees. The `com.kompara.demo` corpus remains as the harness proof.
- **Why deferred:** Real fixture capture needs a physical device running the production Uber Driver MX build + a real driver account receiving offers; neither is available in this build env. The synthetic corpus lets the spec, extraction chains, variant tagging, and `:capture`→`:parsers` wiring all land and be regression-tested now; the real-device pass validates the actual strings/viewIds later.
- **When to fix:** Before launch. On a real device, use the B-028 recorder to capture sanitized node-tree snapshots for each Uber variant (and confirm the package id `com.ubercab.driver`, the real installed `versionCode` for `versionCodeRange`, exact es-MX strings, whether the `MX$X.XX/km` badge actually ships, and the accept-countdown card appear/update/disappear states). Run every snapshot through `SnapshotScrubber`, replace or augment the synthetic fixtures, and re-validate the ≥95% acceptance gate against real screens. Same applies to the DiDi spec (B-030). Keep the parameterized harness (`UberDriverFixtureHarnessTest`) pointed at the corpus.

## TD-007: :parsers ↔ :capture not yet wired (no ScreenSnapshot → ParserSnapshot edge) — RESOLVED (B-029)
- **Date:** 2026-06-10 (resolved 2026-06-10 by B-029)
- **Severity:** low
- **Status:** RESOLVED. B-029 implemented the `ScreenSnapshot.toParserSnapshot(versionCode)` adapter in `:capture` (`android/capture/.../SnapshotAdapter.kt`), delegating the only framework touch point — `Rect` → `RectBox` — to `:parsers`' `toRectBox()` helper. The new `OfferEventPipeline` (`android/capture/.../OfferEventPipeline.kt`) consumes `EventPipeline`'s coalesced snapshot stream, scrubs PII via `SnapshotScrubber`, selects a spec from `SpecRegistry` (seeded by `BundledSpecs` from `:parsers` main resources), evaluates it with `SpecEngine`, and exposes `Flow<OfferEvent>` (`Parsed`/`NoCard`) for downstream consumers (overlay B-031, trip log B-039). Wired into `KomparaAccessibilityService` and provided via `OfferParsingModule` (Hilt). Covered by `OfferEventPipelineTest` (fake snapshot in → OfferEvent out, including PII-scrub, NO_SPEC, NOT_AN_OFFER, and the coalesced-burst flow path).
- **Note for follow-ups:** The legacy `OfferPipeline`/`ParsedOffer`/`OfferParser` (NoOp binding) path from B-027 still exists alongside the new `OfferEventPipeline`/`OfferCard` path. Downstream tasks (overlay/trip log/metrics) should consume `OfferEventPipeline.offers` and `OfferCard`; the legacy flatter `ParsedOffer` seam can be retired once nothing depends on it.

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

## TD-011: DiDi MX parser spec + fixtures are synthetic, not on-device captures (B-030)
- **Date:** 2026-06-10
- **Severity:** medium
- **Context:** B-030 ships the DiDi Conductor MX parser spec (`android/parsers/src/test/resources/specs/didi-mx.json`) and a 16-fixture corpus (`android/parsers/src/test/resources/fixtures/com.sdu.didi.gsui/`). No physical device was available in the build environment, so the "on-device recon" step (capture node-tree fixtures from the live DiDi build) was impossible. The spec anchors, text patterns (countdown "59 s" noise, fare `$54.20`, pickup "a 3 min (1.2 km)", trip "15 min (7.8 km)", `Efectivo`/`Tarjeta` chips, `+$15 extra` bonus, stacked/back-to-back wording) and the fixtures are a carefully reasoned model of DiDi MX cards, NOT recorded captures. Two unknowns remain: (a) the package id — `com.sdu.didi.gsui` is used, but `com.didiglobal.driver` exists in some markets (see `_comment` in the spec); (b) real node text/viewId/layout may differ from the model.
- **Why deferred:** No device in the build env (orchestrator-confirmed scope deviation). Authoring the spec + synthetic corpus now unblocks the spec-engine wiring and lets the regression harness exercise the DiDi path; the harness reuses the exact `(spec, corpus)` contract as the demo/Uber corpora, so swapping in real captures later is a drop-in.
- **When to fix:** When a device with DiDi Conductor MX is available. (1) Confirm the live package id + versionCode and split `didi-mx.json` by `VersionRange` if the UI diverges; (2) capture real PII-scrubbed node trees for every variant and replace the synthetic fixtures (keep the file names / expected-card contract); (3) re-validate ≥95% parse success against the real corpus and adjust anchors/regex as needed.

## TD-012: Import pipeline (Claude Vision + R2) not yet smoke-tested with live credentials
- **Date:** 2026-06-10
- **Severity:** medium
- **Context:** B-044 ported the web MVP's proven parsing pipeline into the backend as `POST /v1/imports` (`backend/src/routes/imports.ts` + `backend/src/imports/`). It is fully unit-tested against pglite with a **fixture-replaying** Claude Vision client (`FakeVision`) and an in-memory `MemoryStorage` — no `ANTHROPIC_API_KEY` and no R2 credentials exist on this machine, so `storageFromEnv()` returns `MemoryStorage` and the real `AnthropicVisionClient` / `R2Storage` paths have never executed against the live Anthropic API or a real R2 bucket. The ported model id (`claude-sonnet-4-20250514`, overridable via `CLAUDE_VISION_MODEL`) and the es_MX prompts are reproduced verbatim from the web app but unverified end-to-end in this service. Image normalization uses `sharp` (lazy-loaded; its native build script is ignored by pnpm's default, but the prebuilt arm64 binary loads fine in tests).
- **Why deferred:** No credentials available; the injectable `VisionClient` + `StorageAdapter` interfaces let the whole endpoint be built and tested without them. Provisioning an Anthropic key and an R2 bucket (endpoint, access key, secret, bucket name) is an ops/account task.
- **When to fix:** Before launch. Set `ANTHROPIC_API_KEY` and `R2_ENDPOINT`/`R2_ACCESS_KEY_ID`/`R2_SECRET_ACCESS_KEY`/`R2_BUCKET_NAME` in the deploy env, then run one real upload per platform (Uber PDF, Uber/DiDi/InDrive screenshots) through `POST /v1/imports` to confirm: (1) Claude Vision returns parseable JSON at the ported model id, (2) originals land in R2 under `{driverId}/{importId}.{ext}`, and (3) `sharp` runs in the deploy image (approve its build script or vendor the binary so the screenshot path normalizes). Also confirm a retention/lifecycle policy is configured on the R2 bucket (the web app relied on a 90-day lifecycle to clean up originals).

## TD-013: Breakage-alert delivery (email/Telegram) not wired (B-034)
- **Date:** 2026-06-10
- **Severity:** medium
- **Context:** B-034 ships parser-breakage alerting end to end except the last hop. `computeAlerts()` (`backend/src/telemetry/alerts.ts`) flags any `(host_package, host_version)` pair whose failure rate exceeds 20% over the trailing 48h with >=50 attempts; `GET /v1/telemetry/alerts` (admin-token) exposes it for a dashboard, and `backend/scripts/check-alerts.ts` prints a report and exits non-zero when anything is flagged so a cron wrapper can fire a notification. There is NO built-in email or Telegram delivery — detection and the exit-code signal exist, but a human only finds out if they hit the endpoint or read the cron output/mailer.
- **Why deferred:** Delivery needs an account/ops decision (which channel, which SMTP/Telegram bot creds) and credentials that do not exist in this build env. The exit-code contract lets a plain cron `MAILTO=` or a one-line Telegram curl wrapper deliver alerts today without new backend code.
- **When to fix:** Before relying on alerts in production. Pick a channel, add the creds to the deploy env, and either (a) wrap `check-alerts.ts` in a cron that mails/curls on non-zero exit, or (b) add a small `notify()` in the script that posts the flagged list to email/Telegram. Then simulate a breakage (seed a high-failure-rate counter for a new host version) and confirm the alert actually arrives.

## TD-014: Parser-bundle signing uses a COMMITTED dev keypair — NO real key management (B-033)
- **Date:** 2026-06-10
- **Severity:** HIGH (security; launch blocker)
- **Context:** B-033 ships signed over-the-air parser-config bundles. The signing keypair is an ECDSA P-256 dev key that is **committed to the repo**: the private key at `backend/keys/dev/spec-signing-private.pem` and the matching public key both committed at `backend/keys/dev/spec-signing-public.pem` and embedded in the app at `android/parsers/src/main/resources/keys/spec-signing-public.pem`. Anyone with repo access can forge a bundle the app will accept as authentic — they could push a malicious spec or kill switch to every driver. The backend loader (`backend/src/spec/signing-key.ts`) already prefers `SPEC_SIGNING_PRIVATE_KEY` / `SPEC_SIGNING_PRIVATE_KEY_PATH` env over the committed dev key, so production CAN inject a real key without code changes — but the embedded *public* key in the app is still the dev one, so a prod private key alone is not enough; the app must ship the prod public key too.
- **Why deferred:** A committed dev keypair makes the whole OTA path buildable, testable (sign on the backend → verify on device), and CI-runnable with zero infra or secrets. Standing up a KMS, a signing service, and a key-rotation story is an ops/security task that would have blocked landing the feature.
- **When to fix:** BEFORE LAUNCH. (1) Generate a production keypair in a KMS/HSM (AWS KMS asymmetric ECC_NIST_P256, GCP KMS, or similar); never let the private key touch the repo or disk. (2) Embed the production PUBLIC key in `android/parsers/src/main/resources/keys/spec-signing-public.pem` for the release build (consider a build-flavor-specific resource so debug keeps the dev key). (3) Sign bundles via the KMS (replace `loadSigningPrivateKey()`'s file path with a KMS `sign` call, or run `scripts/sign-spec-bundle.ts` only where the KMS key is reachable). (4) Delete `backend/keys/dev/*` from release artifacts and document the rotation procedure (bump a key id, dual-publish during rollover). (5) Consider pinning a key id in the bundle so the client can select among multiple trusted keys during rotation.

## TD-015: OTA parser bundle served only from the live backend endpoint — no CDN/static hosting fallback (B-033)
- **Date:** 2026-06-10
- **Severity:** low
- **Context:** The signed bundle is fetched from `GET /v1/parser-configs/bundle` (`backend/src/routes/parser-configs.ts`), which signs on every request from the active `parser_configs` rows. The original task suggested an S3/R2/CDN static endpoint. We went with the live endpoint because (a) the backend, auth, and `parser_configs` table already exist (B-041), (b) it lets a kill switch take effect by flipping a row rather than re-uploading a file, and (c) the client already caches the last-known-good bundle so a backend blip doesn't strand drivers. The trade-off: every app-start + 6h WorkManager tick hits the backend, and an outage means no *new* bundles (cached specs still work). The signing script (`backend/scripts/sign-spec-bundle.ts --out file.json`) can already emit a static signed bundle, so the static-hosting path is a small step away.
- **Why deferred:** The live endpoint is simpler to operate at launch scale and the client's last-known-good cache already covers availability. Static/CDN hosting is an optimization, not a correctness requirement.
- **When to fix:** When bundle-fetch traffic or backend availability becomes a concern. Publish the signed bundle (from `scripts/sign-spec-bundle.ts`) to R2/a CDN behind a stable URL, point the client at it (or keep the endpoint as a fallback), and add a cache-control/ETag story. Kill switches would then require re-publishing the static object (or keep the endpoint authoritative for kills and the CDN for the steady-state bundle).

## 2026-06-10 | HIGH (legal; launch blocker) | Onboarding disclosure & ToS-risk copy is PLACEHOLDER, awaiting legal counsel sign-off (B-036 / B-038)

**Context:** B-036 ships the onboarding funnel including the Play-mandated **prominent disclosure** screen and the **ToS-risk** disclosure. All of that user-facing legal copy is *carefully-written placeholder* Spanish text in `android/ui/src/main/res/values/strings.xml`, every string flagged with a `<!-- TODO(legal-B038) -->` comment. The disclosure is structurally Play-compliant (what is read = contenido de pantalla de Uber/DiDi; why = calcular métricas del viaje; where processed = solo en tu teléfono; explicit "Aceptar y continuar" / "No acepto" consent gate; plus a ToS-risk sentence — usar herramientas de terceros puede contravenir los términos de las plataformas), but it has NOT been reviewed by a lawyer. The wording, the risk framing, and the data-safety claims are exactly the kind of thing that must be legally vetted before it is shown to a single real driver or submitted to Google Play.
**Why deferred:** B-038 (legal counsel review) is a HUMAN task and is not done. The placeholder lets the whole funnel be built, tested, and demoed (and lets the Play declaration form be drafted from real screenshots) without blocking on counsel.
**When to fix:** BEFORE LAUNCH (and before any Play submission / external beta). Have legal counsel review and approve (or rewrite) every `onb_disc_*`, `onb_limit_*`, and risk-related string in `android/ui/src/main/res/values/strings.xml`; remove the `TODO(legal-B038)` markers only once signed off. Confirm the disclosure still meets Google Play's prominent-disclosure + data-safety standards after any edits, and re-capture the declaration-form screenshots from the final copy.

## TD-016: Trip-lifecycle inference heuristics are uncalibrated guesses (B-039)
- **Date:** 2026-06-10
- **Severity:** medium
- **Context:** B-039 turns the capture stream into the driver's automatic ledger by inferring the offer→accept→trip→complete lifecycle and shift windows from accessibility signals. Because no Android device is available in the build env, **every threshold and marker in the inference is a documented guess, calibrated only against synthetic event sequences** — not real Uber/DiDi behavior. Specifically: (1) `TripStateMarkers.DEFAULT` (`android/capture/.../lifecycle/TripStateHeuristics.kt`) maps each host package to best-guess view-id/text substrings (e.g. `navigation`, `active_trip`, `ongoing`, `serving`) used to classify a non-offer screen as trip-like vs idle — these are NOT confirmed against real resource ids; (2) `TripStateHeuristics.DEFAULT` timing windows — accept window 12s (card-gone → trip-state ⇒ accepted), decline window 6s (card-gone → idle fast ⇒ declined, else expired), minimum real-trip duration 30s — are unvalidated; (3) `ShiftEntity.INACTIVITY_GAP_MS` = 30 min for shift open/close is a product guess; (4) the **acceptance/decline split is fundamentally a timing proxy for a tap we cannot legally read** (read-only accessibility), so DECLINED vs EXPIRED will be noisy; (5) captured trip **earnings are the offer-fare estimate** (`TripEntity.estimated = true`), not the settled fare — the device can't see the final amount, so aggregates are provisional until reconciled with an imported weekly summary (B-045). The state machine + rollup math are fully unit-tested (`TripLifecycleTrackerTest`, `RollupCalculatorTest`, `StreakCalculatorTest`) so swapping in calibrated values is a data edit, not a logic change.
- **Why deferred:** No device/emulator with the live Uber Driver / DiDi Conductor MX apps in the build env. The whole inference is built behind data-driven constants (`TripStateMarkers`, `TripStateHeuristics`, provided via `LifecycleModule` so they can later be sourced from remote config) precisely so calibration needs no code change.
- **When to fix:** Before relying on captured aggregates for anything user-facing beyond a rough estimate. On a real device: (1) capture PII-scrubbed node trees for the trip-in-progress and idle/home screens of both apps and replace the marker substrings with confirmed view-ids; (2) instrument real accept/decline/expire sequences to tune the accept/decline/min-trip windows (consider per-platform values); (3) validate the 30-min shift gap against real driving sessions; (4) once B-045 imports realized weekly earnings, surface the captured-vs-imported delta to gauge how far the offer-fare estimate drifts and decide whether to keep captured earnings as estimates only.
