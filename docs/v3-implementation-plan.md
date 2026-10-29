# Kompara v3 Android implementation plan

Plan only · 2026-10-29 · target branch `feat/v3-redesign` · target APK version `0.2.0`.

## 1. Scope, evidence and precedence

This plan changes no application code. Implement subsequent slices in this worktree, retain the native Kotlin/Compose architecture, commit with conventional messages ending in `Generated with Codex CLI`, and do not push. `/Users/panama/pilotea` is a read-only reference. No slice below requires changes to the legacy root `src/` or to `backend/`; missing server contracts are explicit release gates, not permission to invent responses. Android's own `android/*/src/` directories are, of course, in scope.

Reference reviewed:

- `/Users/panama/pilotea/docs/ux-elevation-spec.md`, especially §§3–7, 9r–9w, the final §9x corrections and §12, on `docs/ux-elevation-v3`. Initial reference HEAD was `1a6fdad8fb1d575373980fd503e97b16e0b49e2d`; the reference advanced during this review to **`2bb61fedd5bb63c9afc987a0add7455fbc78f5f9` (Round 24e)**. This plan incorporates that final revision, including equal detail-row padding and the remaining first-day Ajustes decision.
- `/Users/panama/pilotea/design-system/ui_kits/android-app-v2/{index.html,base-v3.css,_regla-markup.html,fixture.json,_frame-contract.md,prototype.html}`. The actual index and contract contain **116 frames: 106 day/system states plus N1–N10**, not 113. A4r, B3n and D2e are included below. A3b was merged into A3 and is not another screen to build. The prototype's 113-screen count excludes its three live editor/snackbar states; it is not the static frame count.
- Rendered reference: `/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/final25/`. B1, G2d, C12 and E1f were visually inspected for the day anatomy, inline editor/IME, compact chip and sheet geometry.
- Current `CLAUDE.md`, `docs/android-technical-design.md`, `docs/competitive-analysis.md`, Android source and tests, at implementation base `18df17b00a8e92adce320dec137de195d499299e`. Current source is more reliable than stale comments about a node-only Uber path: OCR and the API 30+ silent screenshot lane already exist.

Precedence when historical prose disagrees: the latest explicit decision, current frame text and final CSS cascade win over older prose/captions. In particular:

1. The **Brand block at the end of CSS** overrides older “ink primary” and “ink active tab” statements. Primary actions are Kompara green, including at night.
2. Amarillo says **“Cumple tu mínimo”**, including equality; not “Entre tu meta y tu mínimo”. C5 says **“Falta tiempo” / “sin cálculo”**; the older state-table “sin semáforo” is stale.
3. E1's **“Por app” opens E1f over Ganancias**, not a silent tab switch to F1. E1's reconciliation opens E1e and carries no amounts on the root row.
4. “No cupo el semáforo” appears only for a positive suppression count. An unread fare is not a suppressed overlay.
5. At 14:40 the 14:32 trip is **“En curso”** and counted as an estimate; the last finished trip is 11:20, D2e. Current month markup also includes `(1 en curso)`; an older §9w open-item sentence saying it does not is stale.
6. “a esa hora” depends only on the historical limit named by the detail sentence. Changing the minimum must not add that suffix to a sentence about an unchanged goal.
7. A3s is an **Android hand-off/test stand-in**, never a Kompara page or fake permission toggle. H6/H6b are actual Android notifications, not an in-app copy of the shade. Native IME, status bars and system dialogs are not pixel replicas of HTML fixtures.
8. Where the kit leaves an interaction undrawn, use the existing state model and §4 grammar: first-day Ajustes reflects the actual off/prepared state; month picking uses the existing period-sheet pattern; B5's ledger is computed, not a prototype “not in this test” note. Keep native targets at least 48dp even where the HTML date label is smaller; let the header grow without overlapping targets.
9. §9x spreads the offer-detail slack evenly around its three ledger rows, while keeping the actions' 24dp inset. Use equal flexible row padding, not one large spacer before the actions: reference padding is 18.1dp for D2/D2b/D2c/D2e and 15.1dp for D2d; remeasure for wrapped historical-limit copy, larger text and native insets.

### Baseline and commands

The working tree was clean before planning. Room is version 4; app `versionName` is `0.1.0`, `versionCode` 1; min SDK 26, target/compile SDK 36. Verified on this base: **`:app:assembleDebug`, `testDebugUnitTest` and `verifyReadOnlyCapture` pass; 953 tests, zero failures/errors/skips** (app 1, billing 40, capture 40, data 42, metrics 140, ocr 74, overlay 54, parsers 189, sync 102, ui 271). No emulator/instrumented tests were run for this plan.

Normal commands, from `android/`:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug --console=plain
./gradlew testDebugUnitTest verifyReadOnlyCapture --console=plain
```

The initial combined invocation hit sandbox cache permissions, not an assertion failure: Kotlin's daemon marker under `~/Library/Application Support/kotlin/daemon`, and Robolectric's `~/.robolectric-download-lock`. The successful retry used `-Pkotlin.compiler.execution.strategy=in-process` and a temporary Gradle init script configuring only `Test` tasks with `user.home=/private/tmp/kompara-v3-test-cache` and `maven.repo.local=/Users/panama/.m2/repository` (existing jars). Neither workaround changed project files or the shell's HOME. Logs: `/private/tmp/kompara-v3-plan-baseline.log` and `/private/tmp/kompara-v3-plan-baseline-retry.log`. Reuse a writable test cache if the same environment requires it; never disable tests to bypass this.

## 2. Existing owners and complete frame/state map

Paths in the owner table are relative to `android/`. `ui/…` means `ui/src/main/java/mx/kompara/ui/…`, and likewise for each module. A renamed screen is an adaptation of its existing implementation/ViewModel, not a second feature stack. NEW means a new state/component with the indicated existing data owner. Frame IDs identify test scenarios, not 116 navigation destinations.

| Owner key | Existing composable / ViewModel / service | v3 treatment |
|---|---|---|
| ON | `ui/onboarding/OnboardingScreens.kt`: `PitchScreen`, `DisclosureScreen`, `AccessibilityGrantScreen`, `LimitedInfoScreen`, `OemSurvivalScreen`, `OnboardingDoneScreen`; `OnboardingViewModel`, `OnboardingNavGraph`, `RootRoute`, `SettingsLinks` | Adapt demo/disclosure/permission/battery; retire limited-info/done detours and three-page pitch after callers move. |
| AUTH | `ui/auth/SignupScreens.kt`, `SignupViewModel`, `SignupUiState` | Reuse number/code; optional, contextual; retire mandatory profile/signup gate. |
| HOME | `ui/screens/InicioDashboardScreen.kt`, `ui/stats/InicioDashboardViewModel.kt`, `InicioStats.kt` | Adapt/rename to Hoy; replace current-week dashboard model with reactive today model. |
| CHIP | `overlay/VerdictChipUi.kt`, `VerdictChipState.kt`, `OverlayController.kt`, `OverlayStateMachine.kt`, `OverlayPosition.kt`, `OverlayPrefs.kt` | Shared visual Regla in `:ui`; overlay retains WindowManager/drag integration. Retire quick editor and expanded chip. |
| DAY | `ui/screens/DayDetailScreen.kt`, `ui/stats/DayDetailViewModel.kt`, `DayDetail.kt` | Adapt Tu día; NEW detail and ledger sheets attached to its dated state, also openable from Hoy. |
| WEEK | `ui/screens/WeekSummaryScreen.kt`, `ui/stats/WeekSummaryViewModel.kt`; `HistoryScreen` / `HistoryViewModel` / `HistoryWeeks` | Adapt to Ganancias Semana; replace history destination with period picker. |
| MONTH | NEW Ganancias Mes presentation; reuse `ui/stats/FiscalMonth.kt`, `PeriodStats.kt`, `WeekClock.kt` and dated DAO queries | Share period accounting with DAY/WEEK, not fiscal thresholds. |
| FISCAL | `ui/screens/FiscalScreen.kt`, `ui/stats/FiscalViewModel.kt`, `ImssTracker.kt`, `ui/fiscal/*` | Move under Mes; launch payment-only E4; E5 remains gated. |
| COMPARE | `ui/screens/CompararScreen.kt`, `ui/stats/CompararViewModel.kt`, `CompareState.kt`, `ComparisonBuilder.kt`, `sync/aggregate/*` | Adapt scopes to Tus apps / Tu ciudad, one basis/metric at a time. |
| SETTINGS | `AjustesScreen` currently in `ui/screens/PlaceholderScreens.kt`; `ui/stats/AjustesViewModel.kt` | Extract and adapt to drill-in Ajustes, grouped valued rows. |
| LIMITS | `ui/screens/ThresholdsScreen.kt`, `ui/stats/ThresholdsViewModel.kt`, `ThresholdEditor.kt`; `overlay/simulator/*`, `ThresholdSheet.kt` | One draft editor; absorb simulator preview; remove live-overlay editor and simulator destination. |
| COST | `ui/screens/CostProfileScreen.kt`, `ui/stats/CostProfileViewModel.kt`, `CostPreview.kt`, `data/settings/CostProfileRepository.kt` | Adapt running costs; NEW effective-dated Lo del coche sheet/repository. |
| READER | `ui/screens/LectorScreen.kt` contains `LectorScreen` and `LectorViewModel`; `data/service/ServiceStatusProvider.kt`, `ScreenReaderState.kt`, `capture/ServiceStateRepository.kt` | Adapt/rename Semáforo; NEW shared readiness model from real capture evidence. |
| ACCOUNT | `ui/screens/AccountScreen.kt`, `ui/stats/AccountViewModel.kt`, `sync/auth/AuthRepository.kt` | Tu perfil; move deletion to NEW Tus datos; keep local reader/history on logout. |
| HELP | `ui/screens/HelpScreen.kt`; `ui/referral/ReferralScreen.kt` / `ReferralViewModel.kt` for unframed invites | Adapt help accordion and contextual referral task. |
| PLAN | `ui/paywall/PaywallScreen.kt`, `PaywallViewModel.kt`, `billing/EntitlementRepository.kt`, `TierGatekeeper.kt`, `PlayBillingClientImpl.kt` | Adapt Premium; NEW Tu plan states using real entitlement capabilities. |
| SHARE | `ui/share/ShareCardScreen.kt`, `ShareCardViewModel.kt`, `ShareCardProvider.kt`, `ShareCardComposer.kt`, `ShareCardRenderer.kt` | One period-specific bitmap for preview and export; hide amounts/hours by default. |
| IMPORT | `ui/imports/ImportScreen.kt`, `ImportViewModel.kt`, `ImportUiState.kt`; `sync/imports/ImportRepository.kt`; `app/ShareImportActivity.kt` | Adapt consent/review/retry; NEW retained draft, correction and reconciliation states. |
| NOTIFY | `ui/onboarding/ServiceWatchdog.kt`, `WatchdogState.kt`, `ui/share/WeekClose*`, `ui/fiscal/FiscalMonthEnd*`, `ocr/OcrCaptureService.kt` | Unified incident notifications plus dated summaries; NEW daily-summary scheduling. |

### A — First run, permission and later setup (11)

| Frame | State / acceptance anchor | Existing owner → destination | Slice |
|---|---|---|---|
| A1 | Interactive four-state demo; “Antes de aceptar, ve cuánto te deja.” | ON `PitchScreen` + simulator engine → demo using shared Regla | S15 |
| A2 | “Lo que hace Kompara”; explicit “Uber y DiDi” line before “Acepto” | ON `DisclosureScreen` | S15 |
| A3 | “Enciende Kompara en Accesibilidad”; “EJEMPLO”; “Abrir ajustes” | ON `AccessibilityGrantScreen` | S15 |
| A3s | Android Settings return, toast and both permission outcomes | ON `SettingsLinks` + NEW lifecycle result handling; OS surface, not a composable | S15 |
| A3n | “Semáforo apagado · aún falta el permiso” | ON permission screen returned without grant | S15 |
| A4 | Battery restriction on first run; “1. En Aplicaciones” | ON `OemSurvivalScreen` conditional battery flow | S15 |
| A4b | Battery from B2, permission still missing | ON battery flow + READER state; return to B2 | S15 |
| A4r | Battery after recovery; “Semáforo preparado”, “permiso listo” | ON battery flow + READER state; return to B2r | S15 |
| A5 | Later efficiency question; `$2.84 por km`, sample `$34` | COST + NEW short efficiency entry; not mandatory onboarding | S13 |
| A6 | Optional WhatsApp number; “Mandar código” | AUTH number screen | S15 |
| A7 | Six digits, auto-submit, resend cooldown | AUTH code screen | S15 |

### B — Hoy (11)

| Frame | State / acceptance anchor | Existing owner → destination | Slice |
|---|---|---|---|
| B1 | 14:40; “Llevas hoy · aprox.” `$548`; “En curso” | HOME + READER + DAY rows | S7 |
| B2 | Permission lost; dated yesterday `$1,026`; “Que no se vuelva a apagar” | HOME recovery using READER/NOTIFY incident | S7, S19 |
| B2r | Permission restored, history retained; prepared, not functioning | HOME + ON return state | S7, S15 |
| B3 | First permission granted; “Listo. Abre tu app de viajes” | HOME empty/prepared + shared Regla example | S7 |
| B3n | First-day skip; “Activar el semáforo”; no money | HOME off/no-history + ON skip | S7, S15 |
| B4 | Morning; costs question, yesterday dated `$284`, last read `20:10` | HOME + COST confirmation | S7, S13 |
| B5 | Paused; `$434`, `4 h 30 min`, “Reanudar el semáforo” | HOME + READER pause | S7 |
| B6 | Partial support; “Llevas hoy sin contar DiDi · aprox.” `$166` | HOME + supported-app readiness | S7 |
| B7 | “Batería restringida · aún funciona”; same `$548` | HOME + READER attention | S7 |
| B8 | One taken trip under way; `−$100`, `($214 por día)` | HOME alternate accounting state | S7 |
| B9 | Positive no-room count; row “sin semáforo en pantalla” | HOME + CHIP presentation outcome | S7 |

### C — Overlay (13)

| Frame | State / acceptance anchor | Existing owner → destination | Slice |
|---|---|---|---|
| C1 | `$9.49/km`, “Arriba de tu meta”, ▲ | CHIP full Regla | S4 |
| C2 | `$7.16/km`, “Cumple tu mínimo”, ◆ | CHIP full Regla | S4 |
| C3 | `$2.20/km`, “Abajo de tu mínimo”, ▼ | CHIP full Regla | S4 |
| C4 | “Sin lectura” / “falta la tarifa”; no rate | CHIP NEW unavailable state | S4 |
| C5 | “Falta tiempo” / “sin cálculo”; `$200/h`, `$150/h` limits | CHIP NEW chosen-unit unavailable state | S4 |
| C6 | 240×72 stopped-capture tab; service still alive | `ReaderDownBannerUi` / CHIP → NEW passive `ReaderStoppedTab` | S6, S19 |
| C7 | `−$1.05/km`; same red plate, no clamp | CHIP full Regla | S4 |
| C8 | Tall host card; higher-left clear placement | CHIP + NEW host geometry | S5 |
| C9 | Navigation banner, next-turn exclusion; higher-right placement | CHIP + NEW host geometry | S5 |
| C10 | 1.3× full plate, 312×145.6, recalculated placement | CHIP measured growth | S4, S5 |
| C11 | No safe band; offer still logged, no plate | CHIP suppression + persisted presentation result | S5 |
| C12 | Compact 240×80, both limits retained | CHIP shared compact Regla | S4, S5 |
| C13 | Permission absent; no overlay at all; notification icon only | READER/NOTIFY + CHIP detach | S6, S19 |

### D — Tu día and sheets (11)

| Frame | State / acceptance anchor | Existing owner → destination | Slice |
|---|---|---|---|
| D1a | 14:40, `$548`, 10 offers newest first, one “En curso” | DAY screen/VM made reactive | S8 |
| D1b | D1a with 12:10 offer “sin semáforo en pantalla” | DAY + presentation metadata | S8 |
| D1 | 21:10, `$1,026`, 13 offers, `8 h 10 min` | DAY late-day state | S8 |
| D2 | 14:32 offer detail; `$114 sin el coche`, `$285/h` | NEW `OfferDetailSheet`, DAY/HOME state owner | S8 |
| D2b | Not taken 12:10; “Te habría dejado · aprox.” `$27` | Same NEW sheet, stored offer evaluation | S8 |
| D2c | 14:50 taken; `$86`, “cumple tu mínimo de $6.00/km” | Same NEW sheet, historical minimum | S8 |
| D2d | Not shown on host; `$27`, suppression explanation, day `$548` | Same NEW sheet + presentation metadata | S8 |
| D2e | Last finished 11:20; `$133 sin el coche`, `$308/h`, day `$548` | Same NEW sheet, finished lifecycle | S8 |
| D3 | Day ledger at 21:10: `$1,590 − $350 − $214 = $1,026` | NEW `CalculationSheet` + DAY accounting | S8 |
| D3a | Day ledger at 14:40: `$979 − $217 − $214 = $548` | Same NEW sheet + shared as-of clock | S8 |
| D3b | October ledger: `$20,429 − $4,666 − $4,071 = $11,692` | Same NEW sheet + MONTH accounting | S10 |

### E — Ganancias and IMSS (17)

| Frame | State / acceptance anchor | Existing owner → destination | Slice |
|---|---|---|---|
| E1 | Closed week `$4,980`, 62 trips, `vie $1,166` | WEEK → Ganancias Semana | S9 |
| E1b | Por día sheet; displayed days `$4,979`, rounded-once week `$4,980` | NEW `DayBreakdownSheet`, WEEK/period accounting | S9 |
| E1c | Alternate Sunday “Sin viajes”; `−$214`; week `$4,481` | Same NEW sheet, calendar-day costs | S9 |
| E1d | Week ledger; “Uber $5,028 · DiDi $3,372” | `CalculationSheet` + WEEK | S9 |
| E1e | “Solo Uber, antes de gastos”; `$5,028`, `$5,062`, `+$34` | NEW `StatementDifferenceSheet`, WEEK + IMPORT provenance | S9, S16 |
| E1f | “Por app · 12 – 18 oct”; `$10.34/km`, `$9.13/km` | NEW `PerAppSheet`, WEEK + shared comparison calculation | S9 |
| E2 | Older week, own `$4,747` always visible; breakdown locked | WEEK + period-aware PLAN gate | S9, S17 |
| E3 | October `$11,692`, 155 trips, current period | MONTH NEW view in adapted Ganancias | S10 |
| E3d | Por semana sheet, partial-week scope, today's `$548` | NEW `WeekBreakdownSheet`, MONTH | S10 |
| E3b | Imported undated week only; payment `$5,340`, no net/plot | MONTH + IMPORT coverage | S10, S16 |
| E3c | Mixed month; `$7,170`; `$5,340` “no entra en el total” | MONTH + IMPORT coverage | S10, S16 |
| E4 | Launch IMSS payments: Uber `$12,487`, DiDi `$7,942` | FISCAL → IMSS del mes under Ganancias | S10 |
| E5 | Legal-gated example; `$11,738`, `$7,465`, `$9,582` | FISCAL gated preview only until validated rule | S10 |
| E6 | Elegir semana; newest first; imported week identified | WEEK `HistoryViewModel` → NEW period sheet | S9 |
| E7 | Static local skeleton; known title retained | WEEK loading state | S9 |
| E7b | “Sin conexión”; local `$4,980`/bars unchanged | WEEK offline state | S9 |
| E8 | Open week `$548`; “Cada día se restan $214 del coche” | WEEK current-period accounting | S9 |

### F — Comparar (5)

| Frame | State / acceptance anchor | Existing owner → destination | Slice |
|---|---|---|---|
| F1 | “DiDi te dejó $1.21 más por km”; each bar “sin el coche” | COMPARE Tus apps | S11 |
| F2 | Confirmed source/valid cohort; `78 %`, `$149/viaje`, `$131/viaje` | COMPARE Tu ciudad, eligible Premium state | S11 |
| F3 | Same own figure/rank; only average slot locked | COMPARE + period/source-aware PLAN gates | S11, S17 |
| F4 | No cohort; own DiDi `$120/viaje` vs `$107/viaje`, `+12 %` | COMPARE no-cohort/own-history state | S11 |
| F5 | “Falta tu resumen de Uber para ver tu lugar”; `$161/viaje` | COMPARE unconfirmed state + IMPORT route | S11 |

### G — Semáforo and settings (23)

| Frame | State / acceptance anchor | Existing owner → destination | Slice |
|---|---|---|---|
| G1 | Ajustes valued rows, costs and limits | SETTINGS; extract out of `PlaceholderScreens.kt` | S13 |
| G1b | “Apagado · falta el permiso” | SETTINGS + shared READER state | S13 |
| G2b | Limits at rest, preview, disabled Save/reset slots retained | LIMITS draft editor | S12 |
| G2 | Goal `$9.50/km`, yellow `$9.49/km` preview, dirty | LIMITS draft editor | S12 |
| G2c | Minimum `$6.50/km`; goal unchanged; green preview | LIMITS draft editor | S12 |
| G2d | Typing in place; “Menor que tu meta ($8.00/km)” | LIMITS field focus + native IME | S12 |
| G2e | “¿Guardar el cambio?”; “No guardar” / “Guardar” | NEW unsaved-draft dialog in LIMITS | S12 |
| G3 | Running-cost formula, EV/direct-input paths | COST screen/VM | S13 |
| G4 | Working; “Lectura comprobada · 14:32”; limits first | READER → Semáforo screen/VM | S12 |
| G4b | Battery restricted but reading | READER attention state | S12 |
| G4c | “No cupo el semáforo en 1 oferta hoy” → Tu día | READER + CHIP suppression records | S12 |
| G4d | Paused, “Se reanuda mañana”, reactivation | READER pause state | S12 |
| G4e | Prepared on first use; waiting for first offer | READER first-use state | S12 |
| G4f | Prepared next morning; “Última lectura ayer 20:10” | READER persisted per-app read evidence | S12 |
| G4g | Solo Uber; DiDi “Por ahora no la leemos” | READER parser capability state | S12 |
| G4h | Off, no permission; “Volver a dar el permiso” | READER incident + ON recovery | S12 |
| G4i | Updated limits + “Guardado: tu mínimo ahora es $6.50/km” | READER opener + NEW shared save snackbar | S12 |
| G5 | Lo del coche, weekly amount and effective date | NEW `CarCostSheet` over SETTINGS; COST repository | S13 |
| G6 | Tus datos; telemetry/contribution, local and account deletion | NEW screen/VM; ACCOUNT + settings/sync owners | S14 |
| G6b | “¿Eliminar tu cuenta?” and factual retention copy | ACCOUNT action moved to Tus datos confirmation | S14 |
| G7 | Avisos, system notifications blocked | NEW screen; extract SETTINGS preferences + NOTIFY | S14, S19 |
| G8 | Help, single-open five-question accordion | HELP screen | S14 |
| G9 | Profile city selection and confirmation, thresholds preserved | ACCOUNT screen/VM | S14 |

### H — Tasks and notifications (15)

| Frame | State / acceptance anchor | Existing owner → destination | Slice |
|---|---|---|---|
| H1 | Contextual Premium offer, explicit 14-day trial | PLAN paywall adaptation; real eligibility gate | S17 |
| H1b | WhatsApp before trial; no automatic trial start | PLAN + AUTH contextual flow | S17 |
| H2 | Trial/grant access until `9 de noviembre` | NEW Tu plan screen/VM, PLAN repositories | S17 |
| H2b | “Google Play cobra hoy”; `$79`; banked `21 días` | NEW Tu plan purchase explanation, capability-gated | S17 |
| H2c | Grace and promotional days; real dates, payment attention | NEW Tu plan state + billing status | S17 |
| H2d | “Pago en proceso”; preserve existing access | NEW Tu plan pending state | S17 |
| H3 | Exact-period bitmap; rank `78 %`, 34 trips, hidden amounts | SHARE adaptation | S18 |
| H4 | “Trae tu resumen”; consent on picker and Android Share routes | IMPORT entry | S16 |
| H5 | Review retained parse; `$5,340`, 29 trips, 26 h, no km/net | IMPORT review adaptation | S16 |
| H5b | Partial coverage reconciliation; 14 vs 20 trips, missing km of 6 | NEW IMPORT reconciliation state/ledger | S16 |
| H5c | DiDi second capture absent; keep first parsed result | NEW IMPORT partial-draft recovery | S16 |
| H5d | “Corregir lo leído”, native numeric entry | NEW IMPORT correction state | S16 |
| H5e | Failed upload; selected file retained; “Reintentar” | IMPORT retry adaptation | S16 |
| H6 | Amount-free outage/day/week/month notices | NOTIFY; NEW daily scheduler | S19 |
| H6b | Next-morning deduplicated permission incident, correct tap route | NOTIFY incident persistence + HOME recovery | S19 |

### N — Night twins (10)

These use the same composables, state and source records as their day twins; no duplicated night ViewModels. Night is a theme, not a different as-of time.

| Frame | Day twin | Existing owner → destination | Slice |
|---|---|---|---|
| N1 | B1, including “En curso” and the basis column note | HOME | S7, S20 |
| N2 | B2, dated yesterday; green primary retained | HOME | S7, S20 |
| N3 | D1, scrollable offer log | DAY | S8, S20 |
| N4 | E1, figure beside bars and E1f door | WEEK | S9, S20 |
| N5 | E3 at 14:40 | MONTH | S10, S20 |
| N6 | F1, own-app comparison | COMPARE | S11, S20 |
| N7 | F2, statement-based city comparison | COMPARE | S11, S20 |
| N8 | C1, dark host | CHIP | S4, S5, S20 |
| N9 | C2, dark host | CHIP | S4, S5, S20 |
| N10 | C4, dark host, stronger keyline | CHIP | S4, S5, S20 |

Also required without a separate frame: loading/empty/error for each data destination; no-history/single-app comparison; Datos/provenance and contribution sheets; date/month selection; cost-entry sheets; explicit cancellation; report-read consent; optional sign-in/restore; referral; theme override; account-deleted/error outcomes. Their owning slices below include these states. Never implement the prototype's temporary notes as product actions.

## 3. Design-system layer first

### Tokens and theme

Adapt `ui/theme/{Color,Type,Theme}.kt`, rather than creating another app theme. Keep semantic v3 tokens available through a typed theme object/CompositionLocal where Material3's slots cannot express them. Existing components may temporarily use compatibility aliases to these tokens; remove aliases only when all callers move. `:overlay` already depends on `:ui`; put the pure Regla visual and its display model in `ui/components/` so onboarding and limits can use it without a dependency cycle. Mapping metrics to that model stays with the appropriate caller.

| Role | Day | Night / special rule |
|---|---|---|
| Paper / secondary paper | `#FFFFFF` / `#F2F2F0` | `#121211` / `#262624` |
| Ink / secondary / tertiary | `#111110` / `#5C5B57` / `#8A8984` | `#F2F1EC` / `#A8A7A1` / `#74736E` |
| Rule | `#E8E7E3` | `#2C2C29` |
| Brand / on-brand | **`#187A36` / `#FFFFFF`** | Buttons stay exactly these values |
| Active nav and selected text-tab underline | Brand | `#3FB565` (dedicated active-navigation token, not a new verdict) |
| Verde / on-verde | `#187A36` / `#FFFFFF` | Fixed across themes |
| Amarillo / on-amarillo | `#F2B705` / `#1A1300` | Fixed across themes |
| Rojo / on-rojo | `#D32F2F` / `#FFFFFF` | Fixed across themes |
| Chip ink / on-chip | `#0E0F10` / `#EDEDEA` | Fixed; grey band `#2A2C2F` |
| Tray marker tints | `#2FB35A` / `#F2B705` / `#FF5C5C` | Tray only, never paper/status colours |
| Chip dark-host edge | — | White 12% keyline; grey state 20%; deeper shadow |

Brand fills primary buttons and on switches, colours the K, selected OTP underline and focused limit outline. Text actions, money, error states and destructive text remain ink. Verdict hues remain confined to verdicts, their teaching cues and the healthy status dot; a green button has no verdict cue or meaning. No dynamic Material You colours, slate/emerald substitutes, independent overlay palette, red error theme or coloured status cards. Follow system theme by default, with persisted `Automático / Claro / Oscuro` override under `Tema`; remove `DARK_PREFERRED`.

### Bundled typography

Bundle Barlow 400/600 and Barlow Semi Condensed 500/600/700 in `ui/src/main/res/font/`, with their OFL notices under `android/licenses/`. No runtime font downloads or horizontal scaling. UI = Barlow; figures/rates/counts = Barlow Semi Condensed with `tnum` and `lnum`. Validate actual font tables and glyph advances, including the minus/peso sign and Spanish accents. The ship gate is 13sp Barlow on a real device at 1×; if the font files or legibility fail, use **Roboto for UI and Archivo SemiCondensed 600 for money**, per the spec (the older CSS Roboto Condensed stack is not the build fallback). No 800/900 weight. Keep Inter until every old caller migrates, then remove it.

| Role | sp / line height | Weight / face |
|---|---|---|
| Root title | 30 / 36 | 700 money |
| Day display only | 72 / 74 | 700 money; Hoy/Tu día only |
| Period figure beside chart | 40 / 44 | 700 money |
| Headline / finding sentence | 24 / 30 | 700 money |
| Section | 22 / 28 | 700 money |
| Number / limit / offer total | 32 / 36 | 600 money |
| Drill-in / sheet / dialog title | 20 / 26 | 600 UI |
| Offer outcome / meaning | 18 / 24; 15 / 20 | 600 UI outcome, regular meaning |
| Body / buttons / status / quiet line | 16 / 22 | UI 400/600; offer rate money 600 |
| Small / nav / caption | 13 / 18 | UI; normal product text floor |
| A5 efficiency value | 56 | Money; explicit exception, not another hero size |

The D2 “sin el coche” suffix is money 500 at 20sp on the 32sp number baseline. Regla uses its separate size ladder below. Native permission illustration text is part of a tagged picture, not an interactive small-font permission control.

### Shared components and geometry

- `StatusLine` and `StatusDoor`: six drawn glyphs, always with words. Healthy = 10dp green dot/halo; waiting = hollow ring; off/problem = slashed ring; paused = two bars; fact/done = check; working with a fix = ring with `!`. Glyph family 16dp, ink except healthy dot. Door has a sub-line/chevron and at least 48dp target. No verdict glyph in status slots.
- `OfferMark` and `OfferRow`: 16×24dp mark, 18×28 in offer rows, radius 3, 1.5dp outline, two rules and categorical upper/middle/lower fill; unknown dashed/unfilled. Row has outcome, time/meaning and one net rate, absent for unread; newest first. Keep `ui/stats/DayDetail.kt`'s existing data class `OfferRow` distinct by renaming it `OfferRowModel` when introducing the composable. No coloured dots, pills or platform-logo tiles.
- `PeriodHero` / `HeroQuietLine`: label, day display or figure row, and basis/count line with a real ledger door; 48dp minimum, grows to fit hours/in-progress count. No money before today's first trip even when a car charge accrues in period accounting. Do not show `$0` for loading/missing data.
- `FigureRow`, `PeriodBars`, `CompareBars`: 40sp figure beside 168×72dp plot at baseline; best-bar label `vie $1,166` at 13sp secondary ink, other bars rule, zero baseline retained for negatives. Comparison bars share a scale, 16dp height, 24dp row gap; plain findings with a provenance door. No fixed car-cost line across earnings charts.
- `TextTabs` and `PeriodSelector`: selected 24×2dp underline, selected text weight width reserved; date label/picker read first, then `Semana anterior` / `Semana siguiente`, or `Mes anterior` / `Mes siguiente`. Disable future movement. Keep Tu día's compact top-bar day stepper but give arrows spoken date labels and 48dp targets.
- Root/drill/task scaffolds: root header 72dp, drill/task header 64dp, gutter 20dp, rhythm 4/8/12/20/32; bottom bar 80dp, three drawn icons, 1dp top rule/3dp active brand indicator. Bottom bar only on roots and Tu día, including beneath its sheets. Tasks use close, editors use back. Insets measured from Android, never fixture status-bar coordinates.
- `SheetScaffold` / `CalculationSheet`: paper, radius 16 top, 32×4dp handle, 35% scrim, 20sp title, 24dp content bottom inset plus actual system insets. Ledger rows at least 48dp with 1dp separators, 2dp total rule. Sheet height and scroll adapt without slicing labels or hiding focused content.
- Buttons: adapt `PrimaryButton` to brand fill, white 16sp/600, 56dp, radius 10, full width; text buttons ink/600 and 48dp; `GreyButton` secondary paper, 52dp/radius 10. One primary per screen when needed, none on read-only screens. Disabled button treatment follows final CSS, never a second active primary.
- Inputs: adapt `TextField`/`OtpInput`; `LimitControl` filled 48dp field/radius 8 with fixed `$` and unit, 32sp editable number, two 48dp ring steppers; A5 `StepperField` has 56dp controls. Editor logic is S12, not hidden inside the component.
- Save snackbar: ink surface/paper text/check, 16sp/600, 4s, polite accessibility announcement, no action. Place 8dp above the opener's first footer action, or 16dp above bottom if none; move into the first gap with 8dp row clearance rather than covering any row. Retain on navigation back to the opener; never show before persistence succeeds.
- `ListRow`, `Observation`, static skeleton, ink notice, `MaskedValue`, `StateLegend`, shared confirmation dialog and the kit's drawn vector icons. Import the sprite geometry into vector drawables, not a replacement stock Material icon family. Rings are permitted for the status family and the specified steppers, not decorative icon tiles.

S1 includes a debug-only component/fixture host and screenshot-test infrastructure, not fake user data. Add Compose test dependencies in the existing version catalog/module files only as needed. Screenshot baselines come from computed fixture state; no bitmap-backed production screens.

## 4. Money, state and persistence contract

### One money definition

Keep two explicit quantities, not one overloaded `netEarningsMxn`:

```text
runningCost(offer) = (pickupKm + tripKm) × costPerKm(effectiveCostVersion)
tripContribution = offeredDriverPayment − runningCost
ratePerKm = tripContribution / totalKm
ratePerHour = tripContribution / (totalMinutes / 60), only with complete time

calendarCarCost(date) = effectiveWeeklyCarCost(date) / 7
periodResult = sum(eligible taken-estimate or item-confirmed contributions)
             + explicitly reconciled adjustments with compatible coverage
             − sum(calendarCarCost(date) for elapsed dates in the selected period)
```

Use decimal domain arithmetic from raw inputs with explicit high-precision division, then round **only at display**. Never calculate from `$2.84/km` or `$214.29/day` displayed values. Existing Double storage can be read through decimal adapters; persist raw inputs/effective versions, not rounded display strings. Do not turn this into an unrelated rewrite of every numeric field in the app.

- The car runs from its explicit effective date, on every elapsed calendar day including today and days not driven; never future days, never divided by workdays, trips, shifts or apps. Income coverage must not silently reduce the car's calendar-day count. Period accounting uses one car charge per date after combining apps. UI hiding today's figure before its first trip does not remove that date's car charge from a week/month.
- Open periods say `Llevas hoy / Llevas esta semana / Llevas en octubre · aprox.`; closed periods `Te quedaron…`. Trip/app contribution says `Te deja` in progress, `Te dejó` finished, `Te habría dejado` not taken, always appropriately qualified. “La tomaste” still means inferred/estimated; a closed trip is not proof of paid fare.
- Include accepted in-progress estimates immediately, once; expose the in-progress count. Unresolved/unread outcomes do not contribute; explicit cancellations remove only that trip. Aggregate statements never cancel individual trips. A trip spanning midnight belongs once to its dated start bucket; shift durations split by local calendar boundaries and stop at the shared as-of instant.
- Period totals are after configured running costs and car, **before taxes**. Trip/app rates exclude the car. Missing payment basis, required pickup distance, trip distance or chosen-unit time gives unavailable/grey, not zero, red or another unit. Complete km inputs with missing time still permit a km verdict. A known zero is distinct from missing input.
- No automatic commission deduction from a fare whose meaning is uncertain. Preserve the read fare and payment basis; an ambiguous basis renders `Sin confirmar` / `sin cálculo` until supported by a parser contract.
- Evaluate thresholds before display rounding: green `>= goal`; yellow `>= minimum && < goal`; red `< minimum`. Equal limits are invalid. Freeze both limits, selected unit, cost version, evaluation and presentation result when the offer is judged. Current limits never recolour old offers.
- Source and coverage travel with every figure: captured estimate, parsed statement, user-corrected import, server-confirmed statement; period/app; dated vs undated; completeness of trips/km/time; confirmation scope. A rank pairs the same source/basis/period/city/app only. `IMPORTED` alone is not proof of confirmation.
- Never sum captured and imported rows covering the same work. Preserve both source records and an explicit field-level selection/reconciliation decision. E1 stays on captured `$4,980` until a deliberate compatible replacement; E1e compares Uber payment before expenses, not `$5,062` to the all-app net.
- A statement without km can support payment and, when eligible, payment-per-trip; never running-cost net or net-per-km. A wholly contained undated week may support a month's **labelled payment scope**; it cannot invent days/bars. A boundary-crossing undated week stays at week scope, with dated month records untouched. Partial km cannot be divided into a full statement numerator.

### Fixture invariants, all executable

Port a read-only copy of `fixture.json` and its frame contract into Android test/debug resources in S1, recording the reference revision. Later slices extend adapters, not hand-enter competing numbers. Supply a fixed `AppClock`/zone and the scenario's date/time; do not seed a real installation with example money.

| Scenario | Must agree across consumers |
|---|---|
| Costs | `24.50 / 12 + 0.80 = 2.841666…`; display `$2.84`; car `1500 / 7` from 2026-09-28 |
| C1/C2/C3/C7 | `$9.49/km`, `$7.16/km`, `$2.20/km`, `−$1.05/km`; thresholds 8/6 |
| 19 Oct 14:40 | `$979 − 76.3 × cost − 1500/7 → $548`; six taken, one in progress; HOME/DAY/E8/month use the same snapshot |
| 19 Oct 21:10 | `$1,590 − 123.2 × cost − 1500/7 → $1,026`; nine taken, none in progress |
| B5/B6/B8 | `$434` after car; `$166` without DiDi; `−$100` on the one-trip alternate day; not alternate definitions of net |
| Last finished trip | 11:20, `$172 − 13.6 × cost → $133`; `$9.81/km`, `$308/h`; no car allocation |
| 12–18 Oct | `$8,400 − 675.8 × cost − $1,500 → $4,980`; displayed days total `$4,979`, explain the actual rounding residual in E1b |
| Per-app week | Uber `$5,028 / 420 − cost → $9.13/km`; DiDi `$3,372 / 255.8 − cost → $10.34/km`; difference `$1.21` computed before rounding |
| October at 14:40 | `$20,429 − 1642 × cost − 19 × 1500/7 → $11,692`; rows `$1,417 + $4,747 + $4,980 + $548`; 155 taken, one in progress; no false rounding note |
| Reconciliation | Uber captured `$5,028`, statement `$5,062`, difference `+$34`, all before costs; weekly confirmation never marks each offer paid |
| City example | Statement `$5,062 / 34 → $149/viaje`; cohort mean `$131/viaje`, interpolated rank `78 %` from matching fixture distribution; no synthetic production ranking |
| Month payment split | Uber `$12,487`, DiDi `$7,942`; all today's `$979` belongs to Uber |

The JSON has aggregate-only examples (not every trip has a duration; B6 explicitly supplies running-cost net). A test adapter may use those aggregates at their declared scope, but cannot invent itemized production history. Compare bars with signed values need a real zero origin; uncomputable values are absent, not zero-length earnings.

## 5. Ordered implementation slices

Execute in order. Each slice is one bounded run/commit with its listed testable output; do not combine persistence, geometry and a screen migration into an oversized run. Transitional routes/adapters may remain until the replacement is wired. Any unavailable external capability remains disabled in production and has a deterministic debug preview; no fixture prices, ranks or successful permissions appear as live facts.

Every slice: inspect current git status and relevant callers, preserve unrelated work, run its focused tests then `:app:assembleDebug` and the full `testDebugUnitTest verifyReadOnlyCapture` suite. Add instrumentation for semantics/navigation/Room where specified and run it on an available emulator; report when no device is available. A dependency's preview is not a license to ship an inert button. Review fixture screenshots for affected frames at 393×852 logical dp, then test smaller width, 1.3× and larger font/system insets without clipping. S20/S21 broaden the final visual/device matrix. Keep documented external gates separate from implemented Android acceptance.

### S1 — V3 theme, bundled fonts and shared components

**Files:** `ui/theme/{Color,Type,Theme}.kt`, `ui/components/{PrimaryButton,Button,TextField,OtpInput,Switch,KomparaProgressBar,Dialog}.kt`; NEW shared components listed in §3, `ui/src/main/res/{font,drawable,values/strings.xml}`, `android/licenses/`; `ui/build.gradle.kts`, `gradle/libs.versions.toml`; test/debug fixture host/resources and theme preference in `data/settings/{Settings,SettingsRepository,SettingsSerialization}.kt`.

**Acceptance:** §3 tokens and type ladder, day and night at first introduction; B1/D1 hero, E1 figure row, F1 bars, G2 controls, G4 status and H5 ledger represented in the component host. Exact `Hoy`, `Ganancias`, `Comparar`, `Guardar`, `te deja por km, sin el coche`. All six status glyphs/row marks; primary brand uses verified against final CSS. Old screens continue building through temporary adapters; no wholesale screen rewrite in this slice.

**Tests:** font metadata/tabular-advance check; theme preference serialization/default; shared component semantics, minimum targets, long values, actual-strings screenshots, light/dark. Extend `FormattersTest` for U+2212, grouping, whole-peso totals/hour rates, two-decimal km rates/limits, rounding percentages rather than current truncation, dated Spanish relative labels and no currency/unit splitting.

**Risks:** fonts may lack the required OpenType features; activate the specified fallback, never synthesize condensing. Don't double-apply font scaling or carry default Material card/tonal elevations into new components.

### S2 — Effective-dated costs and one period-money calculation

**Depends on:** S1. **Files:** `data/db/{KomparaDatabase,KomparaMigrations}.kt`, `data/db/dao/CostProfileDao.kt`, NEW cost-version/car-cost entities and DAOs, `data/settings/CostProfileRepository.kt`; `metrics/{CostProfile,CostProfileMapper}.kt`, NEW period accounting/calculation models; adapt `data/rollup/RollupCalculator.kt`, `metrics/rollup/RollupRecomputer.kt`, `ui/stats/PeriodStats.kt`, DI and exported Room schemas.

**Acceptance:** §4 formula and fixture invariants for B1/B5/B6/B8, D3/D3a/D3b, E1b/E1c/E1d/E3/E8. Establish typed typical/confirmed running costs, gas/EV/direct modes and dated fixed costs. One calculator produces values/ledger components and provenance; car never enters per-app rates. Legacy fixed-per-day fields are retained for migration evidence, not silently converted to a retrospective weekly bill. Ask for/confirm amount and start date through G5 later; unset car means `ya restamos gasolina y desgaste`. Existing confirmed running profile can migrate as a legacy version; unknown earlier history is marked, not attributed a fabricated version.

**Tests:** `RollupCalculatorTest`, `CostProfileMapperTest`, `PeriodStatsTest`, NEW `PeriodAccountingTest`, effective-date/migration tests. Cover leap/month/week boundaries, local midnight, no-trip days, current-day/no-future charge, zero/unset car, negative results, version changes, no duplicate car per app, rounding residuals and the full fixture equality matrix. Extend `data/src/androidTest/.../KomparaDatabaseMigrationTest.kt`; never destructive fallback.

**Risks:** current rollups use the current marginal cost for all history and only closed trips. Keep raw source rows intact while introducing the new calculator; S3 supplies lifecycle completeness. Recomputing must be transactional, deterministic and limited to affected dates, not silently rewriting 730 days on every edit.

### S3 — Offer lifecycle, historical limits and provenance

**Depends on:** S2. **Files:** `data/db/entity/{OfferEntity,OfferOutcome,TripEntity}.kt`, relevant DAOs/migrations/schemas; NEW evaluation/presentation/coverage records as needed; `capture/lifecycle/{TripLifecycleTracker,LifecycleSignal,OfferEventLifecycleMapper}.kt`, `ocr/OcrLifecycleClassifier.kt`, `metrics/{TripOffer,OfferMetrics,Verdict,NetProfitEngine}.kt`, `parsers/model/OfferCard.kt`, `overlay/OfferMapping.kt`, `ui/stats/DayDetail.kt`.

**Acceptance:** nullable/missing inputs survive capture/storage; accepted-estimated, in-progress, finished, unknown, not-taken and cancelled are distinct. Confirmation is separate provenance requiring itemized evidence, never inferred from finishing or an aggregate import. Persist the evaluation's limits/unit/cost version and source, plus a joinable offer/session identity for overlay presentation. “En curso” contributes once; finishing it changes tense/count, not duplicate income. No estimate of completion solely from advertised minutes: the fixture explicitly drives its end at 14:56; production follows observed lifecycle evidence. Historical `a esa hora` logic compares the named limit only. Known/complete km may be evaluated when time is unavailable; chosen-hour missing time stays unknown.

**Tests:** extend `TripLifecycleTrackerTest`, `OcrLifecycleClassifierTest`, `OfferMappingTest`, `NetProfitEngineTest`, `DayDetailBuilderTest`; migration round-trip including legacy zero rows, cancellation, process restart, host/session changes, duplicate OCR events, overlapping offers, midnight and missed acceptance. Update obsolete tests expecting missing-fare red/unit substitution to the documented new contract, preserving regression coverage.

**Risks:** current `fare ?: 0.0`, partial-leg sums and `TripEntity(0,0,0)` erase uncertainty. Legacy values cannot be reverse-engineered as exact missing-field history. Preserve their stored verdict where available but do not invent historical thresholds; suppress unsupported explanation/monetary claims with the specified unknown state. A pending/unlinked trip is not evidence of income.

### S4 — La regla states and shared rendering

**Depends on:** S1–S3. **Files:** NEW `ui/components/Regla.kt` and render model; adapt `overlay/{VerdictChipUi,VerdictChipState,OverlayStateMachine}.kt`, `metrics/{NetProfitEngine,Verdict,VerdictLevel}.kt`; overlay/UI strings and simulator preview adapter.

**Acceptance:** C1–C5/C7/C10/C12 and N8–N10 use one visual. Full 240×112/radius 14, 56dp fixed band, 30dp cue, 30sp money, 14sp unit, 17sp phrase; two 15sp limit labels/2dp rules and 22×5dp categorical marker bar. Compact 240×80, 38dp band/42dp tray, 26sp rate/24dp cue/14sp limits and 22×4dp marker; hides only visible phrase, retains spoken phrase and both limits. At 1.3× full = 312×145.6, compact = 312×104, with no truncation/shrink. Grey has no trip rate, cue or marker, dim dashed lines, missing-input words; compact grey puts headline/reason on one line. Equal minimum is yellow; negative remains signed. Entire band changes atomically. Clear old values at new offer/host, retain `distinctUntilChanged`; same-offer disappearance grace is 500ms. No pulse, count-up, countdown, sound, haptic or post-trip toast.

Exact: `Arriba de tu meta`, `Cumple tu mínimo`, `Abajo de tu mínimo`, `Sin lectura`, `falta la tarifa`, `Falta tiempo`, `Falta distancia`, `Sin confirmar`, `Leyendo oferta`, `sin cálculo`. Spoken example: `Arriba de tu meta: 9.49 pesos netos por km`, limits read afterwards. Both limits use cents when either has cents; per-hour values retain `/h`.

**Tests:** `VerdictChipStateTest`, `OverlayStateMachineTest`, `NetProfitEngineTest`; new Regla measurement/semantics screenshots, equality/near-rounding-boundary, negative, long limit/hour labels, grey no peso-rate assertion, reduced-motion and atomic-state tests. Greyscale C1–C5/C7/C12 and status-family renders must preserve distinct patterns without hue. Appearance target ≤150ms after parse; 120ms fade+16dp rise, 100ms fade only for reduced motion; exit 160ms after the grace.

**Risks:** sp already scales in Compose; scale physical plate geometry once, not text twice. Move visual ownership without adding `:ui → :overlay` or `:ui → :capture`. Do not delete simulator computation until A1/G2 use the shared visual.

### S5 — Measured overlay placement and suppression

**Depends on:** S3–S4. **Files:** `overlay/{OverlayPosition,OverlayPrefs,OverlayController}.kt`; `parsers/model/OfferCard.kt`; `capture/{ScreenSnapshot,WindowSnapshotSource,OfferEvent}.kt` as needed; `ocr/{OfferFramePipeline,OfferCardSelector,ChipMask,UberOcrParser,DidiOcrParser}.kt`; NEW pure host-geometry model/adapters and presentation recorder.

**Acceptance:** C8–C12 and N8–N10. Replace the 25%-bottom exclusion/top-48px heuristic with measured bounds: top chrome/nav banner bottom, full card top, protected controls and marker outer bounds, including next manoeuvre. Insets are density-aware screen/window coordinates. Foot 16dp above card, side 12dp; try right-foot, left-foot, higher-right, higher-left. Full then compact; if only map markers prevent either, compact foot wins over a marker. A marker alone never causes suppression. Require full height+32dp or compact height+32dp; C11's 80dp band suppresses, C12 uses compact. Store/revalidate position per host and orientation, including every resize/font/layout change; restrict drag to valid geometry and exclude Back-gesture insets. Tap/long-press inert; remove live threshold sheet hooks when no callers remain.

Persist displayed/full/compact/suppressed result against the same offer, with cause/time; do not label an unread grey chip “no cupo”. Reference coordinates are **test inputs only**: C1 card top 545 → y417–529; C8 x12–252/y147.5–259.5; C9 x141–381/y243–355; C10 card top469 → y307.4–453; C12 top342 → compact y246–326. Revalidate horizontal fit after growth; external 12dp side and 16dp clearance margins remain fixed (C10 proves this), while the plate's internal geometry scales. Drag snaps to the nearer edge only when that position remains valid.

**Tests:** extend `OverlayPositioningTest`, `OverlayPrefsTest`, `OverlayStateMachineTest`, `ChipMaskTest`, OCR selector/parser tests with measured geometry fixtures; property tests for protected controls, exact 144/112dp boundaries, scale/orientation/back-inset conversion, stale bounds, drag persistence and marker fallback. Verify OCR mask follows actual final bounds so the overlay does not corrupt its own next read. Device replay in supported Uber/DiDi layouts before production enablement.

**Risks:** current `contentBounds` is just a fare/leg union, not card/chrome/map geometry. Build explicit adapters for validated host layouts; no hard-coded fixture coordinates in production. If the control/card geometry cannot be established, treat that host/layout as unsupported for overlay placement and retain the reading in-app; distinguish unvalidated geometry from a measured no-room event. This is a capability gate, not permission to pretend the C8–C12 physics is validated. Map exclusions need observed bounds; avoid adding a speculative general map-vision engine.

### S6 — Shared readiness, pause and incident state

**Depends on:** S3–S5. **Files:** extend `data/service/ServiceStatusProvider.kt`, NEW shared readiness/incident repository and persisted settings; `capture/{ServiceStateRepository,KomparaAccessibilityService}.kt`, parser-registry status adapters, `ocr/{SilentScreenshotLane,SilentLaneGate,OcrCaptureService}.kt`, `ui/onboarding/{ServiceWatchdog,WatchdogState}.kt`, `overlay/{OverlayController,ReaderDownBannerUi}.kt`, app DI.

**Acceptance:** state engine supports every B/G4 variant plus C6/C13. Bound permission/service alone = `Semáforo preparado`; verified host offer since bind and today = `Semáforo funcionando`, with per-app last read and midnight/rebind decay. Persist first seen outage time, positive no-room count and pause-until next local day. Pause suppresses reads/overlay/incident alerts and resumes tomorrow or explicitly. Unsupported DiDi is per-app limitation, not total outage; battery restriction is attention while reads still work. C6 passive 240×72, 5s once per host foreground, pass-through/no drag; C13 no overlay because permission is lost.

**Tests:** extend service/watchdog/silent-lane tests; NEW readiness reducer, local-date pause and incident dedupe tests, rebind/restart persistence, two-app states, zero suppression count, granted-but-not-bound, capture-stop vs permission-loss. Verify `verifyReadOnlyCapture` still enforces all modules.

**Risks:** connected/running booleans are insufficient; avoid UI-local competing status derivations. Do not claim battery settings definitely removed Accessibility permission: preserve observed cause and separately detected battery restriction. Notification delivery is S19; the state contract and passive tab must work without notification permission.

### S7 — Three-tab navigation and Hoy

**Depends on:** S1–S6. **Files:** `ui/nav/{KomparaApp,KomparaDestination,KomparaBottomBar,KomparaRoot}.kt`, `ui/screens/InicioDashboardScreen.kt`, `ui/stats/{InicioDashboardViewModel,InicioStats,AppClock}.kt`, app route wiring, UI strings.

**Acceptance:** B1–B9/B2r/B3n, N1/N2. Three roots `Hoy`, `Ganancias`, `Comparar`; gear to Ajustes, active-tab retap returns to root, no wordmark header or Premium promotion on Hoy. Today is reactive and shares one as-of snapshot with its drill-ins. `$548`/in-progress count and latest three offers, exact `Ver todas las ofertas de hoy`; B5 `$434`, B6 `$166`, B8 `−$100`. Before first trip show absence/yesterday dated, not `$0` or a negative hero. `Ayer, lunes 19 oct, te quedaron` and `Ayer, domingo 18 oct, te quedaron` derive date and figure together. Healthy/paused/prepared/attention doors go to the corresponding Semáforo state; off is a plain line with recovery primary. Initially wire unfinished drill-ins to their functioning existing route, then replace in their owning slice; no new inert controls.

**Tests:** adapt `InicioStatsTest`, `ActiveTabTest`, add Hoy state/navigation/clock tests and Compose route tests for all doors, permission recovery, no-history, process restoration, dynamic day boundary and large-font scroll. Assert no goal/streak/platform-pill weekly dashboard survives on Hoy.

**Risks:** current dashboard queries the week and freezes date bounds at construction. Preserve existing deep links using route aliases while moving root ownership; don't remove account/session behavior until S15 supplies optional auth.

### S8 — Tu día, offer details and day ledgers

**Depends on:** S7. **Files:** `ui/screens/DayDetailScreen.kt`, `ui/stats/{DayDetailViewModel,DayDetail}.kt`; NEW `ui/screens/OfferDetailSheet.kt`, `CalculationSheet.kt` and state builders; reactive offer/trip/shift DAO queries and cancellation command; nav/sheet routes.

**Acceptance:** D1/D1a/D1b/D2–D2e/D3/D3a and N3. Correct date argument; 10 offers at 14:40, 13 at 21:10, descending time with continuing list under the bar. `2 ofertas arriba de tu meta se fueron sin tomar`; no money per shift. Every row opens its own record; gray detail explains missing input. D2e `$133 sin el coche`, `$9.81/km`, `$308/h · 26 min`; D2 at 14:40 `Te deja · aprox.` and `Oferta de las 14:32 · en curso`, later `Te dejó · aprox.`. `Llevas hoy, con el coche` opens the same date/as-of ledger with `Se resta por día ($214), no por viaje`. D2b/D2d use `Te habría dejado · aprox.`; D2d retains `Sin semáforo: no cupo en la pantalla (navegación)`. Cancellation is explicit, report-reading action requires consent, and editing limits returns to this sheet without rewriting its historical judgement.

**Tests:** extend `DayDetailBuilderTest`; reactive ViewModel tests, snapshot-limit suffix tests, record identity/navigation and sheet/back tests, cancellation removes only the selected eligible trip. Golden ledger rounding and source scope tests; detail with keyboard/snackbar/large font preserves bottom inset and no cut text.

**Risks:** current day screen loads once and sorts oldest first. Do not display a mixed clock while a row or total is refreshing. Historical closed-day row uses its date/past tense, not a hard-coded “hoy”.

### S9 — Ganancias Semana and its sheets

**Depends on:** S8. **Files:** adapt `ui/screens/{WeekSummaryScreen,HistoryScreen}.kt`, `ui/stats/{WeekSummaryViewModel,HistoryViewModel,HistoryWeeks}.kt`; NEW shared selected-period state and period/day/per-app/reconciliation sheets; `CalculationSheet`, navigation, relevant DAO projections.

**Acceptance:** E1/E1b–E1f/E2/E6/E7/E7b/E8 and N4. `$4,980`, `62 viajes tomados`, best label `vie $1,166`; worded week steps, picker line, disabled future navigation. `Cuadrar con tu resumen de Uber` opens E1e; exact `Solo Uber, antes de gastos`, `Lo que leyó Kompara`, `Lo que dice tu resumen`, `Diferencia +$34`. `Por app` opens E1f, leaves Ganancias selected and retains week on close; `te dejó por km, sin el coche` said once above rates, `Lo del coche cuesta igual en las dos apps.` below. Open week `$548`, one in progress; today equals Hoy. E1c negative Sunday and E1b rounding-note conditions. E2 own total visible; free breakdown window is current calendar week plus previous three, tested as of 2 Nov for 5–11 Oct. Static skeleton and local offline values, not network-gated money.

**Tests:** `HistoryWeeksTest`, `PeriodStatsTest`, week state and selected-period persistence; all sheet entry/close routes and bar taps; rounding-note truth, zero/negative bars and source matching. E1e only appears with comparable actual source records; fixture DTOs exercise the future import data path without implying production verification.

**Risks:** `WeekSummaryViewModel.rowsForWeek` currently selects all imported rows if any exist, potentially dropping another app. Replace that shortcut with explicit per-app/field coverage selection. History cleanup waits until old routes/notification callers migrate.

### S10 — Ganancias Mes and factual IMSS

**Depends on:** S9. **Files:** NEW month presentation/state within Ganancias; adapt `ui/stats/{FiscalMonth,FiscalViewModel,ImssTracker,PeriodStats}.kt`, `ui/screens/FiscalScreen.kt`, month/week/calculation sheets and `ui/fiscal/{FiscalAggregateMapper,FiscalReportComposer,FiscalPdfRenderer}.kt` where their basis changes; `sync/fiscal/FiscalConfigRepository.kt` capability wiring.

**Acceptance:** E3/E3b/E3c/E3d/D3b/E4/E5/N5. October `$11,692` at 14:40, `Llevas en octubre`, 155 trips/one in progress; partial 1–4 Oct week `$1,417`, not a whole-week cost. E3b `$5,340` is payment, `29 viajes según tu resumen, sin km`, no inferred day bars/net. E3c excludes that payment from `$7,170` net and says `no entra en el total`. E4 shows per-app payments `$12,487` and `$7,942`, `Consultar mi vigencia en el IMSS` and `El IMSS confirma tu vigencia. Kompara no lo representa.` E5 remains explicit debug example/disabled production feature until the validated rule/config/source gate clears; no coverage badge or tax withholding assertion based on fixture `0.94`.

**Tests:** `FiscalMonthTest`, `FiscalUiStateTest`, `FiscalAggregateMapperTest`, month accounting and cross-boundary undated-import tests, shared selected week when switching from month to Comparar (month's last closed week, visibly labelled), PDF source labels/gates and month picker semantics.

**Risks:** current fiscal code already computes regulatory outputs; hide those behind the gate, don't let an existing implementation count as legal validation. Imported month projection must not fabricate dated income or a confirmed tax base.

### S11 — Comparar with source and coverage eligibility

**Depends on:** S9–S10. **Files:** `ui/screens/CompararScreen.kt`, `ui/stats/{CompararViewModel,CompareState,ComparisonBuilder,ComparisonModels,CompareUiText,PlatformSelection}.kt`; `metrics/compare/*`, `sync/aggregate/{BenchmarksRepository,PercentileRepository}.kt`, DTO/cache metadata adapters, shared selection/provenance/contribution sheets.

**Acceptance:** F1–F5/N6/N7. Tus apps uses running-cost rates, `DiDi te dejó $1.21 más por km`, each bar's `sin el coche`, `Manejaste cada app a distintas horas`, and `Ya restamos gasolina y desgaste; lo del coche cuesta igual en las dos apps.` No reader per-app hours. Tu ciudad compares matching statement payment-per-trip, `Antes de gastos`; valid fixture gets `78 %`, own `$149/viaje`, mean `$131/viaje`. Free retains own number and rank; only paid counterpart slot masked. Without a valid cohort use F4, without confirmed statement F5, never sell a lock on missing data. Context selectors work in every state; source-detail sheet explains counts, dates and basis. Single-app state does not invent DiDi data.

**Tests:** update `CompareStateTest`, `ComparisonBuilderTest`, `CompareUiTextTest`, percentile and repository tests; source/basis/week matching, synthetic cohort exclusion, cached offline eligibility, no per-app duplicated hours, null vs zero, negative net, free/paid/empty states, contribution opt-in independent of viewing.

**Risks:** existing population DTO has period/metric/synthetic flag but no full source/basis certification. F2/F3 production require that certification; render F4/F5/own data until then. Do not relabel the existing net benchmark as before-cost payment.

### S12 — Limits editor and Semáforo screen

**Depends on:** S6–S11. **Files:** `ui/screens/{ThresholdsScreen,LectorScreen}.kt`, `ui/stats/{ThresholdsViewModel,ThresholdEditor}.kt`; shared components/Regla, readiness adapter, settings repository and navigation return result; `overlay/{ThresholdSheet,simulator/*}.kt` only to retire superseded routes after use checks.

**Acceptance:** G2b/G2/G2c/G2d/G2e/G4–G4i. Exactly `Tu meta y tu mínimo`, `Tu meta · ▲ desde`, `Tu mínimo · ▼ abajo de`, `de $0.50 en $0.50`, `Toca la cifra para escribirla.`; Por hora step `$10`, no automatic unit switch. Filled fields type/select in place, accept `6.5`, `6,50`, `$6.50`; km maximum `$99.99`; preserve cents off grid and step directionally (`6.30 + → 6.50`, `− → 6.00`). Equality/crossing rejected on Save with `Tu mínimo debe ser menor que tu meta` or mirrored goal wording, not red; empty Save disabled. Preview holds last valid pair. Draft including selected unit persists only on Save; reset edits draft, save/reset slots do not jump. Keyboard back first closes IME; next back asks `¿Guardar el cambio?`. Successful Save returns to opener with changed pair and named 4s snackbar; cancellation emits none.

Semáforo limits row first in all states, per-app proofs and truthful unsupported status, `No cupo el semáforo` only >0 and linked to its day's record, `¿No ves el semáforo?` opens the relevant help answer. Pause until tomorrow is reversible; no mock live offer card or redundant OCR start button on capable silent-lane devices.

**Tests:** extend `ThresholdEditorTest`, new ViewModel saved/draft/error/IME/back tests, chosen-hour validation, snackbar placement on all four opener types, large-font/narrow limits, saved-at-hour suffix regression, Semáforo state screenshots and route tests. Retire `ThresholdSheetTest` only when its feature has no callers; retain pure simulator-engine tests used by demo.

**Risks:** existing sliders persist immediately and allow equality; neither behavior may leak through the new draft editor. Atomically save pair/unit so capture cannot observe a half-saved rule. Keep hour input limits distinct from km's `$99.99` cap; preserve valid existing hourly settings.

### S13 — Ajustes, running costs and Lo del coche

**Depends on:** S2/S7/S12. **Files:** extract `AjustesScreen` from `PlaceholderScreens.kt`; `ui/stats/AjustesViewModel.kt`, `ui/screens/CostProfileScreen.kt`, `ui/stats/{CostProfileViewModel,CostPreview}.kt`, NEW `CarCostSheet`, effective-date/input sheets, data settings/cost repositories and nav.

**Acceptance:** G1/G1b/G3/G5/A5 and B4 cost action. Account/plan first, valued limits/cost/car/status rows; first-day gear remains truthful even though that frame is undrawn. Formula `$24.50 / 12 + $0.80 → $2.84 por km`; gas/EV selection stored explicitly and restored; direct per-km input possible. `Desgaste`, `Combustible`, `Escribe tu costo por km`; km/L 4–40 or `Revisa este número`. Lo del coche only via its own row/sheet, `Cada semana`, `Se resta desde`, `Se resta de cada día: $214, también los días que no manejas. Nunca del semáforo.`, `Guardar` / `Quitar`. Save carries effective date; removing stops future charge without silently erasing history. Typical costs remain labelled until B4 `Sí, son mis costos`; efficiency retro-correction asks before reapplying old periods. Theme override is available without onboarding question.

**Tests:** extend `CostPreviewTest`, cost repository/version/migration tests; EV mode/value restoration (currently lost), validation/confirmation/effective date/removal and explicit retro-correction, settings live state after save, formula vs direct-input handling.

**Risks:** old insurance/rent values have no trustworthy effective start; surface them for explicit confirmation rather than auto-billing past days. No recomputation against current prices for all past trips.

### S14 — Privacy, profile, Avisos preferences and help

**Depends on:** S6/S13. **Files:** `ui/screens/{AccountScreen,HelpScreen}.kt`, `ui/stats/AccountViewModel.kt`; NEW `TusDatosScreen/ViewModel`, `AvisosScreen/ViewModel`; `data/settings/*`, sync auth/consent/upload-scheduler adapters, nav; strings.

**Acceptance:** G6/G6b/G7/G8/G9, plus their result/error states. `Tus viajes se calculan en este teléfono`, `Tu pantalla se lee sin guardarse`, `Sale del teléfono`, `Enviar conteos de lectura`, `Sin cifras de tus viajes`, `Aportar al promedio`. Default telemetry on/disclosed, contribution off; disabling contribution/deleting account cancels pending uploads. Delete account preserves local history; local erase is a separate confirmation/action. Append Play cancellation sentence only for actual subscription/trial. `Cuenta eliminada` or `No pudimos eliminarla. Inténtalo otra vez.` follows real result. Profile city change preserves saved limits and only offers city initial values for explicit adoption; logout retains local reader/history. Help has five questions and actual support route. G7 preferences mirror OS block with `Tu teléfono los tiene apagados` and `Activar avisos en el teléfono`; delivery is S19.

**Tests:** account/session/consent repository tests, pending work cancellation, distinct local/account erase, city change never silently changes limits, help entry expansion, disabled-OS vs saved notification choices. External deletion URL/support destination are release configuration checks, not fabricated successes.

**Risks:** current account flow couples profile/save/auth and deletion; move ownership without duplicating destructive entry points. Missing server deletion semantics or support configuration gates the affected external action, with existing truthful error handling; do not promise unverified cloud restoration.

### S15 — Short onboarding and native permission return

**Depends on:** S4/S6/S7/S13/S14. **Files:** `ui/onboarding/{OnboardingScreens,OnboardingNavGraph,OnboardingViewModel,OnboardingStep,DisclosureDecision,RootRoute,SettingsLinks,OemSteps}.kt`, `ui/nav/KomparaRoot.kt`, `ui/auth/{SignupScreens,SignupViewModel,SignupUiState}.kt`, `app/{MainActivity,OcrConsentActivity}.kt`, relevant manifests/string resources.

**Acceptance:** A1–A4r/A6/A7 and B2r/B3/B3n. A1 → A2 → A3 → OS settings → conditional battery step → Hoy. No mandatory costs/phone/profile before local use. A1 demo uses real fixture engine, once through four states ≈1.5s each, ends green; tap stops cycle, reduced motion no autoplay. A2 exact full four-line disclosure, including `Uber y DiDi no son parte de Kompara y podrían no permitir apps como esta. Kompara nunca toca su app.` A3 tagged inert `EJEMPLO`, no fake tappable Allow; actual Settings intent capability-tested, fallback list, long system toast `Busca «Kompara» y activa el interruptor` or `Activa el interruptor`. Recheck on resume: `Permiso listo` for 800ms then prepared, missing returns A3n, skip lands B3n or B2 according to history. A4b vs A4r uses live permission evidence and returns to its actual opener. Cost confirmation later, auth optional and caller-returning, session expiry never disables reader.

**Tests:** `DisclosureStateMachineTest`, `RootRouterTest`, `FunnelCountersTest`, auth state tests, lifecycle-return/intent fallback tests, permission granted without service binding, refusal/retry/skip, OEM battery-only branch, restored session and numeric code auto-submit. Device verification of real Accessibility/MediaProjection hand-offs is required; never assert it from a mock flag.

**Risks:** user-specific OEM capability is not an SDK-only assumption. Silent screenshot capability and fallback consent must match reality. Native Android toasts follow system styling; no overlay permission is added to imitate the mock.

### S16 — Import once, correct and reconcile explicitly

**Depends on:** S2/S3/S9/S10/S15. **Files:** `ui/imports/{ImportScreen,ImportViewModel,ImportUiState,SharedImportBuffer,ShareTargetClassifier}.kt`, `sync/imports/ImportRepository.kt`, `sync/api/ImportDtos.kt` adapters, `app/ShareImportActivity.kt`, NEW durable local import-draft/provenance/reconciliation models/DAOs and migrations; accounting source selector.

**Acceptance:** H4/H5/H5b/H5c/H5d/H5e, E1e/E3b/E3c. Consent before upload on every entry, including Android Share; `Tu archivo se sube a Kompara solo para leer tus números.` Retain selected files/draft on retry; correct fields locally and preserve one parse result through review and Save. H5 `$5,340`, 29 trips, 26 h, `faltan los kilómetros`; H5b preserves original 1–4 Oct data, explicit 7-day PDF overlay, `faltan los km de 6 viajes`, no fabricated net; different apps never overwrite one another. H5c stores first parse while awaiting missing DiDi input; save only after review. `Usar el PDF` applies field-level selection transactionally and idempotently, never marks individual trips confirmed/cancelled.

**Contract decision:** current API `preview(dry_run=true)` returns metrics without an import ID; `confirm` uploads/parses again. Implement **real local draft/save now** using the single preview result, including provenance as parsed/user-corrected and retained source coverage. Do not call legacy `confirm` just to obtain verification. Server-confirmed sync/restore/ranking requires a future authenticated preview-token/commit contract that binds original parse, allowed corrections, app/period/coverage and idempotency. Until available, local saved import is not server-confirmed and cannot manufacture F2 eligibility; see §6. A local save still performs the advertised local-history operation.

**Tests:** `ImportViewModelTest`, `ImportRepositoryTest`, share-buffer/classifier tests, new reconcile tests for partial apps/fields/days, duplicate Save/retry/process restoration, user correction, unknown km vs zero, month boundary and aggregate-does-not-cancel-trip. Assert exactly one parse per unchanged selected input set; adding the missing file parses only the necessary new data when supported, otherwise clearly restarts a new input-set parse without auto-saving either draft.

**Risks:** cloud confirmation is not an Android-only UI change. Preserve existing imports and distinguish their unknown basis/coverage from verified records. Do not advertise file deletion/retention timing that the service does not implement.

### S17 — Premium, Tu plan and referrals

**Depends on:** S9/S11/S14/S15/S16. **Files:** `ui/paywall/*`, NEW `TuPlanScreen/ViewModel`; `billing/{BillingModels,Entitlement,EntitlementRepository,EntitlementStore,TierGate,TierGatekeeper,PlayBillingClientImpl}.kt`, sync subscription/config adapters, `ui/referral/*`, nav/strings.

**Acceptance:** H1/H1b/H2/H2b/H2c/H2d, E2/F3 gates, unframed referral. Own money, reader, limits, costs, recovery and factual IMSS remain free. Current week + previous three/current month breakdown window is date-based; never lock absent data. Trial is explicit server promotional grant, 14 days without card and no auto-conversion, distinct from the existing `trial-7d` Play offer. Eligibility checked before sales; auth returns without silently starting trial. Price/currency and actual billing dates from Play/server, fixture `$79` only for test scenario. Preserve cached positive entitlement on transient errors. Pending preserves previous grant; grace/banked days shown only from supported real state. Exact fixture `Empezar mi prueba`, `Google Play cobra hoy`, `Pago en proceso`, `Arreglarlo en Google Play`, `Restaurar compra`. Invite bonus from backend, collapse redemption; keep this under Ajustes.

**Tests:** extend billing entitlement/merge/gate/launch tests, `PaywallViewModelTest`, referral tests; free window dates, pre-purchase eligibility, no-account path, explicit trial start, offline cached grant, pending/grace/cancelled/expired and actual localized prices. Deterministic previews for each H state.

**Risks:** Android's current simple entitlement/status DTO does not prove availability of explicit 14-day trials or banked promotional days. Those branches are feature-flagged off until the service contract exists; do not fake days or enable the old card-required Play trial under “sin tarjeta”. Core redesign and final debug APK must remain usable without these sales branches.

### S18 — Period-correct sharing and PDF surfaces

**Depends on:** S9–S11/S17. **Files:** `ui/share/{ShareCardScreen,ShareCardViewModel,ShareCardProvider,ShareCardComposer,ShareCardData,ShareCardLayout,ShareCardRenderer,ShareCardSharer}.kt`; relevant `ui/fiscal/*` PDF adapters, nav period arguments and strings.

**Acceptance:** H3, plus week/month share entry and E4 PDF route. Renderer generates the exact previewed/exported bitmap for selected period, not always current month. `Así se verá`, `Ocultar montos y horas`, `Compartir en WhatsApp`, `Más opciones`; hide amounts/hours by default. H3 fixture uses `78 %`, 34 trips and `Semana confirmada con mi resumen`, only when supported. No streak or “mejor app”, no layout-variant toggle. Missing standing uses only truthful own-data content; no sample rank. PDF labels maintain period/source/tax-gate rules, premium export gate never obscures free factual values.

**Tests:** existing share composer/layout/renderer/sharer and fiscal PDF tests; historical period arguments, preview-byte identity, hiding all amounts and hours in bitmap/caption/accessibility, absent WhatsApp fallback, real shared URI grant and no retained private export beyond policy.

**Risks:** current provider and ViewModel default to current month and expose amounts; migration must not leak private values via caption or cached bitmap after the toggle. Fonts must be available to both Compose and Canvas/PDF renderers.

### S19 — Outage recovery and notifications

**Depends on:** S6/S7/S10/S14/S15/S18. **Files:** `ui/onboarding/ServiceWatchdog.kt`, `ocr/OcrCaptureService.kt`, `ui/share/WeekClose*`, `ui/fiscal/FiscalMonthEnd*`, NEW daily-summary/incident notifier/scheduler; settings/channel migration, app deep-link dispatch/manifest.

**Acceptance:** C6/C13/H6/H6b/B2/B2r/G7. Channels `Estado del semáforo` and `Resúmenes`; one incident notification per unresolved cause, original timestamp retained overnight. Exact `Se apagó el semáforo`, `Falta el permiso de Accesibilidad. Abre Kompara al estacionarte.` or stopped-capture cause as actually observed. Tap opens Hoy's current recovery state; an old notice after repair must not reopen a fabricated off state. C6 lasts 5s once per host foreground with pass-through touches; lost permission never shows it. `Tu resumen de hoy está listo`, `Tu semana está lista`, `{Mes}: revisa tus registros del mes`; no money anywhere, public version titles only. Summary taps open their own day/week/month; defer summaries while a ride app is foreground **or any offer/trip is known active**, including when a navigation app is in front, and suppress outage alerts while paused. Keep mandatory FGS notification only where the chosen capture lane needs it, with legitimate stop action.

**Tests:** watchdog/incident fake-clock tests, `WeekCloseDecisionTest`/scheduler tests, daily/month worker tests, notification privacy/deep-link payload tests, foreground/active-trip deferral and later delivery, pause/repair dedupe, Android notification denial and channel migration. Device check of stale next-morning notice and permission recovery.

**Risks:** don't duplicate OCR-stopped and watchdog alerts for the same event, or imply notification permission can be bypassed. Battery setting and Accessibility grant are separate actions; both must recheck state on return.

### S20 — Vocabulary, night theme and retired-surface sweep

**Depends on:** S1–S19. **Files:** UI/overlay/capture/OCR resource strings, touched screen/shared-component files, theme/icon/font cleanup, test contracts; `HomeUiText.kt` / `HomeViewModel.kt` and superseded classes only after `rg` proves zero references; `techdebt.md` entries for still-gated external/device work.

**Acceptance:** all 116 frame scenarios and specifically N1–N10. Exact frame text and latest suffix/tense rules; Spanish accents, sentence case and neutral address; no old “Conviene / Regular / No conviene”, “tu rojo”, product “Lector”, open-period “Te quedan”, slate/emerald cards, pills or old five-tab chrome. Preserve “lector” only where the spec names a data source and Android-owned wording where required. No “a esa hora” after changing only the other limit. Brand primaries stay green at night, active nav/underline `#3FB565`, invariant chip colours/keylines. Remove Inter, retired quick sheet/simulator/history destinations, dead strings/components only after all paths and preview tests migrate. `overlay/VerdictColors.kt` is named by the spec but is already absent in this checkout; do not invent a deletion.

**Tests:** full fixture-string/number/screenshot matrix; semantic colour audit (no overlay-owned palette), resource-reference checks, night forced/system transitions, changed-cents limits on every opener, normalized no-break formatting, large-font accessibility. Retain migrated regression coverage when deleting legacy tests.

**Risks:** captions contain old wording and counts; use current rendered text/explicit decisions. Don't “fix” deliberate `lo del coche`/`con el coche`/`sin el coche` distinctions into one grammatically wrong phrase. No unrequested broad code cleanup.

### S21 — Device polish, accessibility and final 0.2.0 APK

**Depends on:** S20. **Files:** only demonstrated UI/inset/semantics defects, `app/build.gradle.kts` version fields, scoped QA evidence/release notes, tests for actual fixes.

**Acceptance:** real keyboard/back/rotation/navigation, TalkBack phrase-first chip and period-before-steps order, decorative cues hidden from accessibility, all actions ≥48dp, no clipped number/sign/unit at 1×/1.3×/large fonts and narrow devices, readable 13sp font gate, dark-host grey/yellow edges, reduced motion. Physical Uber/DiDi replay validates safe geometry, drag and capture lane recovery without touching host buttons. Apply `versionName = "0.2.0"` and monotonically increment versionCode from the then-current value. Deliver `android/app/build/outputs/apk/debug/app-debug.apk`, checksum and exact build/test evidence; no Play publish or push. Report enabled/gated features honestly; passing a debug fixture does not satisfy server/device/driver gates.

**Tests:** final `:app:assembleDebug`, `testDebugUnitTest`, `verifyReadOnlyCapture`, appropriate Android lint, Room migration and Compose instrumentation on available emulator/device. Re-run only affected tests after polish, then final suite/build once. Human gates remain the real-driver task study and mounted glance test (§11), not model scores: 1s exposures, ≥95% state identification and no worse than the comparison chip, zero grey-as-red calls, and ≥8/10 correct yellow explanations after A1; cover full/compact and navigation-banner hosts. Apply the driver-test plan's complete pre-registered rule, including its neutral money probe on D2e alone: `$133`, car not deducted there, day `$548` after car. Don't claim those gates passed without participants/evidence.

**Risks:** build success is not OEM capture or driver legibility proof. Record unavailable hardware/backend validation as explicit remaining gate; don't silently ship unsafe geometry or placeholder finance as live data.

## 6. Data gaps and decisions

“Real now” means implement in the named Android slice. “Derived” means calculated from actual stored evidence, never a fixture substituted into production. “Gated” means debug/test rendering only or a truthful available state until the named capability is proven; no inert production CTA or invented success.

| Gap and current evidence | Decision / owner | Production behavior and test boundary |
|---|---|---|
| Fixed cost is unread `insurancePerDayMxn + rentPerDayMxn`, workdays default 6; no dated weekly car | **Real now**, S2/S13: effective-dated weekly car schedule, calendar divisor 7 | Existing fixed values offered for confirmation; no fabricated historic start. Unset basis omits car. Tests include no-trip day, change/removal, leap/month boundaries. |
| Running costs singleton; null profile → `CostProfile.ZERO`; EV mode/inputs lost | **Real now**, S2/S13: labelled typical defaults, explicit mode, version/confirmed-at/direct input | Keep known legitimate zero distinct from absent. Unknown invalid cost cannot become optimistic zero. Defaults are labelled estimates, never fake personal facts. |
| Offer columns non-null; tracker coalesces missing data to zero | **Real now**, S3: missing-field/payment-basis state, nullable raw inputs | Legacy ambiguous zeros remain unresolved; no historical accuracy claim. Grey state has no verdict rate. |
| No thresholds/unit/cost snapshot per offer | **Real now**, S3: immutable evaluation snapshot/version at judgement | “a esa hora” only for changed named limit. No re-evaluation using today's settings; old unsnapshotted history cannot get an invented pair. |
| Open `TripEntity.endedAt == null` exists, but rollups/queries use completed trips only | **Real now**, S3/S8: accepted live estimates + explicit lifecycle state | Include once, show `En curso`, transition from actual evidence; last finished is a separate query. Unknown state never totals. |
| No cancelled/unknown outcome or user correction | **Real now**, S3/S8: explicit cancellation/source evidence | Driver cancels chosen trip; aggregate imports do not. Preserve audit/provenance and recompute affected scope transactionally. |
| Per-app earnings/km already available, hours duplicated across app aggregate rows | **Derived**, S2/S9/S11: sum contributions/km by app, dedupe shift intervals for overall time | No captured per-app $/h; statement hours only with matching app/coverage. Open shifts clipped at as-of time, not bucket end. |
| Offer presentation not persisted; OCR card bounds are only fare/leg union | **Real now + geometry gate**, S3/S5: per-offer presentation outcome, measured host adapters | Actual no-room events drive B9/G4c/D1b/D2d; unread != suppressed. Unsupported geometry is a distinct capability limitation, not invented marker bounds. |
| No readiness proof/decay/pause/incident persistence | **Real now**, S6/S19 | Shared state feeds every screen/notice; persisted last read by app, pause local date, one incident until resolution. No permission implies no overlay. |
| Week selector is local to each VM; day/month snapshots can disagree | **Derived**, S7/S9/S10: shared period/as-of selection and reactive queries | Period survives tabs/back/process restore; month→Compare visibly uses last closed week; no future car/day totals. |
| Imports are aggregate rows with null→zero coercion; review and save parse twice | **Real local logic now**, S16; **gated server confirmation** | One preview result saved locally with explicit missing fields, source, coverage/corrections. Authenticated parse-token/commit contract needed for server-confirmed sync/restore/eligibility; no backend edits in these slices. |
| Reconciliation currently prefers imported entire weeks, can drop another app, lacks field/coverage selection | **Real now**, S2/S9/S16 | Keep original captured/statement records, explicit per-app/period/field selection. E1e is a real derived payment delta; H5b missing 6 trips' km leaves net unavailable. No double counting or weekday distribution of undated imports. |
| Week verification is currently a session-level signal; cohort DTO lacks full source/basis/coverage provenance | **Gated eligibility**, S11/S16; real own-data comparison now | F2/F3 ranks require exact week/app/basis/confirmed-source and real cohort. Future API/cache metadata must prove these, not `verified=true` or `source=IMPORTED` alone; until then F4/F5/own values. |
| Cohort may be synthetic; fixture has illustrative distribution | **Gated**, S11 | No synthetic rank in production, free or paid. Debug fixture demonstrates `78 %`; primary/release eligibility filters synthetic/mismatched samples. |
| Entitlement is Free/Premium; Play product is `trial-7d`; no explicit no-card 14-day start/banked-days contract in Android DTO | **Gated**, S17 | Implement UI/reducers/test states; enable only backed branches. Future grant API must supply eligibility, explicit start, trial expiry, banked days and authoritative grace/paid dates. Real purchases use actual Play price, never `$79` hard-coded. |
| Current fiscal calculators expose income-base/withholding assumptions; fixture factor `0.94` is explicitly pending | **Real E4 now / gated E5**, S10 | Payment-only view and official vigencia link ship. Validated jurisdiction/year/source/config required before E5 or withholding language; no legal rule inferred from sample. |
| Anonymous-first spec vs mandatory `RootRoute.AUTH` | **Real now**, S15 | Local reader/history/settings usable without account; login only for actual server features, restore and explicit trial. Session expiry does not turn off reading. |
| Notifications split between watchdog/OCR/week/month; daily summary and privacy controls incomplete | **Real now**, S6/S14/S19 | Unified incident, cause/time, pause suppression; new daily schedule; amount-free content/public version, foreground deferral and dated links. |
| Share provider selects current month, amounts default visible; export basis may drift | **Real now**, S18 | Explicit period/source snapshot to one renderer, hidden amount/hour default, consented share only. Statement-confirmed copy only with matching evidence. |
| Telemetry/contribution flags exist, but complete data-management surface is absent | **Real now**, S14; external semantics validated before enabling promises | Local erase vs account delete separate, scheduled uploads cancelled, opt-in revoked. Existing cloud endpoint must meet declared deletion/retention behavior; external request/support URLs must be configured/tested. |
| Typical fuel price, city proposal and support contacts are not reliable live facts merely because a fixture has them | **Derived/configured**, S13/S14/S17 | Use existing config/defaults labelled `referencia`/`valor inicial`; no automatic threshold change on city edit, no stub WhatsApp phone, no live fixture user number. |

## 7. Explicit deferrals and release gates

- **No code implementation, fonts, schemas, tests or version bump in this planning commit.** Only this plan is committed. Future deferrals receive dated/severity/context/exit-criterion entries in `techdebt.md` when their slice executes; this run keeps them here to honor the one-file instruction.
- No backend or legacy web implementation in S1–S21. Required shared contracts to schedule separately: authenticated single-parse import commit/verification; source/basis/week-certified cohorts; explicit promotional trials/banked days. Android previews and flags must make these dependencies testable without pretending the backend supports them. Existing endpoints may be reused only for the semantics actually verified.
- E5 regulatory-base bar, coverage claims and withholding estimates remain off pending B-038/current rule validation. E4 factual payments and official verification route are in scope. This plan records product gates; it does not offer new legal/tax advice or validate policy law from the reference's historical research.
- No automatic accept/decline, gesture dispatch, host tapping, Uber/DiDi credential collection or remote capture processing. `verifyReadOnlyCapture` must remain intact. No generic map computer-vision project or unvalidated absolute-coordinate placement fallback.
- No inDrive capture rollout; keep truthful `Aún no disponible` unless independently validated parser support exists. Do not substitute another app's rate or hours.
- No new brand/logo exploration, rewards/streak system, recommendation feed, alternate share layouts, simulator destination, overlay quick editor, five-tab navigation, or redesign of the native OS permission/keyboard/notification surfaces.
- No reconstruction of unsaved historic offer thresholds or missing trip/payment data. Preserve old records with honest provenance. No retrospective fixed car charges without an effective date/explicit confirmation.
- No fixture money, cohort, plan dates, user profile or false “working” signal in a live install. Deterministic fixture/debug hosts are test infrastructure, not production placeholders.
- Real-driver comprehension and mounted-glance validation, battery-cause/OEM deep-link validation, physical Uber/DiDi geometry checks, Play-console submissions and external service deployment are release gates with evidence requirements; they cannot be declared complete from a static screenshot or JVM test. No publication/push as part of this plan or final APK slice.

## 8. Completion checklist for each implementation run

1. Name the slice and its dependency revisions; read its current source and the referenced frames/contract. Preserve the read-only design worktree.
2. Finish the slice's real implementation or explicitly gated path, including navigation and state restoration; migrate existing owners rather than leaving duplicate routes/models.
3. Verify money with the executable fixture; screenshots with current exact strings, tenses, basis and brand; logic with focused tests; build/full unit suite/read-only guard. For migrations/device flows, include the relevant instrumented evidence or state precisely why it is pending.
4. Report changed files, frame/state coverage, tests and remaining external gates. Commit only intended slice files with a conventional message and final line `Generated with Codex CLI`. Never push.

Slice ledger: S1 theme/components → S2 costs/accounting → S3 offer history → S4 Regla rendering → S5 placement → S6 readiness → S7 Hoy/navigation → S8 Tu día → S9 Semana → S10 Mes/IMSS → S11 Comparar → S12 limits/Semáforo → S13 Ajustes/costs → S14 privacy/profile/help → S15 onboarding → S16 imports → S17 plan/referrals → S18 sharing → S19 notifications → S20 copy/night → S21 device polish/APK.
