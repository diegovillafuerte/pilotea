# Kompara 0.2.0 — S21 QA and APK handoff

Date: 2026-10-29. Branch: `feat/v3-redesign`. Local HEAD: `71e08ac`; the supplied staged S1–S20 implementation is preserved. Reference: read-only `/Users/panama/pilotea`, including the 118-state contract pinned by S20. No staging, commit, push or publication is part of this handoff.

## Artifact and automated evidence

Version: `0.2.0`, version code **2** (previous code 1). Debug application ID: `mx.kompara.app`. APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

SHA-256: `899795654a7f48bd97ea00bba1bc0c661f56c20cf03d548dd6c235db2d6409f7`. The adjacent `app-debug.apk.sha256` passes `shasum -a 256 -c`. Android `aapt dump badging` and `output-metadata.json` both confirm `mx.kompara.app`, code 2, name 0.2.0, min SDK 26/target 36. `apksigner verify --verbose` passes with one signer and APK Signature Scheme v2. This is a debug QA artifact; physical-device and human release gates below remain separate from automated results.

All Gradle commands run from `android/` after:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

| Check | Evidence |
|---|---|
| Final required gate | Exact `./gradlew :app:assembleDebug testDebugUnitTest verifyReadOnlyCapture --console=plain` **PASS**, 40s; **1,343 tests, zero failures/errors/skips**. Log: `/private/tmp/kompara-v3-s21-final.log`. |
| Android lint | Final `:app:lintDebug` **PASS**: zero errors, 40 warnings. Log: `/private/tmp/kompara-v3-s21-focused-final.log`; report: `android/app/build/reports/lint-results-debug.html`. |
| Instrumentation packaging | Final `:data:assembleDebugAndroidTest :ui:assembleDebugAndroidTest` **PASS**, including three new Compose accessibility tests. Same focused-final log. |
| Device availability | `adb devices -l`: one attached phone, `unauthorized`; `emulator -list-avds`: empty. No installation or instrumented execution claimed. |

Lint warnings concern dependency/SDK update availability (36), Android backup configuration (1), the existing v26 resource folder (1), and two unused app resources. No baseline or suppression was added. Updating SDKs/dependencies and unrelated resources is outside this polish slice.

Final JVM counts: app 1, billing 48, capture 49, data 105, metrics 154, OCR 82, overlay 74, parsers 198, sync 110, UI 522. S21 adds ten JVM regression cases and three instrumented cases. Existing screenshots compare unchanged; E1, E3, C6 night and N10 at 2× were also visually inspected. `git diff --check` passes. The sandbox's existing Kotlin daemon-marker warning uses Gradle's normal in-process fallback; no custom compiler flags, test exclusions or baseline regeneration were used for the final gate. The first OTP-test attempts timed out because the Robolectric main looper was paused; the harness now drains it while waiting for the mocked response, and both retained-request regressions pass in the full suite.

## Scoped polish

- Semana/Mes text tabs now have at least 48dp in both dimensions; the short `Mes` label previously supplied only a height floor. Text and underline placement remain unchanged.
- OTP request and verification survive composition recreation with their existing destination ViewModel. Explicit cancellation still cancels the flow; leaving composition for rotation no longer cancels a request already sent.
- The native navigation header includes horizontal safe-drawing insets, protecting actions from landscape cutouts. The month's share action announces `Compartir mes`.
- The add-image document-picker intent retains its mode through recreation, so a returned additional image remains an addition to the retained import draft.
- C6 stays transparent and inaccessible while WindowManager is still applying its placement. It checks actual screen coordinates, window origin, current insets and measured dimensions before showing, and detaches an old host's window before admitting another host. Its five-second once-per-host lifetime and production geometry gate are preserved.

Frame coverage: A6/A7; Ganancias E1/E3 and N4/N5; shared period selectors; H4/H5c; C6 window placement; C1/C12 phrase-first semantics. Existing rendered-string, money, narrow/large-text, night/greyscale and reduced-motion suites remain the regression evidence. No screenshot baseline is regenerated merely to hide a difference.

The native `V3AccessibilityInstrumentedTest` checks actual Compose bounds/touch selection at 1×/1.3×/2×, picker-before-steps traversal, and one phrase-first Regla band followed by its limits. It does not claim to hear or certify TalkBack. Once an authorized device is available, run:

```sh
./gradlew :data:connectedDebugAndroidTest :ui:connectedDebugAndroidTest --console=plain
```

This runs the Room migration chain, native PDF generation/reopen/render and Compose accessibility checks. Then exercise actual IME-first Back, rotation, process death, navigation, permission return and shares on the device; JVM composition replacement is narrower evidence.

## Enabled and gated behavior

Local Hoy/Tu día/Ganancias accounting, historical offer details, limits/costs, anonymous use, privacy/preferences, retained import review/correction, own-app comparison, period-specific sharing and factual IMSS payment records are implemented. Real inputs and capability availability determine the visible state; no fixture money is seeded into a live installation.

The following remain gated, unchanged by the version bump:

| Capability | Required evidence before enabling |
|---|---|
| Live Uber/DiDi net verdicts | Certified driver-payment basis; current OCR parsers retain `UNCONFIRMED` (TD-046). |
| Live overlay placement and C6 | Current host/version geometry extraction and physical replay; the default validated-layout set remains empty (TD-048). A debug chip does not prove that a live overlay appears. |
| City rank, confirmed statement sharing and contribution uploads | Certified source/basis/app/period/coverage/cohort contracts (TD-053/TD-056). |
| Server-confirmed imports, cloud restoration and incremental DiDi parsing | Authenticated single-parse commit/restore and supported partial-input contracts (TD-055/TD-056). Local saved review is not server confirmation. |
| Promotional trial, banked days and unsupported referral claims | Authoritative backend capabilities and terms (TD-057). Actual monthly Play purchase also requires verified configured legal URLs and available paid results. |
| Regulatory IMSS estimates/coverage | Validated rule/source/configuration; factual E4 remains available (TD-052). |
| Direct support, external deletion request and city proposals | Verified release configuration (TD-050/TD-054). |

## Physical-device gates still open

No authorized phone or emulator was available at either S21 device inventory. Keep TD-044/TD-045/TD-047/TD-048/TD-049 open until evidence is recorded:

- Read 13sp Barlow at 1× on real hardware; follow the specified Roboto/Archivo fallback only if that gate fails. Check 1×/1.3×/large text and narrow devices for clipped signs, figures and units.
- TalkBack: phrase first, then both chip limits; period before previous/next; no decorative cue announcements; reachable, distinct 48dp actions. Check real keyboards, Back, cutouts, system bars, theme changes and rotation/process restoration.
- Replay current Uber/DiDi hosts with full/compact/no-room states, navigation banner, dark grey/yellow edges, drag/Back exclusion, reduced motion and measured appearance latency. Never tap host actions. Certify each supported layout/version before populating the geometry gate.
- Check silent capture and MediaProjection fallback/recovery, pause/resume, C6 pass-through, C13 absence, Android permission/battery return, notification denial and original next-morning incident timestamps. Verify native PDF/PNG chooser grants, actual Play state and phone/browser handoffs.

## Human study — pending, no participants or scores claimed

Use the complete read-only [driver-test plan](/Users/panama/pilotea/docs/ux-elevation/driver-test-plan.md) and [spec §11](/Users/panama/pilotea/docs/ux-elevation-spec.md). Model or screenshot scores cannot substitute for these gates.

Walkthrough: 10 CDMX Uber drivers, five renters/five owners, at least three Uber+DiDi drivers and two with screened colour-vision deficiency. Run task 8 first, then 1–7 and 9, without hints; task 8 must actually return from Android Settings with permission on. Counterbalance real Uber baseline tasks 1/4/9, obtain consent to view real earnings, and record no amounts. Record first taps for tasks 6/7 and the prescribed neutral comprehension probes. All five pre-registered rules must hold:

1. Mean nine-task SEQ at least 6.0; 5.6–5.99 adds five participants and all gates are then decided on all 15; below 5.6 fails. On 15, anything below 6.0 fails.
2. Matched Kompara tasks 1/4/9 are within 0.5 of the same drivers' Uber mean or better. Record device/scale limitations; the closing 1–10 comparison is reported, not gated.
3. A task below 5.5 mean fails when it is also among the three most-named difficulties, ties included.
4. B1's $548 is correctly understood after costs including car by at least 8/10 (12/15); nobody reads B8's −$100 as an error or debt. Use **D2e alone** for all three neutral questions: `¿Cuánto te dejó este viaje?` → $133; `Esos $133, ¿ya tienen descontado lo que pagas del coche?` → no; `¿Y en todo el día, cuánto llevas ya descontando el coche?` → $548. Each needs 8/10 or 12/15. Three of five renters asking for an after-car trip figure triggers founder review. Record car naming without adding a new gate.
5. No participant reports a money inconsistency.

Mounted glance: 12 drivers, at least three with colour-vision deficiency; phone at 60cm, road-video fixation, 1s exposure at about 25° eccentricity. Cover day/night, C1–C5/C7, C12 full/compact at 1×/1.3×, and C9's navigation banner. State identification must be at least 95%, no worse than the generic comparison chip, median response at most 1s, and compact within five percentage points of full. Require zero grey-as-red and zero yellow-as-below calls; after A1 at least 8/10 correctly explain yellow (`cumple` or `entre`). Verify the C9 plate is not confused with the host banner, observe turn/pin obstruction, gross/net confusion, label/marker legibility and attempted taps. Record yellow take/skip reasons without imposing a right decision. If the comparison chip wins, return that design choice to the founder.
