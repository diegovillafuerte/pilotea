# V3 shared-component renders

S1 owns component scenarios inspired by B1/D1 (day hero and offer rows), E1 (figure and period bars), F1 (comparison bars), G2 (controls and legend), G4 (six status glyphs and save notice), and H5 (import ledger). These are **component hosts**, not completed screen migrations. S4 adds the shared Regla scenarios below; live screen migrations and safe host placement belong to their later slices.

`ui/src/debug/assets/v3/` contains the unmodified fixture and frame contract from design reference `2bb61fedd5bb63c9afc987a0add7455fbc78f5f9`. `V3Fixture` calculates displayed values from those raw inputs, with a fixed Mexico City clock. Nothing seeds a production repository. The debug Activity can be opened explicitly:

```sh
adb shell am start -n mx.kompara.app/mx.kompara.ui.fixtures.V3ComponentActivity --es frame E1 --ez dark true
```

`V3ComponentTest` uses Compose semantics and Robolectric API 34 native graphics. It draws the Android content view to PNG (PixelCopy is not available in this JVM environment). The viewport is 393×852dp, plus a 320dp narrow case. Each scenario has day/night 1×, day 1.3×, and night 2× text. Android font scaling is applied by Compose once; labels scroll with the content. Native OS chrome is excluded rather than copied from the HTML illustration.

Normal `testDebugUnitTest` compares every pixel with the reviewed PNGs in `v3/`, writes current images to `ui/build/outputs/v3-screenshots/`, and checks text overflow independently (1px tolerance for native subpixel glyph measurement). Failures point to the actual image. Baselines are declared Gradle test inputs. Explicitly regenerate only after reviewing the change:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :ui:testDebugUnitTest --tests '*V3ComponentTest' -Pv3.recordScreenshots=true --console=plain
```

The S1 repair supplies a writable Robolectric home in `android/.gradle/test-home`; the ordinary command needs no temporary init script. Kotlin may log a daemon-marker sandbox error and then complete via its normal in-process fallback.

The fonts come from the Google Fonts Barlow and Barlow Semi Condensed distributions; exact download URLs, weights and SHA-256 hashes are in `android/licenses/v3-fonts.json`, alongside both OFL notices. `FontContractTest` parses OS/2 and GSUB metadata, tests native `tnum` digit advances, peso/minus and Spanish glyph coverage. The fonts use lining digits by default; `lnum` is requested alongside `tnum` and needs no substitution table. No synthetic horizontal scaling or runtime download is used. Actual mounted-device 13sp legibility is still the S21 gate (TD-044).


## Regla, the v4 pill (WP-B)

`ReglaTest` records 17 component PNGs in `v4/`, named `regla_<frame>_<day|night>_393_<fontScale>` after the v4 kit's chip frames: S1–S5, S9 and S10 on a day and a dark host at 1×, S7 at 1.3×, and S5 (the tray) at 2×. Values come from `reglaFrame`, which evaluates the kit's offers with `NetProfitEngine` and `CounterOffers`; no host-map bitmap or production coordinate is embedded. The v3 plate's `regla_*` and `gallery_*` renders were retired with its frames (contract/v4/WpB.kt).

Pure/Compose tests check the measured sizes (56 and 90 dp × font scale; widths hug the content), every drawn word, the one accessible name per chip, which counter-offers the verdict lets the tray show, long rates growing instead of truncating, atomic swaps and presence/reduced-motion behaviour.

Record only this suite after a reviewed rendering change:

```sh
./gradlew :ui:testDebugUnitTest --tests '*ReglaTest' -Pv4.recordScreenshots=true --console=plain
```

Software View PNGs do not certify Android's hardware shadow, mounted legibility, actual host separation or parse-to-photon latency. Those device/driver checks remain S5/S21 gates (TD-047). No device result is inferred from these screenshots.


## S7 — Hoy screens

`HoyScreenTest` renders the production `HoyContent` with `HoyFixture` and the actual three-tab component. The 18 `hoy_*.png` baselines cover B1–B9/B2r/B3n, N1/N2, 1.3× B1/B3, and 320dp/2× N1/N2/B8. It checks complete text layout, scroll reachability, minimum targets and action dispatch. Fixture accounting comes from `V3Fixture`; B6's aggregate-only alternate and illustrative rows are explicitly debug data, never seeded in a live installation. `HoyNavigationTest` exercises the production door mapping/root navigation and saved-state restoration without constructing server/capture services.

```sh
./gradlew :ui:testDebugUnitTest --tests '*HoyScreenTest' -Pv3.recordScreenshots=true --console=plain
```

The existing S1 component baselines stay unchanged. Physical permission return, notification prompts, host launch, TalkBack and process death still require the authorized-device gate (TD-049/S21).


## S8 — Tu día and its sheets

`DayScreenTest` renders the production list, detail and ledger contents using `DayFixture`, which reads the checked-in raw fixture and runs the shared accounting calculator. Twenty `day_*.png` baselines cover D1/D1a/D1b/N3, D2–D2e, D3/D3a, live and gray detail, 1.3× and 320dp/2×. Sheet screenshots host the actual sheet contents in a deterministic native View; interaction tests exercise `ModalBottomSheet`, its accessible dismiss action, consent, navigation and restoration. Native system Back/IME/window placement still requires authorized hardware (TD-049/S21).

```sh
./gradlew :ui:testDebugUnitTest --tests '*DayScreenTest' -Pv3.recordScreenshots=true --console=plain
```

The large-text test also scrolls a 420dp viewport to its last action, with a save snackbar in a reserved slot, and verifies the 24dp content bottom inset. Physical status bars and keyboards are not HTML replicas. All prior baseline files remain unchanged.

## S9 — Ganancias Semana

`WeekScreenTest` renders the production week presentation and sheet contents with `WeekFixture` and the native header/navigation. Eighteen `week_*.png` baselines cover E1/E1b–E1f/E2/E6/E7/E7b/E8/N4, 1.3× and 320dp/2×. It checks all sheet/bar doors, restored sheet identity, dated day navigation, future-step disabling, offline totals and the large-text scroll-to-total/bottom inset. Sheet edges follow measured bar/row boundaries; narrow viewports scroll their content. The E2 preview is dated 2 Nov and uses the truthful `Ver Premium` entry until S17 can support promotional trials.

```sh
./gradlew :ui:testDebugUnitTest --tests '*WeekScreenTest' -Pv3.recordScreenshots=true --console=plain
```

For manual debug inspection, `V3ComponentActivity` accepts `--es weekFrame E1f` (and the other S9 IDs) plus the existing `--ez dark true`; it never seeds production storage. S1 component baselines remain separate and unchanged. Actual native window/Back/TalkBack/process-death checks remain TD-049/S21.

## S10 — Ganancias Mes and factual IMSS

`MonthScreenTest` renders E3/E3b/E3c/E3d/D3b/E4/E5/N5 and the month picker from `MonthFixture` and the production screen/sheet components. Fifteen `month_*.png` baselines include 1.3× and 320dp/2×. It checks the sheet/ledger/IMSS doors, restored sheet identity, partial-week navigation, disabled future steps, factual-payment semantics and the official action's large-text scrolling/bottom inset. The week sheet follows the measured basis-line boundary so it cannot slice that sentence at 1.3×. All preceding baselines remain unchanged.

```sh
./gradlew :ui:testDebugUnitTest --tests '*MonthScreenTest' -Pv3.recordScreenshots=true --console=plain
```

The debug-only `V3ComponentActivity` accepts `--es monthFrame E3d` (or another S10 ID) and `--ez dark true`. E5's labelled pending rule can only be supplied from the debug source set; it has no production route or toggle. `FiscalPdfRendererTest` additionally writes `build/outputs/v3-screenshots/month_factual_pdf.png` from the same factual lines used by the PDF export and verifies pagination. Physical Android/browser/PDF hand-offs remain TD-049/S21.

## S11 — Comparar

`CompareScreenTest` renders the production comparison and selector/provenance/contribution contents using `CompareFixture`. Sixteen `compare_*.png` baselines cover F1–F5/N6/N7, all five day states at 1.3× and four 320dp/2× states. Tests check context/restoration, source counts, consent independent of viewing, free own values and rank, no average leakage through bar scale or accessibility, no-cohort/unconfirmed actions and large-text scroll/insets. F3 uses `Ver Premium` until S17 has a supported promotional-trial contract.

```sh
./gradlew :ui:testDebugUnitTest --tests '*CompareScreenTest' -Pv3.recordScreenshots=true --console=plain
```

The explicit debug Activity accepts `--es compareFrame F2` (or another F/N ID), with `--ez dark true` for night. Fixture certification is supplied only in the debug source set. Current server/cache metadata is insufficient for live F2/F3; own-data F4/F5 remain available. Physical TalkBack, system Back/insets and process-death checks remain TD-049/S21.


## S12 — Limits and Semáforo

`LimitsScreenTest` renders the production G2b/G2/G2c/G2d/G2e editor and G4–G4i readiness screens with `LimitsFixture`. Twenty-one `limits_*.png` baselines include the chosen-hour example, 1.3× and 320dp/2× night cases. G2d uses the real selected text field; no fake keyboard is drawn. G2e draws Android's actual dialog decor centered over the editor. The 2× Regla retains its measured geometry and can be inspected horizontally; the form and readiness rows scroll above their reserved footer.

Tests verify selection/replacement, repeating steppers, draft/save/back behavior, fixed Save/reset slots, route identity and snackbar clearance on Semáforo, first-day Hoy, Ajustes and offer detail. The same preview uses the production decimal engine. Existing component and screen baselines remain unchanged.

```sh
./gradlew :ui:testDebugUnitTest --tests '*LimitsScreenTest' -Pv3.recordScreenshots=true --console=plain
```

The debug Activity accepts `--es limitsFrame G2c` (or any G4 ID, plus `hour`) and `--ez dark true`. It seeds no production data. Physical keyboard resizing, system Back/IME consumption, TalkBack, real process death and OEM consent/settings still require authorized hardware (TD-049/S21).


## S13 — Ajustes, running costs and Lo del coche

`CostsScreenTest` renders the production settings, cost and efficiency contents with `CostsFixture`. Seventeen `costs_*.png` baselines cover G1/G1b/G1e/G1n/G3/G5/A5, electric/direct alternatives, 1.3×, and 320dp/2× night. G5's deterministic render uses the same sheet content; native modal and date-picker interaction remain device checks. Tests cover each settings door, independent formula edits, mode switching, grouped amount/cursor semantics, typed zero, scroll and reachable footers. The debug host accepts `--es costsFrame G5` (or another listed state) and `--ez dark true`; it never seeds production data.

```sh
./gradlew :ui:testDebugUnitTest --tests '*CostsScreenTest' -Pv3.recordScreenshots=true --console=plain
```

Account/plan/referral rows use supported live capabilities: no fixture trial/reward subtitle is asserted. Theme is the additional undrawn settings row. Earlier baselines remain unchanged. Physical IME/Back, date-picker, TalkBack, system insets and process death remain TD-049/S21.


## S14 — Tus datos, profile, Avisos and Ayuda

`PrivacyScreenTest` renders the production screen contents with `PrivacyFixture`. Eighteen `privacy_*.png` baselines cover G6/G6b/G7/G8/G9, allowed notices, profile, deletion result/error and unavailable support, including 1.3× and 320dp/2× night. G6b/G9 use actual Android dialog decor; G9 places the same city choices in a deterministic sheet. Tests verify separate confirmations, explanatory contribution consent, five single-open help answers, saved choices while notifications are blocked, configured URL/draft encoding and scroll reachability. Earlier baselines are unchanged.

```sh
./gradlew :ui:testDebugUnitTest --tests '*PrivacyScreenTest' -Pv3.recordScreenshots=true --console=plain
```

The debug Activity accepts `--es privacyFrame G6` (or another listed state) and `--ez dark true`. Its phone, city proposal and external-link availability never seed production. Release properties `kompara.supportWhatsApp` and `kompara.accountDeletionUrl` default empty pending destination verification (TD-054); physical notification-settings/WhatsApp/browser return, native Back/IME, TalkBack and process death remain TD-049/S21.

## S15 — Short onboarding and optional identity

`OnboardingScreenTest` renders the production A1/A2/A3/A3n/A4/A4b/A4r/A6/A7 contents, the 800 ms permission acknowledgement, required-capture explanation and auth error. Twenty-two `onboarding_*.png` baselines include 1.3× and 320dp/2× night. The demo evaluates the v3 teaching offers through `NetProfitEngine`; tests compare their raw rates with `V3Fixture`, exercise its single autoplay cycle and tap/reduced-motion behavior. Large text scrolls above the footer; the oversized Regla retains its measured geometry and can be inspected horizontally. A6's field and A7's cells are native editable fields; no keyboard or Settings replica is rendered.

```sh
./gradlew :ui:testDebugUnitTest --tests '*OnboardingScreenTest' -Pv3.recordScreenshots=true --console=plain
```

The debug Activity accepts `--es onboardingFrame A3n` (or another listed scenario), with `--ez dark true`. The A1/A6 sign-in wording deliberately omits unverified cloud restoration/backup promises (TD-055); no sample profile enters live state. `OnboardingReturnTest` checks real Android Settings values under Robolectric, native intent resolution/fallback/toast and restored pending returns; `OnboardingNavigationTest` exercises caller return. These are JVM checks. A3s remains the OS hand-off, never a product screen; real Accessibility/MediaProjection, OEM battery, IME/Back, TalkBack and process-death validation still need authorized hardware (TD-049/S21).

S16 adds `import_*`: H4/H5/H5b/H5c/H5d/H5e, honest current-service missing-image,
reading/auth/unreadable states; six reference states at 1.3× and four narrow 320dp
night/2× cases (20 PNGs). `ImportFixture` uses the shared JSON; H5c's first-image
values are explicit frame-only examples because that partial parser is not available
in production. Run `ImportScreenTest` with `-Pv3.recordScreenshots=true` only to
record these snapshots. Native IME is not painted into the test artifact; a separate
focus/keyboard-sized viewport test checks the correction editor. Debug host:
`--es importFrame H5b`.

## S17 — Premium, Tu plan and referrals

`PlanScreenTest` adds 24 `plan_*` PNGs for H1/H1b/H2/H2b/H2c/H2d, pending with no prior access, cancelled/expired/Play-unavailable/absent-result states and collapsed/expanded referrals. All six reference states run at 1.3×; five narrow 320dp/night/2× scenarios check overflow and reachable actions. `PlanFixture` derives money and dates from the shared JSON and comparison calculator. Unsupported promotions, banked days and referral terms exist only in this debug host, never in live storage. Debug Activity: `--es planFrame H2c`. Record only this suite with `--tests '*PlanScreenTest' -Pv3.recordScreenshots=true`, then run the ordinary comparison gate. Native Play, keyboard, browser and WhatsApp hand-offs remain TD-049/TD-057/S21.

## S20 — Shared contract and night matrix

`FrameContract` reads the pinned debug `_frame-contract.md` (118 states, including final G1e/G1n), checks actual screen semantics/layout text and writes evidence to `ui/build/outputs/v3-contract/`. It normalizes only word joiners, nonbreaking spaces and whitespace; native separated input prefixes/units and phrase-first Regla descriptions have explicit equivalents. The lazy day log is inspected completely, then restored before its screenshot. G9's uncertified `$7.70/km` proposal remains explicitly excluded under TD-054. `V3SurfaceContractTest` requires each frame's existing snapshot or native owner; A3s/C13/H6/H6b remain real Android hand-off/notification/state-machine tests.

Fourteen added PNGs complete N1–N10 at 1×/1.3×/2×. At 2×, Regla uses a host wide enough for its measured plate; separate placement tests verify suppression when the actual host cannot fit it. Seven updated PNGs reflect grouped H5d input and Ayuda's shared drawn chevrons. All 21 additions/updates were visually reviewed; unchanged baselines continue to compare normally. `NightThemeTest` also tests system/forced theme changes, native icon contrast and preserved selection without rebuilding the screen. Resource/vocabulary/font/palette checks prevent retired surfaces from returning.

Record only an intentionally changed screen suite with `-Pv3.recordScreenshots=true`, inspect the new images, then rerun the ordinary test gate without that flag. JVM screenshots and window assertions do not replace physical TalkBack, system insets, keyboard, typography or driver checks (TD-044/TD-049/S21).
