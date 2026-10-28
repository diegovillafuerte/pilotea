# Round 17 debate — Opus 5.5

**What I read:**
- the brief `240`, the two whole-product board tallies (`180`, plus all twelve judges' per-screen notes and top changes in `final/boards-tally.md` and `final/board6-tally.md`), and my reports `212` and `232`;
- spec §0, §2, §3, §4, §5.1–5.6 and F14;
- the fixture;
- sheets A–H and N, and frames B1, D1, E1, E8 and A1 at full size;
- uber-1 to uber-6.

Every figure below is computed from the fixture at full precision ($2.841667/km, car $214.2857/day) and rounded once.

| Proposal | Verdict |
|---|---|
| P1: one money definition | **AGREE WITH CHANGES.** Add the reversal, a verb split, a B6 rule and a negative-figure rule. Also fix one wrong figure in the brief. |
| P2: cut the second tier | **AGREE WITH CHANGES.** Every proposed cut is right. Add one cut ("Tu lugar…") and keep one element ("Ver tu día"). Fix A1's legend form, not only its line. |
| P3: drawn icon set | **AGREE WITH CHANGES.** Make it solid geometry, not a 2dp outline set; keep the mark off the nav; do it last. |
| My one change | **The 72sp display belongs to the day only.** Ganancias leads with its figure beside its bars (uber-5's layout); Comparar leads with its finding as a sentence. |

---

## P1 — One money definition: AGREE WITH CHANGES

### Why the reversal is right

1. **The record already broke the old rule; the day screens are the last holdouts.**
   - F14 (my own Round 2 call) said the car is "never from the chip or the day".
   - The fixture already allocates the car by calendar day: D3b shows "$214.29 por día × 19 días".
   - E3d lists today as "19 oct · hoy · 9 viajes $1,026 · 1 día del coche".
   - E8 draws a $762 bar under a $548 hero on the same screen. I saw that render myself; no judge needs to point it out.
   - So today the same Monday carries $762 (Hoy), $1,240 at 21:10 (D1) and $1,026 (Mes).
2. **A weekly-only rule cannot produce a sane open-week figure.**
   - Take off $1,500 on Monday and E8 reads −$738.
   - Take it off on Sunday and the hero falls off a $1,500 cliff with no trip.
   - The daily accrual is therefore already the model everywhere except Hoy and Tu día.
3. **The renter's real question, "¿cuánto me quedó?", is answered after the rent.**
   - Lo del coche is optional, so owners who never set it see nothing change.
   - Renters set it precisely to see it taken off.
   - Drivers who pay a daily "cuenta" already think "first the car, then it's mine". This is a hypothesis for the driver test (below), not a fact I can prove from here.
4. **It fixes the cause of my own R12-01.** I flagged that E1's bars were before the car while its hero was after. The dashed car line was the patch for that. Judges still read "bars sum to $6,480, hero says $4,980" (3 of 6 on board 6). With P1 the bars, rows and hero share one basis, so the patch and its 120dp caption column can go.

**The chip stays on running costs (strong agree).** Putting the car into a rate would subtract $1,500 ÷ 675.8 km = $2.22/km. That turns C1's $9.49 verde into $7.27 amarillo, for a cost that is identical whether he takes the trip or not. That would make drivers reject trips that pay.

### Effects, recomputed

| Frame | Today | Under P1 | Full precision |
|---|---|---|---|
| B1, 14:40 | $762 | **$548** (= E8's hero) | 762.18 − 214.29 = 547.90 |
| D1 / B2 "Ayer te quedaron" | $1,240 | **$1,026** (= E3d) | 1,239.91 − 214.29 = 1,025.62 |
| B4, Sunday "Ayer te quedaron" | $499 | **$284, not the brief's $285** | 498.55 − 214.29 = 284.27 (the brief rounded twice) |
| B5 | $648 | **$434** | 648.28 − 214.29 = 433.995 |
| B6 (net given) | $380 | **$166** (rule below) | 380 − 214.29 = 165.71 |
| E1 hero | $4,980 | $4,980 | unchanged |
| E1 bars / E1b rows | 808 · 950 · 910 · 1,041 · **1,380** · 891 · 499 (sum $6,480) | 594 · 736 · 695 · 827 · **1,166** · 677 · 284 | Rows add to $4,979; the hero is $4,980, rounded once |
| E1 bar heights | 59 69 66 75 100 65 36 % | 51 63 60 71 100 58 24 % | The car line and its caption column go; bars take the full width |
| E8 | $762 bar over a $548 hero | $548 bar = $548 hero | |
| E3, E3d, D3b | already after the car | unchanged | |

### The rules I would write

**§4 vocabulary (replaces the "Period result" and "Rate" rows):**
- **"Te quedan / Te quedaron"**: a period's money after all your costs.
  - Gasolina and desgaste are taken off per km.
  - Lo del coche is taken off by calendar day ($1,500 ÷ 7 = $214.29), including days you don't drive.
  - Day, week and month use this one definition: a day's figures add to its week, and a week's to its month.
- **"Te deja / Te dejó"**: what one trip or one app leaves after running costs only.
  - D2: "Te dejó · aprox. $114", and "Te dejaría" for an offer not taken.
  - F1: "DiDi te dejó $1.21 más por km".
  - The promise line: "Ve si el viaje te deja".
- **"neto"**: a rate after running costs ("$9.49/km neto").
- The chip, the offer verdict, Tu semáforo and every rate never include the car.

**Quiet line.**
- When Lo del coche is set: "…, ya sin gasolina, desgaste ni coche ›". Otherwise: "…, ya sin gasolina ni desgaste ›".
- Day versions:
  - B1: "6 viajes tomados, / ya sin gasolina, desgaste ni coche".
  - B5: "5 viajes tomados en 4 h 30 min, / ya sin gasolina, desgaste ni coche".
- Amend §4 so that the car clause, like hours or source, permits `.sub2.two`. The longer clause does not fit one line at 16sp on B1.

**Cómo se calculó · hoy (D3).**

| Row | Sub-label | Amount |
|---|---|---|
| 9 viajes tomados | | $1,590 |
| Gasolina y desgaste | 123.2 km × $2.84 | −$350 |
| Lo del coche | $1,500 ÷ 7 días | −$214 |
| **Te quedan hoy · aprox.** | | **$1,026** |

- The 13sp line becomes "El pago final lo confirma tu resumen de la app. Antes de impuestos."
- Delete "Aquí no entra lo del coche… se resta en Ganancias."

**Cómo se calculó · semana.**

| Row | Sub-label | Amount |
|---|---|---|
| 62 viajes tomados | | $8,400 |
| Gasolina y desgaste | 675.8 km × $2.84 | −$1,920 |
| Lo del coche | | −$1,500 |
| **Te quedaron** | | **$4,980** |

**E1b.**
- The seven rows above, each opening Tu día.
- Total `Te quedaron $4,980`, with the sub-line "Redondeado una vez · los días suman $4,979".
- Delete "Antes de lo del coche", "Semana $6,480" and "Lo del coche −$1,500".

**G5.** "Se resta de tu semana y tu mes: $214 por día. Nunca del semáforo ni de tu día." becomes **"Se resta de cada día: $214, también los días que no manejas. Nunca del semáforo."**

**Negative figures (the brief's question).**
- Before the first trip taken today, Hoy shows no figure: its existing empty or preparado form (B2, B4). The car's −$214 never appears alone at 7:30.
- From the first trip, the hero shows the signed result. The fixture's own 14:32 trip alone would read **"−$100"** (113.90 − 214.29):
  - a true minus kerned to the $, in ink at 72sp;
  - never rojo (that is a verdict colour);
  - never "te faltan…", and no progress bar (the goal bar is killed and "maneja más" pressure is banned).
- A past day with no trips reads "Te quedaron el domingo · aprox." **−$214** over "Sin viajes tomados; lo del coche corre igual ›". Its week bar sits on the $0 line.

**B6 (partial support).**
- The hero takes the car share like every "te quedan" total: **$166**.
- Its label names what is missing rather than what is counted: **"Te quedan hoy sin contar DiDi · aprox."**
  - The driver's correction is then a pure addition: his DiDi trips after their running costs.
  - "Te quedan hoy en Uber $166" would charge the whole day's car to Uber.
- Ledger:

  | Row | Sub-label | Amount |
  |---|---|---|
  | 3 viajes de Uber | ya sin gasolina ni desgaste | $380 |
  | Lo del coche | | −$214 |
  | **Te quedan hoy sin contar DiDi** | | **$166** |

  with the 13sp line "DiDi no entra: por ahora no lo leemos."
- The "Por ahora no leemos DiDi" row stays, as a state.

**Record and checker.**
- Amend F14 and §2 (the car line retires as a signature carrier).
- Add a checker assertion: Σ of a week's day "te quedan" equals the week hero ± a stated rounding peso. No screen may show a day on a second basis.
- Add to §11: 5 renters and 5 owners read B1 at $548 and at −$100. It passes if ≥ 8 of 10 read "what I keep after costs, car included" and none reads the minus as an error.

---

## P2 — Cut the second tier: AGREE WITH CHANGES

| Item | Verdict | Exact change |
|---|---|---|
| B1 best-hour row | **Right; cut, with no forward-looking version** | At 14:40, "9 a 10 h" is history. The fixture holds only today's hours (`today_hourly_net_shift1`). A forward hour needs weeks of hourly history and is advice, which §5.1 bans. The observation slot becomes state-only: B6's limitation, or a fix that is waiting. |
| B1 "Ver tu día" | **Keep; cutting it would be wrong** | It is uber-5's grey "Estadísticas", the one root secondary. The quiet line opens a different place (the ledger). Under P1 that ledger is where "¿por qué $548?" gets answered. Also: each offer row opens Tu día, as on B5. |
| E1 "Tu mejor día fue el viernes" | **Right** | The ink bar and its figure say it, and any bar opens Por día. The same rule cuts "La mejor fue 12 – 18 oct" on E3 and "La mejor fue 21 – 27 sep" on E3c. |
| E1 "Tu resumen de Uber dice un poco más" | **Right; rewrite it as a fact** | **"Según tu resumen, Uber te pagó $34 más ›"** ($5,062 − $5,028; one figure on the row; same sheet). |
| E1 "Tu lugar entre los de Uber en CDMX" | **Add this cut** | It is a bare link to a tab and states no fact; three judges named it. The standing lives in Comparar. E7b's offline line goes with it. |
| D1 shift timeline | **Right; fold it into the quiet line, not a new line** | "9 viajes tomados en 8 h 10 min, / ya sin gasolina, desgaste ni coche ›". No per-shift money: under P1, $648 + $592 would not add up to $1,026. The pause shows as the gap in the rows' times. "Ofertas · 23" rises about 120dp. `ShiftLine` retires. |
| A1 summary line | **Right; keep one word** | Replace the 60-character line with a 13sp "Ejemplo" at the gutter above the demo. The demo must never read as a live reading. |
| A1 legend | **Keep it, but its form is the problem** | Five of six judges read it as a picker or as ragged: "Entre tu meta y tu mínimo" wraps, and the shown state sits in a filled `--paper-2` cell that reads as a selected pill (pills are banned). Make it one column of four 40dp rows (mark · cue · phrase, each on one line). The shown state is ink 600 and the others ink-2, the text-tab grammar. Pay for the height by cropping the host demo about 100dp (drop the host's top chrome, keep route, plate and fare). Gris must still be taught on day one. |

**Rule (§4, replaces the observation lines):**
- Every screen is one hero block, at most one visual and one list.
- Between the hero block and the list, a row survives only if it either:
  - (a) states a fact that neither the hero nor the visual shows, with its amount; or
  - (b) is a state the driver must know or act on (a reader limitation, a waiting fix).
- Cut on sight:
  - a past fact presented as news;
  - a sentence that restates the visual;
  - a bare link to a tab;
  - a second door to the same destination.

---

## P3 — A drawn icon set: AGREE WITH CHANGES

**It is worth doing at design stage, but it is third in priority and should follow P1 and P2.**
- P2 deletes four of the glyphs that would otherwise need drawing: the clock, the calendar, the compare-arrows row glyph, and B6's circled "i".
- The visible defect today is a weight mismatch in the nav: the filled house next to the hairline bar_chart. The circled info glyph also breaks §4's "no circled glyphs".
- Only 2 of 12 judges asked for icons, so the expected gain is modest. The cost is about half a day for roughly 14 glyphs.

**Two changes to the proposal:**
1. **Not a 2dp-stroke outline set.**
   - A uniform outline set with 2dp strokes is the Lucide/Feather default, the most "AI template" icon look there is. Square caps do not rescue it.
   - Uber's own glyphs are solid geometry with square terminals: the shield, pin, sliders and list in uber-3 and uber-4, and the car, bag and money in uber-1.
   - Kompara's chip is the same: a solid cue over 2dp lines.
2. **The three-line mark must not be the Hoy glyph.**
   - The mark means an offer's place against the limits. Round 15 removed it from Comparar for exactly that reason.
   - In the nav it would sit on every root, Comparar included.
   - An inactive ink-3 mark twins the dashed gris "sin lectura" mark.
   - The brand glyph is the K-raya founder fork and stays off the nav.

**Rules:**
- **Set (≤ 14 glyphs):**
  - Nav: Hoy as a house; Ganancias as three vertical bars on a 2dp baseline, its own chart; Comparar as two horizontal bars, the top one longer, the `.cbar` object. `compare_arrows` reads as a transfer, which is wrong in a money app.
  - Chrome: person, share (keeps the Android three-node shape for recognition), ←, ✕, chevron, selector caret.
  - Rows: document, IMSS building, lock, check.
  - B6's limitation is led by the reader's dashed segment (the status grammar), not an icon.
- **Grid:** 24dp box with a 20dp live area, keylines on whole dp, snapped at 1× and 2×.
- **Form:**
  - Line glyphs (←, ✕, chevrons, caret, ✓, share links) use a 2dp stroke, square caps and mitre joins, the chip's line weight.
  - Object glyphs (house, person, document, lock, building, nav bars) are solid fills with 2dp knock-outs, never outline-only.
- **Corners and angles:** radius 0 or 3dp (the mark's radius); angles of 0°, 45° and 90° only.
- **States:** one glyph per destination; active ink, inactive ink-3; no filled/outline swap.
- **Weight check:** the three tab glyphs' ink area matches within ±10 %. Glyph mass at 24dp matches Barlow 600 at 16sp.
- **Reserved shape:** solid triangles (▲ ◆ ▼) belong to the chip only, and every disclosure in the app is an open 2dp chevron.
  - The reverse rule matters more. 4 of 6 board-6 judges read the tray's white ▲/▼ at the plate's right end as "a collapse caret" or "a dropdown/expand control". On a drag-only overlay where taps do nothing, that is a false affordance on the most-used surface.
  - Fix it in the chip track. The candidate is the mark's own slot fill in the band colour, which also makes the plate and the row mark one object. Test it in the lineup-8 format before it is ported.

---

## The one change: the 72sp display belongs to the day only

**Rule.**
- The display step (72/74 700) is reserved for the day's figure: Hoy (B1, B5, B6), Tu día (D1) and their night frames. Hoy v5 won its board at 8.1 with it.
- Every other screen leads with its own object:
  - **Ganancias (E1, E2, E3, E3b, E3c, E8):** uber-5's layout.
    - The 16sp label, then one 88dp row: the figure on the left at the existing **number step, 32/36 600**, and the period's bars on the right (about 160 × 72dp, best bar in ink, no figure on the bars).
    - The quiet line goes under the row, full width.
    - The freed ~110dp takes the period's list on the root. Those are the Por día / Por semana rows, which under P1 finally add up to the figure, so E1b and E3d stop being sheets.
  - **Comparar (F1–F5):** the finding as the 24/30 700 headline, with its figure inside.
    - F1: "DiDi te dejó $1.21 más por km". F2: "Te pagan más por viaje que al 78 % de los de Uber en CDMX".
    - The qualifier goes directly under it ("Según el lector, a distintas horas ›"), then the bars.
  - **Task heroes (G3, H1, H2b, H5):** the figure at the number step under the 24/30 headline.

**Why this change and not another.**
1. **It removes a template signal the boards named and none of P1–P3 touches.**
   - Three of six board-6 judges wrote some form of "Stop putting a giant number on every screen", "the same hero-caption-chevron-rows formula on four tabs" and "stop forcing the large-amount hero onto content".
   - E1 drew 4 of 6 template votes.
   - F1's "+$1.21" was read as a rate or as earnings by 4 of 6.
2. **It is Uber's own distribution.**
   - Uber keeps one persistent large earnings figure (the pill in uber-2). Its Ganancias sets the figure about as large as the title, beside its chart: in uber-5 the "Ganancias" caps are about 24dp tall and "MXN 645,99" about 21dp.
   - Kompara puts a 55dp bold cap height under a 22dp title on 18 day frames.
3. **Each tab becomes recognisable by shape:**
   - Hoy: status and a big number;
   - Ganancias: a figure beside its bars, over its days;
   - Comparar: a sentence over two bars.
   Today it is one layout copied four times.

**Gate.** Re-run the same eight-screen product board with E1 and F1 in both forms. Keep the change if those screens rise by ≥ 0.5 Uber-class and their template votes fall.

**Order of work.**
1. P1: about 12 frames change figures, and it decides E1's chart.
2. P2, applied with P1.
3. The one change, laid out on P1's basis.
4. P3, once P2 has removed the glyphs it no longer needs.
5. Re-run the board.

### Critical Files for Implementation
- /Users/panama/pilotea/docs/ux-elevation-spec.md
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/fixture.json
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/check-fixture.py
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/base-v3.css
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/index.html