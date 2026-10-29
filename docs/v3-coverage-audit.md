**0.2.0 contains substantial v3 UI, but the full redesign is not available end to end.** The largest gaps are live semáforo rendering, certified comparison/import data, restoration, and Premium flows.

This was read-only: no edits, builds, test runs, commits, or pushes. I audited HEAD `91d90d0`. The reference currently contains **118 frames**, not 113. The existing 0.2.0 APK matches the recorded QA checksum; existing reports contain 1,343 passing tests. Those results do not establish live feature availability.

**1. Frame coverage**

“Implemented” means the production renderer/state projection exists for valid stored data. It does **not** certify pixel equivalence or physical-device behavior. “Partial” identifies an additional concrete rendering, navigation, or production-wiring gap.

Two shared limitations recur below:

- **L — live money:** Uber and DiDi OCR emit `UNCONFIRMED` payment basis; the money engine requires `DRIVER_PAYMENT`. Consequently, implemented money screens do not establish that current live capture can produce their pictured net amounts.
- **G — live geometry:** no production host layout is certified, and capture does not supply the required geometry. The overlay therefore detaches rather than appearing.

Locations are relative to `/Users/panama/pilotea-v3/android/`. **U/** abbreviates `ui/src/main/java/mx/kompara/ui/`; **O/** abbreviates `overlay/src/main/java/mx/kompara/overlay/`.

| Frame | Coverage | Code location | Gap / finding |
|---|---|---|---|
| A1 | Partial | `U/onboarding/OnboardingScreens.kt:72` | Demo, phrases, cues and green primary exist. `Recuperar mis datos` became `Entrar con WhatsApp`; restoration is absent. Host illustration also differs. |
| A2 | Implemented | `U/onboarding/OnboardingScreens.kt:149` | Four disclosure paragraphs, consent actions and read-only explanation match. |
| A3 | Implemented | `U/onboarding/OnboardingScreens.kt:174`; `U/onboarding/SettingsLinks.kt:13` | Instructions, labelled illustration, settings handoff and toast implemented. |
| A3s | Implemented | `U/onboarding/SettingsLinks.kt:16`; `U/onboarding/OnboardingViewModel.kt:131` | Correct native Android handoff. The kit explicitly prohibits building its prototype stand-in into the app. |
| A3n | Implemented | `U/onboarding/OnboardingScreens.kt:192` | Exact missing-permission return wording; retry and skip retained. |
| A4 | Implemented | `U/onboarding/OnboardingScreens.kt:220` | Conditional battery setup and prepared state. Step spacing differs: 24dp instead of the specified 32dp. |
| A4b | Implemented | `U/onboarding/OnboardingScreens.kt:232` | Correct missing-permission status and return behavior; same spacing difference. |
| A4r | Implemented | `U/onboarding/OnboardingScreens.kt:234` | Recovered permission check and `permiso listo`; same spacing difference. |
| A5 | Implemented | `U/screens/CostProfileScreen.kt:147` | Efficiency stepper, formula, `$2.84 por km`, sample cost and save behavior. |
| A6 | Partial | `U/auth/SignupScreens.kt:57` | Uses `Entra con tu WhatsApp` and anonymous-use explanation instead of the kit’s backup title/subtitle. Backup is unavailable. |
| A7 | Implemented | `U/auth/SignupScreens.kt:61`; `U/auth/SignupViewModel.kt:39` | Six digits, automatic submission, resend countdown and return to caller. |
| B1 | Implemented | `U/screens/InicioDashboardScreen.kt:179`; `U/stats/InicioStats.kt:97` | Correct open-day label, in-progress count, offer meanings and basis. **L applies.** |
| B2 | Implemented | `U/screens/InicioDashboardScreen.kt:128`; `U/stats/InicioStats.kt:107` | Cause-first recovery, dated yesterday amount and battery action. |
| B2r | Implemented | `U/screens/InicioDashboardScreen.kt:137`; `U/stats/InicioStats.kt:78` | Prepared state, permission confirmation and dated previous-day money. |
| B3 | Implemented | `U/screens/InicioDashboardScreen.kt:137` | First-run example, limits and `Abrir Uber para probar`. |
| B3n | Implemented | `U/screens/InicioDashboardScreen.kt:122` | Off state and `Activar el semáforo`. |
| B4 | Implemented | `U/screens/InicioDashboardScreen.kt:167` | Typical-cost question, dated yesterday result and previous reading time. |
| B5 | Implemented | `U/screens/InicioDashboardScreen.kt:117`; `U/stats/InicioStats.kt:100` | Paused state, hours, money and resume action. **L applies.** |
| B6 | Implemented | `U/screens/InicioDashboardScreen.kt:182`; `U/stats/InicioStats.kt:94` | `Llevas hoy sin contar DiDi` and support limitation. **L applies.** |
| B7 | Implemented | `U/screens/InicioDashboardScreen.kt:117`; `U/service/ReaderStatusText.kt:11` | Battery-attention status and normal earnings content. **L applies.** |
| B8 | Implemented | `U/stats/InicioStats.kt:99`; `U/screens/InicioDashboardScreen.kt:205` | Negative amount, daily-car explanation and single offer. **L applies.** |
| B9 | Implemented | `U/stats/InicioStats.kt:83` | Suppression count and retained judgement. Real measured suppression awaits **G**. |
| C1 | Partial | `U/components/Regla.kt:43`; `O/VerdictChipState.kt:24` | Correct green band, ▲ and phrase; blocked live by **L/G**. |
| C2 | Partial | `U/components/ReglaModel.kt:12`; `U/components/Regla.kt:54` | Correct yellow ◆ and `Cumple tu mínimo`; **L/G**. |
| C3 | Partial | `U/components/Regla.kt:54` | Correct red ▼ and phrase; **L/G**. |
| C7 | Partial | `U/components/Regla.kt:76` | Negative rate preserves `−`; **L/G**. |
| C4 | Partial | `U/components/ReglaModel.kt:16`; `O/VerdictChipState.kt:34` | Grey renderer is correct, but OCR rejects missing-fare cards instead of emitting this state; **G** also applies. |
| C5 | Partial | `U/components/ReglaModel.kt:16`; `U/components/Regla.kt:163` | Correct missing-time/hourly rendering. OCR requires minute-bearing legs, preventing this incomplete-card state; **G**. |
| C6 | Partial | `O/ReaderDownBannerUi.kt:22`; `O/OverlayController.kt:389` | Correct stopped tab; attachment requires fresh offer geometry. Idle-host placement is not supplied. |
| C8 | Partial | `O/OverlayPosition.kt:52`; `O/MeasuredVerdictChip.kt:28` | Tall-card placement logic exists; no certified live geometry extractor. |
| C9 | Partial | `O/OverlayPosition.kt:94` | Navigation and manoeuvre exclusions exist; live geometry absent. |
| C10 | Partial | `U/components/Regla.kt:159`; `O/MeasuredVerdictChip.kt:29` | Font-scaled measurement exists; live placement and physical clipping remain unverified. |
| C11 | Partial | `O/OverlayPosition.kt:63`; `O/PresentationRecorder.kt` | Measured suppression exists. Current live failure is unvalidated geometry, not demonstrated “no room.” |
| C12 | Partial | `U/components/Regla.kt:71`; `O/OverlayPosition.kt:56` | Compact 240×80 renderer exists; no live geometry selects it. |
| C13 | Implemented | `O/OverlayController.kt:145`; `U/onboarding/ServiceWatchdog.kt:39` | Permission loss removes overlay and posts notification. `En línea` belongs to the host app. |
| D1a | Implemented | `U/screens/DayDetailScreen.kt:131`; `U/stats/DayPresentation.kt:34` | Full current-day list, count, hours and basis. **L applies.** |
| D1b | Implemented | `U/stats/DayPresentation.kt:57` | Suppression wording preserves verdict/rate; production suppression awaits **G**. |
| D1 | Implemented | `U/screens/DayDetailScreen.kt:131` | Complete list and day hero. **L applies**; historical zero-trip defect described below. |
| D2 | Implemented | `U/screens/OfferDetailSheet.kt:85`; `U/stats/DayPresentation.kt:62` | Trip tense, same-line `sin el coche`, ledger and day-after-car link. **L applies.** |
| D2b | Implemented | `U/stats/DayDetail.kt:69`; `U/stats/DayPresentation.kt:90` | `Te habría dejado · aprox.` and untaken-offer treatment. **L applies.** |
| D2c | Implemented | `U/stats/DayPresentation.kt:68` | Exact `cumple tu mínimo de …` explanation. **L applies.** |
| D2d | Implemented | `U/stats/DayPresentation.kt:82`; `U/screens/OfferDetailSheet.kt:97` | Suppression cause and conditional trip amount. **L/G apply.** |
| D2e | Implemented | `U/stats/DayDetail.kt:65`; `U/stats/DayPresentation.kt:90` | Finished-trip amount excludes car; linked day includes it. **L applies.** |
| D3 | Implemented | `U/screens/CalculationSheet.kt:14`; `U/stats/DayPresentation.kt:137` | Separate running/car costs and period total. |
| D3a | Implemented | `U/stats/DayPresentation.kt:141` | Same calculation uses the current snapshot and in-progress contribution. |
| D3b | Implemented | `U/screens/MonthSummaryScreen.kt:136`; `U/stats/MonthPresentation.kt:73` | Month ledger and calendar-day car allocation. |

| Frame | Coverage | Code location | Gap / finding |
|---|---|---|---|
| E1 | Implemented | `U/screens/WeekSummaryScreen.kt:89`; `U/stats/WeekPresentation.kt:46` | Chart, hero, discrepancy and app breakdown. Typical-cost disclosure has an additional state gap below. |
| E1b | Implemented | `U/screens/WeekSheets.kt:19`; `U/stats/WeekPresentation.kt:61` | Daily values and conditional rounding explanation. |
| E1c | Partial | `U/screens/WeekSheets.kt:19`; `U/stats/DayPresentation.kt:39` | Sheet shows `−$214`; opening that no-trip day hides its negative hero. |
| E1d | Implemented | `U/screens/WeekSheets.kt:106` | Weekly ledger, app-payment breakdown and separate car deduction. |
| E1e | Implemented | `U/screens/WeekSheets.kt:58` | Exact source labels, difference and explicit source selection; requires comparable stored statement evidence. |
| E1f | Implemented | `U/screens/WeekSheets.kt:39` | Single basis header, app rates, different-hours qualification and car explanation. |
| E2 | Partial | `U/screens/WeekSummaryScreen.kt:94`, `:119` | `Ver Premium` replaces `Probar 14 días gratis`; locked chart loses baseline/day labels. |
| E3 | Implemented | `U/screens/MonthSummaryScreen.kt:70`; `U/stats/MonthPresentation.kt:33` | Month figure, week slices and breakdown; typical-cost disclosure gap below. |
| E3d | Implemented | `U/screens/MonthSummaryScreen.kt:138` | Month-scoped weekly rows and total. |
| E3b | Implemented | `U/screens/MonthSummaryScreen.kt:73`; `U/stats/MonthPresentation.kt:29` | Imported payment remains scoped, without fabricated net or daily bars. |
| E3c | Implemented | `U/screens/MonthSummaryScreen.kt:81`, `:91` | Net total and undated imported payment remain separate. |
| E4 | Implemented | `U/screens/FiscalScreen.kt:43` | Factual payments, official destination and PDF entry. |
| E5 | Missing | `U/screens/FiscalScreen.kt:43`; debug `ui/src/debug/java/mx/kompara/ui/fixtures/MonthFixture.kt:39` | Regulatory example exists only in debug fixtures. Intentionally deferred under the spec’s legal gate. |
| E6 | Implemented | `U/screens/WeekSheets.kt:74` | Week picker, grouping, selection and import entry. |
| E7 | Implemented | `U/screens/WeekSummaryScreen.kt:170` | Static loading skeleton preserves layout. |
| E7b | Implemented | `U/screens/WeekSummaryScreen.kt:133` | Local figures remain visible with offline explanation. |
| E8 | Partial | `U/screens/WeekSummaryScreen.kt:122`; `U/nav/KomparaApp.kt:247` | Car row opens Ajustes rather than directly opening Lo del coche; typical-cost disclosure also missing. |
| F1 | Implemented | `U/screens/CompararScreen.kt:115`; `U/stats/ComparisonBuilder.kt:22` | App-rate comparison, `sin el coche`, proportional bars and qualifications. **L applies.** |
| F2 | Partial | `U/stats/CompararViewModel.kt:58`; `U/stats/ComparisonBuilder.kt:68` | Ranked renderer exists; production supplies neither confirmed statements nor certified cohorts. |
| F3 | Partial | `U/screens/CompararScreen.kt:94`, `:142` | Same missing rank evidence; CTA is `Ver Premium`, not the trial action. |
| F4 | Implemented | `U/stats/ComparisonBuilder.kt:91`; `U/stats/CompareUiText.kt:43` | Missing-cohort explanation and own-week comparison are implemented. |
| F5 | Partial | `U/screens/CompararScreen.kt:97`; `U/stats/ComparisonBuilder.kt:106` | Unconfirmed state exists, but import cannot complete the specified transition to F2. |
| G1 | Partial | `U/screens/AjustesScreen.kt:70`; `U/nav/KomparaApp.kt:293` | Trial/referral subtitles missing; free-plan row opens Tu plan instead of H1. |
| G1b | Partial | `U/stats/AjustesViewModel.kt:30`; `U/screens/AjustesScreen.kt:77` | Off state works; inherits G1 plan/referral gaps. |
| G1e | Partial | `U/stats/AjustesViewModel.kt:67`, `:77` | First-use values work; referral subtitle missing. Account row promises saving costs/meta despite missing restoration. |
| G1n | Partial | `U/stats/AjustesViewModel.kt:42`; `U/screens/AjustesScreen.kt:41` | Permission route works; inherits G1e gaps. |
| G2b | Implemented | `U/screens/ThresholdsScreen.kt:52`; `U/components/LimitControl.kt:145` | At-rest limits, example, cue labels and $0.50 steps. |
| G2 | Implemented | `U/screens/ThresholdsScreen.kt:75`; `U/stats/ThresholdEditor.kt:102` | Goal changes recalculate the example and return after save. |
| G2c | Implemented | `U/screens/ThresholdsScreen.kt:83`; `U/stats/ThresholdsViewModel.kt:110` | Minimum editing, persistence and confirmation. |
| G2d | Partial | `U/components/LimitControl.kt:150`, `:171` | Editing behavior exists; selection highlight does not explicitly implement the kit’s 14% ink treatment. |
| G2e | Partial | `U/screens/ThresholdsScreen.kt:97` | Correct question/actions; default `AlertDialog` omits specified 4dp shape/hairline. |
| G3 | Implemented | `U/screens/CostProfileScreen.kt:54`; `U/stats/CostPreview.kt:42` | Running-cost formula and gas/electric editing; car excluded. |
| G4 | Implemented | `U/screens/LectorScreen.kt:81`; `U/stats/SemaforoState.kt:70` | Working state, app readings, limits, help and pause; zero count omitted. |
| G4b | Implemented | `U/stats/SemaforoState.kt:53`, `:66` | Battery cause, consequence, attention glyph and fix. |
| G4c | Implemented | `U/screens/LectorScreen.kt:121`; `U/stats/SemaforoState.kt:84` | Exact suppression count/cause/reassurance; live qualifying records await **G**. |
| G4d | Implemented | `U/stats/SemaforoState.kt:54`, `:77` | Paused state, retained reading and resume action. |
| G4e | Implemented | `U/stats/SemaforoState.kt:67`, `:80` | First-use prepared state and waiting apps. |
| G4f | Implemented | `U/stats/SemaforoState.kt:43`, `:79` | Next-morning prepared state retains previous reading. |
| G4g | Implemented | `U/stats/SemaforoState.kt:74` | `Por ahora no la leemos` with unavailable-app glyph. |
| G4h | Implemented | `U/stats/SemaforoState.kt:55`, `:75` | Permission cause, incident time and recovery action. |
| G4i | Implemented | `U/components/SaveSnackbar.kt:30`; `U/stats/ThresholdEditor.kt:89` | Exact saved-minimum message and updated pair. |
| G5 | Implemented | `U/screens/CarCostSheet.kt:23` | Weekly amount, dated start, calendar-day explanation and removal/save. |
| G6 | Partial | `U/screens/TusDatosScreen.kt:53`; `sync/src/main/java/mx/kompara/sync/privacy/UploadPrivacy.kt:27` | Local controls exist; contribution upload remains gated, external deletion link unconfigured. |
| G6b | Implemented | `U/screens/TusDatosScreen.kt:97`; `U/stats/TusDatosViewModel.kt:90` | Confirmation and account-deletion call preserve local history. |
| G7 | Partial | `U/screens/AvisosScreen.kt:34`; `U/stats/AvisosViewModel.kt:61` | Switches render; import-reminder preference has no production scheduler/consumer. |
| G8 | Partial | `U/screens/HelpScreen.kt:21`, `:44` | Accordion answers exist; WhatsApp primary disabled by empty support configuration. |
| G9 | Partial | `U/screens/AccountScreen.kt:57`; `U/stats/AccountViewModel.kt:51` | City editing works; production never supplies the `$7.70/km` proposed goal. |
| H1 | Partial | `U/paywall/PaywallScreen.kt:59`; `U/paywall/PremiumEligibility.kt:45`, `:63` | No production personal/cohort evidence or promotional trial; empty legal URLs also suppress sales action. |
| H2 | Partial | `U/paywall/TuPlanScreen.kt:31`; `U/paywall/TuPlanViewModel.kt:31` | Basic access/price/restore work; trial/referral composition lacks production data. |
| H2b | Partial | `U/paywall/PaywallScreen.kt:49`, `:62` | Caller omits banked days, reminder date and reminder action; legal URLs empty. |
| H2c | Partial | `U/paywall/TuPlanViewModel.kt:34` | Payment-failure state exists; subsequent banked-days phase is not supplied. |
| H2d | Implemented | `U/paywall/TuPlanViewModel.kt:38`, `:43` | Pending purchase preserves existing grant/date with waiting glyph and restore. |
| H1b | Partial | `U/auth/SignupScreens.kt:49`; `U/paywall/PaywallViewModel.kt:66` | Trial-specific phone screen exists, but unsupported trial eligibility prevents entry. |
| H3 | Partial | `U/share/ShareCardScreen.kt:31`; `U/share/ShareCardProvider.kt:29` | Preview/export/privacy exist; confirmed 78% standing card lacks production evidence. |
| H4 | Partial | `U/imports/ImportScreen.kt:84`; `U/imports/ImportViewModel.kt:61` | Consent works; single-image DiDi flow asks users to identify the platform instead of detecting it. |
| H5 | Implemented | `U/imports/ImportScreen.kt:147`; `sync/src/main/java/mx/kompara/sync/imports/ImportRepository.kt:106` | Parsed receipt, missing-km treatment, correction and local save. Server confirmation remains gated. |
| H5b | Implemented | `U/imports/ImportScreen.kt:183`; `U/imports/ImportReviewSource.kt:22` | Coverage-aware overlap ledger, withheld net and car treatment. |
| H5c | Partial | `U/imports/ImportViewModel.kt:79`; `sync/src/main/java/mx/kompara/sync/imports/ImportRepository.kt:79` | First DiDi image cannot produce the pictured parsed partial result; parsing waits for two images. |
| H5d | Partial | `U/imports/ImportScreen.kt:215`, `:228` | Corrections work, but an always-visible week/date field changes the specified frame. |
| H5e | Partial | `U/imports/ImportScreen.kt:135`, `:140` | Retry/retention work; retained row shows filename/size instead of platform/source/period. |
| H6 | Implemented | `U/notifications/NoticePoster.kt:27`; `U/notifications/SummaryDelivery.kt:130` | Correct native notifications, no amounts, private lock-screen version and delivery deferral. |
| H6b | Implemented | `U/onboarding/ServiceWatchdog.kt:44`; `U/notifications/NoticeNavigation.kt:22` | Original incident time retained; tap opens Hoy. Android renders relative time. |

| Frame | Coverage | Code location | Gap / finding |
|---|---|---|---|
| N1 | Implemented | `U/screens/InicioDashboardScreen.kt:65`; `U/theme/Color.kt:45` | B1 night palette and basis; inherits **L**. |
| N2 | Implemented | `U/screens/InicioDashboardScreen.kt:128`; `U/theme/Color.kt:45` | Recovery screen uses night tokens and green primary. |
| N3 | Implemented | `U/screens/DayDetailScreen.kt:131`; `U/theme/Color.kt:45` | Night day list, marks and fade; inherits day-data limitations. |
| N4 | Implemented | `U/screens/WeekSummaryScreen.kt:60`; `U/components/Bars.kt:35` | Night weekly figures/chart; inherits week-state gaps. |
| N5 | Implemented | `U/screens/MonthSummaryScreen.kt:46`; `U/theme/Color.kt:45` | Night month layout; inherits month-state gaps. |
| N6 | Implemented | `U/screens/CompararScreen.kt:115`; `U/theme/Color.kt:45` | Night app comparison and basis; inherits **L**. |
| N7 | Partial | `U/screens/CompararScreen.kt:49`; `U/stats/CompararViewModel.kt:58` | Night ranked renderer exists; F2 production evidence is absent. |
| N8 | Partial | `U/components/Regla.kt:98`; `O/OverlayController.kt:207` | Dark chip styling exists; **G** and system-theme-versus-host-theme mismatch. |
| N9 | Partial | `U/components/Regla.kt:54`, `:98`; `O/OverlayController.kt:207` | Correct yellow dark styling; same geometry/theme gaps. |
| N10 | Partial | `U/components/Regla.kt:94`; `O/OverlayController.kt:207` | Correct grey keyline/dashes; missing-fare emission, geometry and host-theme gaps. |

The shared contract is substantially implemented:

| Check | Result |
|---|---|
| Brand and active navigation | `#187A36` primaries/day active tab; night active tab `#3FB565`, correctly following the final Brand override. [Color.kt:7](/Users/panama/pilotea-v3/android/ui/src/main/java/mx/kompara/ui/theme/Color.kt:7), [Scaffolds.kt:83](/Users/panama/pilotea-v3/android/ui/src/main/java/mx/kompara/ui/components/Scaffolds.kt:83) |
| Verdicts | Exact green/yellow/red tokens, ▲ ◆ ▼ paths and current phrases. Grey has no rated trip amount. [Regla.kt:54](/Users/panama/pilotea-v3/android/ui/src/main/java/mx/kompara/ui/components/Regla.kt:54) |
| Typography, marks, status | Bundled Barlow/Barlow Semi Condensed, type ladder, row marks and status glyph family present. [Type.kt:11](/Users/panama/pilotea-v3/android/ui/src/main/java/mx/kompara/ui/theme/Type.kt:11) |
| Money arithmetic | Running costs deducted per trip; car deducted only from periods, once per elapsed calendar day, including non-driving days and excluding future days. [PeriodAccounting.kt:159](/Users/panama/pilotea-v3/android/data/src/main/java/mx/kompara/data/accounting/PeriodAccounting.kt:159), [calendar charges:196](/Users/panama/pilotea-v3/android/data/src/main/java/mx/kompara/data/accounting/PeriodAccounting.kt:196) |
| Money language | Open/closed period tense, trip tense and `sin el coche` implemented. Two presentation defects remain: historical zero-trip days and typical-cost week/month disclosures. |
| Chip geometry/motion | Full 240×112 and compact 240×80, font-scaled measurement, categorical marker, placement exclusions, compact fallback, 500ms disappearance grace and presence animation exist. **Live attachment remains gated.** |
| Night theme | Main app follows Automatic/Claro/Oscuro correctly. Overlay night treatment incorrectly uses system mode as evidence of the host app’s mode. |

**2. Retired wording still present**

None of these appears in production-visible strings: **“Te quedan hoy”, “Entre tu meta y tu mínimo”, “ofertas verdes”, “Ofertas sin semáforo”, “antes de lo del coche”, “neto por km”.**

The requested search for **“lector”** finds these visible occurrences. They describe data provenance, which §4 permits; they are not retired names for the semáforo.

| File:line, under U/ | Text |
|---|---|
| `screens/CompararScreen.kt:196` | `Solo cuentan tus semanas confirmadas con resumen; las estimaciones del lector no se suman` |
| `screens/TusDatosScreen.kt:91` | Same source explanation. |
| `stats/ComparisonBuilder.kt:60` | `Las horas del lector no se pueden repartir entre apps que usaste a la vez.` |
| `imports/ImportReviewSource.kt:29` | `Lector · aprox.` |
| `imports/ImportUiState.kt:23` | `Lector · aprox.` |
| `imports/ImportScreen.kt:207` | `Lector: $left` |

One non-rendered draft remnant remains in the debug asset: `android/ui/src/debug/assets/v3/fixture.json:337` contains **“2 ofertas verdes se fueron sin tomar”** inside `_note`. Technical identifiers such as `LectorScreen`, the `lector` route, and legacy-notification migration tests are not visible UI violations.

**3. Highest-impact changes to make first**

1. **Make live semáforo placement possible.** Implement certified geometry extraction and pass observations from both capture lanes; populate validated host/layout IDs. The current adapter defaults to an empty set, and the overlay detaches without geometry. Preserve rejection of unknown layouts. Also provide measured idle-host placement for C6. [HostGeometry.kt:45](/Users/panama/pilotea-v3/android/parsers/src/main/java/mx/kompara/parsers/model/HostGeometry.kt:45), [OverlayController.kt:172](/Users/panama/pilotea-v3/android/overlay/src/main/java/mx/kompara/overlay/OverlayController.kt:172).

2. **Enable valid live money and incomplete-offer states.** Certify payment meaning per supported variant, then emit `DRIVER_PAYMENT` for those variants. Recognize offers separately from successful fare/time extraction so C4/C5 receive nullable inputs rather than disappearing. Current blockers are [UberOcrParser.kt:51](/Users/panama/pilotea-v3/android/ocr/src/main/java/mx/kompara/ocr/UberOcrParser.kt:51), [DidiOcrParser.kt:34](/Users/panama/pilotea-v3/android/ocr/src/main/java/mx/kompara/ocr/DidiOcrParser.kt:34), and [NetProfitEngine.kt:31](/Users/panama/pilotea-v3/android/metrics/src/main/java/mx/kompara/metrics/NetProfitEngine.kt:31).

3. **Correct the two money-display defects.** In [DayPresentation.kt:39](/Users/panama/pilotea-v3/android/ui/src/main/java/mx/kompara/ui/stats/DayPresentation.kt:39), suppress the zero-trip amount only for today before its first trip. A past configured-car day must show its negative total and **“Sin viajes; lo del coche corre igual”**. Make [WeekPresentation.kt:56](/Users/panama/pilotea-v3/android/ui/src/main/java/mx/kompara/ui/stats/WeekPresentation.kt:56) and [MonthPresentation.kt:37](/Users/panama/pilotea-v3/android/ui/src/main/java/mx/kompara/ui/stats/MonthPresentation.kt:37) use **“con costos típicos”** when the dated cost evidence is unconfirmed.

4. **Complete certified import → comparison → share wiring.** Persist and supply confirmed statements and certified cohorts to the production comparison/share builders. Currently the comparison caller omits statements and the cohort adapter returns an empty list. This blocks F2/F3, the F5 completion path, N7 and H3. Complete retained partial DiDi parsing for H5c as well. [CompararViewModel.kt:58](/Users/panama/pilotea-v3/android/ui/src/main/java/mx/kompara/ui/stats/CompararViewModel.kt:58), [BenchmarksRepository.kt:65](/Users/panama/pilotea-v3/android/sync/src/main/java/mx/kompara/sync/aggregate/BenchmarksRepository.kt:65).

5. **Complete Premium’s actual production states.** Replace `PromotionalTrials.UNSUPPORTED` with authoritative eligibility/start behavior; supply trial/referral/banked dates and wire H2b’s reminder. Then restore **“Probar 14 días gratis”**, eligible settings subtitles, and the H1 destination. Configure verified terms/privacy URLs before enabling sales. [PremiumEligibility.kt:63](/Users/panama/pilotea-v3/android/ui/src/main/java/mx/kompara/ui/paywall/PremiumEligibility.kt:63), [PaywallScreen.kt:49](/Users/panama/pilotea-v3/android/ui/src/main/java/mx/kompara/ui/paywall/PaywallScreen.kt:49).

6. **Resolve restoration and advertised-but-inactive controls.** Implement authenticated restoration before restoring A1/A6’s backup copy; meanwhile remove the unsupported saving promise from the anonymous Ajustes row. Connect contribution consent to certified uploads and implement the G7 import-reminder scheduler. Configure verified support and external-deletion destinations. The current generated build has all four destination fields empty. [AjustesViewModel.kt:78](/Users/panama/pilotea-v3/android/ui/src/main/java/mx/kompara/ui/stats/AjustesViewModel.kt:78), [BuildConfig.java:11](/Users/panama/pilotea-v3/android/ui/build/generated/source/buildConfig/debug/mx/kompara/ui/BuildConfig.java:11).

7. **Finish navigation and presentation fidelity.** Route E8 directly to G5; retain E2’s baseline/day initials; implement G2d’s selection tint and G2e’s dialog geometry; match H5d/H5e fields/descriptors; supply validated G9 city proposals. Match offer clocks to the kit’s `9:51` style instead of `09:51`. Derive overlay dark treatment from host evidence rather than system mode.

8. **Strengthen whole-frame verification.** The generic debug router sends every C frame to a component host that defaults several scenarios—including C6/C9/C11/C13—to a green chip. Dedicated tests cover some of these separately, but that router cannot demonstrate complete frame coverage. Correct those scenes before using the fixture gallery as acceptance evidence. [V3ComponentHost.kt:42](/Users/panama/pilotea-v3/android/ui/src/debug/java/mx/kompara/ui/fixtures/V3ComponentHost.kt:42). Keep E5’s regulatory gate until its required validation exists.

**4. Verdict: No—the 0.2.0 build contains much of the v3 rendering system, but live semáforo gates and incomplete production flows prevent full redesign coverage.**