# Round 13, Opus 5.5: scoring v3 on both rubrics

**What I reviewed.** I read the scoring brief (`r13-brief-resolved.md` / `150`), the change brief (`140`), both rubrics (`02`, `03`), my Round 12 report (`132`), Codex's (`131`), the Round 13 CSS block in `base-v3.css`, `fixture.json`, the frame captions, and the spec passages that changed (qualifier rule, §9g/§9h reconciliation, chip placement and suppression, C4 tense). With the Read tool I looked at all nine contact sheets (A–H, N). I opened at full 2× size A1, B1, B3, B4, C1, C2, C4, C8, C9, C10, C11, D1, D3, E1, E3, E4, E5, E7b, E8, F1, F2, F4, F5, G2, G6, H3, H5b, H6, N4, N9 and N10, plus crops of H3's standing sentence and A1's marks row. I did not open Codex's Round 13 output.

**Renders are current.** All 80 frame fragments in `index.html` are byte-identical to the fragments inside `png-all/<id>.html`. `index.html` equals `cat _head.html part-*.html`, and the parts were last written at 09:38, before the 09:39–09:40 render.

**Checker.** `python3 check-fixture.py` prints `checked 154 figures in 50 frames, 23 spec strings, 1 date rule, 46 stale patterns, 6 caption lints, 5 bar charts → OK`.

**Figures I re-derived by hand from `fixture.json`** (cost per km c = 24.5 ÷ 12 + 0.80 = **2.841667**). Rendered positions are pixel scans of the 2× PNGs.

| # | Figure | Arithmetic | Rendered | ✓ |
|---|---|---|---|---|
| 1 | **E1 car line** | Car share $1,500 ÷ 7 = **$214.29/day**. Day nets before the car: L 1,050 − 85.0c = 808.46 · M 950.38 · M 909.76 · J 1,041.39 · **V 1,900 − 183.0c = 1,379.97** · S 891.08 · D 498.55. The line sits at 214.29 ÷ 1,379.97 = **15.53 %** of the V scale. | Plot base y 1036, V top y 908 (128 px = 64dp). The 2dp line spans y 1014–1017: 14.1–17.2 % of the plot, centre 16.0 %, which contains 15.53 % (within 1 px). Bar heights measure 59.4 / 68.8 / 65.6 / 75.0 / 100 / 64.8 / 35.9 %, against 58.6 / 68.9 / 65.9 / 75.5 / 100 / 64.6 / 36.1. | ✓ |
| 2 | **E1 hero = ink above the line** | Σ days = 6,479.59 (week check: 8,400 − 675.8c = 6,479.60). Σ(day − 214.29) = 6,479.59 − 1,500 = **4,979.59 → $4,980**. The identity holds because the smallest day (D, 498.55) clears 214.29. The L bar's split is 214.29 ÷ 808.46 = 26.5 % (brief: 26 %). | `$4,980`. On every bar the light `--rule` fill runs from the base to the line and the ink-3 fill above it. | ✓ |
| 3 | **E8** | B1's day so far: shift 1 is 831 − 64.3c = 648.28, plus the 14:32 offer 148 − 12c = 113.90, gives 762.18. The line is at 214.29 ÷ 762.18 = **28.1 %**. Hero: 762.18 − 214.29 = 547.89 → **$548**. | The line spans y 886–889 over a 128 px bar: 26.6–29.7 %, centre 28.5 %. `$548`. | ✓ |
| 4 | E3 / E3d month | 1–4 oct: 2,950 − 237.8c − 4 × 214.29 = 1,417.11 · 5–11: 8,100 − 652.1c − 1,500 = 4,746.95 · 12–18: 4,979.60 · 19 today: 1,239.91 − 214.29 = 1,025.62. Sum 12,169.28; rounded rows sum to 12,170. | `$12,169`, the `$12,170` note, and bars 28 / 95.3 / 100 / 21 % (from 28.5 / 95.3 / 100 / 20.6). | ✓ |
| 5 | F1 | Uber (5,028 − 420c) ÷ 420 = 9.130 · DiDi (3,372 − 255.8c) ÷ 255.8 = 10.341 · difference 1.211 | `$9.13/km`, `$10.34/km`, `$1.21`, three figures | ✓ |
| 6 | F2 | 5,062 ÷ 34 = 148.88. 70 + 10 × (148.88 − 142) ÷ 9 = 77.65. Mean 162,440 ÷ 1,240 = 131.0 | `78 %`, `$149`, `$131` | ✓ |
| 7 | F4 / F5 | DiDi 3,372 ÷ 28 = 120.43 · 3,100 ÷ 29 = 106.90 · ratio 1.1266. Uber 5–11 oct: 5,000 ÷ 31 = 161.29 | `$120`, `$107`, `+13 %`, `$161/viaje` | ✓ |
| 8 | C8 / C11 host gross | 148 ÷ 12 = 12.33 · 62 ÷ 12.3 = 5.04 | `$12.33/km`, `$5.04/km` (the host's own grey line) | ✓ |
| 9 | G2 preview | 9.49 < 9.50 (the edited verde limit) and ≥ 6.00 | The lane is lit amarillo | ✓ |
| 10 | H5b / D3 | 150.6c = 427.95 · 123.2c = 350.09; 1,590 − 350.09 = 1,239.91 | `−$428`, `−$350`, `$1,240` | ✓ |
| 11 | E5 reference line | 9,582 ÷ 11,852 = 80.8 % · 7,926 ÷ 11,852 = 66.9 % · 9,582 − 7,926 = 1,656 | Line at 82 %, DiDi bar at 67.4 %, `faltan $1,656` | ✓ |

**Contrast mass, hero versus chart.** I used the same script, the same boxes and the same 2× renders as Round 12, summing luminance contrast against the paper.

| Frame | Hero | Chart | Chart ÷ hero | Round 12 |
|---|---|---|---|---|
| E1 | 2,540 | 5,988 | **2.4×** | 5.8× |
| N4 | 2,331 | 2,007 | **0.86×** | 3.1× |
| N5 | 2,446 | 2,832 | **1.16×** | 3.3× |
| E8 | 1,926 | 2,215 | 1.15× | — |
| E3 | 2,657 | 9,134 | **3.4×** | not measured |

**Glance strip** (`/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/r13opus/glance13.png`). It covers C1–C5, N8, N9, N10, C9 and C10, each rendered three ways: in colour, as blurred greyscale, and with a blurred deuteranopia simulation. The four lightness patterns hold everywhere, now including night amarillo (N9), night gris (N10), the dropped dock (C9) and the 110dp plate (C10).

---

## 1. Scorecard A — structural (`02-rubric-uber-bar.md`)

| # | Criterion | Score | Evidence |
|---|---|---|---|
| 1 | One job per screen | **9** | Each root has one 56sp hero on the paper: B1 `$762`, D1 `$1,240`, E1 `$4,980`, E8 `$548`, E3 `$12,169`, F1 `$1.21`, F2 `78 %`. E1's chart fell from 5.8× to 2.4× the hero's contrast mass (N4 0.86×), so the number now wins. Not 10: E3's four 80dp week blocks still carry 3.4× the hero (R13-04). |
| 2 | One primary action | **10** | One 56dp ink verb at the thumb wherever an action exists (A1, A5, B2, B3, E4, F5, G2, H2b, H3, H5b). Reading roots carry none, and share is a top-bar glyph. The Round 12 exception is gone: B4's "Ajustar" is now the content row "Ajustar mis costos ›" under the cost line, and "Sí, son mis costos" stands alone. |
| 3 | Navigation that disappears | **10** | Three tabs plus a bare person glyph. The three roots now share one top-bar grammar: Hoy is a title, while Ganancias and Comparar are each a title over a 16sp text-tab row with a 24 × 2dp underline and 48dp targets (B1, E1, F1, N6). Drill-ins keep their parent tab lit (D1), sheets keep the root behind them (E1b, D3), and H flows close with ✕. |
| 4 | Numbers discipline | **9** | 154 asserted figures pass, and I re-derived eleven families by hand, including both car lines. E1's two bases are gone: bars and hero are one basis, and the hero is the ink above the line. H6 now says "8 h 10 min". Barlow `tnum` throughout, 32/36 for every compact figure (E4, E5, A7). Not 10: E1's first viewport carries four figures (`$4,980`, `62`, `−$1,500`, `$214`) and states one cost twice (R13-03). F2 also carries four. |
| 5 | Copy | **9** | `tú`, sentence case, one qualifier row per hero said once. The population is now honest everywhere ("que compartieron su resumen", F2/F3/H3/N7). Also fixed: E4/E5 "Kompara no lo representa", G6 "Enviar conteos de lectura · Sin cifras de tus viajes", and H5b's consequence line. Not 10: H3 says "Uber" twice in one sentence (R13-05), E1's qualifier wraps to two lines (R13-03), and G6's "Cuenta y lo encendido" is cryptic. |
| 6 | States are designed | **9** | Everything from Round 12, plus the four placement states that were only prose: tall offer (C8), navigation banner (C9), 1.3× font (C10), suppressed (C11), and night amarillo and gris (N9, N10). F5 now puts "sin confirmar · según el lector" on the driver's own number. Not 10: suppression leaves no trace the driver can read. C11's caption logs the offer in Tu día with a rojo mark the driver never saw (R13-01). |
| 7 | Time-to-value | **8** | A1 now teaches all four states on C1's real host before any ask (`.marks4`, 48dp targets). Every later step is one ask (A2–A6), and the WhatsApp number is optional. **Held at 8 by my own rule:** install-to-first-live-verdict is unmeasured. |
| 8 | Consistency & feel | **9** | One chip geometry everywhere: A1's plate measures 400 × 176 px at 2×, identical to C1 and C8, with 2dp corners. The reference line has one anatomy: E1's car line, E5's "Mínimo 2026" and Comparar's hung caption. Status segments and the ink primary are shared. Not 10: Comparar's Tu ciudad tab uses two selector grammars (F2/F3/N7 inline dotted words; F4/F5 a context row, R13-02). H3 breaks the standing sentence differently from F2 (R13-05). E4 has a ghost band between two hairlines (R13-08). E3's bars are 80dp wide against Semana's 30dp (R13-04). |
| | **Total** | **73 / 80** | Every criterion is ≥ 8. **Structural bar met.** |

## 2. Scorecard B — craft (`03-rubric-r11-craft.md`)

| # | Criterion | Score | Evidence |
|---|---|---|---|
| 9 | Signature idea | **9** | **The signature no longer stops at Tu día.** One sentence now describes the whole product: *Kompara draws your line, and shows which side of it you are on.* The chip is a dark plate whose colour sits above, inside or below the lane between your two limits (C1–C5). Hoy and Tu día log that verdict as the mark (B1, D1). Ganancias draws **your car's daily cost** as the 2dp raya: each bar is light below it and ink above it, so the hero is literally the ink above your line (E1, E7b, E8, N4; I checked the identity in figure 2). IMSS uses the same anatomy for the legal minimum (E5). Comparar hangs the reference under the line with your row above it, led by the chip's own mark in ink (F1, F2, F4). The unconfirmed state uses the chip's dashed gris rules at page scale (F5). The driver **sets** the lane on the chip itself: G2 widens the plate, names both limits on it, and shows a $9.49 offer falling into amarillo once verde is raised to $9.50. The demo teaches all four positions (A1). A driver could draw it, and nothing in the bench folder works this way. **Why not 10:** <br>• E3 (Mes) silently turns the same 2dp line into a $0 axis on the tab switch. <br>• The share card, the one artefact that leaves the app, carries no line in its default privacy-on state (R13-06). <br>• The Comparar mark (top-filled = above the reference) is a subtle reuse that drivers may not decode. <br>• In form, the Ganancias line is a conventional target line; its ownership comes from its meaning. |
| 10 | Glance physics | **8** | The static evidence is stronger than in Round 12: <br>• Placement is drawn for the tall card (C8: the band is 144–297 and the plate keeps 144–232), the turn banner (C9: the plate drops to 196–284) and 1.3× font (C10: 200 × 110, zones 47/16/47, 42sp, K kept). <br>• C8 puts the host's grey gross `$12.33/km` beside the chip's `$9.49/km neto aprox.` in a different face, size and place. <br>• The glance strip shows four lightness patterns at night and in the new placements, and position still separates verde from rojo under deuteranopia. <br>**Held at 8, by my Round 11 rule:** nothing above 8 without the mounted test. Open risks: <br>• Amarillo is still the smallest lit mass. <br>• C9 stacks the black plate 16dp under the host's black banner (R13-09). <br>• Suppression frequency is unknown (C11). |
| 11 | Craft | **9** | Round 12's five craft defects are all closed: <br>• E1's bars are ink-3 over `--rule` and no longer out-shout the hero (2.4×, N4 0.86×). <br>• A1 uses C1's real host. <br>• H3's orphan rule is gone. <br>• B1's header and action are distinct (16/600 ink title; 16/400 ink-2 "Ver tu día ›"). <br>• D1 ends on a 24dp paper fade. <br>New work is finished to the same standard: <br>• G2 is one plate instead of a preview plus a strip, with no text shadows. <br>• The C8–C11 host also scales and re-frames (C10's card rises; C9's map re-centres on the puck). <br>• E5 adopts the car line's caption column. <br>Beside the Uber offer-card benchmark it reads as one team's work. **Why not 10:** <br>• the split selector grammar on Tu ciudad (R13-02) <br>• H3's run-in "al" (R13-05) <br>• E4's ghost band (R13-08) <br>• E3's block weight (R13-04) <br>• dead 6px chip radii still in `base-v3.css` (R13-10) |
| 12 | Radical simplicity | **9** | Each screen fits one sentence. G2 lost a duplicate object, the "Verde desde" overline and the empty "Más ajustes" row. F1 lost its trip count. E1 lost the "antes del coche" tag because the chart now shares the hero's basis. Not 10: <br>• E1 now says the car cost twice: "(−$1,500)" in the qualifier and "$214 por día" on the line (R13-03). <br>• F2's standing sentence runs four lines with two embedded selectors. <br>• H5b is a sanctioned 69-word exception. |
| | **Total** | **35 / 40** | Every criterion is ≥ 8. **Craft bar met.** |

### Hard-fail check

| Rubric | Hard fail | Status | Frame evidence |
|---|---|---|---|
| 02 | Auto-action proposed | **Absent** | The chip is inert (C1–C11, N8–N10). A2 says "nunca acepta ni rechaza". C8/C11's "Rechazar" is the host's. |
| 02 | Platform logos | **Absent** | The host is unbranded. "Uber" and "DiDi" appear only as words (E1, F1, H3). The Tailwind greys (`#1f2937`, `#6b7280`, `#e5e7eb`) appear only inside the host mock's map and route. |
| 02 | A screen that fails the 1-second glance test | **Absent on static evidence** | Four position-coded lightness patterns survive greyscale, blur and deuteranopia, by day, by night and in C9/C10 (`glance13.png`). C11 shows no chip by design. Unmeasured on a mount. |
| 02 | A paywall that blanks the free experience | **Absent** | E2 keeps `$4,747` and its trip count. F3 keeps the standing, the mark and `$149/viaje`, and locks only the average with a bare lock. |
| 02 | An onboarding step with more than one ask | **Absent** | A1's marks are a demo selector, not an ask. A2 accept · A3 accessibility · A4 battery · A5 one stepper · A6 optional. |
| 03 | Chip is a rounded coloured rectangle with a number and a glyph | **Absent** | An ink plate with colour in one zone, 2dp exposed corners and no glyph (C1–C11). G2's widened plate follows the same rules. |
| 03 | A list of same-weight cards | **Absent** | 0 `class="card"` in `index.html`. Every list is rows. H3's single share card is the artefact preview. |
| 03 | A "K" square as the brand | **Absent** | An unboxed verde K: the wordmark initial on A1, plus H3, H6 and the chip at 60 %. |
| 03 | A Tailwind-default palette | **Absent** | Kompara's tokens are paper/ink (`#F5F4EF`, `#111110`, `#5C5B57`, `#8A8984`, `#DCDAD2`) with the reserved verdict triple. Tailwind hexes appear only in the third-party host mock. |

## 3. Round 12 defects: status

### 3.1 Mine (`132`)

| ID | Defect | Status | Evidence now |
|---|---|---|---|
| R12-01 | Ganancias carries the signature in name only; the chart out-shouts the hero; two bases | **Fixed** | E1/E7b/N4: a 2dp ink line at $214.29 (15.5 %, measured 16.0 % centre) with the "Lo del coche / $214 por día" caption in its own column, two-tone bars (`--rule` below, ink-3 above), and the hero = ink above the line. E8: line at 28 % (measured 28.5 %), `$548`. The "antes del coche" tag is deleted. Contrast ratio 5.8× → 2.4× (N4 3.1× → 0.86×, N5 3.3× → 1.16×). Max bar 64dp. Residual: E3 at 3.4× (R13-04). |
| R12-02 | The lane is never taught; G2 not tied to the limits; A1 teaches one state | **Fixed** | G2: one widened plate, "Verde desde $9.50/km" (selected, underlined) and "Rojo debajo de $6.00/km" as the stepper's selector, with the preview's lit zone following the value (amarillo for $9.49). A1: four marks as 48dp targets under the one-line legend. |
| R12-03 | Comparar's raya reads as a table divider; no ink mark | **Fixed** | F1/F2/F4: the reference is a 13sp caption hung 4dp under the 2dp line, with value on the same baseline and no row padding. The Tú/DiDi row leads with the ink mark (top filled). F5 uses the dashed line. |
| R12-04 | One host geometry; growth and suppression not drawn; no night amarillo or gris | **Fixed** | C8 (card top y≈313; band 144–297), C9 (chip at 196–284 under a 96dp banner), C10 (200 × 110), C11 (53dp band, suppressed), N9, N10. |
| R12-05 | A1 uses the retired tile grid | **Fixed** | C1's SVG host, control row and Viaje card; the plate is pixel-identical to C1's. |
| R12-06 | H3 orphan raya; no denominator | **Fixed** | No rule while amounts are hidden; "1,240 conductores · antes de gastos". |
| R12-07 | F5 "Sin confirmar" on the wrong number | **Fixed** | Tú `$161/viaje` with "sin confirmar · según el lector"; dashed line captioned "Promedio · al confirmar tu semana". |
| R12-08 | E4 lost the non-representation line | **Fixed** | E4/E5: "El IMSS confirma tu vigencia. Kompara no lo representa." |
| R12-09 | B1 header and action identical; header crowds the rule | **Fixed** | 16/600 ink title vs 16/400 ink-2 "Ver tu día ›" with a chevron; ≈16dp from the text to the first rule (B1, B6, N1). |
| R12-10 | D1's last row cut by the nav | **Fixed** | A 24dp paper fade above the nav (D1, N3). |
| R12-11 | C4 vs spec tense | **Fixed** | The spec's chip row says the live chip uses "falta la tarifa" and the log uses "faltó". |
| R12-12 | Corner radius disagrees between frames and spec/CSS | **Fixed in render and spec; partly in source** | Every plate renders 2dp, and the spec says 2dp. `base-v3.css` lines 173/188 still declare 6px, overridden at line 282 (R13-10). |
| R12-13 | B4 secondary floats above the primary | **Fixed** | "Ajustar mis costos ›" content row. |
| R12-14 | Three top-bar grammars | **Fixed** | Comparar has a title over a 16sp tab row, like Ganancias (F1–F5, N6, N7). |
| R12-15 | H6 "8 h" | **Fixed** | "9 viajes · 8 h 10 min en turno". |
| R12-16 | Stale `_note` | **Fixed** | `rojo_negativo._note` now says the 232dp step was retired. |
| R12-17 | Two number sizes | **Fixed** | `.t-num` 32/36, `.otp i` 32. E4 digits measure 45 px at 2× (= C1's chip digits). |
| R12-18 | C3/C7 route identical for different pickups | **Fixed (by caption)** | "Same map geometry as C1; the pickup distance differs in the card, not on the map". |
| R12-19 | `78 %` drawn two ways | **Partly** | `%` is now 56sp on F2 and H3. H3 sets "al" as a run-in on the number's baseline while F2 ends line 1 with "al", although the H3 caption says "drawn the same way" (R13-05). |

### 3.2 Codex (`131`)

| ID | Defect | Status | Evidence now |
|---|---|---|---|
| R12-01 | H5b omits the cross-month consequence; weak result emphasis | **Fixed** | The ink line "El PDF queda en la semana; octubre conserva lo leído del 1 al 4." sits directly above "Usar el PDF". "Te quedaron —" stands on the 2dp rule at 600 with "faltan los km de 6 viajes"; the partial cost names its coverage ("de 14 viajes · 150.6 km"). |
| R12-02 | Percentile overstates the population | **Fixed** | "…de los conductores de Uber en CDMX que compartieron su resumen" on F2, F3, N7 and H3. |
| R12-03 | E1 hero source implicit | **Fixed** | "Según el lector · ya sin gasolina, desgaste ni coche (−$1,500) · aprox." on E1, E7b and N4; "Según el lector…" on E2 and E8. |
| R12-04 | G6 telemetry label too narrow | **Fixed** | "Enviar conteos de lectura · Sin cifras de tus viajes"; the export row reads "Cuenta y lo encendido". |
| R12-05 | Targets below 48dp | **Fixed in the mock** | `.hero .qual{min-height:48px}`, `.tabs span{min-height:48px;min-width:48px}`, `.marks4 .mk` 48 × 48. Native hit regions are unverified. |
| R12-06 | H3 empty "Promedio" remnant | **Fixed** | Removed while amounts are hidden. |
| R12-07 | D1/N3 last row meets the nav | **Fixed** | Fade (D1, N3). |
| R12-08 | Preview and host chips differ | **Fixed** | No inline overrides. A1 = C1 = C8 plate geometry (measured). |
| R12-09 | F1 extra count | **Fixed** | "Datos ›"; F1 shows three figures. |
| R12-10 | Type steps 28/34 | **Fixed** | 32/36 everywhere. |
| R12-11 | Spec reconciliation overclaims | **Fixed** | 0 matches for "only this product" in the spec. §9g/R11-2 now argues the distinction by form, and the qualifier rule names "Según el lector". |

## 4. Remaining defects, ranked by damage

No high defect remains. Everything below is bounded.

| ID | Frame(s) | Severity | Defect | Evidence | Concrete fix |
|---|---|---|---|---|---|
| **R13-01** | C11 → D1/B1/D2 | **Medium** | Suppression leaves no trace the driver can read, and the log would show a verdict the driver never saw. | C11's caption: the offer "would still be logged in Tu día as 'Se fue sin tomar' with its rojo mark". The spec says "the reason is recorded", but no frame shows where. A driver who navigates daily sees the chip vanish on tall cards and has no way to tell that from "the reader is off". | Log the row with its mark plus a muted reason, in the "faltó la tarifa" grammar: `[R] 12:10 · Se fue sin tomar · sin semáforo en pantalla`. D2 for that offer gets one 13sp line: "No se mostró: la app de viajes llenaba la pantalla." G4 (Lector) gets a count: "Hoy, 1 oferta sin semáforo por espacio". Draw the D1 row variant. |
| **R13-02** | F2, F3, N7 vs F1, F4, F5, N6 | **Medium-low** | One tab, two selector grammars. | On Tu ciudad, F4 and F5 put scope in the 48dp context row ("DiDi · CDMX · Por viaje ▾"). F2/F3/N7 instead make "Por viaje" and "Uber en CDMX" dotted-underlined words inside the 20sp sentence, with the caret only after "CDMX". Confirming a week (F5 → F2) moves the selector, and the dotted "Por viaje" has no affordance. | Give F2/F3/N7 the same context row "Uber · CDMX · Por viaje ▾" and set the sentence plain. The 78 % moves 48dp down, and the viewport still ends above "Aportar al promedio". |
| **R13-03** | E1, E7b, N4 | **Low** | The car cost is said twice and the viewport carries four figures. | The qualifier wraps to two lines: "…ni coche (−$1,500) · aprox.". Under it, the line says "$214 por día". First-viewport figures are `$4,980` · `62` · `−$1,500` · `$214`. | Drop "(−$1,500)" so the qualifier is one line, as on E8. The weekly −$1,500 stays in E1b's ledger ("Lo del coche −$1,500") and D3's note. Move the contract's `−$1,500` assertion from E1 to E1b and update spec line 219. |
| **R13-04** | E3, E3c, N5 | **Low** | Mes bars outweigh the hero, and the line changes meaning on the tab switch. | Four 80dp-wide ink-3 blocks carry 9,134 contrast units against the hero's 2,657 (3.4×). Semana's bars are 30dp. The 2dp line means "lo del coche" on Semana and an uncaptioned $0 on Mes. | Draw the week bars at Semana's 30dp, centred in their slots. That is about 0.375× the area, giving an estimated ≈1.3× the hero. Keep the $0 line; the qualifier row already says "ya sin … coche". |
| **R13-05** | H3 | **Low** | The standing sentence breaks differently from F2, and the caption claims it does not. "Uber" appears twice. | H3: "Por viaje, Uber me pagó más que" / small "al" on the 78's baseline / "78 %". F2/N7: "…más que al" / "78 %". Line 1 is 279dp; adding " al" (≈22dp) still fits the card's 321dp text width. | Break as F2: "Por viaje, Uber me pagó más que al" / "78 %". Consider "…de sus conductores en CDMX que compartieron su resumen" to say Uber once. |
| **R13-06** | H3 | **Low** (path to 10 on 9) | The signature does not travel. | In the default privacy-on state the card has the K, the sentence and a status segment, but no line. | Draw position without amounts: "Tú" with the ink mark above the 2dp line and a "Promedio" caption under it, no figures. It is not an orphan, because something stands above the line, and it is the chip's grammar leaving the app. |
| **R13-07** | A1 | **Low** | The four marks may read as a static legend. | Only the shown state is put into words ("Color arriba: llega a tu mínimo."). The other three meanings require a tap that nothing invites. | On first view, step once through the four states (≈1.5 s each, atomic swaps, the underline moving), then rest on verde. No auto-step under reduce-motion. Taps still select. |
| **R13-08** | E4 | **Low** | Ghost band. | Hairlines at y 320 and 353dp enclose 33dp of nothing between the DiDi row and "¿Cómo funciona?". G6 separates groups with one hairline and air. | Drop the second list's top border (or the first list's bottom border) on E4, keeping the 32dp gap. |
| **R13-09** | C9 | **Low** (evidence) | Two ink slabs stack on the right half. | The black plate sits 16dp under the host's black 96dp turn banner. The plate's 28 % white keyline is invisible on the light map, so the chip may group with host chrome. | Put it in the mounted test (§6). If drivers group the two, raise the gap below a dark host element to 24dp. |
| **R13-10** | `base-v3.css` 173, 188 | **Nit** | Dead declarations. | `.chip` and `.lector-tab` still declare `border-radius:6px 0 0 6px`; line 282 overrides them to 2px. The Android port reads this CSS as a spec. | Change the original declarations to 2px and delete the override. |

## 5. The one-second read (windshield mount, about 60 cm; predictions, not measurements)

- **B1:** "Hoy me quedan **$762** y el lector lee." The 56sp hero, the solid segment, then three marks giving the last offers as colour position. Read parked.
- **E1:** "La semana pasada me quedaron **$4,980**." The hero now wins the first fixation (the chart is 2.4×, not 5.8×). The second fixation reads "the grey above the black line is what I kept; Friday was best."
- **F1:** "DiDi me deja **$1.21 más por km**." Then DiDi above the line, Uber hung under it.
- **C1:** "Color arriba: pasa mi mínimo, **$9.49/km**."
- **C2:** "Franja amarilla en medio: entre mis límites, **$7.16/km**." Still the smallest lit area.
- **C3:** "Color abajo: debajo de mi rojo, **$2.20/km**." Not "loses money"; C7 is the negative case.
- **C4:** "Sin color, rayas punteadas: no hay veredicto." "Falta la tarifa" needs reading.
- **C5:** Same as C4 at a glance, which is correct. "Falta tiempo · sin semáforo" needs reading.
- **C8:** Same as C1. The host's grey `$12.33/km` under the fare is smaller and in another face, so the eye takes the plate's `$9.49/km neto aprox.` as Kompara's.
- **C9:** "Franja en medio: piénsalo, **$7.16/km**." The plate sits under the black turn banner, and whether it reads as host chrome is the open question (R13-09).
- **C10:** Same as C1, bigger: a 110dp plate with the same three-zone pattern and the number in the same place.
- **C11:** Nothing from Kompara. The driver sees only the host's gross `$5.04/km`. Correct for safety, but silent (R13-01).

## 6. What only evidence can settle

1. **The mounted glance test** (12 CDMX drivers, at least 3 colour-vision deficient, about 25°, 300 ms and 1 s, day and night, including C9 and C10). Pass: ≥ 95 % identification, zero gris-as-rojo, C6 never read as a verdict, and the C9 plate not grouped with the banner. This alone can lift criterion 10.
2. **Suppression frequency** on real Uber and DiDi layouts (tall cards, banners, 1.3× font): how often C11 happens, and whether drivers read the missing chip as a failure.
3. **Comprehension:** after A1, without instruction, "¿Qué quiere decir el color en medio?" (≥ 8 of 10). On E1, "¿Qué es lo gris arriba de la raya?" Pass: "lo que me quedó".
4. **Install to first live verdict**, timed. This alone can lift criterion 7.
5. **Shipped rendering:** bundled Barlow `tnum`, 13sp legibility and native 48dp hit regions. Also legal sign-off B-038 on A2, E4 and H2b.

## 7. Verdict: **PASS**

| Rubric | Scores | Total | Bar |
|---|---|---|---|
| Structural (`02`) | 9 · 10 · 10 · 9 · 9 · 9 · 8 · 9 | **73 / 80** | Met: all ≥ 8, total ≥ 68 |
| Craft (`03`) | 9: 9 · 10: 8 · 11: 9 · 12: 9 | **35 / 40** | Met: all ≥ 8, total ≥ 34 |

**Hard fails:** none on either rubric.

The one point I withheld in Round 12 is earned. The line now means something personal on every surface: your floor on the chip, your car on Ganancias, the legal minimum on IMSS, the city on Comparar, and your two limits in G2, where you set them. The hero on Ganancias is literally the ink above your line. Round 12's craft list is closed, and the new placement frames show the chip surviving real host layouts.

**Before build, in order:**
1. R13-01: make suppression legible in the log.
2. R13-02: one selector grammar on Tu ciudad.
3. R13-03 and R13-05: say the car cost once, and break H3 like F2.
4. R13-04 and R13-08: E3's bar width and E4's ghost band.
5. Run the mounted test (§6.1).

Criterion 10 stays at 8, and criterion 7 at 8, until that evidence exists. Re-run `check-fixture.py` after R13-03: the `−$1,500` assertion moves from E1 to E1b.
