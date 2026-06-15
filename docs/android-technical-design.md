# Kompara Android — Technical Design (Rebuild)

> **Date:** 2026-06-10
> **Status:** Approved direction (decisions locked with Juan; details evolve per task)
> **Replaces:** the web-era `technical-design.md` as the forward-looking design. The web doc remains as reference for ported logic (percentiles, parsers, metric definitions, schema concepts).

## 0. Decisions locked

| Decision | Choice |
|---|---|
| Platform | Native Android, **Kotlin + Jetpack Compose** |
| Backend | **Full greenfield**, thin, on-device-first (web stack becomes reference only) |
| Reader launch scope | **Uber + DiDi** (inDrive fast-follow) |
| MVP scope | Realtime reader + full port of web-MVP value (stats, percentiles, compare, upload import) |
| Legal/architecture posture | Capture, parsing, and verdicts **on-device**; consented aggregates only to backend; **read-only v1** (no auto-accept/decline) |
| Monetization | Reader free (hook) → subscription for benchmarks/compare/history (Play Billing, ~$59–99 MXN/mo, free trial without card) |

## 1. Capture architecture (the core)

**Primary: AccessibilityService node-tree reading.** Event-driven (`TYPE_WINDOW_CONTENT_CHANGED` / `TYPE_WINDOW_STATE_CHANGED`), filtered to target driver apps via `packageNames` in the service config. Tens-of-ms latency, structured text (no OCR misreads), minimal battery, and no foreground service required — the bound accessibility service persists while enabled.

**Overlay: `TYPE_ACCESSIBILITY_OVERLAY`** drawn from the service itself — requires no `SYSTEM_ALERT_WINDOW` permission and is not suppressed by a host app calling `setHideOverlayWindows(true)`. Also unaffected by `FLAG_SECURE`.

**Secondary (ACTIVE since S-023, 2026-06-11): MediaProjection + on-device OCR** for the
SurfaceView platforms (DiDi/inDrive), which expose no accessibility text (§7). Originally the
"designed-but-dormant hedge"; on-device validation proved DiDi requires it, so v1 ships a dual
capture path: node tree for Uber, screen-capture OCR (`:ocr`, ML Kit, mediaProjection FGS) for
DiDi/inDrive. Costs accepted: per-session consent (driver-initiated from the Lector tab behind a
prominent disclosure, with a tap-to-restart notification when Android revokes the projection —
B-075), persistent capture chip on 15+, heavier battery. Both paths publish to the shared
OfferEventBus; the accessibility service hosts the one overlay.

**Explicitly rejected for v1:**
- *NotificationListener*: offer cards are in-app surfaces, not notifications; notification payloads can be withdrawn server-side (Para/Lyft precedent). Auxiliary signal only.

**Hot-path budget:** event → parsed offer → overlay verdict in **<150 ms**, all on-device. Offer cards live ~8–20 s.

### Parser resilience (treat as a permanent product function)
1. **Parsers as data, not code**: declarative specs (field extractors as anchored text/regex chains over the flattened node tree; resource-ID matching where stable), versioned per `(targetPackage, versionCode range)`, delivered via remote config with a kill switch. UI churn in Uber/DiDi must be fixable without a Play release.
2. **Never** match by absolute child index or raw coordinates; Compose-era host apps lose resource IDs, so anchor on text patterns + roles + bounds as fallback signals.
3. **Breakage telemetry**: anonymized parse success/failure counters per host-app version (never raw screen text off-device). Alert on failure spikes.
4. **Driver-sourced fixtures**: "report unread offer" captures a PII-scrubbed node-tree dump with explicit consent → regression corpus → parser CI.
5. Locale hardening: es-MX number formats, surge chips, multi-stop/reservation card variants — each variant is a fixture class.

## 2. Permissions, Play policy, onboarding

- Distribute via **Google Play** (Android 13+ "restricted settings" punishes sideloaded accessibility apps).
- `isAccessibilityTool="false"` + the **AccessibilityService API declaration form** (Play Console), framed as driver decision-support/distraction-reduction; demo video required. Precedent: Mystro, StopClub, Ruta Rentable, GigU all live on Play with this model.
- **Prominent disclosure screen** (standalone, before deep-linking to Settings): what is read, why, on-device processing, explicit Agree. This is both Play policy and our legal posture.
- Data-safety form: screen content processed on-device, never sold/shared.
- **OEM survival kit**: Xiaomi/Oppo/Vivo task killers dominate Mexican driver handsets — per-OEM onboarding for autostart whitelisting + lock-in-recents (dontkillmyapp patterns), plus a service-health watchdog with re-enable prompts.
- **Offer simulator** in onboarding: replay fixture cards so the driver sees the overlay work before driving (also the Play-review demo).
- v1 adds **no automation** of the host app — keeps us in the read-only policy/legal class.

## 3. App architecture

- **Modules**: `:capture` (service, event pipeline), `:parsers` (spec engine + per-app specs), `:overlay` (Compose-in-window verdict UI), `:metrics` (net-profit engine, thresholds, verdicts), `:data` (Room: trips, offers, shifts, costs profile; DataStore: settings), `:sync`, `:ui` (app screens), `:billing`.
- **DI**: Hilt. **DB**: Room. **Background**: WorkManager for rollups/sync only (no FGS needed for capture).
- **Metrics engine** (ported concepts from web): gross → net via driver cost profile (fuel $/km, maintenance, insurance, rent/financing per day); $/km (incl. pickup leg), $/min, $/trip, $/hr; one configurable threshold shared by every platform (B-076; legacy per-platform keys migrate on read); traffic-light verdict.
- Trip lifecycle inference: offer seen → accepted (card dismissed + state transition) → trip events → auto shift/day/week rollups. This **replaces uploads as the primary data source**; uploads become import/backfill.

## 4. Greenfield backend (thin)

- Scope: auth (WhatsApp OTP — port the concept), consented aggregate sync, **city benchmarks/percentiles** (port the engine + synthetic seeds), upload-import parsing (Claude Vision — port prompts), remote parser-config hosting, subscription state.
- Stack decided at implementation task (bias: boring + managed — e.g. Postgres + a thin TypeScript or Kotlin service; reuse of web percentile SQL and parser prompts is encouraged even though the deployment is new).
- **Stack chosen (B-041):** TypeScript + Hono on Node 24, Drizzle ORM + `postgres` driver over managed Postgres, zod validation, Vitest + `@electric-sql/pglite` (in-memory Postgres) for zero-infra DB tests; deploys as a containerless Node service on Render. Lives in `backend/` (own pnpm workspace, never touches the web app); the web app's `get_percentile` SQL and synthetic population seeds are ported verbatim.
- **Anonymous-first**: the reader works with no account; account required only for sync/benchmarks/premium.

## 5. Risk register

| Risk | Mitigation |
|---|---|
| Uber native $/km badge (already piloting in MX) | Lead with **net** profit, thresholds, multi-app, percentiles — things Uber won't ship against itself |
| Host app strips decision data pre-acceptance (Para/Lyft precedent) | Product remains valuable post-trip (stats, fiscal, benchmarks); OCR hedge dormant |
| Host UI churn breaks parsers | Remote-config specs, telemetry, fixture CI, <24h fix SLO |
| Play policy enforcement | Read-only v1, clean declaration, prominent disclosure, on-device processing |
| Platform ToS pressure on drivers | In-app risk disclosure; no credentials, no interference; StopClub/TJ-SP + CADE precedents documented in `competitive-analysis.md` |
| OEM task killers | Onboarding survival kit + watchdog |

## 6. Verification debts (from research session)

- Confirm current Android 16 behavior changes affecting accessibility/overlays before B-027 lands.
- APK teardown of Ruta Rentable + StopClub (manifest accessibility config, overlay types) as parser-framework input.
- ~~On-device check: node exposure in current Uber MX + DiDi MX driver builds.~~ **RESOLVED 2026-06-11 (Samsung S25, real apps):** see §7.
- Re-check Uber v StopClub merits ruling + CADE case status quarterly (lead indicator for platform countermeasures).

## 7. On-device node-exposure finding (2026-06-11) — CRITICAL

Verified against the live MX driver apps on a Samsung S25 (uiautomator + our own reader):

| App | Rendering | Accessibility text exposed? | Node-tree reader |
|---|---|---|---|
| **Uber Driver** (`com.ubercab.driver`) | Native views (TextView/Button/WebView) | **Yes** — real text strings present | **Works** ✅ *(offer card SUPERSEDED 2026-06-15 — see §7.1)* |
| **DiDi Conductor** (`com.didiglobal.driver`) | `SurfaceView` (full-surface render) | **No** — 53 nodes, zero text/content-desc | **Cannot read** ❌ |
| **inDrive** (`sinet.startup.inDriver`) | `SurfaceView` | **No** — zero text | **Cannot read** ❌ |

**Implication:** the accessibility node-tree approach (B-027/B-028) reads **Uber only**. DiDi and inDrive render everything on a SurfaceView and expose nothing to accessibility, so the reader receives no text for them — confirmed by their home/login screens (the offer card is on the same surface). This is the contingency anticipated in §1 ("OCR hedge if a platform strips accessibility nodes"). It also explains how Ruta Rentable supports DiDi in MX: it must use **MediaProjection + OCR**, not accessibility nodes.

**Decision pending (Juan):** build the MediaProjection + on-device OCR capture path (ML Kit) for DiDi/inDrive — the hedge becomes required, not optional — vs. launch Uber-first on the working node-tree path and add OCR after. Tradeoffs: MediaProjection needs per-session consent + a persistent capture indicator on Android 14+, changes the Play data-safety/declaration story, and is heavier on battery. The node-tree path for Uber has none of that.

### 7.1 Uber offer card is no longer node-readable (2026-06-15) — CRITICAL UPDATE

Re-verified on the same Samsung S25 (CDMX) against a **live Uber offer card** (the 2026-06-11 check confirmed Uber exposed *some* text, but had not been validated against a real trip-offer card). The current Uber Driver build renders the trip-offer card inside `com.uber.rib.core.compose.root.UberComposeView` — a full-screen (`[0,0][1080,2340]`) Compose host — plus an `android.view.TextureView`, with **no accessibility semantics on the card**. `getWindows()` returns only ~22 chrome nodes (`"Buscando viajes"`, `"+MXN 10"`, `"No es posible desconectarse"`); the fare/distance/`Aceptar` text is in **neither `text` nor `contentDescription`** (confirmed via `uiautomator dump` + `KomparaCapture` logs). MediaProjection OCR reads the same card perfectly.

**Implication:** the node-tree path now reads **none** of the three target apps' offer cards. The `uber-driver.json` declarative spec is effectively dead for live offers (kept for the simulator + as a fixture reference). The "OCR hedge" of §1 is now the **primary** path for all three platforms.

**Shipped (branch `feat/uber-ocr-capture`, verified live):**
- `mx.kompara.ocr.UberOcrParser` — parses the Uber card from OCR text (fare `MXN<amount>` with `.` *or* `,` decimal; pickup leg vs. `Viaje:`-labelled trip leg; `Exclusivo`/`Radar de solicitud de viaje` variants; excludes `+MXN… incluido` / `por inicio de viaje` bonuses).
- `OcrCaptureService` tries `UberOcrParser` then `DidiOcrParser` per frame — disjoint by fare format (`MXN` vs bare `$`), so `card.platform` self-identifies; emits `OfferEvent` tagged with the right package.
- `KomparaAccessibilityService.OCR_OWNED_PACKAGES` now includes `com.ubercab.driver`, so the node path stops emitting Uber `NoCard`s that would race and blank the OCR verdict ("one writer per platform").

**Consequence:** Uber now **requires the screen reader (MediaProjection consent)** like DiDi/inDrive — it no longer works on accessibility alone. The reader-down banner (B-078) already covers this since it keys off `OCR_OWNED_PACKAGES`.
