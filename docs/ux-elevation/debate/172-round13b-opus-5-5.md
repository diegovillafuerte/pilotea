# Round 13b, Opus 5.5: confirming the residual pass

**What I reviewed.** I read the brief (`r13b-brief-resolved.md` / `170`), the residual brief (`160`), spec §9i and §10, and my Round 13 report (`152`). At full 2× size I opened E1, E1b, E7b, E3, E3c, E3d, E4, F2, F3, F4, G2, G4, H3, C3, C9, C11, D1, A1, N4, N5 and N7, plus all nine contact sheets (A–H, N). I did not open Codex's Round 13b output.

**Checker.** `python3 check-fixture.py` prints `checked 154 figures in 50 frames, 23 spec strings, 1 date rule, 46 stale patterns, 6 caption lints, 5 bar charts → OK`.

**Scope of the change.** I diffed every frame fragment in today's `index.html` against my Round 13 snapshot (`r13opus/frames.pkl`):
- **16 frames changed in markup:** E1, E1b, E3, E3c, E3d, E4, E6, E7b, F2, F3, G2, G4, H3, N4, N5, N7.
- **5 changed only in their captions:** A1, C3, C9, C11, D1.
- **All other frames are byte-identical.**

E1b, E6 and E3d changed only in their backdrops, which inherit the new qualifier or the new bar width. No chip, host or Hoy frame was touched.

**Measurements** (2× renders, same script and boxes as Round 13):
- **Mes bars:** E3, E3c and N5 bars are 60 px = 30dp, the same as E1's day bars (59–60 px). Each is centred over its axis label (bar centres 121 / 301.5 / 483.5 / 665; label centres 120 / 302 / 483 / 665).
- **E3 contrast mass:** the chart is **1.48×** the hero, down from 3.4×. N5 is 0.62×. E1 (2.36×) and N4 (0.86×) are unchanged.
- **H3 share card:** 706 × 883 px, which is 0.7995, so the card is still 4:5.
- **G2 targets:** the wrappers run from y −7 to 41 and from 46 to 94 against the 88dp plate. Each is 48dp tall, 5dp apart, `role="tab"`, `aria-selected` on the selected one.

## 1. My Round 13 list

| ID | Item | Status | Frame evidence |
|---|---|---|---|
| R13-01 | Suppression leaves no readable trace | **Partly (accepted)** | Done: the rule now lives in C11's and D1's captions and in spec §9i row 1 (mark in its verdict colour + "sin semáforo en pantalla"). G4 draws the count row "Sin semáforo en pantalla hoy · 0". Not done: the D1 row variant is still undrawn, because the 19 oct fixture has no suppressed offer. The D2 line was dropped. The captions disagree on the row's anatomy (R13b-01). |
| R13-02 | Two selector grammars on Tu ciudad | **Done** | F2, F3 and N7 now carry the 48dp context row "Uber · CDMX · Por viaje ⌄" at y 458, the same y and grammar as F4 and F5. The sentence is plain, with no dotted words. "Aportar al promedio" still sits above the nav (F2 y 1290), and F3's primary sits above it. |
| R13-03 | Car cost said twice; four figures in the first viewport | **Done** | E1, E7b and N4 now read "Según el lector · ya sin gasolina, desgaste ni coche · aprox." on one line inside the 48dp row. First-viewport figures are `$4,980` · `62` · `$214`. The −$1,500 appears only in E1b's ledger, and the checker asserts it there. |
| R13-04 | Mes bars outweigh the hero | **Done** | The bars are 30dp and the chart is 1.48× the hero (N5 0.62×). Heights still read 28.7 / 95.3 / 100 / 21.7 % against the fixture's 28.5 / 95.3 / 100 / 20.6 %, and the $0 raya is unchanged. |
| R13-05 | H3 breaks differently from F2; "Uber" twice | **Done** | The break is "Por viaje, Uber me pagó más que al" / `78 %` / "de sus conductores en CDMX" / "que compartieron su resumen", the same as F2. "Uber" appears once. |
| R13-06 | The signature does not travel | **Done** | In the privacy-on state the card shows "Tú" with the ink mark (top filled) above a 2dp line and "Promedio" hung under it, with no figures. The card stays 4:5. |
| R13-07 | A1 marks may read as a static legend | **Done (spec)** | A1's caption specifies one auto-step run: about 1.5 s per state, atomic swaps, the underline moving with the state. It ends on verde, never runs under reduce-motion, and a tap ends it. A static render cannot show this. |
| R13-08 | E4 ghost band | **Done** | E4 now has one hairline under DiDi, then 32dp of air before "¿Cómo funciona?" (`border-top:0`). |
| R13-09 | C9 plate stacks under the black banner | **Done (as evidence item)** | The gap stays 16dp. The caption and §6 make it an R14 test item: if drivers group the plate with the banner, the gap rises to 24dp. |
| R13-10 | Dead 6px chip radii | **Done** | `base-v3.css` line 173 `.chip` and line 188 `.lector-tab` now declare `2px 0 0 2px`, and `.chip.left` declares `0 2px 2px 0`. No 6px declaration or override remains. |

## 2. Regressions

**None in the renders.** Every changed frame shows exactly the intended change. Every figure still checks, and no frame outside the 21 above moved. Below are three things the pass introduced. None of them costs a point; the first and third are why criterion 8 stays at 9.

| ID | Where | Severity | Evidence | Fix |
|---|---|---|---|---|
| R13b-01 | C11 caption vs D1 caption and §9i | Nit (spec) | C11 describes the reason as "a 13sp muted run like '· faltó la tarifa'". But "· faltó la tarifa" renders inline at **16sp** (`.muted` sets only the colour; see D1 and B1), while D1 and §9i call the reason "the row's 13sp ink-2 line". That gives one undrawn row two anatomies. Only the second-line version fits beside `$2.20/km`. | Change C11's wording to "the reason on the row's 13sp ink-2 second line (D1)". |
| R13b-02 | F2, F3, N7 | Nit (copy) | The scope is now said twice within about 40dp: "Uber · CDMX · Por viaje" in the context row, then "Por viaje … de Uber en CDMX" in the sentence. F3 runs to 49 words. | Accept it: the sentence is the verbatim claim that H3 carries off-app, and F1 and F4 already pair the control with a claim that restates it. Or drop "Por viaje," from the on-screen sentence only. |
| R13b-03 | E3, E3c, E3d, N5; G2 | Nit (source, port) | The Mes bars are slot-wide `<i>` elements painted 30px wide with an inline gradient. Semana's `.spark.car` draws its bars differently, so the same bar has two implementations. G2's target wrappers are also inline-styled `role="tab"` elements with no `tablist` parent. | Before the Compose port, make a `.spark.wk` class (a 30dp element) and a `.lane-edit .lbl` rule inside a `role="tablist"`. |

## 3. Scores

| # | Criterion | R13 | R13b | Reason for the change, or why not 10 |
|---|---|---|---|---|
| 1 | One job per screen | 9 | **10** | The only not-10 in Round 13 was E3's weight (3.4×), and it is now 1.48× (N5 0.62×). Every root's 56sp hero now wins. |
| 2 | One primary action | 10 | **10** | Unchanged. |
| 3 | Navigation that disappears | 10 | **10** | Unchanged. The Tu ciudad selector no longer moves between F5 and F2. |
| 4 | Numbers discipline | 9 | **9** | E1 is fixed (three figures, the car cost once). Not 10: F2's first viewport still carries four figures (`78 %`, `$149`, `$131`, `1,240`). |
| 5 | Copy | 9 | **9** | H3 now names "Uber" once and E1's qualifier fits one line. Not 10: G6's "Cuenta y lo encendido" is still cryptic, and F2 states its scope twice (R13b-02). |
| 6 | States are designed | 9 | **9** | The suppression trace is now specified and counted in G4. Not 10: the row itself is undrawn, and its anatomy is stated two ways (R13b-01). |
| 7 | Time-to-value | 8 | **8** | Held by my rule: no timed install. |
| 8 | Consistency & feel | 9 | **9** | All four Round 13 splits are closed (selector grammar, H3's break, E4's band, Mes bar width). Not 10: the source and spec still carry two small splits the port will read (R13b-01, R13b-03). |
| | **Structural total** | 73 | **74 / 80** | Every criterion ≥ 8. |
| 9 | Signature idea | 9 | **9** | The line now travels: H3 shows position without amounts. Not 10: Mes still turns the line into an uncaptioned $0 axis, the Comparar mark is a subtle reuse, and the Ganancias line is conventional in form. |
| 10 | Glance physics | 8 | **8** | Held by my rule: no mounted test. The chip frames are byte-identical to Round 13. |
| 11 | Craft | 9 | **10** | All five not-10 items from Round 13 are closed in the renders (Tu ciudad grammar, H3's "al", E4's band, E3's blocks, the 6px radii), and I found no new rendered defect. R13b-03 is source-only. |
| 12 | Radical simplicity | 9 | **9** | E1 says its cost once and F2 lost its embedded selectors. Not 10: F2 and F3 restate their scope, F3 runs to 49 words, and H5b remains a sanctioned 69-word exception. |
| | **Craft total** | 35 | **36 / 40** | Every criterion ≥ 8. |

**Hard fails:** none on either rubric. No auto-action, no platform logos, no blank paywall, one ask per onboarding step, no rounded-rectangle chip, no same-weight cards, no K square, no Tailwind palette. The 1-second glance passes on static evidence, and the chip frames are unchanged from Round 13.

## 4. Verdict: **PASS**

Structural **74/80** (10 · 10 · 10 · 9 · 9 · 9 · 8 · 9). Craft **36/40** (9 · 8 · 10 · 9). Every criterion is ≥ 8, both bars are met, and the pass introduced no regression. Criteria 7 and 10 stay at 8 until the timed install and the mounted glance test (§11, R14) exist. Before the port, fix R13b-01 (one caption sentence) and R13b-03 (two CSS classes).
