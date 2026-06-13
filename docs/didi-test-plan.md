# DiDi On-Device Test Plan

> **Date:** 2026-06-12 · **Owner:** Juan (real DiDi driver account) · **Device:** Samsung S25 (the phone the OCR slice was validated on, 2026-06-11)
> **Scope:** end-to-end validation of the driver-facing product against the **real DiDi Conductor app**: signup, reader setup, live verdicts, threshold bands, the new P0 surfaces (explainer, Ajustes editor, Ayuda), and resilience.
> **Why DiDi:** it exercises the *hard* path — DiDi renders via Flutter/SurfaceView (no accessibility text), so the reader uses MediaProjection + ML Kit OCR (`OcrCaptureService` → `DidiOcrParser` → `OfferEventBus` → overlay). Uber's node-based path is strictly easier; anything that passes here derisks both.

---

## 0. Prerequisites (do once)

| # | Item | How |
|---|---|---|
| P1 | Debug APK installed | `cd android && ./gradlew :app:assembleDebug` → `adb install -r app/build/outputs/apk/debug/app-debug.apk` (needs `JAVA_HOME` → Android Studio JBR, `ANDROID_HOME` → `~/Library/Android/sdk`) |
| P2 | Backend reachable from the phone | Debug builds call `http://10.0.2.2:8080` — an **emulator-only** alias. For the physical phone: (1) run the backend locally (`backend/` → dev server on port 8080); (2) change the debug `API_BASE_URL` in `android/sync/build.gradle.kts` to `http://127.0.0.1:8080` and rebuild; (3) `adb reverse tcp:8080 tcp:8080` (re-run after every replug). Alternative: point it at your Mac's LAN IP over shared Wi-Fi and skip adb reverse. (TD-022 tracks the real fix.) |
| P3 | OTP without Twilio | With no `TWILIO_*` env, the backend uses `DevLogSender`: the 6-digit code prints in the backend console (`[DevLogSender] WhatsApp OTP for +52…: 123456`). Read it there during signup tests. |
| P4 | Cost profile known | Set a cost profile you can hand-calc against (suggested: rendimiento 13 km/L, gasolina $24.50/L, mantenimiento $0.60/km → marginal ≈ $2.48/km). Write it here: ______ |
| P5 | DiDi floors known | Note your DiDi floors before testing (fresh install defaults: verde 8.00/km · 90/h, rojo 6.00/km · 67.5/h). After "Volver a la mediana de CDMX": verde 7.41/km · 148/h. Write them here: ______ |
| P6 | DiDi account online-ready | DiDi Conductor logged in, documents valid. ⚠️ Plan short off-peak sessions and let unwanted offers **time out** rather than mass-declining — protect the account's acceptance metrics. |

**Hand-calc worksheet** (used by C3/C4): `totalKm = pickup km + trip km`, `totalMin = pickup min + trip min`, `net = fare − totalKm × marginal($/km)`, `netPerKm = net / totalKm`, `netPerHour = net / (totalMin/60)`. Verdict: both ≥ verde → **verde**; both < rojo → **rojo**; otherwise **amarillo**.

---

## Suite A — Signup & onboarding (fresh install: `adb shell pm clear mx.kompara.app`)

| ID | Steps | Expected | ✓/✗ |
|---|---|---|---|
| A1 | Launch app | 3-page pitch; Spanish reads natural; "Entérate si conviene antes de aceptar" / "Gana hasta 30% más por turno" | |
| A2 | Pitch → **Crea tu cuenta** | Phone screen with +52 prefix; CTA disabled until 10 digits | |
| A3 | Enter `123` → CTA stays disabled; enter your 10-digit number → tap CTA | Code screen says "…al +52 XX XXXX XXXX"; backend console prints the OTP | |
| A4 | Enter a **wrong** 6-digit code | Inline error "El código no es válido o ya venció…"; stays on screen | |
| A5 | Tap "Reenviar código" | Disabled with countdown "Reenviar en 30 s"; re-enables; new code logged | |
| A6 | Enter the right code | Profile screen (Cuéntanos de ti): name + city dropdown (10 cities) | |
| A7 | Pick a city ≠ CDMX, save | Continues to disclosure; later check Comparar/benchmarks use that city | |
| A8 | Kill the app mid-flow right after A6, relaunch | Onboarding resumes; signup step lands directly on the **profile** step (no re-OTP) | |
| A9 | Finish disclosure → accessibility grant → OEM kit → done | Each screen's Spanish is clean; accessibility auto-advances when the service flips on | |
| A10 | Backend check: `drivers` row | UUID id, your phone, name/city saved; `devices.driver_id` linked (anonymous merge) | |
| A11 | Upgrade-path gate: reinstall **without** clearing data (or use the pre-signup install) | Root shows the standalone signup gate, not the tabs; completing phone+code lands in MAIN | |

## Suite B — Reader setup for DiDi (OCR path)

| ID | Steps | Expected | ✓/✗ |
|---|---|---|---|
| B1 | Lector tab → Encender lector → enable Kompara in accessibility settings | Lector shows "El lector está encendido" | |
| B2 | Start screen capture: Lector tab → "Iniciar lector de pantalla" → disclosure dialog → Continuar (B-075; activity no longer adb-launchable — not exported) | Disclosure shows on-device-only copy → system screen-share dialog → accept → OCR service starts; Lector shows "Activo"; no crash; launcher icon still opens MainActivity normally | |
| B3 | Battery: Ajustes → Batería → Kompara "Sin restricciones"; pin the app card in Recents | Settings stick after reboot | |
| B4 | Ayuda check: Ajustes → Ayuda → "¿Cómo activo el lector?" | Steps match what you just did (incl. the DiDi screen-capture mention) | |

## Suite C — Live DiDi offers (the core suite; drive off-peak, 30–60 min)

For each received offer, before it expires, note: fare, pickup ("a X min (Y km/m)"), trip ("X min (Y km)"). Photograph/screen-record the phone if possible (second phone) — the chip overlays the real card.

| ID | Case | Expected | ✓/✗ |
|---|---|---|---|
| C1 | Standard offer card appears | Chip appears over the card in ≤ ~1.5 s (500 ms OCR throttle + parse) | |
| C2 | Chip content | Verdict word + **$X.XX/km hero** + **$N/h** beneath; no "ganancia neta" in collapsed view | |
| C3 | Hand-calc $/km and $/hr for 3+ offers (worksheet above) | Chip figures within rounding (±0.1 $/km, ±2 $/h) of hand calc | |
| C4 | Verdict color vs your floors for those offers | Color matches the two-tier rule (incl. amarillo when between floors, or one metric weak) | |
| C5 | Tap the chip | Detail expands: explainer line first ("Rinde por hora, pero deja poco por km." etc. — must name the actually-weak metric), then ganancia neta, por min, bruto por km | |
| C6 | **Meters pickup** variant (close pickup, e.g. "a 2 min (350 m)") | Pickup parsed (not treated as km); $/km plausible | |
| C7 | **Pon Tu Precio** bid card | Fare = the "Aceptar $X" amount, NOT a higher bid option | |
| C8 | Surge/multiplier marker on card (e.g. "1.5x") | Parse still succeeds; fare is the final amount | |
| C9 | Offer expires / card disappears | Chip hides within ~3–4 s (6 unparseable frames @ 500 ms); no zombie chip over the map | |
| C10 | Chip stability while card visible | No flicker/blink during the offer's lifetime | |
| C11 | Drag the chip to the other edge mid-offer | Drags smoothly, snaps to edge; next offer appears at the saved position | |
| C12 | Long-press chip → quick sheet → raise "Verde desde" above the last offer's $/km | Next comparable offer grades lower (verde→amarillo); sheet never leaves DiDi | |
| C13 | Chip never covers DiDi's Aceptar button (bottom safe zone) | Accept tap always lands on DiDi, never on the chip | |
| C14 | An accepted trip + an ignored offer | Later: Inicio "Hoy" shows the offer funnel (vistas/aceptadas/rechazadas) and the day's numbers | |
| C15 | Unreadable/odd card (if any occurs) | Chip shows a missing-data hint ("Sin tarifa"/"Datos incompletos") or stays away — never a wrong confident verde | |

## Suite D — Thresholds & explainer without driving (simulator + Ajustes)

| ID | Steps | Expected | ✓/✗ |
|---|---|---|---|
| D1 | Ajustes → Ver el simulador; step through the 3 DiDi demo offers | Verde / amarillo / rojo as scripted; chip shows $/km + $/h | |
| D2 | Playground slider: drag the km floor up/down | The three verdicts re-grade live | |
| D3 | Ajustes → **Tu semáforo** → DiDi chip | Shows current floors; sliders for verde/rojo × km/h | |
| D4 | Drag "Rojo por debajo de" above "Verde desde" | Red caps at green (can't cross) | |
| D5 | Drag "Verde desde" below the red floor | Red follows green down | |
| D6 | "Volver a la mediana de <ciudad>" | Floors jump to the city p50 (CDMX DiDi: 7.41/km, 148.12/h) | |
| D7 | Long-press chip in the simulator → quick sheet | Quick sheet shows the values just set in Ajustes (same store) | |
| D8 | Kill + relaunch app | All floors persist | |

## Suite E — Resilience (any time)

| ID | Steps | Expected | ✓/✗ |
|---|---|---|---|
| E1 | Reboot phone, don't open Kompara, go online in DiDi | Accessibility service auto-restarts; (OCR consent must be re-granted — Android revokes MediaProjection on reboot: re-run B2) | |
| E2 | Force-stop Kompara while reader on | Watchdog notification "El lector de Kompara se apagó" + banner on next open | |
| E3 | Battery saver ON during an offer | Chip still appears (or document degradation) | |
| E4 | Incoming WhatsApp call during an offer | No crash; chip recovers on next offer | |
| E5 | Dark mode + smallest/largest system font | Chip and new screens (semáforo editor, Ayuda, signup) legible, nothing clipped | |
| E6 | Airplane mode → open app | MAIN still opens (session cached); signup screens show the network error string only when acting | |

## Exit criteria

- **Parse:** ≥ 95% of received DiDi offers produce a chip with correct fare (C1–C8); zero wrong-fare verdicts on Pon Tu Precio cards.
- **Math:** 100% of hand-calced offers within tolerance (C3) and correct color (C4).
- **Lifecycle:** no zombie chips (C9), no flicker (C10), no Aceptar occlusion (C13).
- **Funnel:** A-suite completes start-to-finish on a clean install with zero copy issues worth filing.
- Any failure: capture via the debug snapshot recorder / screen recording, file as a `B-0xx` task with the offer's raw values.

## Results log

| Date | Build (commit) | Suite | Pass | Fail | Notes / filed tasks |
|---|---|---|---|---|---|
| 2026-06-12 | 3863caa + session fixes | A (signup/onboarding) | A1–A7, A9, A10 | — | A8/A11 not run. OTP, error path, resend cooldown, profile→backend (name+city in drivers row), city→settings mirror all verified on device. Findings F1, F2 (fixed in-session) |
| 2026-06-12 | ″ | B (reader setup) | B1, B2, B4 | — | Accessibility auto-advance celebration works; OCR consent (Android 15 full-screen mode) starts the FGS; B3 battery not exercised |
| 2026-06-12 | ″ | C (proxy via rendered DiDi card) | C1, C2, C3, C5, C9, C13 | — | Full OCR→parser→engine→overlay pipeline on a synthetic DiDi card displayed fullscreen: chip "Conviene $15.00/km / $400/h" matches hand calc exactly; explainer + detail correct; chip hides ≤1.5 s after leaving; never covers Aceptar. **Live offers (C6–C8, C12, C14) still need a real driving session** |
| 2026-06-12 | ″ | D (simulator + Tu semáforo) | D1–D8 | — | RED→YELLOW live re-grade with HOUR_WEAK explainer; red≤green invariant both directions; reset to **Monterrey** medians ($7.70/$5.78/km, $154/$116/h) proves city plumbing; per-platform isolation (DiDi untouched); cross-surface sync (playground↔editor). Finding F4 |
| 2026-06-12 | ″ | E (resilience subset) | E2 | — | Watchdog notification (canal "Estado del lector") + red Inicio banner with Reactivar on verified service kill. ⚠️ Methodology: OneUI silently restores `settings put secure enabled_accessibility_services ""` — use `settings delete` to simulate the kill. E1/E3–E6 not run |

### Findings (2026-06-12 session)

- **F1 (low, filed B-073):** bottom CTA on onboarding screens renders under the gesture-nav inset ("Continuar"/"Empezar"/"No acepto" partially covered; taps near the bottom edge can be eaten by the system).
- **F2 (fixed in-session):** `%%` rendered literally in unformatted strings — "Gana hasta 30%% más" on pitch page 2 and the fiscal PDF rates note. Fixed with `formatted="false"` + single `%`.
- **F3 (fixed in-session, launch-class):** Android blocks cleartext HTTP, so debug builds could never reach a dev backend (OTP, spec fetch, device registration all silently failing on device). Fixed with a debug-only network security config allowing 127.0.0.1/localhost/10.0.2.2; release keeps the platform default.
- **F4 (low, filed B-074):** the simulator's guided headline text is static per demo shape while its color is live — after moving the playground floor the chip says amarillo but the script still reads "Rojo: …".
- **F5 (low, filed B-074):** tapping the already-selected bottom tab from a detail screen (e.g. Ayuda) doesn't pop back to the tab root.
