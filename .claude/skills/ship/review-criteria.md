# Adversarial Review Criteria

Use this checklist when reviewing Kompara changes: the Android app in `android/` (Kotlin, Jetpack Compose) and the backend in `backend/` (TypeScript, Hono, Drizzle, Postgres). Not every category applies to every diff — focus on what's relevant. Each section is tagged with the reviewer(s) responsible; the prompts in `.claude/skills/ship/reviewers/` carry the detail.

## Critical (blocks ship)

### Product invariants [Architecture reviewer]
- [ ] Capture stays read-only: no `performAction(`, `dispatchGesture(`, `performGlobalAction(` or `GLOBAL_ACTION_` in production source, and the accessibility config keeps `isAccessibilityTool="false"` with no gesture, key or touch-exploration capability
- [ ] `verifyReadOnlyCapture` (`android/build.gradle.kts`) and its CI step are not weakened
- [ ] Capture, OCR, offer parsing and verdicts stay on the phone (earnings documents the driver uploads are parsed on the backend by design); the reader works without an account or subscription
- [ ] One `TYPE_ACCESSIBILITY_OVERLAY` window, hosted by the accessibility service; no `FLAG_SECURE`
- [ ] Both capture lanes feed `OfferFramePipeline`; one writer per platform (`OCR_OWNED_PACKAGES`)
- [ ] No driver credentials: earnings arrive only as documents the driver uploads

### Module boundaries [Architecture reviewer]
- [ ] `:data` and `:billing` depend on no Kompara module; `:metrics` and `:parsers` depend only on `:data`
- [ ] `:ui` doesn't depend on `:capture`, `:ocr` or `:overlay`; nothing depends on `:app`
- [ ] Backend domain modules (`auth/`, `imports/`, `referrals/`, `spec/`, `subscriptions/`, `telemetry/`, `jobs/`) never import `routes/` or `middleware/`; `backend/` never imports the legacy `src/`

### Privacy and security [Security reviewer]
- [ ] No reader-captured screenshot, OCR text, unscrubbed node dump or passenger detail is stored, logged in release or sent off the phone. Diagnostic snapshots need `SnapshotScrubber` and per-report consent; earnings screenshots and PDFs the driver picks go through the authenticated import flow (confirmation, type and size checks, nothing stored on a dry run); debug-only paths stay behind `BuildConfig.DEBUG`
- [ ] The accessibility `packageNames` and the foreground-app capture gate are not widened
- [ ] Each upload keeps its own consent: aggregates `shareAggregates`, counters `telemetryEnabled`, diagnostic reports per-report consent; no third-party analytics or crash SDK
- [ ] No secrets in code or in the APK (`BuildConfig`, resources, assets)
- [ ] Backend account and driver-data routes use `requireBearer(db)` and scope by `c.get("driverId")` (OTP login and device registration are public by design); `requireDevice` covers anonymous data only; admin checks are constant-time and fail closed
- [ ] OTP codes and session tokens are stored only as hashes; OTP codes are compared in constant time, rate-limited, expiring and single-use; a session stops working once revoked or expired
- [ ] No SQL built from request strings; no PII (phone numbers, codes, tokens, earnings) in logs or error bodies
- [ ] Parser bundles are verified before use; entitlement is never granted from client-only state

### Correctness [Correctness reviewer]
- [ ] Every coroutine has an explicit owner (a ViewModel, a service scope cancelled on teardown, or `KomparaApplication`'s `appScope`), never `GlobalScope`; `CancellationException` is rethrown; nothing blocks the main thread
- [ ] Capture resources are released on every exit path; no stale frame writes after a teardown
- [ ] OCR parsing handles es-MX amounts and fails when in doubt instead of producing a confident verdict; never a false $0
- [ ] Verdict math records absent inputs in `missingInputs`, never gives GREEN on incomplete data, counts the pickup leg, and uses the driver's configured thresholds
- [ ] Timestamps are wall-clock epoch milliseconds
- [ ] Backend promises are awaited; no empty catch blocks; multi-step writes run in one transaction

### Data integrity [Correctness reviewer]
- [ ] A Room `version` bump ships its `Migration` in `KomparaMigrations.ALL`, the exported schema JSON and a migration test; no destructive fallback
- [ ] A renamed DataStore key migrates the old value
- [ ] A `backend/src/db/schema.ts` change ships its drizzle-kit migration, and migrations work with the code already deployed (Render migrates before the new version takes over)
- [ ] API changes don't break app versions already installed
- [ ] Purchases are acknowledged; `PENDING` grants nothing

### Documentation freshness [Docs reviewer; Architecture reviewer for `AGENTS.md` and `techdebt.md`]
- [ ] New modules, backend areas and commands are reflected in `AGENTS.md`
- [ ] Docs in the Doc Inventory (`reviewers/docs.md`) still match the code, `docs/play-compliance.md` and `docs/privacy-policy.md` first
- [ ] Consciously deferred work has a `techdebt.md` entry with date, severity, context, why deferred and when to fix

### User experience [UX reviewer — conditional]
- [ ] The chip shows the net number big, with colour as the verdict, and never covers the fare or the accept button
- [ ] Semáforo colours mean verdicts only; colour is never the only signal
- [ ] Copy is plain es-MX with `tú`: no internal jargon, no English
- [ ] Every call to action leads somewhere; errors tell the driver what to do, in Spanish, without exception text

## Advisory (does not block, but worth noting)

### Test coverage [Correctness reviewer]
- [ ] New code paths have tests (OCR fixtures, metrics boundaries, Room migration tests, `*.test.ts` on pglite)
- [ ] Edge cases tested (no card, a partial card, null legs, zero amounts, an empty import)
- [ ] Error paths tested, not just happy paths
- [ ] Device-only behavior (capture, OCR, overlay position, service lifecycle) is flagged for an on-device check

### Consistency [Architecture reviewer]
- [ ] Naming follows the `AGENTS.md` conventions (`kebab-case.ts`, Zod at route boundaries, Kotlin tests in `src/test`)
- [ ] New files sit in the module that owns the concern
- [ ] UI follows the design system: theme roles, no raw hex or `fontSize`, one `PrimaryButton` per screen [UX reviewer]

### Performance [Correctness reviewer]
- [ ] The capture hot path stays within its ~150 ms budget (no repeated OCR of an unchanged frame, no per-frame Room writes)
- [ ] No N+1 query patterns or unbounded selects on the backend

### Harness integrity [Architecture reviewer]
- [ ] `AGENTS.md` and `CLAUDE.md` stay maps: detail goes to `docs/`
- [ ] New enforceable rules use a build check or linter with a remediation message, not just documentation (principle: never send an LLM to do a linter's job)
- [ ] `pming/` task/story status reflects completed work (if tasks were worked on)
- [ ] No LLM-generated context blobs added to instruction files (reduces agent success rates)
