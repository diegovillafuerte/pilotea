# Kompara 0.2.2 — capture crash fix and APK handoff

## 0.2.2 — screenshot capability and service survival (2026-10-29)

Version **0.2.2**, version code **4**. Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

SHA-256: **`d36ff7e0962a406bc8e0d6517dffc0703155bd0e2a67a31a8c21c1ef3ca788f2`**. `aapt dump badging` and `output-metadata.json` confirm 0.2.2 / 4. The packaged service XML has screenshot capability enabled, `isAccessibilityTool=false`, and no gesture capability. `apksigner verify --verbose` passes (v2, one signer).

**Device finding:** On Galaxy S25 SM-S931B / API 36, debug 0.2.1 crashed twice about three seconds apart after Accessibility was enabled. `takeScreenshot()` threw synchronous `SecurityException: Services don't have the capability of taking the screenshot.` Android then reported no bound services, marked `KomparaAccessibilityService` crashed and set `accessibility_enabled=0`; the chip never appeared. The screenshot capability was never declared, including on main, so choosing the silent lane on 2026-06-18 had not established a working device capture path.

**Fix:** The service XML now declares `canTakeScreenshot="true"`, retains `isAccessibilityTool="false"`, and grants no action capability. The lane checks the granted capability before launch; a missing/stale grant or synchronous SecurityException immediately stands it down to manual MediaProjection and the reader-down banner. Other synchronous RuntimeExceptions count toward the existing hard-failure threshold. Cancellation remains cancellation, callback completion is once-only, and running state is released only by its owner. Startup refusal now records a capture incident even before a first frame, without overwriting a running fallback.

The coroutine audit added exception handlers alongside supervisor jobs to the Accessibility service (snapshot/spec/offer collectors, silent lane and foreground polling), overlay controller (collectors, expiry and persistence), MediaProjection (readiness and frame pipeline), and application collectors sharing the service process (including telemetry, ledger and readiness). Failed components stop and log their error type; the silent loop also contains unexpected failures and cleanup errors. MediaProjection setup/image callbacks and teardown are guarded. Rate-limited `KomparaOCR` / `OverlayController` diagnostics explain incomplete reads, readiness rejection, idle foreground gates, geometry rejection with bounds/age, occlusion and hidden windows. `KomparaSilentLane` / `KomparaOCR` identify the selected lane and reason once per start. Raw OCR, payment amounts, addresses and names are absent from these diagnostics.

**Automated evidence:** Required gate **PASS** (`:app:assembleDebug testDebugUnitTest verifyReadOnlyCapture --console=plain`), **12s; 373 actionable tasks: 49 executed, 324 up-to-date**. Log: `/private/tmp/kompara-0.2.2-final-gate.log`. JVM reports total **1,453 tests, zero failures/errors/skips**: app 1, billing 48, capture 63, data 111, metrics 156, OCR 109, overlay 88, parsers 220, sync 110, UI 547. **15 added regression cases** cover the actual XML declaration, SecurityException/IllegalStateException/cancellation classification, missing capability, startup incident/fallback ownership, failed pipeline/overlay collector isolation, and diagnostic throttling/privacy. The first run caught a test's assumption about exception object identity; the corrected test verifies the type and original cause across coroutine stack-trace recovery. No tests were excluded or assertions weakened. Kotlin used its normal in-process fallback after the sandbox denied its daemon marker. `git diff --check` passes.

**Still unverified:** This change has not been installed or replayed on the Galaxy S25. Verify the service stays bound, screenshots/OCR succeed and the chip attaches with fresh valid geometry; test complete/incomplete Uber and DiDi offers, missing/stale geometry and occlusion, and confirm the reason logs. Test an upgrade with a stale grant, service off/on to refresh the grant, manual MediaProjection consent/recovery, and the reader-down banner. Existing physical calibration, OEM recovery and human QA below remain open. Missing, invalid or stale geometry still prevents attachment; the fix does not bypass that gate. `verifyReadOnlyCapture` remains unchanged, with no action APIs, auto-accept or secure-window flag added.

## Historical 0.2.1 — F3 handoff

Date: 2026-10-29. Branch: `feat/v3-redesign`; base `a046678` (F2). This handoff supersedes the artifact and capability inventory in [S21's 0.2.0 QA](v3-s21-release-qa.md). The [0.2.0 coverage audit](v3-coverage-audit.md) remains unchanged as historical evidence. The design reference at `/Users/panama/pilotea` is read-only. No staging, commit, push or publication is part of this run.

### F3 artifact and automated evidence (historical)

Version **0.2.1**, version code **3**. Debug application ID `mx.kompara.app`; min SDK 26, target SDK 36. APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

SHA-256: **`7b7ae4e7dbdb91dee9e8c1ec50fd53ade01e036a12bdcefb987f8fd56189fad9`**. The adjacent `app-debug.apk.sha256` verifies with `shasum -a 256 -c`. Android `aapt dump badging` and `output-metadata.json` confirm code 3/name 0.2.1. `apksigner verify --verbose` passes with one signer and APK Signature Scheme v2. This is a debug QA artifact, not a Play release or a physical-device certification.

All Gradle commands run from `android/` with:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug testDebugUnitTest verifyReadOnlyCapture --console=plain
```

| Check | Evidence |
|---|---|
| Final required gate | **PASS**, 41s; **1,438 tests, zero failures/errors/skips**, including `verifyReadOnlyCapture`. Log: `/private/tmp/kompara-v3-f3-final.log`. |
| Focused capability checks | **31 tests PASS**. Log: `/private/tmp/kompara-v3-f3-capabilities-final.log`. |
| Focused comparison/share/migration/notification checks | **119 tests PASS**. Log: `/private/tmp/kompara-v3-f3-wiring.log`. The final full gate also includes the subsequent unknown-trip-count regression. |
| Native migration packaging | `:data:assembleDebugAndroidTest` **PASS**, including the new v8→v9 test and complete v1→v9 chain. Log: `/private/tmp/kompara-v3-f3-migration-apk.log`. No device execution claimed. |
| Artifact and diff | Version, signature and checksum verified; `git diff --check` passes. |

F3 adds **17 JVM regression cases and one native migration case**. Coverage includes explicit proof provenance, reactive comparison updates, no-cohort/locked rendering, snapshot sharing and unknown/partial counts, upgrade preservation, unavailable control visibility, contribution capability enforcement, reminder scheduling/cancellation/re-enable, per-app import coverage, duplicate delivery, Android denial and driving deferral. Only the intentional unavailable-support screenshot changed, after visual review; existing comparison/share and other fixture baselines pass. Final module counts: app 1, billing 48, capture 60, data 109, metrics 156, OCR 101, overlay 86, parsers 220, sync 110, UI 547. The normal Kotlin in-process fallback handled the sandbox daemon-marker warning. No test exclusions or weakened assertions were used. S21's lint results remain historical; lint was not rerun in F3.

## What is live in this APK

- Local Hoy, Tu día, week/month accounting, historical offer details, dated costs and car charges, limits, anonymous use and local preferences. No fixture money is seeded into production. F2 fixes historical no-trip car losses and typical-cost disclosure.
- F1's supported Uber/DiDi offer parsing supplies estimated driver payment and incomplete-input states. Both capture lanes supply measured host evidence for full/compact placement or suppression; C6 can use idle-host evidence. Missing, invalid or stale geometry prevents attachment, and unknown payment variants remain unconfirmed. These code paths are enabled; physical replay and calibration are still open.
- Retained import consent, preview, correction, reconciliation and local save; own-app comparison; period-specific PNG sharing with amounts initially hidden; factual IMSS payment records and gated PDF export. Import parsing still uses the existing service. Saving locally does not confer server confirmation.
- F3 supplies stored statement proof to comparison and sharing without fabricating cohort evidence. Room v9 adds nullable certification ID, city and confirmed fields; existing imports migrate unchanged with no proof. Missing city cohorts show the designed own-data/empty state. The locked/ranked renderers remain available for eligible evidence; no sample rank or city average is substituted into production. Shared summaries retain known imported trip counts, show `Viajes —` when the count is unknown, and withhold a whole-period net amount when some payments lack calculable costs.
- Support, external-deletion and legal actions appear only when their destinations are configured. Unsupported contribution controls and backup claims are hidden. Local privacy controls and supported account actions remain usable.
- G7's saved import-reminder choice controls unique local WorkManager jobs, anchored to Monday 09:00 local with app-open catchup and inexact Android delivery. A reminder is due for the last closed week only when a driven supported app lacks a locally saved full-week statement; server confirmation is not required to stop reminding. It uses the Resúmenes channel, contains no amounts and opens Importar. Disabling cancels pending jobs and the posted reminder. Delivery honors Android notification denial and driving deferral; it never uploads or accepts an offer. The spec gives the setting label but no cadence or notification text: this implementation reuses the established Monday schedule and H4's `Trae tu resumen`, with the week dates as the body.
- Premium retains its available-result, Play-unavailable, pending, active, grace, expired and restore states. Production promotional-trial eligibility remains `PromotionalTrials.UNSUPPORTED`.

## Backend, configuration and certification gates

| Capability | Why it remains gated |
|---|---|
| Server-confirmed imports and city rank | Authenticated confirmation/proof and certified cohort source, basis, app, city, week and field coverage are absent from the current service (TD-053/TD-056). Stored proof is consumed only when explicitly present; existing parsed/corrected imports are not promoted. The production cohort adapter remains empty. |
| Confirmed standing on share cards | Requires a matching confirmed statement and certified cohort for the exact selected week. Without both, sharing uses available own-period data and makes no rank claim. |
| Contribution uploads | The existing uploader cannot yet restrict its payload to qualifying confirmed statements. Its production capability stays disabled, independently of previously saved consent (TD-053). |
| Cloud backup/restoration | There is no authenticated, tested restore contract for costs, limits and totals. WhatsApp login is not a backup promise (TD-055). |
| Incremental DiDi import | The current parser needs both images; the first stays locally retained until the pair is submitted. Local partial-review support does not certify incremental service parsing (TD-056). |
| Trial, banked days and trial-end reminders | Authoritative eligibility/start, promotional-day accounting and dates are unsupported. No trial eligibility flag is enabled by this slice (TD-057). G7's local import reminder is separate from H2b's promotional reminder. |
| Monthly Play purchase | Needs available paid results, a current regular monthly Play product and verified configured terms/privacy URLs. This build's legal destinations are empty, so sales remain unavailable. Real billing must be exercised on an internal Play track (TD-057). |
| Support and external account deletion | Verified support recipient and public deletion destination are not configured; their controls are absent. The existing authenticated in-app deletion flow is retained (TD-050/TD-054). |
| City goal proposals and IMSS regulatory estimates | Require sourced city values and validated regulatory rules/configuration. G9's proposed goal and E5's regulatory example remain fixture-only (TD-052/TD-054). |

## Device and human QA still open

F3 device inventory: one attached phone reports `unauthorized`; `emulator -list-avds` returns no AVDs. No installation, device test execution or physical pass is claimed.

- Execute Room migrations and native PDF/Compose checks on an authorized device: `./gradlew :data:connectedDebugAndroidTest :ui:connectedDebugAndroidTest --console=plain`.
- Verify WorkManager delivery after process death/reboot and battery deferral, Monday scheduling, Android notification/channel denial and re-enable, disabling the reminder, no duplicate reminders, and warm/cold notification taps into Importar. Confirm suppression once the needed summaries are locally saved.
- Replay current Uber/DiDi offers for full/compact/suppressed overlays, incomplete readings, dark host appearance, navigation markers, edge padding, latency, idle C6 and C13 absence. Calibrate geometry and payment semantics per supported variant; no host actions may be automated.
- Verify Accessibility and MediaProjection recovery, pause/resume, OEM battery settings and next-morning incident time. Check 13sp Barlow legibility, large text/narrow devices, TalkBack, real IME/Back, cutouts, rotation and process restoration.
- Verify actual document-provider imports, native PDF/PNG chooser grants, WhatsApp/browser return and configured support/deletion/legal destinations. Test Play purchase/pending/grace/cancel/restore and price changes on the appropriate track once its gates are met.
- Complete the real-driver walkthrough and mounted-glance studies under [S21's full pre-registered rules](v3-s21-release-qa.md#human-study--pending-no-participants-or-scores-claimed), including D2e's neutral $133/no-car/$548-after-car probes. No participants or human scores are claimed by automated tests.
