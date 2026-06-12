# Kompara Launch Runbook — Founder's Playbook

> The human-gated steps to get Kompara from code-complete to launched. The app is built and verified; everything here needs Juan (accounts, a real device, counsel) — not more code.
> Recommended order: **1 → 2 this week** (need nothing external), **4 in parallel today** (counsel has lead time), then **3 → 5 → 6**.

## 1. Install on your Android phone (~30 min)

**Developer mode:**
1. Settings → About phone → tap **Build number** ×7.
2. Settings → System → Developer options → enable **USB debugging**.
3. Connect phone to Mac via USB.

**Build + install:**
```bash
cd /Users/panama/pilotea/android
./gradlew assembleDebug
adb devices          # accept the on-phone "Allow USB debugging?" prompt
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
APK: `app/build/outputs/apk/debug/app-debug.apk` (~11 MB, auto-signed debug key).

**Critical gotcha (Android 13+):** sideloaded apps are blocked from accessibility by default.
- Settings → Apps → **Kompara** → **⋮** → **Allow restricted settings**.
- (Disappears once installed from Play — sideload-only friction.)

**Samsung gotcha (One UI, default-on):** **Auto Blocker / "Bloqueador automático"** blocks USB commands — symptom: tapping USB debugging does nothing and shows *"bloqueado por bloqueador automático"*, and `adb install` fails. Fix: **Ajustes → Seguridad y privacidad → Bloqueador automático → off** for the whole testing period (re-enable after). Sideload-only — Auto Blocker allows Play Store installs, so real users are unaffected.

**Onboarding** deep-links you to Settings → Accessibility → Kompara to enable the reader.

**Works with no backend:** Lector (reader/overlay), Simulator (Ajustes — run first, proves the pipeline offline), local stats, onboarding.
**Needs backend (item 3):** accounts, percentiles/benchmarks, PDF import, sync.

## 2. Verify package IDs + validate the live reader (highest-value work)

App currently targets `com.ubercab.driver`, `com.sdu.didi.gsui` — flagged in code as needing on-device verification.

**A. Confirm IDs (5 min, no ride needed):** install Uber Driver + DiDi Conductor, then:
```bash
adb shell pm list packages | grep -iE 'uber|didi'
```
If different, fix in BOTH and rebuild:
- `android/capture/src/main/res/xml/kompara_accessibility_service.xml` → `android:packageNames`
- `android/parsers/src/main/resources/specs/*.json` → `targetPackage`

**B. Validate against real offers (while driving / high-demand zone):**
1. Reader on, go online in Uber Driver, wait for an offer card.
2. Verdict chip appears with sensible $/km? → parser works. Wrong/blank/absent? → spec needs tuning.
3. Capture the real node tree **with the offer card on screen** (in-app report button not wired yet; use adb):
```bash
mkdir -p ~/kompara-fixtures
adb shell uiautomator dump /sdcard/dump.xml && adb pull /sdcard/dump.xml ~/kompara-fixtures/uber-$(date +%s).xml
```
4. Send a few XML dumps per platform → Claude converts the synthetic fixture corpus into a real one and tightens specs. **This retires the biggest launch risk.**

## 3. Deploy the backend (~half day — unblocks accounts/percentiles/import/sync)

1. Render account + **Render Postgres** (note internal `DATABASE_URL`).
2. New **Web Service** from repo, root `backend/`, build `pnpm install && pnpm build`, start `pnpm start`.
3. Env secrets: `DATABASE_URL`, `TWILIO_ACCOUNT_SID/AUTH_TOKEN/WHATSAPP_FROM`, `ANTHROPIC_API_KEY`, `R2_*`, `ADMIN_TOKEN`, spec-signing key (replace committed dev key — launch blocker).
4. `pnpm db:migrate` + seed population-stats, fiscal-config, parser specs.
5. Point app BuildConfig base URL at the service; rebuild + reinstall.

External accounts to set up (start Twilio early — WhatsApp sender approval has lead time): Twilio WhatsApp, Cloudflare R2, Anthropic API.

## 4. Legal review — B-038 (start today, parallel, external)

Engage MX data-privacy/tech counsel. Brief: `docs/competitive-analysis.md` §4 + two open questions (on-device offer **retention limit**, **fixture-report minimization**).
Deliverables: (a) sign-off/edits on every `TODO(legal-B038)` string (disclosure, fiscal, risk copy), (b) review of Uber MX / DiDi MX driver-agreement third-party-tool clauses, (c) one-page posture memo.

## 5. Play Store submission — B-053 (after deploy + legal + validation)

1. Play Console account ($25 one-time).
2. Generate **release** signing key (debug key can't publish; use Play App Signing).
3. Create app + Spanish listing + screenshots.
4. **AccessibilityService declaration form** — frame as driver decision-support; attach **demo video** (screen-record the Simulator).
5. **Data-safety form** — on-device processing, never sold/shared.
6. **FGS `mediaProjection` declaration + screen-capture data-safety (B-065)** — drafts below, paste into Play Console.
7. Upload signed release AAB → **internal testing** track.

### B-065 drafts: screen-capture (MediaProjection) declarations

**FGS `mediaProjection` justification (declaration form):**
> Kompara is a decision-support tool for ride-hailing drivers. Some driver apps (DiDi Conductor,
> inDrive) render trip offers on a SurfaceView that exposes no accessibility text, so the app uses
> MediaProjection — started only after the user taps "Iniciar lector de pantalla", sees a prominent
> disclosure, and accepts the system screen-capture consent — to read the trip-offer card on screen
> and overlay a real-time profitability verdict while the driver works. Capture runs as a
> mediaProjection foreground service with a persistent notification; the user can stop it at any
> time from the notification or the system cast icon. All frames are processed on-device with ML
> Kit; no screen content is stored or transmitted.

**Data-safety form (screen capture rows):**
- Screen content: processed **on-device only**, ephemeral (frames OCR'd and discarded); **not
  collected, not shared, not sold**; no frame ever leaves the device.
- The derived trip-offer numbers (fare, distance, duration) are stored locally; weekly aggregates
  upload **only** with explicit opt-in consent (existing B-043 row).

**Prominent disclosure (in-app, shown before the system consent — shipped in B-075,
`lector_ocr_disclosure_body`; TODO(legal-B038) counsel review):**
> "Para mostrarte el semáforo sobre las ofertas de DiDi, Kompara captura tu pantalla y la analiza
> únicamente en tu teléfono. Nada de lo que aparece en tu pantalla se guarda en servidores ni sale
> de tu dispositivo. Puedes detener la captura cuando quieras desde la notificación o el ícono de
> transmisión del sistema."

## 6. Driver beta — B-054 (after internal track live)

Recruit 20–30 CDMX drivers (FB/WhatsApp driver groups; founding-tester lifetime premium), add by email to the track, run a feedback WhatsApp group. Their offer cards = fixture firehose + first NPS.

---

### Launch-blocker techdebt to close along the way
Real Play purchase verification + RTDN signature; production spec-signing key (KMS); counsel sign-off on disclosure/fiscal copy; real-device fixture corpus; production fiscal-config seed.
