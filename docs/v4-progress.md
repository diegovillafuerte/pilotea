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

**WP-AB (2026-11-10): the traversal that reveals a window draws the final chip.** The 571 px first measure
is the "Leyendo oferta" plate. Reading attached the window with `VerdictChipState.reading`, and the window's
first traversal measured that plate before the verdict's Showing arrived (6 to 92 ms after Reading in the
phone's lines). The verdict (666 px) then needed later frames: a recomposition, a re-measure, a hide for the
size change, a move and a second reveal. 35d9 was revealed at 571 with the verdict already held and drew
nothing after that. A window stopped there would now show "Leyendo oferta" whole, which is not a verdict, and
the WP-AA check would take it down about 0.5 s later, because the composed plate is not the held verdict.
Two changes make the first composition the final one. A new window waits for the verdict (`if (evaluated)
attach()`); a window already up still swaps to Leyendo in place for a back-to-back offer, so an old verdict
never stays over a new one. And in the overlay's window the plate is present in its first composition
(`MutableTransitionState(visible)` in `MeasuredVerdictChip`), with no 120 ms fade and no 16 dp rise. So
`window drawn … preDraws=1` follows `window reveal` in the same traversal, and the deadline is disarmed there.
Exits, and returns during an exit, still animate; `ReglaPresence`'s own default and its other callers keep
their entrance. Verdict latency is unchanged: before, the verdict also showed only at the second reveal. A
window that stops after its reveal (3f3c, 35d9) now keeps showing the right chip. One that stops before
drawing a later change (a new verdict, a back-to-back Leyendo, a hide) is still replaced. Debug builds now
log each window's life at its detach and probe a window that seems to have stopped (`window life`,
`window stall`; how to read them in TD-V4-13). `InvisibleChipReplayTest` replays 3f3c and 35d9 stopped at
the reveal in three ways (composition, traversals, and frames no longer delivered to the process), a later
verdict and a back-to-back Leyendo on a stopped window, and the probe's lines. The JVM proves the
controller's bookkeeping and the window's own software draw. Only a phone screenshot shows that the reveal's
frame reaches the screen, and only the new lines on the phone can say why windows stop.
Two more fixes came out of that review. First, a crash: `MeasuredVerdictChip` took the display slot's
`single()`, which threw `NoSuchElementException` during measure, on the main thread, once the plate had fully
left. That happens when an Exiting fade (160 ms) ends before Hidden (160 ms after Exiting) takes the window
down; the plate's own removal asks for that measure. It also happens in a window attached while the chip is
leaving (a configuration change). A throw there takes the service's process down. Now an absent plate lays out
1 px and still reports its placement. The controller sees a window that is not the placed size with no plate
present (`chip hidden reason=PLATE_ABSENT`), holds it hidden and pass-through, and takes the reader's mask
down. So it claims no chip until the plate returns at the placed size or the window detaches. Second, a window
that stops before its first reveal used to sit attached and transparent for the whole offer with no line: no
deadline ran before a reveal. The attach now arms the same deadline (entrance plus 400 ms). Only the reveal's
own confirming traversal meets it, and a lapse replaces the window like an undrawn one, with `revealed=false`
in the line. A window attached while the chip is leaving arms none. Replays cover the exit that ends before
Hidden, the window attached while leaving, and a window whose traversals stop at its attach; each failed
before its fix. The draw check stays a plain message (TD-V4-13).

**Found on the S25, 2026-11-10 (WP-AC): an Uber fare printed without decimals got no chip.** An UberX
Exclusivo stayed 15 s on screen; all 17 frames were read cleanly and all 17 were rejected as
`PARSED_CONTRADICTORY` (`grade=3 repairBits=2 provenanceBits=4374 distanceEval=false moneyEval=false`). Uber
printed the fare `$109` (109.00 / 8.6 km = `$12.67/km`), and `UberCardChecksum.money` read a separatorless
amount only as a lost decimal: 1.09. The money identity failed for it, no distance assignment was admissible,
and a contradictory card publishes nothing. Uber drops `.00` on the rate too (`$11/km`, 2026-11-08), which
left that offer's rate unread and its fare unvalidated. The rule now: a separatorless amount is whole units
(`$109` is 109.00, `$11/km` is 11.00); with three or more digits it may instead be a two-decimal amount whose
separator OCR lost (`$17756` is 177.56, REPAIRED with the decimal bit), so the slot has two readings. A whole
amount with thousands separators and no decimals (`1,320`) is one whole reading. No reading of a token without
decimals is "as printed", even a slot's only one (round 2, Codex): every prefix of a whole number's digits is
another whole number, so `$109` read `$10` beside a rate read `$12.6/km` (malformed, unread) published 10.00 in
round 1. Such a reading (`Candidate.noDecimals`, modelled on `hourOnly`) is established only by the money
identity, evaluated with exactly that reading and holding; with the fare, the rate or TOTAL km unread its slot
is withheld as unread and the legs still publish. `solve` enumerates fare and rate with the three distances, so
the identity also chooses between two readings; two readings that both satisfy it are competing values
(contradictory). A validated whole amount is no repair (READ), so the phone's frame is CONSISTENT; two-decimal
amounts keep being published as printed. The legacy path (no TOTAL row) keeps its two-decimal grammar: it has no
arithmetic to choose with, so a whole-peso fare there stays unread (TD-V4-14). On the modern headline `$0` now
reads zero and is no price (it used to compete as unreadable); `$5` reads 5.00 and still competes for the
headline, though it could never be published without the identity. The sanitized phone frame reproduces the
phone's codes on the old code and now reads 109.00, 4 min / 0.8 km, 47 min / 7.8 km, CONSISTENT; through the
pipeline its first frame publishes the payment and the next frames keep it, and the truncated `$10` frame shows
no payment as a session's first frame and leaves 109.00 in place mid-session (`UberWholePesoFareTest`,
`OfferFramePipelineValidationTest`). `UberCardChecksumMoneyTest` runs the solver over 275,899 generated cards
(plausible and adversarial, fixed seed) against an independent oracle: no fare while contradictory; a fare
from a token without decimals (one or two digits, three or more, whole thousands) only with its identity
holding for exactly that reading, and none at all with the rate unread; competing readings always
contradictory. The 190-frame f8aaab8 golden and every corpus test pass unchanged: the repo corpus has no
two-digit rate, and its `$17756`/`$3730` headlines resolve as before. The old and the new reader were also
replayed over 31,611 distinct saved device frames (June to 2026-11-10, kept outside the repository): 31,577
results are identical in every field; the 17 `$109` frames now publish; the 17 `$11/km` frames publish the same
fare and legs and now have the rate read and the identity holding; no frame publishes less. Only another
whole-peso offer on the phone will show the chip appearing for it live, and the `$11/km` rate now validating
its fare.

**WP-AD (2026-11-10): a card Kompara cannot read or trust gets the grey "Sin lectura" chip.** The WP-AC offer
showed nothing for 15 s because no frame of it could be published and, with no session, nothing is drawn. WP-AC
fixed that cause, not the class: a card whose arithmetic contradicts itself or whose route is unread still drew
no chip, so the driver could not tell an unknown card format from Kompara being off. The founder decided
(2026-11-10) that such a card gets the grey unread pill once it has been on screen for a couple of seconds. The
rule (`UnreadCardNotice`, one holder for the current host, all numbers from `CardPresenceTracker`): a frame is
*unread* when a card signature is on screen, the frame parsed a card with host geometry for a host the reader may
read, published nothing, and no session is present. Three unread frames with no signature-free frame between
them, spanning 1,800 ms, start the notice: the third frame at the S25's 0.9 s cadence, the fourth at 0.85 s. A
signature frame that is not unread (no card selected, no geometry) neither counts nor breaks the run. While it
shows, every unread frame republishes it with that frame's geometry, any other signature frame is the overlay's
heartbeat (`LayoutUncertain`), a signature-free frame soft-hides it (`SIGNATURE_ABSENT`; one under our own chip
never counts as absence), and absence corroborated the way a session's is (three signature-free frames over
1,800 ms, or the frame's own clear absence) hides it (`CONFIRMED_ABSENCE`) and resets the run. 15 s after it
first showed (the parse ceiling) it is retired even with the card still there, and it stays off until absence is
corroborated. A session that reaches its own parse ceiling with the card still there retires it the same way
(decided in WP-AD: otherwise a lookalike that once started a session would get a grey chip 1.8 s after its 15 s), and
since round 5 tells the overlay with `CONFIRMED_ABSENCE` when the notice was still on screen.
A publication ends it silently: the session's `Parsed` takes the window over in place under its own key. A host
change, the end of capture and a reader that may not read reset it; the end of capture's `NOT_VISIBLE` hides it, and
since round 4 a readiness reset sends `CONFIRMED_ABSENCE`.
What it is not: `OfferEvent.Unreadable` carries a card stripped to host geometry, content and fare bounds and
instance evidence (no fare, legs, session, validation or raw values). The overlay's state machine builds the pill
itself (`VerdictChipState.unread` on the driver's preferred unit, the function `RowBackedVerdict` now also uses
for an unread payment) and never calls `evaluate`, so there is no session, ledger row or presentation row, and the
day's history never shows it. Its placement event is identity-unresolved, so taps always pass through, on DiDi
too, and placement evidence treats each of its frames as possibly another card. The frame still reaches neither
the lifecycle classifier nor the ledger bus; `failedOffer` and the fixture recorder are unchanged. Telemetry counts
it as a failed read; that collector reads only the node path's stream, which never carries it. Debug builds: the
`TEMP DIAG frame` line ends with `notice=` (0 none, 1 pending, 2 shown, 3 retired), and rate-limited lines
`offer visibility: UNREAD_NOTICE_SHOWN; frames= spanMs=` and `offer visibility: UNREAD_NOTICE_ENDED; cause=
shownMs=` (cause `PUBLISHED`, `ABSENCE`, `CLEAR_ABSENCE`, `CAP`, `MASKED`, `CAPTURE_END`, `READINESS` or
`SESSION_CEILING`). On the JVM: the
holder's rule (`UnreadCardNoticeTest`); the sanitized `$109` frame with a contradictory TOTAL row, and one with its
trip row unread, through the pipeline (`OfferFramePipelineUnreadNoticeTest`: nothing for two frames, then the
notice on every frame, no lifecycle signal, a readable frame taking over with a normal session, never with a
session present or a host the reader may not read); the state machine; the real controller and Compose window on
the S25 Priority card through the bus (`InvisibleChipReplayTest`: the pill drawn whole at the placed rectangle,
`FOOT` 577,351 to 1044,519 above a card whose top is 605, pass-through, no ledger or presentation row, then the
card's first readable frame, captured under that chip, shows its verdict in the same window); a DiDi notice
pass-through in a slot where a settled offer takes touches; and the notice refreshing both watchdog deadlines like
a parse (`OverlayControllerPlacementTest`). The saved device logs (about 90 offers) say the rule would have fired
once, on the WP-AC offer. Only the phone can show the chip appearing on a real unreadable card, and no normal
offer getting it.

**WP-AD round 2 (Codex): the notice never touches the card it announces, and the cap keeps absence evidence.**
On the tall Pon Tu Precio layout (card from y 423) ordinary placement docks the 168 px pill below DiDi's decline
reservation, across the card's top edge. A notice there masked the card, so the tracker's first-frame rule rejected
every complete frame as `PARSED_CARD_MASKED` and the verdict waited for the 15 s cap. Now `OverlayPositioning.place`
takes the notice's keep-clear rects (its card envelope and card bands, the rectangles that rule tests) and, in every
tier and in the usual order, skips a slot whose rect expanded by the keep-clear distance touches one of them. The
distance is the capture's mask margin (`ScreenReaderState.CHIP_MASK_MARGIN_PX`, now the one constant
`ChipMask.DEFAULT_MARGIN_PX` aliases, so overlay and capture cannot drift) plus the OCR-jitter allowance
(`PlacementEvidence.CEILING_JITTER_DP`, 4 dp): 20 px on the S25. Round 2b added the allowance: the card top is
OCR-derived (on DiDi from the heading) and moves a few px between frames, and a notice edge to edge with it would
blink on a 1 px jitter and could still cost the first readable frame. With no clear slot the result is
`Suppressed(CARD_KEEP_CLEAR)`: the window is taken down before any reveal, no chip rect is reported, and the
decline-reservation waiver trial is not run for a notice. Offers pass no rects, so their placement is the code path
it was; the keep-out never enters `PlacementEvidence`. On the saved S25 layouts, through the real controller: the
notice shows above the card on every Uber layout and on the shorter DiDi cards (the foot slot, or DiDi's top dock),
with 48 px or more of room to the card top (28 px beyond the 20 px distance at the least, on Pon Tu Precio and the
bid card); a replay reads that card's top 11 px higher on the next frames and the notice stays put, and the first
readable frame under it starts its session. The two tall Pon Tu Precio cards (tops 423 and 437) get no notice; their
first readable frame starts its session at once, and their verdicts dock within 20 px of the card top (379,260 to
1044,428 and 433,261 to 1044,429 in the replay harness) by the unchanged ordinary placement. Second finding: retiring at
the cap used to zero the absence count, so a card expiring as the cap fell left the holder retired and the next
unreadable card got no notice for its whole life; the absence already counted now stands.

**WP-AD round 3 (Codex): the keep-clear rule holds on the footprint the capture masks, every notice frame.**
`place()` only judged slots it was asked about again, and nothing forced that: a notice already up kept its footprint
when the card under it changed. Headless Pon Tu Precio (heading and trip row unread) docks the notice at 577,261 to
1044,429 above a card inferred at 726; if the window's composition then stops (TD-V4-13) and the tall Pon Tu Precio
card (from 423) replaces it, `show()` kept the old dock slot (ordinary validity knows no card envelope), no draw
deadline armed, and every settle re-reported the stale rect, so each readable frame was rejected as
`PARSED_CARD_MASKED` until the cap. Now `show()`, on every notice frame and before anything else, tests the footprint
the capture is masking (`ScreenReaderState.overlayChipRect`, authoritative for the next capture) and the window's
current placement (what a later settle could report) with `OverlayPositioning.nearKeepClear`, the same helper and
distance `place()` filters with. Near this frame's card, the window comes down there and then (`NOTICE_NEAR_CARD`),
clearing the chip rect, with no composition, measure or pre-draw involved; a fresh window measures against the new
card and finds a clear slot or none. A settle can only report a placement this check or `place()` has already judged
against the same frame (geometry and keep-clear rects change together, only in `show()`), so it needs no test of its
own; a configuration change detaches before it re-shows. Offers' chips never run the check.

**WP-AD round 3b: the invariant at its source.** A frame the tracker rejects as `PARSED_CARD_MASKED` while there is no
session ends the notice at once (`UnreadCardNotice.Frame.MASKED`): with no session the only mask is the notice's own,
so that rejection means it is in the reader's way, whatever the overlay knew. The pipeline emits
`NoCard(CONFIRMED_ABSENCE)`, so the overlay detaches and clears the chip rect before the next capture; the run starts
over, nothing is retired, and the end line says `cause=MASKED`. The guard only reads `decision.rejection` and only
with no session, so what the tracker decides and what a session does are unchanged; with no notice up it neither
counts toward a run nor breaks one. The readable replacement of round 3 is now ended by this guard on its first
(masked) frame, and an unread replacement by the controller's check. A card read without host geometry is never
rejected as masked (the tracker's mask lives in the card's host geometry): it publishes and ends the notice. What is
left: the capture taken before the controller or the pipeline sees a card change is lost, and the session starts on
the next eligible unmasked capture (on the MediaProjection lane, 300 ms captures with about 450 ms of OCR, a frame
already queued under the old mask is discarded too, by the lane's window check); and a card whose signature lies
under the notice's mask reads as absence and ends the notice after three frames (TD-V4-15).

**WP-AD round 4 (Codex): every end of a notice that was on screen leaves the overlay an event that removes it.** A
readiness reset (the pipeline's stale-token early return, or a host the reader may no longer read) cleared the
holder and emitted nothing, on the assumption that the overlay's readiness flow hides the window. A delayed "Borrar
datos del teléfono" (`forgetHistory()`) invalidates capture tokens only, so that flow never changed: the window and
its mask stayed, a readable replacement was rejected as masked, the holder (now empty) let it pass, and publication
could wait for the overlay's 15 s detach. Both readiness sites now also emit `NoCard(CONFIRMED_ABSENCE)` when the
reset ended a notice that was on screen, for the notice's own host: `currentHost` at the early return (the frame's
host is not resolved yet, and every host change ends the notice through `onCaptureEnd`, so a live notice can only be
the current host's), the frame's host at the other site. If the overlay's gate drops the event, its readiness flow
hides. The other ends already had their event: a publication's `Parsed`, the end of capture's `NOT_VISIBLE`, and
`CONFIRMED_ABSENCE` for absence, the cap and a masked frame. No other path resets the holder: each capture lane
owns one pipeline and its stop runs `onCaptureEnd`, capture ownership allows one lane at a time, and the per-host
session map never drops or replaces the notice, which lives beside it.

**WP-AD round 5 (Codex): the session ceiling's retirement tells the overlay too.** The one end that is not a reset:
when a session reaches its parse ceiling with the card still there, the pipeline retires the notice directly. A
notice can still be on screen then, if that session never published (Codex's sequence: three DiDi frames read
`$40.00` without trip minutes and show the notice; a complete `$300.00` frame starts a session that the outlier guard
holds, so the notice only beats; 15 s pass in OCR timeouts; the next partial frame expires the session). The
retirement cleared the notice before its cap could fire and nothing reached the overlay. Now, when the notice was on
screen, that frame also sends `NoCard(CONFIRMED_ABSENCE)` and the end line says `cause=SESSION_CEILING`; it stays
retired until absence is corroborated. In that held state the notice only beats (`LayoutUncertain`): it is not unread
while a session is present and a masked rejection needs no session, so nothing else is re-emitted; and if the guard's
window later agrees, the session publishes and takes the window over as any publication does. So every end of a
notice that was on screen now leaves the overlay an event that removes it: a publication's `Parsed`, the end of
capture's `NOT_VISIBLE`, or `CONFIRMED_ABSENCE` (absence, the cap, a masked frame, a readiness reset, a session's
parse ceiling).
