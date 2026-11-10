# Kompara v4 — the simple launch set: progress

Branch `feat/v4-simple-launch` (integration on `wip/v4-i-integration`), version **0.3.0** (`versionCode` 30;
23–29 are left for the 0.2.x line). The plan is `docs/v4-simple-launch-plan.md`; the design is the v4 kit
(38 frames). This page says what is built, what proves it, what is not built, and how to try it.

**Status (2026-11-09):** every package merged (WP-A rules, WP-B chip, WP-C shell, WP-D Ganancias, WP-E
Comparar and IMSS, WP-F Ajustes and first run) plus the integration sweep. All JVM checks pass
(`verifyReadOnlyCapture assembleDebug testDebugUnitTest lintDebug`). **No device run yet** (TD-V4-05).

## Built, frame by frame

Baselines are reviewed PNGs in `android/ui/src/test/snapshots/v4` (overlay ones in
`android/overlay/src/test/snapshots/v4`), named `<screen>_<frame>_<day|night>_<width>_<fontScale>`. Every
screen frame (P, L, K, M, J) has day and night at 393 dp 1×, day at 1.3× and night at 320 dp 2×, and its test
asserts the frame's contract strings (`_frame-contract.md`) through `V4Contract.assertFrame`, nothing
clipped, and no retired word. `V4ContractTest` fails if any built frame lacks its baselines or native owner.

| Frames | What | Test | Baselines |
|---|---|---|---|
| S1–S5, S7, S9, S10 | the pill: K head, colour, ▲◆▼, rate, /km or /h over neto/bruto; unread; Pon Tu Precio tray | `ReglaTest` (sizes, words, accessible name, tray rules); `MeasuredVerdictChipTest` (overlay placement) | `regla_S*` (17), overlay `placement_S*` |
| S6 | the pill fits a tall offer under navigation | `overlay/.../MeasuredVerdictChipTest.kt` (native) | — |
| S8 | "Semáforo apagado" tab, unchanged from v3 C6 | `overlay/.../ReaderStoppedTabTest.kt` (native) | v3 C6 renders |
| P1–P4 | first run (P2 keeps 0.2.20's four disclosure lines) | `SettingsFramesTest` | `firstrun_P1`–`P4` |
| L1–L8 | Ganancias: Hoy, first day, off, Semana closed and under way, a past day, the offer sheet, Cómo se calculó | `GananciasScreenTest`, `GananciasStatsTest`, `GananciasViewModelTest`, `DayPresentationTest`, `WeekPresentationTest` | `ganancias_L1`–`L8` |
| K1, K2 | Comparar · Tú, and its first days | `CompararScreenTest`, `TuPresentationTest`, `CompararViewModelTest` | `comparar_K1`, `K2` |
| (no kit frame) | Tu ciudad's one state, "todavía no abre" | `CompararScreenTest` | `comparar_ciudad_*` |
| M1–M4 | IMSS: the month, over the amount, ¿Qué cambia?, incentives | `ImssScreenTest`, `ImssPresentationTest`, `ImssViewModelTest`, `:metrics` `ImssMonthTest` | `imss_M1`–`M4` |
| J1–J7 | Ajustes, Tu meta y tu mínimo (and the staged bruto switch), Tus costos, Semáforo, Tus datos, Ayuda | `SettingsFramesTest`, `LimitsScreenTest`, `CostsScreenTest`, `SemaforoStateTest`, `LectorViewModelTest` | `ajustes_J1`, `limits_J2`/`J7`, `costs_J3`, `semaforo_J4`, `datos_J5`, `ayuda_J6` |
| V1 | the two notices, "Se apagó el semáforo" and "Tu semana está lista" | `notifications/LaunchNoticesTest.kt` (native); landing: `KomparaShellTest`, `NoticeContractTest` | — |

States the kit does not draw, kept from 0.2.20 and restyled, each with one day baseline: `limits_hour`,
`costs_electric`, `costs_direct`, `semaforo_off`, `semaforo_prepared`, `semaforo_stopped`, `datos_signed-out`,
`datos_erased`, `firstrun_missing`, `firstrun_granted`, `firstrun_battery-first`, `ganancias_stopped`, and the
three tab roots in the shell (`shell_*`).

**A stopped reader.** The launch set has no pause, but below Android 11 the screen-capture service's own
"Detener" (`:ocr`) still stores one. Ganancias (L3's layout) and the Semáforo screen (J4) then say
"Detuviste el semáforo", "Enciéndelo cuando quieras; mientras tanto no leemos tus ofertas." and one button,
"Encender el semáforo", which clears it. No "mañana" anywhere (TD-V4-07).

**Notices.** "Tu semana está lista" opens `week/{monday}`: Semana alone on that week, the shell's back arrow,
no bottom bar. "Se apagó el semáforo" opens Ganancias, which shows the cause. A 0.2.x import reminder still
in the shade opens Ganancias too. The "Resúmenes" channel says "Tu resumen de la semana. Sin montos."

**Review fixes (WP-X).** What the two reviews changed, each with its test:
- Pon Tu Precio: a counter-offer is the smallest whole-peso fare the engine itself rates at the limit
  (`CounterOffers` checks through `judgedRate`, the engine's own rate; `CounterOffersTest` sweeps a grid).
- A day's offer rows show the rate in the unit and basis they were judged in, beside the frozen mark; a basis
  other than the current one follows the rate ("$12.33/km bruto"). The column still names the current choice.
- The offer sheet's "a esa hora": a limit the sentence names (yellow: both), the unit or the basis changed.
- Semana: the car is covered only while the week's running total is at or above the bill (the covering day is
  the first from which it never falls under again); no car bar when the week has no figure; a closed week
  without trips says "No tuviste viajes esa semana."
- IMSS: a block for every app with trips this month or last, or typed incentives ("$0", no pace); M4 keeps the
  month and app it was opened for (saved state); a reaching pace rounds up, a shortfall up to the hundred;
  "Entre las dos" adds the printed amounts and says "juntaste" on a closed month; "No hubo viajes en …".
- First run: a saved page 0.3.0 no longer shows (0.2.20's `SIGNIN`, kept as a retired constant so its saved
  state deserializes) restores as the first page.
- Copy: Tu cuenta's title and its city confirmation ("Tu ciudad cambia a León. Tu meta y tu mínimo siguen en
  …", limits from `ThresholdEditor.summary`), Tus datos' "Qué sale del teléfono" and erase confirmation, the
  never-on Ganancias state without a secondary button, Comparar's per-hour unit and ties, one "desde …" format
  (`Formatters.formatSince`), the reset link and the basis-switch snackbar, "de esos días" on a week's sheet,
  the day sheet's title ("Cómo se calculó · lunes 19 oct").
- Re-recorded: `comparar_K2_*` (4), `limits_hour_day_393_1.0`, and the v3 `privacy_G9_*`/`privacy_profile_*` (4,
  title "Tu cuenta" and the new city confirmation).

**The same checks on any machine (WP-Y).** PR #59's CI check (Linux x86-64, Temurin 21), the first CI run of
these tests, failed 20 that pass on this Mac (Apple silicon, JBR 25), for two measured reasons:
- *How a rendered frame is compared with its baseline.* One rule, `BaselineComparison`
  (`android/test-support/java/mx/kompara/testing/`, a test source directory shared by `:ui` and `:overlay`), in
  premultiplied bytes as `sameAs` compares: both ARGB_8888 and the same size; identical passes; otherwise any
  number of pixels may differ by 1 level of 255, at most 0.1 % of the pixels by more, and no channel of any pixel
  by more than 3. The software rasterizer is not bit-exact across CPUs. On CI's run of 4190ee7, of 220 frames with
  a baseline, 33 were identical and 187 differed, every pixel by 1 or 2 levels, none by 3 or more. One level:
  388,340 pixels in all, on antialiased edges, alpha-blended text and shapes, and whole blended surfaces (a sheet's
  scrim over the night background rounds to (12,12,11) on one CPU and (12,12,12) on the other, half of
  `ganancias_L7_night` and `imss_M3_night`; the first rule's 5 % share rejected exactly those two). Two levels: 393
  pixels in all, in 53 frames, at most 31 in one frame and at most 0.0103 % of one. Layout, text and colours were
  the same. A frame accepted with differences prints one line in the test report (pixels by one level, by more,
  the largest); an identical one prints nothing. `BaselineComparisonTest` pins the rule and one real pair
  (ReglaTest's S1 as the runner and this Mac drew it). Recording is unchanged.
- *Short month and weekday names are the app's own* (`spanishDate`, `ui/format/SpanishDate.kt`): `ene … dic`,
  `lun … dom`, what JDK 25 prints for es-MX. The locale data said "sept" on JDK 21, and Android's ICU data
  differs between releases the same way, so two phones could print the same date two ways. Full names and
  numbers stay with the platform. `SpanishDateTest` fails if main code takes a short or narrow month or weekday
  name (`MMM`, `LLL`, `E`–`EEE`, `eee`, `ccc`, the five-letter narrow forms, `TextStyle.SHORT`/`NARROW`) any
  other way.

The JDK gap itself is TD-V4-10.

## Not built, and why

- **K3, K4** (Tu ciudad open, and its count and button): founder decision 1, sharing averages by app,
  weekday and hour (privacy policy, Play data safety, counsel), then server work. `V4Contract.notBuilt`.
- **J5's second switch**, "Sumar mis promedios a mi ciudad": the same decision. `V4Contract.notBuiltStrings`
  requires it to be absent.
- **P2's third v4 line**: P2 keeps 0.2.20's disclosure word for word (Play prominent disclosure, B-038).
- **After-launch surfaces** (premium, statement import, the share card, referrals, the IMSS PDF): code and
  tests kept, no entry point. How each comes back: TD-V4-01. `V4ContractTest` fails on a launch-set
  `navigate(` to their routes, on any nav deep link, and on the manifest switches being undone.

## Commands

Every shell:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
cd android
```

Checks (CI's order; the definition of done):

```bash
./gradlew verifyReadOnlyCapture assembleDebug testDebugUnitTest lintDebug --console=plain
```

Record v4 baselines for one suite after a reviewed change, then look at each PNG next to its kit frame and
rerun the checks without the flag (`-Pv3.recordScreenshots=true` is the same for the v3 ones):

```bash
./gradlew :ui:testDebugUnitTest --tests '*GananciasScreenTest' -Pv4.recordScreenshots=true --console=plain
```

The side-by-side debug build installs as `mx.kompara.app.v4`, labelled "Kompara v4", next to 0.2.20
(`mx.kompara.app`). Without the property the build is unchanged.

```bash
./gradlew :app:assembleDebug -Pkompara.appIdSuffix=.v4
adb install app/build/outputs/apk/debug/app-debug.apk     # or: ./gradlew :app:installDebug -Pkompara.appIdSuffix=.v4
```

Both apps have their own Accessibility service; with both on, both draw a chip. Turn one off while testing
the other.

Before **any** install on the phone, pull 0.2.20's ledger. It is WAL-mode: pull all three files or recent
rows look missing.

```bash
for f in kompara.db kompara.db-wal kompara.db-shm; do adb exec-out run-as mx.kompara.app cat databases/$f > k.$f; done
```

(Then open `k.kompara.db` with sqlite3; it replays the WAL. Real ledgers and OCR frames never enter the repo.)

## Device checklist (TD-V4-05)

1. The pill on live Uber and DiDi offers: colour, rate and neto/bruto right; the K head on every variant.
2. A DiDi Pon Tu Precio card: the tray ("Desde ◆ $… ▲ $…") sits below "Rechazo permitido" and never covers it.
3. Every tap on an Uber chip reaches Uber.
4. An upgrade over 0.2.20 with a real ledger, through the Room 9 → 10 migration: history, costs and limits
   intact; old offer rows judged NET; a stored pause cleared once; a stored LIGHT/DARK theme ignored.
5. The notices: "Se apagó el semáforo" opens Ganancias with its cause; Monday's "Tu semana está lista" opens
   Semana on that week.
6. Below Android 11, if a phone is at hand: "Detener" on the capture notification, then "Encender el
   semáforo" on Ganancias, then the capture consent again from the Semáforo screen.

**Found on the S25, 2026-11-10 (WP-Z):** a fresh install sits in Samsung's default "Optimizada" battery mode,
and Samsung's freezer froze the process about 6 s after each accessibility event: the reader's 0.8 s loop
stopped for 73 s to 12 min with Uber in front, while the app reported nothing wrong and the first run skipped
its battery page. `ReaderDeviceEnvironment.batteryRestricted` only saw the "Restringida" mode. It now also
answers yes on a Samsung (`OemDetector`, moved to `:data` with `OemProfile`) when the app is not exempt from
battery optimization. So on a Samsung the first run shows its battery page, and with the reader on the
Semáforo screen says "Tu teléfono puede apagar el semáforo" with "Quitar restricción de batería"; the status
reads "Batería con restricciones · aún funciona" once an offer is read. Choosing "Sin restricciones" puts the
app on the exemption list and clears all of it on return (confirmed on the S25: freezer `stop trying to
freeze`). No notice fires for it. Every other maker keeps the restricted-only rule until it is measured, so
there the app still cannot see a pause: TD-V4-11.

**Also found there:** 0.3.0 never asked for `POST_NOTIFICATIONS` (API 33+), so a new install showed
"Notificaciones: Bloqueado" and neither notice could ever arrive; 0.2.20 asked from Hoy's "Avisarme si se
apaga", which the launch set removed. Ganancias now asks (`NotificationPermissionRequest`, from
`GananciasScreen`): the system's own dialog, once per install, the first time the tab shows after the first
run with the reader on (permission given, not off or stopped), on API 33+ and only if not granted. The
stored `notifications_asked` flag keeps it from asking again, a decline included. No screen of its own. With
no Avisos row, a decline cannot be undone inside the app: TD-V4-12.

**Found on the S25, 2026-11-10 (WP-AA):** on 2 of 5 offers, on 0.3.0 and on 0.2.20 alike, the log said
`window reveal` and every frame line said `chip=<rect>`, but the screenshots showed nothing drawn there for
the whole offer. In every such offer no `place` line followed any `show` after the reveal. That is not because
the card stood still: each frame writes new geometry, so a live window re-places on every frame (the
replay of offer 3f3c does, with `rawCardTop` fixed at 626). In those windows the composition and drawing had
stopped. Offer 35d9 shows it best: its rated pill (666 px) was never measured, so it stayed revealed at the
571 px "Leyendo" width. What the code shows is why the claim went wrong. The reveal (`settleWindow`) flips the
window, puts up the reader's mask, logs the reveal and records the presentation. At that moment the plate is
still at its entrance animation's alpha 0 (`ReglaPresence`), and only the window's next frames bring it in. So
any window that stops after the reveal keeps the claim for the whole offer with no ink. Why the window stops
on the phone is **not** established; the code alone cannot settle it.
The controller now checks its claim against the window. A reveal, a new verdict on a visible window and a
hide each set a deadline: the plate's entrance plus 400 ms. Only a pre-draw meets it, and that pre-draw must
draw the current verdict fully present (or nothing, after a hide). A window that misses it is taken down with
`chip hidden reason=UNDRAWN` and its mask; the next frame attaches a fresh one. Presentation history waits for
the drawn plate. The same check stops a window that has stopped drawing from leaving its last chip on screen:
an old verdict over a back-to-back offer, or a chip after the offer leaves. `InvisibleChipReplayTest` replays
3f3c and 35d9 with the window stopped at the reveal, its composition or its traversals, plus both stale
cases. On the next phone run, the `TEMP DIAG window undrawn` line tells which of the two stopped: presence
`false/false` means the plate never got a frame; presence `true/true` with `composed=true` means it was
composed but never drawn again. The replays check the controller's bookkeeping against the window's own
software draw, not what the compositor presents; only a phone screenshot shows a frame reached the screen.
A visible window counts as drawn when `settleWindow` verified that traversal, not when no layout is pending:
the flags-only touch update it makes afterwards requests layout, and a DiDi drag repeats it every frame
(Codex review; a 900 ms drag replay was taken down at 555 ms before this). Also in WP-AA: the notification ask is now recorded from the dialog's answer,
not before it, and a configuration change while the dialog is up does not ask twice. So it is at most
once per answered dialog: a process lost before the answer asks again.
