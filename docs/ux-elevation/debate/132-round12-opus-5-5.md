# Round 12, Opus 5.5: scoring v3 on both rubrics

**What I reviewed.** I read the brief (`r12-brief-resolved.md`), both rubrics (`02`, `03`), the synthesis (`120`), the frame contract, the spec sections the frames depend on (§ chip table, D1, D2, E4/E5, F-states, type and surface rules), all four Round 11 reports (`111`–`114`) and `fixture.json`. With the Read tool I looked at all nine contact sheets (A–H, N). I opened at full 2× size: A1, A2, A5, B1, B2, B3, B4, C1, C2, C3, C4, C5, C6, C7, D1, D2, E1, E1b, E2, E3, E3c, E3d, E4, E7b, E8, F1, F2, F5, G1, G2, H1, H2b, H3, H5b, H6, N1, N4 and N8. I also looked at the Uber offer-card benchmark. I did not open Codex's Round 12 output.

**Checker.** `python3 check-fixture.py` in the kit prints `checked 154 figures in 50 frames, 23 spec strings, 1 date rule, 46 stale patterns, 6 caption lints, 5 bar charts → OK`. The renders are fresh: every PNG is time-stamped after the last `index.html` assembly (07:35:47 → 07:37:13).

**Figures I re-derived by hand from `fixture.json`.** The cost per km is 24.5 ÷ 12 + 0.80 = **2.8417**.

| # | Figure | Arithmetic | Result |
|---|---|---|---|
| 1 | C1 / D2 | 148 − 12.0 × 2.8417 = 113.90; ÷ 12.0 = 9.49; ÷ 0.4 h = 284.8 | `$9.49/km`, `$285/h`, `$114`, `−$34` ✓ |
| 2 | C2 | 120 − 34.10 = 85.90; ÷ 12.0 = 7.16; ÷ 0.45 h = 190.9 | `$7.16/km`, `$191/h` ✓ (6 ≤ 7.16 < 8, so amarillo ✓) |
| 3 | C3 / C7 | 62 − 34.95 = 27.05 → 2.20 and 54.1; 22 − 34.95 = −12.95 → −1.05 and −25.9 | `$2.20/km`, `$54/h`, `−$1.05/km`, `−$26/h` ✓ |
| 4 | B1 / D1 | shift 1: 831 − 64.3 × 2.8417 = 648.28; + 113.90 = 762.18. Shift 2: 759 − 167.38 = 591.62. Day: 1,590 − 350.1 | `$762`, `$648`, `$592`, `$1,240`, `−$350` ✓ |
| 5 | E1 / E1b | 8,400 − 675.8 × 2.8417 = 6,479.6; − 1,500 = 4,979.6. Bars: 808/1,380 = 59 %, 499/1,380 = 36 % | `$4,980`, `$6,480`, bars `59 … 36` ✓ |
| 6 | F1 | Uber: (5,028 − 1,193.5) ÷ 420 = 9.13. DiDi: (3,372 − 726.9) ÷ 255.8 = 10.34 | `$1.21` ✓ |
| 7 | F2 | 5,062 ÷ 34 = 148.88 → `$149`. 162,440 ÷ 1,240 = 131.0. Between deciles 70 (142) and 80 (151): 70 + 10 × 6.88 ÷ 9 = 77.6 | `78 %` ✓ |
| 8 | E4 / E5 | Uber 1,480 + 5,000 + 5,028 + 1,100 = 12,608; DiDi 1,470 + 3,100 + 3,372 + 490 = 8,432; × 0.94 | `$11,852`, `$7,926`; 9,582 − 7,926 = `$1,656` ✓ |
| 9 | H2c | 19 oct + 1 month + 7 days of grace = 26 nov; + 21 banked days = 17 dic | ✓ |

**My Round 11 scores for the previous version** were 3 · 4 · 4 · 5 = 16/40. The scores below judge this version on its own merits against the same rubric.

---

## 1. Scorecard A — structural (`02-rubric-uber-bar.md`)

| # | Criterion | Score | Evidence |
|---|---|---|---|
| 1 | One job per screen | **9** | Every root has one 56sp hero on the paper: B1 `$762`, D1 `$1,240`, E1 `$4,980`, E3 `$12,169`, F1 `$1.21`, F2 `78 %`; B2's hero is the cause. Not 10: on E1, E3 and N4 the bar block carries 3–6× the hero's contrast mass (R12-01). |
| 2 | One primary action | **9** | There is exactly one 56dp ink primary wherever an action exists, at the thumb (A1, A2, A5, B2, B3, E4, G2, H2b, H3). Reading roots (B1, E1, F1) carry none, and share is a top-bar icon. Not 10: B4's "Ajustar" floats centred *above* its primary (R12-13). |
| 3 | Navigation that disappears | **9** | Three tabs plus a bare person icon. Drill-ins are consistent (D1 keeps Hoy active; E4 drops the bar). Text tabs carry a 24 × 2dp underline (E1, F1, D1). Not 10: the three tab roots use three different top-bar grammars (R12-14). |
| 4 | Numbers discipline | **9** | 154 asserted figures pass, and I re-derived nine families by hand (table above). Barlow figures are tabular. `78 %`, `−$1.05/km`, `−$4,071` and `12 – 18 oct` never break, and C7 fits in 200dp. Not 10: E1's bars are *before* the car cost while its hero is *after* it, and E3's bars are after (two bases, separated only by a 13sp label). H6 says "8 h" where D1 says "8 h 10 min". |
| 5 | Copy | **9** | `tú`, sentence case, one ≤ 6-word qualifier row per hero said once, no "aprox." per row. The median root has about 40 words (my count excluding status bar and nav: B1 41, F1 29, F2 38, E1 54 including seven day letters). Not 10: F5 attaches "Sin confirmar" to the wrong number (R12-07), C4 and the spec disagree on the tense (R12-11), and E4 lost the non-representation line (R12-08). |
| 6 | States are designed | **9** | Reader off (B2), first run (B3), pre-confirm (B4), pause (B5), partial support (B6), two gris causes (C4, C5), stopped (C6), locked (E2, F3), loading (E7), offline (E7b), no cohort (F4), unconfirmed (F5), billing (H2c, H2d), import failures (H5c, H5e) and a night set. All are ink and all say the next step. Not 10: the chip's own variants are specified but never drawn (tall offer, navigation banner, 1.3× font, suppressed; R12-04). |
| 7 | Time-to-value | **8** | A1 shows a verdict on a host before any ask. Each onboarding step makes one ask (A2 accept, A3 accessibility, A4 battery only when restricted, A5 one stepper), and the WhatsApp number is optional (A6 "Ahora no"). Held at 8 for the same reason as in Round 11: it is unmeasured. A1 also teaches one state of four in words (R12-02). |
| 8 | Consistency & feel | **9** | One vocabulary: rows on hairlines, sheets, text tabs, the mark, status segments, the ink primary, 20dp gutter. It feels like one hand. Not 10: A1's host is a different drawing from C1's (R12-05). B1's header and link share one style (R12-09). The frames draw the chip with 2dp corners against 6dp in the CSS and spec (R12-12). Night bars glare (R12-01). |
| | **Total** | **71 / 80** | Every criterion is ≥ 8. **Structural bar met.** |

## 2. Scorecard B — craft (`03-rubric-r11-craft.md`)

| # | Criterion | Score | Evidence |
|---|---|---|---|
| 9 | Signature idea | **8** | **The chip is Kompara's and nobody else's.** It is a dark plate docked to the bezel, and the verdict is *where* the colour sits against a two-rule lane (C1 top, C2 lane, C3/C7 bottom, C4/C5 none). Nothing in the bench folder looks like it: GigU is a white bordered card, Uber a gross figure in a blue outline, Ruta Rentable a colour fill. A director can say it in one sentence and a driver can draw it. It carries cleanly into the mark (B1, D1, D2) and the solid/dashed/broken status segments (B1–B5, D1, G1, H2c). **Why not 9: it stops there.** Ganancias' "raya" is an ordinary $0 chart baseline (E1, E3), and Comparar's is a heavier table divider (F1, F2). G2, the one screen where the driver sets the two limits that *are* the lane, never draws the lane. A1 teaches one state of four, in words. H3, the artifact that leaves the app, keeps a raya and caption with nothing above them. (R12-01, -02, -03, -06.) |
| 10 | Glance physics | **8** | **Rendered geometry.** I measured C1: plate x 193–393, y 144–232 (200 × 88dp), docked under the ⌕ control (≈16dp gap) and well above the offer card (top ≈ y 548). The number stays at zone T in every state. Lit area is 41 % for verde and rojo, plus a 10.5 : 1 amarillo stripe. **My test strip** (`/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/r12opus/glance.png`) shows C1–C7 and N8 in colour, greyscale, 10px Gaussian blur and a Machado deuteranopia simulation. It gives four distinct lightness patterns (top lit · bright stripe · bottom lit · uniformly dark and dashed). Under deuteranopia, verde and rojo become the same olive and stay separable only by top versus bottom. C6 is half-height with no lane. **Held at 8, by my own Round 11 rule:** nothing above 8 without the mounted test. Three open risks: amarillo is the smallest mass (18 %) for the state that needs the most thought; the ink plate speaks the host's own black-control dialect (C1's "En línea" capsule and Aceptar); only one host geometry exists (R12-04). |
| 11 | Craft | **8** | **The template is gone.** Warm paper and ink, Barlow / Barlow Semi Condensed on five steps, zero `.card`, no pills in product (the seven `.pill-type` are the host's "Viaje" capsule), real 24dp Material icons in `currentColor`, a credible unbranded host with a control row, route, pickup and Aceptar (C1–C7), and a real night set (N1–N8). Put next to the Uber and DiDi store frames, it reads as a team with a point of view. **Why not 9:** <br>• E1's filled ink-2 bars carry ≈ 5.8× the hero's contrast mass by day. At night (N4, N5) they are the largest light object on the screen (R12-01). <br>• The first screen (A1) still uses the rounded-tile wireframe map (R12-05). <br>• H3's raya floats with an orphan caption (R12-06). <br>• B1's section header and its link are one style, 5dp above the rule (R12-09). <br>• D1's last row is cut by the nav with no fade (R12-10). |
| 12 | Radical simplicity | **9** | Each screen fits one sentence: B1 "cuánto llevo y si lee", E1 "cuánto me quedó y qué día", F1 "en qué app me fue mejor por km". Scope paragraphs are gone, the qualifier is said once, and F1's metric pills and segmented control became one context row ("DiDi y Uber · Por km ▾"). E1 has 7 blocks (was 10). E4 has two figures, one line and two rows. Not 10: E1 still names the car basis twice (the qualifier row, then "antes del coche" on the chart), because the chart and the hero use different bases (R12-01). |
| | **Total** | **33 / 40** | Every criterion is ≥ 8; **the total misses 34 by one point.** |

### Hard-fail check

| Rubric | Hard fail | Status | Frame evidence |
|---|---|---|---|
| 02 | Auto-action proposed | **Absent** | The chip is drag-only with no accept/reject affordance (C1–C7). A2 states "nunca acepta ni rechaza". |
| 02 | Platform logos | **Absent** | The host is unbranded (C1–C7, N8). "Uber" and "DiDi" appear only as plain words (E1, F1, H4). |
| 02 | A screen that fails the 1-second glance test for a driving user | **Absent on static evidence** | The four states give four lightness patterns that survive greyscale, blur and deuteranopia (C1–C7, N8; `glance.png`). C4 and C5 look the same at a glance, which is correct: both mean "no verdict". Unmeasured on a mount. |
| 02 | A paywall that blanks the free experience | **Absent** | E2 keeps `$4,747` and its trip count, with flat placeholders, not a blur. F3 keeps the standing and the Tú row, and locks only the average. |
| 02 | An onboarding step with more than one ask | **Absent** | One ask per step: A2, A3, A4, A5. A6 is optional ("Ahora no"). |
| 03 | The chip is a rounded coloured rectangle with a number and a glyph | **Absent** | C1–C7 are an ink plate with the colour confined to one zone, no glyph and 2dp inner corners. |
| 03 | Any screen that is a list of same-weight cards | **Absent** | 0 `class="card"` in `index.html`. G1, H1 and H4 are rows. H3 has one share-card preview. |
| 03 | A "K" square as the brand | **Absent** | An unboxed verde K letter (A1, H3, H6, and at 60 % on the chip). |
| 03 | A Tailwind-default palette | **Absent** | No slate or emerald hex in the Kompara tokens. The only Tailwind value, `#f3f4f6`, appears 8× and only in the night host mock (`base-v3.css` 217, 265–267). |

## 3. Round 11 defects: status

### 3.1 Mine: `112` (Round 11)

| # | Defect raised | Status | Evidence now |
|---|---|---|---|
| 1 | C1–C3 chip: GigU's badge in Uber's gross typography | **Fixed** | La raya con carril, Barlow Semi Condensed, `neto aprox.` in zone B (C1–C3). |
| 2 | B1: hero in a slate card, gutter link, green status pill | **Fixed** | The hero sits on the paper, the qualifier row runs full width with a chevron, and the status is an ink segment (B1). |
| 3 | E1: ten equal blocks, "Cómo / se / calculó" over three lines | **Fixed** | Seven blocks, one qualifier row (E1). |
| 4 | F2: emerald card, 20 dots, two pills, "78 / %" | **Fixed** | Scope tabs, one context row, the standing sentence, Tú over the line, Promedio below it (F2). |
| 5 | H1: two emerald slabs, the benefit dressed as a button | **Fixed** | One list, one ink button (H1). |
| 6 | E4: legal paragraph as the screen, text seam, orphaned parenthetical | **Fixed** | Two 32sp payments, one line, two rows (E4). The non-representation line was lost on the way (R12-08). |
| 7 | A2: four tinted icon tiles | **Fixed** | Four hairline blocks with bold lead words (A2). |
| 8 | G1: icon squares in slate cards | **Fixed** | Plain text rows (G1). |
| 9 | H3: emerald gradient, K square, stat tiles, "78 / %" | **Fixed** | A paper card, the unboxed K and an unbroken `78 %` (H3). A new orphan raya appeared (R12-06). |
| 10 | C6: failure dressed in the verdict's silhouette | **Fixed** | A 200 × 48 tab with no lane, colour or K (C6). |
| 11 | Slate surfaces | **Fixed** | `--paper #F5F4EF` / night `#121211`. |
| 12 | Emerald on every action; brand green colliding with verdict verde | **Fixed** | Green appears only in verdict zones, marks and the K letter. |
| 13 | Inter, 18 type sizes, weight 900 | **Fixed** | Five steps in product. Residual: E4 and E5 use 34px against the 32 number step (R12-17). |
| 14 | 47 cards | **Fixed** | 0. |
| 15 | 37 pills and 4 segmented controls | **Fixed** | Text tabs and choice rows. |
| 16 | Circled ✓ / ✕ / ? glyphs; tinted icon tiles | **Fixed** | The mark (B1, D1, D2). |
| 17 | Chip anatomy: 16dp radius, glyph, 30sp/900, K square, 24dp shadow | **Fixed** | Ink plate, 2dp inner corners, 0 on the bezel side, `0 2 8` shadow. |
| 18 | K square as the brand | **Fixed** | Unboxed K. |
| 19 | Wireframe grid host hiding the ⌕ conflict | **Partly** | C1–C7 and N8 are credible, with the chip under ⌕. A1 still uses the tile grid (R12-05). There are no tall-offer or banner variants (R12-04). |
| 20 | Motion carries no identity | **Not assessable** | Static frames. The spec now specifies a 16dp slide from the bezel and atomic swaps. |
| 21 | "78 / %" on F2 and H3 | **Fixed** | F2, H3 and N7 are unbroken. H3 sets `%` at 32 and F2 at 56 (R12-19). |
| 22 | "Cómo se calculó" gutters on B1, D1, E1, E3 | **Fixed** | Full-width qualifier rows. |
| 23 | B3: "Ejemplo" pill colliding with the K; "típicos ·" stranded | **Fixed** | A 13sp label at the left, the chip docked right (B3). |
| 24 | D2: orange text action | **Fixed** | Three ink rows (D2). |
| 25 | H6: English developer note in the phone | **Fixed** | H6 is clean. |
| 26 | E3c: annotation spills over the phone | **Fixed** | The label sits above the phone (`top:-18px`, outside the screen). |
| 27 | D1: list cut by the nav with no fade | **Not fixed** | The 11:48 row is cut mid-height by the nav on D1 and N3 (R12-10). |
| 28 | Verde and rojo isoluminant (1.09 : 1); colour-blind cue only a 22dp glyph | **Fixed** | Position code; greyscale and deuteranopia test (`glance.png`). |
| 29 | Chip borrows Uber's gross typography | **Fixed** | The Barlow plate against the host's Inter 800 fare (C1). |
| 30 | "aprox." three times on B1 | **Fixed** | Once. |
| 31 | Dark-first was an inherited assumption | **Fixed** | Day-led frames plus N1–N8. |
| 32 | Scope sentences repeat the qualifier (copy 7) | **Fixed** | One ≤ 6-word row. |

### 3.2 Mine: `114` (Round 11b), including what I adopted from Codex

| Defect | Status | Evidence |
|---|---|---|
| `raya-zoom`: K clipped by the bezel | **Fixed** | K at 16dp from the plate's right edge, in zone T (C1). |
| Number moves between halves | **Fixed** | The rate stays in zone T in every state (C1, C3, C7). |
| Amarillo is an all-yellow slab | **Fixed** | Only the 16dp lane is yellow (C2). |
| C6 must be a different object | **Fixed** | C6. |
| Text tabs need an underline | **Fixed** | 24 × 2dp (E1, F1, D1). |
| D2: keep "¿Se leyó mal?" visible | **Fixed** | "Avisar que se leyó mal" (D2). |
| E1: discrepancy row on the root | **Fixed** | "Tu resumen de Uber dice un poco más ›" (E1). |
| H2b: cut "≈ $18"; move the failed-charge text | **Fixed** | H2b. |
| F2: one context row; "Aportar al promedio" visible | **Fixed** | F2. |
| D1: three shift rows, not a timeline | **Fixed** | D1. |
| Host night version | **Fixed** | N8. |
| Host tall-offer and navigation-banner variants | **Not fixed** | No such frame among the 74 (R12-04). |
| `b1-variants` tells (Inter 900, green status, dot rows) | **Fixed** | B1. |
| `b1-variants` dropped `6 viajes tomados` and `Tu mejor hora hoy: 9 a 10 h` | **Fixed** | Both are on B1. |
| `host-test`: missing control row; old chip over ⌕ | **Fixed** | C1. |
| My critiques of Codex's own proposal (cota mass, plate edge, GigU look, ≡ resemblance, cota covering ⌕, green-tinted neutrals, attention/error hues, C6 broken rules, Roboto Condensed) | **Moot / fixed** | The carril replaced the cota; neutral neutrals; no fifth colour (B2, H2c are ink); Barlow. |
| My critique of Codex's E1: the car basis said three times | **Partly** | Said once in the qualifier, but the chart label "antes del coche" re-opens the basis (R12-01). |
| My critique of Codex's E1: two equal footer actions | **Fixed** | E1 has no footer actions. |

### 3.3 Codex: `111` (Round 11)

| Defect | Status | Evidence |
|---|---|---|
| Surfaces: enclosure supplies the hierarchy | **Fixed** | No cards. |
| Emerald with six meanings | **Fixed** | Ink. |
| Inter 900 everywhere | **Fixed** | Barlow. |
| Button-shaped badge chip | **Fixed** | Docked plate (C1). Residual risk of resembling the host's black controls is evidence-only (§6). |
| Selectors accumulate before the answer | **Fixed** | F1 and F2: one context row. |
| Boxed row icons; subtitle on every row | **Fixed** | G1, H4. |
| Charts look appended, with separate scope, instructions and navigation | **Partly** | Labels are now inside the plot, but the plot dominates the page and the two bases persist (R12-01). |
| Copy repeats provenance | **Fixed** | One row. |
| Dense upper halves, empty lower halves | **Partly** | Roots end in 150–250dp of empty paper (B1, E3, F1). Acceptable, but D1 overflows into the nav (R12-10). |
| Black navigation glyphs | **Fixed** | `currentColor` (B1, N1). |
| F2/F3 split `78` from `%` | **Fixed** | F2, F3. |
| D3b separates the minus from its amount | **Fixed** | `−$4,071` (D3b). |
| E1 squeezes "Cómo se calculó" | **Fixed** | E1. |
| Host: grid and three white roads | **Partly** | C host is good; A1 is not (R12-05). |
| Ten worst: C1 green confirmation button | **Fixed** | C1. |
| Ten worst: C7 232dp | **Fixed** | 200dp (C7). |
| Ten worst: B1 three containers | **Fixed** | B1. |
| Ten worst: B3 centred onboarding composition | **Fixed** | Left-aligned home (B3). |
| Ten worst: A2 boxed icons | **Fixed** | A2. |
| Ten worst: A5 cards and stepper furniture | **Fixed** | A5. |
| Ten worst: E1 competing modules | **Partly** | The chart competes with the hero (R12-01). |
| Ten worst: E4 paragraphs | **Fixed** | E4. |
| Ten worst: F2 selection layers | **Fixed** | F2. |
| Ten worst: H5b buried result | **Fixed** | Two-column ledger with the result row "Te quedaron — · faltan los km de 6 viajes" (H5b). |
| Hard-fail table: G3 repeated field/card blocks | **Fixed** | G3 rows. |
| Criterion 8: skeleton and footer defects | **Fixed** | E7 flat skeleton; E2 flat placeholders. |

### 3.4 Codex: `113` (Round 11b)

| Defect | Status | Evidence |
|---|---|---|
| Opus moved the numeral | **Fixed** | C1–C7. |
| Amarillo "straddles" one line (two thresholds exist) | **Fixed** | Lane between `$8.00/km` and `$6.00/km` (C2). |
| A luminance ratio is not a detection result | **Stands** | Evidence-only (§6). |
| "Only Kompara has a personal mínimo" | **Accepted** | The signature is argued on form (§2, criterion 9). |
| E1 provenance moved off the root | **Fixed** | E1. |
| D1 timeline dropped the shift ledger | **Fixed** | D1. |
| F2: `78 %` describes the participating group, not all Uber drivers in CDMX | **Partly** | F2 shows `Datos · 1,240 conductores` one row below. H3, the shared card, drops the cohort size entirely (R12-06). |
| H2b: banked days not only "cuando canceles" | **Fixed** | Exact synthesis line (H2b). |
| `b1-variants`: restore B1 strings; remove recent-offer rates | **Fixed** | B1. |
| Host: add top controls and a taller offer card | **Controls fixed; taller offer not fixed** | The bench card starts at ≈ y 340; the mock's at ≈ y 548 (R12-04). |
| Exposed corners 2dp, subtle keyline, no glow | **Rendered as asked; diverges from spec** | Frames use 2dp; spec line 351 and the `.chip` CSS say 6 (R12-12). N8 shows the keyline without glow. |

## 4. Remaining defects, ranked by damage

| ID | Frame(s) | Severity | Defect | Exact evidence | Concrete fix |
|---|---|---|---|---|---|
| **R12-01** | E1, E7b, E8, E3, E3c, N4, N5 | **High** | Ganancias carries the signature in name only, and its chart out-shouts the hero. The "raya" is a $0 baseline every chart has. The filled bars are the heaviest mass on the page. E1's bars use a different basis ("antes del coche") from its hero ("ya sin coche"), and E3's bars use the hero's basis. | My pixel count of luminance contrast in 2× renders: E1 chart **14,614** vs hero **2,540** (5.8×). N4: 7,232 vs 2,331 (3.1×). N5: 7,965 vs 2,446 (3.3×). On N4 and N5 the `#A8A7A1` bars are the largest light object on a `#121211` page, against the system's own rule that at night only the verdict should emit light. E1 bars sum to $6,480 while the hero is $4,980, reconciled only by a 13sp "antes del coche" at the plot's top-right. | **Make the raya the car cost.** On E1, E7b and N4, draw a 2dp ink line across the day bars at the daily car share, $1,500 ÷ 7 = **$214.29** (15.5 % of the $1,380 scale), captioned at its right end "Lo del coche · $214 por día" (13sp). The hero is then exactly the ink above the line: Σ(day − 214.29) = 6,479.6 − 1,500 = **4,979.6 → $4,980**. On E8 the line sits at 28 % of the $762 Monday bar, and $762 − 214 = **$548** is the hero. Fill each bar below the line in `--rule` (night `#2C2C29`) and above it in `--ink-2` (night `--ink-3 #74736E`). Use a ≤ 72dp plot. Delete the "antes del coche" label. Keep the `.spark` heights so the checker still passes. On E3 and E3c, keep the $0 line with the same lighter fill. |
| **R12-02** | G2, A1 | **High** | The lane is never taught, and never tied to the two limits that define it. | G2 "Tu semáforo": the preview chip floats top-right with no connection to the `$8.50 /km` stepper. "Rojo debajo de $6.00/km" is buried in "Más ajustes". No lane is drawn. A1's legend is one sentence for one state ("Color arriba: llega a tu mínimo.") behind "Ver otra oferta". | **G2:** under the preview chip, draw the lane at 1:1, full width. Label the upper rule at its right end "Verde desde $8.50/km" and the lower rule "Rojo debajo de $6.00/km". Each label selects which limit the stepper edits. The preview's lit zone follows the values. Keep "Volver al valor inicial ($8.00/km)". **A1:** replace "Ver otra oferta" with a row of the four marks [V] [A] [R] [G] (48dp targets, the current one in ink, the others ink-3). Tapping one swaps the demo chip and the one-line legend. Four states are taught, with no extra paragraph. |
| **R12-03** | F1, F2, F4, F5 | **Medium** | Comparar's raya reads as a heavier table divider, not as "the reference". The spec's ink mark for Comparar (spec line 214) is never drawn. | F2: a 2dp rule sits between the "Tú $149/viaje" row and a padded "Promedio $131/viaje" row, so the eye reads a two-row table. F1 draws the identical rule between DiDi and Uber. | Hang the reference on the line. Its caption sits 4dp under the 2dp rule as one 13sp line, value right-aligned on the same baseline, no row padding, no hairline below ("Promedio · $131/viaje"; F1 "Uber · $9.13/km"; F4 "Semana anterior · $107/viaje"). Lead the Tú/DiDi row with the ink mark (top filled), so Comparar speaks the chip's grammar without a verdict colour. |
| **R12-04** | C1–C7 (missing frames) | **Medium** | One host geometry only. The safe band looks generous, and the placement, growth and suppression rules are specified but never shown. There is no night amarillo or gris. | Every C frame has the offer card top at ≈ y 548. The Uber bench card starts at ≈ y 340 (`uber-offer-card-lightbox.jpg`). The 1.3× growth (200 × 110) and "suppressed when no band fits" exist only in prose (spec line 389). N8 is verde only. | Add **C8**, a tall offer: card from y 340, band 136–324, chip still at 144–232. Add **C9**, a navigation banner at y 84–180: the chip drops to y 196. Add **C10**, 1.3× font: a 200 × 110 plate, K dropped if needed. Add **C11**, no band fits: no chip, and the offer still appears in Tu día. Add **N9/N10** for C2 and C4 on the dark host. |
| **R12-05** | A1 | **Medium** | The first screen a driver sees uses the retired tile-grid host. | Rounded blue-grey tiles with white gutters, no street labels, no pickup dot, and a card with no Aceptar. C1's host has streets, labels, the control row, route, pickup and the Viaje card. | Reuse C1's host, cropped to y 84–440 with the top of the Viaje card (fare `$148.00`) as the demo's lower edge. |
| **R12-06** | H3 | **Medium** | The share card keeps the raya and its "Promedio" caption with nothing above them. The shared claim also leaves the app without its denominator. | With "Ocultar montos y horas" on (the state drawn), a 2dp rule with "Promedio" under it floats below "antes de gastos" with no figure. "más que al 78 % de los conductores de Uber en CDMX" carries no cohort size. F2 has `1,240 conductores`; the card does not. | When amounts are hidden, drop the rule and caption. Add a 13sp line "Entre 1,240 conductores de Uber en CDMX con semana confirmada". Keep "Viajes 34" and "Semana confirmada con mi resumen". |
| **R12-07** | F5 | **Medium** | "Sin confirmar" names the wrong number. | It sits in the Promedio value slot, which reads as "the city average is unconfirmed". What is unconfirmed is the driver's own week (`$161/viaje`, from the reader). | Tú row: `$161/viaje`, with the 13sp sub "sin confirmar · según el lector". The dashed line's caption becomes "Promedio · al confirmar tu semana". |
| **R12-08** | E4 (and E5) | **Medium** | The non-representation statement that both reviewers kept in R11b is gone. | No "representa" anywhere in `index.html`. E4 says only "Tu vigencia la confirma el IMSS, no Kompara.", which disclaims verification, not representation. The spec (line 292) moved non-representation to the store listing and privacy notice. | Add a 13sp ink-2 line under the PDF row: "Kompara no representa al IMSS." B-038 decides the wording. |
| **R12-09** | B1, B6, N1 | **Low** | The section header and its action look identical, and the header crowds the rule. | "Últimas ofertas" and "Ver tu día" are both 16sp 600 ink (inline override in `part-b.html` line 13). There is no chevron, against the spec's "Ver tu día ›". The header baseline sits ≈ 5dp above the first rule. | Header: 13sp 600 ink-2 overline. Action: "Ver tu día ›". 12dp from the header to the first row. |
| **R12-10** | D1, N3 | **Low** | The last offer row is cut in half by the nav with no fade. The caption claims "nothing is cut to fit". | The 11:48 gris row is clipped at the nav's top hairline. | A 24dp paper-to-transparent fade above the nav, or tighten the shift-ledger gap (32 → 20dp) so the first viewport ends on a whole row. |
| **R12-11** | C4 vs spec | **Low** | The chip and spec give different strings for the same state. | C4 zone B: "falta la tarifa". Spec table (lines 359, 376): "faltó la tarifa". The rationale exists only in the C4 caption. | Pick one and write it in the spec table (I'd keep the present tense on the live chip), so Android gets one string. |
| **R12-12** | C1–C7, C6 | **Low** | Corner radius disagrees between frames and spec/CSS. | Every frame overrides `.chip` to `border-radius:2px 0 0 2px`. `base-v3.css` line 173 and spec line 351 say 6. | Choose 2dp (it reads as frame, not card) and update the spec and CSS. |
| **R12-13** | B4 | **Low** | The secondary floats centred above the primary. Every non-root puts it below. | "Ajustar" at y ≈ 665dp, above "Sí, así es". | On roots, make it a content row: "Ajustar mis costos ›" under the cost line. |
| **R12-14** | B1, E1, F1 | **Low** | Three tab roots use three top-bar grammars: a title; a title plus a tab row; tabs as the title. I proposed the third in Round 11, and rendered side by side it reads as inconsistent. | B1 "Hoy"; E1 "Ganancias" with "Semana / Mes" below; F1 "Tus apps / Tu ciudad" at 20sp, no title. | Give Comparar a "Comparar" title with a 16sp tab row (as Ganancias), or drop "Ganancias" for tabs-as-title. One grammar. |
| **R12-15** | H6 | **Low** | One metric appears in two forms. | "9 viajes · 8 h en turno" against D1 "8 h 10 min". | "8 h 10 min en turno", in the spec and in H6. |
| **R12-16** | `fixture.json` | **Low** | Stale internal note. | `rojo_negativo._note`: "chip grows to 232dp", which is retired. | Update the note. |
| **R12-17** | E4, E5 | **Low** | Two "number" sizes. | `.t-num` is 34px. The chip and the spec's number step are 32. | 32/36. |
| **R12-18** | C3, C7 | **Low** | The route geometry is identical for a 2.0 km and a 5.2 km pickup. | The pickup dot sits at the same map spot in C1 and C3. | Move C3/C7's pickup one block farther along the route, or note "same map" in the caption. |
| **R12-19** | H3 vs F2 | **Low** | `78 %` is drawn two ways. | H3 sets `%` at 32px. F2 and N7 set it at 56. | One treatment. I'd keep F2's. |

## 5. The one-second read (windshield mount, about 60 cm)

- **B1:** "$762 hoy, y el lector lee": the 56sp hero and the solid segment. The three marks give the last offers' outcomes as colour position (top · bottom · dashed). This screen is read parked.
- **E1:** "$4,980 la semana pasada", but the eye lands on the bar block first (5.8× the hero's contrast mass). The Friday spike reads second.
- **F1:** "DiDi me deja $1.21 más por km": one number, one phrase.
- **C1:** green on top of a dark plate means "pasa tu mínimo"; `$9.49/km` confirms in the same place as every state.
- **C2:** a bright stripe through the middle means "entre tus límites, piénsalo". It is the smallest lit area of the four (18 %).
- **C3 (and C7):** red at the bottom means "abajo de tu rojo"; `$2.20/km` (`−$1.05/km`) confirms.
- **C4:** a dark plate with a dashed lane and no colour means "no hay veredicto, decide tú".
- **C5:** identical to C4 at a glance, which is correct (no verdict). Only reading separates "Falta tiempo · sin semáforo". C6, by contrast, is visibly half-height with no lane.

## 6. What only evidence can settle

1. **The mounted glance test** (12 CDMX drivers, at least 3 colour-vision deficient, about 25°, 300 ms and 1 s, day and night). Is the 16dp amarillo lane enough? Does top-versus-bottom separate verde from rojo for deuteranopes? Pass: ≥ 95 % identification, zero gris-as-rojo, C6 never read as a verdict.
2. **Control resemblance.** The plate shares the host's black-control dialect. Do drivers try to tap it, or read it as Uber's own badge?
3. **Comprehension after A1**, with no instruction: "¿Qué quiere decir el color en medio?" Pass: ≥ 8 of 10.
4. **An occlusion audit on real Uber and DiDi layouts** (tall offers, navigation banners, 130 % font). Record how often the chip is suppressed, not only whether it overlaps.
5. **Night glare** of the paper and bar surfaces in a dark car, and on-device legibility of Barlow `tnum` and 13sp text.
6. **Legal B-038** on A2, E4 and H2b; **money comprehension** of E1's hero against its bars and against "Tu resumen de Uber dice un poco más".

## 7. Verdict: **NOT_YET**

| Rubric | Scores | Total | Bar |
|---|---|---|---|
| Structural (`02`) | 9 · 9 · 9 · 9 · 9 · 9 · 8 · 9 | **71 / 80** | Met: all ≥ 8, total ≥ 68 |
| Craft (`03`) | 9: 8 · 10: 8 · 11: 8 · 12: 9 | **33 / 40** | **Missed by one point:** all ≥ 8, total < 34 |

**Hard fails:** none present on either rubric.

This is a different product from Round 11. The template is gone, and the chip is a real, ownable, physics-first object. It fails only because the signature stops at Tu día.

**The shortest path to PASS:**

1. **R12-01, the car-cost raya.** Ganancias then draws a line that means something personal, the hero becomes the ink above it, the two-basis confusion disappears and the chart stops shouting. This moves criterion 9 and, with the lighter night fill, criterion 11.
2. **R12-02, teach the lane where it is set.** G2 draws the two limits; A1 offers the four marks.
3. **R12-03, hang Comparar's reference on the line** and add the ink mark.
4. **R12-05, R12-06, R12-09 and R12-10**, the craft clean-up: A1's host, H3's orphan, the B1 header, the D1 fade.

Items 1–3 take criterion 9 to 9, which gives **34/40**. Items 1 and 4 together take criterion 11 to 9, which gives **35/40**. Criterion 10 stays at 8 until the mounted test, by the rule I set in Round 11. Re-run `check-fixture.py` after the bar change: the `.spark` heights must not move.
