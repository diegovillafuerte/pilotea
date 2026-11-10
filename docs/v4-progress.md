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
in the shade opens Ganancias too.

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
