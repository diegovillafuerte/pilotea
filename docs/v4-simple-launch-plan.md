# Kompara v4 — the simple launch set: build plan

Branch `feat/v4-simple-launch`, cut from 0.2.20 (`c63bad3`, `feat/v3-redesign`). Started 2026-11-09.

The founder asked for a simpler app for launch after using 0.2.19 on the road: "simple is better for
the launch". The design is the **v4 kit**, 38 frames instead of v3's 118. This plan turns it into code.
It changes how the app looks and what it shows. It does not change how offers are captured, read or
identified.

## 1. Sources and precedence

1. The founder's stated rules (section 4 quotes them).
2. The v4 kit, which is the design: `/Users/panama/pilotea/design-system/ui_kits/android-app-v4/`
   (untracked in the main checkout, branch `docs/ux-elevation-v3`). Read `README.md`, the
   `part-*.html` files for the exact copy and structure of every frame, `kit.css` for sizes and
   tokens, `fixture.json` + `check.py` for how every figure is derived, `_frame-contract.md` for
   the strings each built frame must show. Rendered sheets, one PNG per section, at 1 dp = 1 px:
   `/Users/panama/pilotea/.session-captures/v4-build/sheets/{S,P,L,K,M,J,V}.png`.
3. This plan.
4. Where the v4 frames draw no state (errors, edge cases, loading), keep what 0.2.20 does and
   restyle it to match, unless it contradicts a rule in section 4.

Published copy of the frames: https://claude.ai/artifact/GUEfFyW72DPsJ6RCaH8PvF

## 2. Baseline and commands

Measured on the unchanged base, 2026-11-09:

- `./gradlew testDebugUnitTest` → 2,086 tests, 0 failures (metrics 156, ui 547, capture 83,
  parsers 231, app 1, ocr 455, sync 110, overlay 338, data 117, billing 48).
- `./gradlew lintDebug` → FAILS on the base with 4 errors, all in `:ui`:
  `stats/WeekSummaryViewModel.kt:88,88,94` (MissingPermission, ACCESS_NETWORK_STATE) and
  `share/ShareCardRenderer.kt:24` (WrongConstant). The branch must end with lint passing.
- `./gradlew verifyReadOnlyCapture assembleDebug` → pass.

Environment, every shell:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
cd android   # the Gradle project is in android/, not the repo root
```

A fresh worktree needs two untracked things before tests can run: `android/local.properties`
(copy from another worktree) and Robolectric's runtime cache in `android/.gradle/test-home/.m2`
(clone from another worktree with `cp -cR`; without it 89 test classes fail with HTTP 429 from
Maven Central). CI runs, in order: `verifyReadOnlyCapture`, `assembleDebug`, `testDebugUnitTest`,
`lintDebug`.

Screenshots: the project renders screens on the JVM (`V3ComponentTest`, Robolectric) and compares
them with reviewed PNGs in `ui/src/test/snapshots/v3` and `overlay/src/test/snapshots/v3`.
Record with `-Dv3.recordScreenshots=true`. New v4 baselines go in `snapshots/v4`.

No device is used in this build. The founder's S25 is running a live test of 0.2.20; do not run
`adb install`, `installDebug` or anything else that touches the phone.

## 3. The launch app

### Three tabs

| Tab | Route | Frames | Replaces |
|---|---|---|---|
| Ganancias (start) | `ganancias` | L1–L8 | Hoy (`inicio`), Tu día, Ganancias semana |
| Comparar | `comparar` | K1, K2, K4 | Comparar |
| IMSS | `imss` | M1–M4 | Ganancias mes, IMSS del mes (`fiscal`) |

A gear on each tab root opens Ajustes (J1). The bottom bar shows on the three tab roots and on a
past day (L6).

### Routes that stay

`ganancias`, `day/{day}` (L6, a past day), `comparar`, `imss`, `ajustes`, `thresholds` (J2/J7),
`cost-profile` and its editors (J3), `semaforo` (J4, was `lector`), `account`, `settings-signin`,
`your-data` (J5), `help?answer=` (J6), `reader-recovery`, `reader-battery`.

### What leaves, and how

Two kinds of removal. Do not mix them up.

**Replaced by a v4 screen: delete the screen, its ViewModel, fixtures, tests and baselines.**
Inicio dashboard (→ Ganancias Hoy), Tu día as its own screen (→ the same day layout, L1/L6),
Ganancias mes (→ IMSS), the Semáforo screen's pause control and suppressed-offer list (→ J4),
Avisos preferences, the Tema row, "Lo del coche" as an Ajustes row (→ a row in Tus costos, J3).

**After launch: unlink, keep the code and its tests.** Premium (paywall, Tu plan, subscribe),
statement import (and "Cuadrar con tu resumen"), the share card, referrals, the IMSS PDF.
- Remove their routes' entry points from every v4 screen. Their own screens, ViewModels, tests
  and v3 baselines stay and keep passing.
- Manifest: `ShareImportActivity` gets `android:enabled="false"`; the `kompara://referral`
  intent filter is removed from `MainActivity`. Leave a comment naming this plan.
- Gates: no v4 screen consults `TierGatekeeper`/`Capability`. Everything in the launch set is
  free. `:billing` is untouched.
- Record the dormant surfaces in `techdebt.md` (one entry), so nobody mistakes them for dead code.

**Upgrade safety (testers come from 0.2.x):**
- If the reader was paused when the app updates, clear the pause once at startup. There is no
  pause control any more.
- The theme follows the system. Ignore a stored LIGHT/DARK preference.
- Daily summaries, the import reminder and the month-end IMSS notice are not scheduled, and any
  already-enqueued work for them is cancelled at startup. Two notices remain (section 4.9).

## 4. Rules

### 4.1 One meaning for every peso

Every peso figure in the app is what the trips paid minus gasoline and wear: the sum, over taken
trips in the period, of fare − cost per km × km, with the cost in force when the offer was seen
(`PeriodMoney.contribution`). **No figure subtracts the car.** The words "sin el coche",
"con el coche", "te quedan", "te quedaron", "aprox." are retired; add them to the
retired-vocabulary test. A trip under way counts.

"Estimate" is said once, in the calculation sheet (L8): "Es un estimado. El pago final lo confirma
tu app de viajes."

### 4.2 Neto or bruto (founder, 2026-11-09)

> "in the chip card, can we add the word Neto or bruto depending on what they choose to show in
> the chip card? Wherever they set up their thresholds and costs they should be able to choose"

- New setting `RateBasis { NET, GROSS }`, default NET. Chosen on Tu meta y tu mínimo (J2) and on
  Tus costos (J3).
- NET rate = (fare − cost per km × km) ÷ km, or × 60 ÷ minutes per hour. GROSS rate = fare ÷ km,
  or fare × 60 ÷ minutes. km and minutes are pickup + trip, as today.
- The verdict compares the rate **in the chosen basis** with the driver's two limits, which are
  kept **in the chosen basis**. The rule that only a `DRIVER_PAYMENT` fare is judged stays for both.
- Switching the basis converts the limits:
  - per km: ± cost per km, rounded to the nearest $0.50; keep meta > mínimo (if rounding makes
    them equal, raise the meta one step).
  - per hour: each limit × (its per-km limit after ÷ its per-km limit before), rounded to the
    per-hour editor's step.
  - Example, cost $2.84: $8.00 / $6.00 neto ↔ $11.00 / $9.00 bruto.
  - J2 stages the switch until Guardar (J7 shows the staged values and "Son tus $8.00 y $6.00
    neto más tu costo de $2.84, redondeados."). J3 applies it at once and says the result under
    the selector: "Tu meta y tu mínimo pasan a $11.00 y $9.00."
- An offer row freezes the basis it was judged in, next to the frozen goal and minimum
  (new column, section 6 WP-A). Old rows are NET.
- **Every rate the app shows follows the choice and says the word** ("por km, neto",
  "$9.49 por km neto", the chip's "neto"/"bruto"). A row's rate is shown in the current choice;
  both are computable from any row. Peso totals never change with the choice.

### 4.3 The chip

One pill. Sizes in dp at font scale 1; everything scales with the phone's font scale (1.3× →
70 dp tall, `.pill.xl` in `kit.css` shows the proportions).

- 56 dp tall, corner 16, a 2 dp paper ring and the drop shadow from `kit.css` `.pill`.
- **Head:** 34 dp wide, full height, ink `#0E0F10`, Kompara's **K** in `#58C47A`
  (Barlow Semi Condensed 700, 22 sp). The K never sits on a verdict colour. Every chip variant
  has the head, including the unread one.
- **Body:** verdict colour (`#187A36` white text, `#F2B705` text `#1A1300`, `#D32F2F` white);
  a 17 × 15 dp ▲ ◆ ▼ (green, yellow, red); the rate (Barlow Semi Condensed 700, 35 sp, tabular
  figures, "−$1.05" keeps the sign); then a two-line unit: "/km" or "/h" (Barlow 600, 15 sp) over
  "neto" or "bruto" (Barlow 600, 12 sp). Gap 9, right padding 16. Width hugs the content.
- **Unread:** the whole pill is ink, Barlow 600 18 sp, `#EDEDEA`. "Sin lectura" when the fare,
  the distance or the time is missing; "Sin confirmar" and "Leyendo oferta" keep their own two
  words. No shape, no number.
- **Nothing else.** No phrase, no "tu meta / tu mínimo" lines, no rule, no tray marker.
- Accessible name, one string: "Arriba de tu meta: 9.49 pesos por kilómetro, neto" /
  "Entre tu mínimo y tu meta: …" / "Abajo de tu mínimo: …".
- "Semáforo apagado" (S8, `ReaderStoppedTab`) is unchanged.

Placement, touch and the self-capture mask are measurement-driven and stay as they are
(0.2.14–0.2.20 rules: fail-open, decline control never covered, no touch on Uber). The placer
takes two sizes, larger and smaller. Feed it the tall chip and the plain pill (4.4); for an
ordinary offer both are the plain pill.

### 4.4 Pon Tu Precio (founder, 2026-11-09)

> "specifically for Didi Pon tu Precio. Can the chip card include two additional numbers? 1. The
> counter offer they need to make to reach their minimum and 2. the counter they need to make to
> reach their target."

- Applies when the card is a DiDi bid (`OfferCard.variant == "bid"`, already set by
  `DidiOcrParser`) and the verdict is rated.
- Counter-offer for a limit L, in pesos, **rounded up to the next peso**:
  - per km: NET `ceil(L × km + running expense)`, GROSS `ceil(L × km)`
  - per hour: NET `ceil(L × minutes ÷ 60 + running expense)`, GROSS `ceil(L × minutes ÷ 60)`
  - running expense = cost per km × km = fare − net; use the row's frozen goal, minimum and basis.
- Show only the ones still ahead: below the mínimo both, between the limits only the meta's,
  at or above the meta none (plain pill).
- Drawn as a 34 dp ink tray under the pill, inside the same rounded shape (`.ptp`, `.tray`):
  "Desde" (Barlow 500, 13 sp, 72 % opacity), then ◆ in `#F2B705` + the mínimo's amount, then ▲ in
  `#58C47A` + the meta's amount (Barlow Semi Condensed 700, 19 sp, white). Shapes 11 × 10 dp.
  Example: `Desde ◆ $203 ▲ $249`, or `Desde ▲ $113`.
- The chip is then 90 dp tall. That is the placer's larger size; the plain pill is the smaller.
  If the tall chip has no slot, the plain pill shows.
- Accessible name adds: "Desde 203 pesos alcanza tu mínimo; desde 249, tu meta."
- The tray shows amounts. It never tells the driver what to bid ("Desde", not "Pide").

### 4.5 Ganancias

**Status line** (top of the tab, one line, opens J4): "Semáforo funcionando" · "Semáforo preparado ·
permiso listo" (permission granted, no offer read yet) · "Semáforo apagado · desde ayer 20:31".

**Hoy (L1).** "Hoy llevas" + the day's figure; "6 viajes · 4 h 50 min" and "Ya restamos gasolina y
desgaste" (opens L8); then "Ofertas de hoy 11" with "por km, neto" at the right, newest first.
- A row: the verdict mark (▲ ◆ ▼, or a dash when unread), the **app's name** (founder: "it should
  say which app the offers came from, Uber or Didi"), under it "14:32 · En curso" / "La tomaste" /
  "Se fue sin tomar" / "Sin lectura", and the rate at the right in the current unit and basis.
  Offers not taken are dimmed. Unread offers show no rate. For outcomes the frames do not draw
  (unknown, cancelled, still on screen) keep 0.2.20's wording.
- Tap a row → the offer sheet (L7). Its actions keep 0.2.20's behaviour.
- No offers yet today, but the ledger has history: "Hoy llevas $0", "Aún no hay viajes hoy", and
  one line "Ayer, lunes 19 oct, ganaste $1,240." that opens that day.
- **First day (L2):** shown until the first offer has ever been read.
- **Apagado (L3):** the tab's body becomes the cause, one primary button and one secondary. Map
  every existing reader-off cause to this layout with its 0.2.20 wording.

**Offer sheet (L7).** "14:32 · Uber · en curso"; "Te deja $114"; "$9.49 por km neto · arriba de tu
meta de $8.00" (yellow: "entre tu mínimo de $6.00 y tu meta de $8.00"; red: "abajo de tu mínimo de
$6.00"; frozen limits, in the row's basis); rows Tarifa, Gasolina y desgaste ("12.0 km × $2.84",
−$34) and the other unit ("Por hora, neto" $285 with "24 min"; in per-hour mode "Por km, neto").

**Cómo se calculó (L8).** Two lines and a total: the fares of the taken trips, gasoline and wear
("76.3 km × $2.84"; when the period spans more than one cost, show the km without the multiplier),
the total, and the one sentence about estimates. The same sheet serves a past day and a week.

**Semana (L4, L5).** Monday to Sunday. Title "12 – 18 oct" between two arrows; the current week
adds "· en curso" and its forward arrow is disabled. "Ganaste" (closed) or "Esta semana llevas".
"62 viajes · ya restamos gasolina y desgaste" opens the calculation sheet.
- Seven bars, heights relative to the best day. The best day is ink and carries its amount; a day
  without trips is a hairline. Tap a day → `day/{day}` (L6).
- **The car is a weekly bill** (only when a weekly car cost is set). One bar under the chart:
  - covered: "El coche, cubierto el martes" $1,500 · "Tuyo" $4,980. The hatched part is
    car ÷ earned; the rest is ink. The covering day is the first day the week's running total
    reached the bill.
  - not yet, week under way: "El coche" "$762 de $1,500" · "Te faltan" $738. The hatched part is
    earned ÷ car.
  - not covered, week closed: "El coche" "$1,200 de $1,500" · "Te faltaron" $300.
  The bill is the weekly amount in force that week, whole. Nothing is charged per day.

**A past day (L6)** is Hoy's layout with a back arrow, the date ("Lunes 19 oct") and "Ganaste".

### 4.6 Comparar

Two parts, as text tabs: **Tú** and **Tu ciudad** (founder: "part is comparing your own earnings,
the other is comparing vs the rest of the population").

**Tú (K1).** The driver's own taken trips of the last 28 days, compared with each other, in the
current unit and basis ("Tus viajes · últimas 4 semanas · por km, neto").
- A group's rate is a ratio of sums: Σ net (or Σ fare, in bruto) ÷ Σ km (or ÷ Σ hours).
- Three questions, each one sentence and one small chart:
  - "¿Uber o DiDi?" — "DiDi te deja $1.67 más por km." Two horizontal bars.
  - "¿Qué días?" — "El sábado te deja $5.05 más que el viernes." Seven bars, best in ink with its
    rate, worst labelled too.
  - "¿A qué hora?" — "De 18 a 22 h es cuando más te deja." Blocks 6–10, 10–14, 14–18, 18–22, 22–6,
    by the trip's local start time.
  - In bruto say "te paga" / "te pagan" instead of "te deja". Differences are taken from the
    printed figures.
- A group needs 3 trips to be drawn. With one app only: "Solo tienes viajes de Uber en estas
  semanas." Use the ledger's own definition of a day.
- **First days (K2):** under 30 taken trips in the window: "Aún juntamos tus viajes", "Con 30
  viajes empezamos a comparar tus apps, tus días y tus horas. Llevas 6.", a progress bar, and
  "Mientras tanto, el semáforo ya compara cada oferta con tu meta."

**Tu ciudad.** Not open at launch: the city comparison needs drivers to share averages, and that
is the founder's decision 1 (section 8). Build one honest state and nothing that sends data:
- "Tu ciudad todavía no abre"
- "Cuando haya suficientes conductores de CDMX, aquí verás en qué lugar estás entre ellos: en
  total, por app, por día y por hora." (the driver's city; "de tu ciudad" when unknown)
- K3 (the open state) and K4's count and button are **not built**.

### 4.7 IMSS (founder's rule, 2026-11-09)

> "if the driver total earnings (including trips, incentives, referral incentives, and not
> including tips) * (1-% cost deduction which right now for vehicles is 48%) * (1-IVA). If those
> earnings are higher than the minimum monthly wage of cdmx then the driver classifies for full
> coverage"

- need = minimum monthly wage CDMX ÷ ((1 − 0.48) × (1 − 0.16)). For 2026: 9,582.47 ÷ 0.4368 =
  21,937.89, shown and compared as **$21,938** (rounded up to the peso). The three numbers live in
  one place in code, with the year. Serving them from the server is a tech-debt entry.
- counted(app, month) = fares of the app's taken trips that started in the month + the incentives
  the driver typed for that app and month. Each app counts on its own. Tips are never read.
- pace(app) = trips ÷ day of month × days in month + incentives, shown rounded to the hundred,
  from the 7th day of the month on.
- The screen (M1): month between arrows ("Octubre · quedan 12 días"; past months just the name;
  "queda 1 día"; "último día"); the rule sentence with the amount and its small print; one block per
  app that has trips or incentives in the month: name, "de $21,938", the amount, a bar, the row
  "Viajes $12,487 · Incentivos $1,800" or "… · Incentivos: agregar" (opens M4), and one sentence:
  - "A este ritmo cierras en unos $22,200. **Lo alcanzas.**" / "… unos $13,000. **Te faltarían
    $9,000.**"
  - at or over the amount: "**Ya pasaste los $21,938 con Uber**, según lo que leímos y lo que
    escribiste. Confírmalo en el IMSS."
  - a past month short of it: "Te faltaron $9,651."
  - With two or more apps, none there, but the sum there: "Entre las dos llevas $22,229. En una
    sola app ya lo habrías alcanzado."
  - No trips in the month: "Aún no hay viajes en octubre."
- Rows: "¿Qué cambia si lo alcanzas?" (M3) and, once an app is over, "Ver mi vigencia en el IMSS"
  (external; reuse 0.2.20's link).
- **No screen says "cubierto" or "ya te toca".** Only the app and the IMSS can certify coverage.
- M3 and M4 are sheets; their copy is in `part-m.html`. M4 takes one whole-peso amount per app
  and month, with Guardar and Quitar.
- The percentages appear only in M3's last paragraph.

### 4.8 Ajustes

Six rows (J1), each showing its current value: Tu meta y tu mínimo · Tus costos · Semáforo ·
Tu cuenta · Tus datos · Ayuda.
- **J2 / J7** Tu meta y tu mínimo: "Decides por" Por km | Por hora; "Muestra" Neto | Bruto; one
  helper line; the example chip for a $148, 12 km, 24 min offer, live; the three-colour band
  (domain 0 → 1.5 × meta, ticks at the two limits); the two steppers; Guardar; "Volver a $8.00 y
  $6.00" (when a basis switch is staged: "Cancelar").
- **J3** Tus costos: "Manejar te cuesta $2.84 por km"; "El semáforo muestra" Neto | Bruto and its
  helper; Rendimiento, Gasolina, Desgaste, "Escribir mi costo por km" (0.2.20's editors); then
  "El coche": "Renta, crédito o seguro · $1,500 a la semana" (0.2.20's car sheet) and "Lo ves en tu
  semana. No cambia el semáforo."
- **J4** Semáforo: the state, one proof row per app ("Leyó tu última oferta · 14:32"), inDrive
  "Aún no disponible", "¿No ves el semáforo?" and "Qué lee Kompara". Off: the cause and the button
  from L3. No pause.
- **J5** Tus datos: as drawn, **without** the second switch ("Sumar mis promedios a mi ciudad").
- **J6** Ayuda: as drawn.

### 4.9 First run and notices

- P1 as drawn (the demo steps through the three colours with the new chip; no "Entrar con
  WhatsApp" here, sign-in is in Ajustes › Tu cuenta).
- **P2 keeps 0.2.20's four disclosure lines word for word.** They are the Play prominent
  disclosure and are under legal review (B-038). The v4 drawing's third line waits on decision 1.
- P3 and P4 keep 0.2.20's content.
- Two notices (V1): "Se apagó el semáforo" with its cause, and on Monday morning "Tu semana está
  lista" with "62 viajes · 12 – 18 oct". Neither shows a peso amount.

## 5. Frame map

Frame ids are the v4 kit's. `_frame-contract.md` lists the strings each built frame must show.

| Frames | Built as | Package |
|---|---|---|
| S1–S5, S7, S9, S10 | the chip composable + overlay | WP-B |
| S6 | placement (replay tests) | WP-B |
| S8 | `ReaderStoppedTab`, unchanged | — |
| P1–P4 | onboarding | WP-F |
| L1–L8 | Ganancias | WP-D |
| K1, K2, "Tu ciudad todavía no abre" | Comparar | WP-E |
| M1–M4 | IMSS | WP-E |
| J1–J7 | Ajustes and what hangs from it | WP-F |
| V1 | notices | WP-C |
| K3, K4 | not built (decision 1) | — |

## 6. Work packages

Each package works in its own worktree and branch off `feat/v4-simple-launch`, commits there when
its checks pass, and never pushes.

- **WP-A Rules and data** (`:metrics`, `:data`). `RateBasis` and its storage; the engine judging in
  the chosen basis; limit conversion; the frozen basis on the offer row and a `monthly_incentives`
  table (one Room migration, 9 → 10, with schema JSON and tests); counter-offers; the IMSS month;
  the driver's own slices; the week's car bill. Pure functions with unit tests on the kit's figures.
- **WP-C App shell** (`:ui` navigation, `:app` manifest). Three tabs, the route list of section 3,
  stubs for the new tab roots, removal of replaced screens, unlinking of after-launch surfaces,
  upgrade safety, notices, the v4 frame contract and snapshot scaffolding.
- **WP-B Chip** (`:ui` chip composable, `:overlay`). Section 4.3 and 4.4.
- **WP-D Ganancias**, **WP-E Comparar and IMSS**, **WP-F Ajustes and first run** (`:ui`).
- **Integration.** Merge, vocabulary and unused-string sweep, lint to green, version 0.3.0 (23),
  `techdebt.md`, `docs/v4-progress.md`.

## 7. Invariants

- Capture, OCR, parsing, offer identity, placement rules and touch policy do not change.
  `./gradlew verifyReadOnlyCapture` stays green: the reader informs, it never acts.
- The accessibility disclosure text and `docs/play-compliance.md` statements do not change.
- No new data leaves the phone. No new permission.
- Real OCR frames with addresses (`.session-captures/`) never enter the repo and are never sent to
  an external service.
- Tests are not deleted to get green. A deleted screen takes its tests with it; a kept screen
  keeps them; every new screen gets ViewModel tests, a screen test on the kit's fixture figures and
  day + night baselines at the font scales the v3 frames used.
- User-visible strings are Mexican Spanish, in the kit's words.

## 8. Founder decisions, and how this build treats them

| Decision | State | In this build |
|---|---|---|
| 1. Sharing averages by app, weekday and hour (opens Tu ciudad) | open; needs the privacy policy, Play data safety and counsel | not built; Tu ciudad says it is not open; P2 and J5 unchanged |
| IMSS: IVA as × 0.84 or ÷ 1.16 | open | × 0.84, as the founder wrote it ($21,938; ÷ 1.16 would be $21,376) |
| IMSS: each app on its own, or the apps added | open | each app on its own |
| 3. The car as a weekly bill | drawn, not objected to | built as drawn |
| 4. Three tabs and the after-launch list | drawn, not objected to | built as drawn; after-launch code kept, unlinked |

## 9. Checklist for every package

1. Read sections 1–4 and 7, your frames in `part-*.html`, their sheet PNG, and the code you replace.
2. Build to the frames: same structure, same words, figures from the fixture.
3. `./gradlew verifyReadOnlyCapture assembleDebug` and the unit tests of every module you touched.
4. Record baselines, then **look at each PNG next to the drawn frame** and fix what differs.
5. Commit on your branch. Report: what changed, test totals before and after, what you could not
   do, and anything in this plan that turned out wrong.
