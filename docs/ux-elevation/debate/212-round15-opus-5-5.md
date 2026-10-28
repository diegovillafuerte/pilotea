# Round 15 — Opus 5.5

**What I reviewed:** the brief, `180` (Rounds 14b–16), my Round 14 report (`202`) and Codex's (`201`). I opened all nine sheets and these frames at full size: A1, B1, C1, C5, C7–C10, D1, E1, F1, G1, G2, G4, H3 and N8. I compared them with uber-2, uber-5 and uber-all. Every measurement below was taken from pixels in the 2× PNGs and is given in dp. I also read the Round 16 block of `base-v3.css`, `_regla-markup.html`, the C1, C8–C11, A1, B1, D1, E1, F1 and G4 captions, spec §6, §11 and §12, and the checker source. I ran a script over every `.regla` instance in `index.html`.

**Checker:** `checked 150 figures in 50 frames, 15 chips and 27 offer rows against their state phrase, 23 spec strings, 1 date rule, 46 stale patterns, 6 caption lints, 5 bar charts → OK`.

**Placement as rendered:**

| Frame | Plate | Host card top | Foot (spec: 16) |
|---|---|---|---|
| C1–C5, C7, C9 | 417–528.5 | 547.5 (captions and spec say 545) | **19** |
| N8–N10 | 417–528.5 | 547.5 | **19** |
| C10 | 311.4–456.5 | **469.0** (caption, markup and spec say 473) | **12.5** |
| C8 | x 12–252 × y 149–261 | 316 (caption says 313) | 55 |
| A1 | ends 442.5 | 459 | 16.5 |

- **C8 clearances:** the pickup dot (279–289) is 18dp below the plate, and the control row is 21dp above it.
- **C11:** the band between the banner (180) and the card (268) is 88dp, against 144 needed, so the chip is correctly suppressed.

**Host hierarchy:** on C1 the chip's digits have a 21dp cap height against 26.5dp for the host's `$148.00` (0.79). The rate now sits under the fare.

**Proportional bars:** I measured each bar's length against its figure.

| Frame | Bar ratio | Figure ratio |
|---|---|---|
| F1 | 0.878 | 0.883 |
| F2 | 0.878 | 0.879 |
| F4 | 0.888 | 0.888 |
| H3 | 0.879 | 0.879 |

E1's dashed car line sits at 15.5% of the best day's bar, which is correct. D1's timeline segments are on a true 08:00–18:00 scale (270/110/220 min, within 1px).

## 1. Scores

| # | Criterion | R14 | R15 | Why (not 10) |
|---|---|---|---|---|
| 1 | One job per screen | 10 | **10** | Unchanged. The overlay's reading order is now fixed: band, then phrase, then limits. |
| 2 | One primary action | 10 | **10** | Unchanged. G2 has Guardar plus a text reset; Hoy keeps its grey secondary. |
| 3 | Navigation | 10 | **10** | Three tabs. Hoy now has one route to Tu día (the best-hour row no longer taps through). |
| 4 | Numbers discipline | 9 | **9** | The chip went from 4 figures to 3 ($/h is off the overlay). Not 10: D1's new timeline puts 7 more figures in the first viewport (R15-08). E1 has 4 and F2 has 4. |
| 5 | Copy | 9 | **9** | "abajo" is fixed and the vocabulary is clean everywhere. Not 10: G4 leads with "Funcionando" on a warning screen (R15-07), "Tu resumen de Uber dice un poco más" is vague, and G6's "Cuenta y lo encendido" is still there. |
| 6 | States are designed | 9 | **9** | The dot rule, the gris accessible name and C5's figures are all done. Not 10: A1's demo hides the pickup pin (R15-05). D1's suppressed-offer row is still undrawn (deliberate, since the fixture has no such offer). |
| 7 | Time-to-value | 8 | **8** | Held by rule: there is still no timed install. |
| 8 | Consistency & feel | 9 | **9** | One threshold and no dead chip CSS. Not 10: the foot measures 12.5 to 19dp where the spec says 16 (R15-01). G2's tray mixes "$9.50" with "$6" (R15-06). Stale comments remain (R15-10). |
| | **Structural** | 74 | **74 / 80** | Every criterion ≥ 8. |
| 9 | Signature idea | 9 | **8** | **Down 1.** The record states the trade: distinctiveness fell from 7.5 to 5.8, and point-of-view votes went from 5/6 for the moving band to 0/6 for opus-r16a. The "above / between / below your two limits" idea now lives in a 14×12dp white marker at the tray's right end. The row marks still carry it across the app, as do G2 and E1's car line, but the chip itself is no longer the sketchable form. |
| 10 | Glance physics | 8 | **8** | Better on every channel: a fixed 56dp band, a 30dp solid cue, the position phrase, and a rate under the fare. Lineup 8 put it in the glance lead group (3.5 against 3.2–3.3). Held at 8 by rule (no mounted test) and by three open defects: C10's 12.5dp foot (R15-01), C9 covering the turn the banner announces (R15-02), and C1's pin 6–8dp off the plate (R15-03). |
| 11 | Craft | 9 | **9** | The plate is precise: even 5dp marker clearances, 2dp lines at 75% white, and consistent insets (the amarillo phrase ends 13.5dp from the edge). Not 10: R15-03, R15-06, and verde still has three meanings (the brand K, the reader dot, the verdict). |
| 12 | Radical simplicity | 9 | **9** | Not 10: the plate encodes one fact five ways (colour, cue, phrase, marker, and the rate against the limits), and every row repeats the phrase. This is accepted as colour-blind support. Once R14 data exists, the marker or the phrase is the first thing to cut. |
| | **Craft** | 35 | **34 / 40** | Every criterion ≥ 8. **There is no margin.** |

**Hard fails:** none on either rubric.

The closest call is "a rounded coloured rectangle with a number and a glyph". The v16 band, taken alone, matches those words literally: a coloured block, a big white ▲ and a bold number. It does not trigger the fail for three reasons:
- the plate has two zones, and the ink half draws the driver's own limits as labelled lines with a position marker;
- the words name a position, not a verdict;
- blind template votes were 1/6, against 6/6 for every generic chip.

The other hard fails also hold:
- no auto-action;
- no platform logos (only text names);
- E2 and F3 tease without blanking the free value;
- one ask per onboarding step.

## 2. My Round 14 items

| ID | Status | Evidence |
|---|---|---|
| R14-01 dot rule | **Done** | G1's Lector row and G4's head use the solid ink segment beside "Funcionando · batería restringida". The green dot appears only on B1, B6 and N1. Residue: R15-07. |
| R14-02 one threshold | **Partly** | Spec lines 391 and 730 and the C11 caption all say plate + 32 = 144; there is no "+ 24" anywhere. **Not done:** the true geometry. The captions and spec still give card tops of 545, 313, 265 and 473, but the renders show 547.5, 316, 268 and 469. `base-v3.css` line 313 still says "plate top 429". |
| R14-03 dead CSS and lede | **Done**, with residue | The `.chip` and `.lane-edit` rules are gone. The lede and README describe v16, the legend adds the reader dot, the night set reads N1–N10, and B4's button says "Sí, son mis costos". Residue is in R15-10. |
| R14-04 checker guards | **Done**, partly blind | Nine vocabulary bans and one markup ban exist (lines 114–117), and the aria-label phrase is checked on 15 chips and 27 rows. Gaps: the summary line still prints "46 stale patterns" because the new guards are not counted. The visible `.rg-p`, the cue and marker paths, and the rate-against-limits state are not asserted. My own script confirmed all 15 are consistent today. There is no ledger-sum check, so D3b passes (R15-04). |
| R14-05 C5 per-hour limits | **Done** | C5 shows "tu meta $200/h · tu mínimo $150/h", and `.rg-ph` starts the lines at 131. |
| R14-06 "abajo" | **Done** | G2 reads "Tu mínimo · rojo abajo de", and "debajo de tu mínimo" is banned. "verde desde" makes the ≥ boundary explicit. |
| R14-07 gris accessible name | **Done** | C4 and N10 carry `role="img"` with "Sin lectura: falta la tarifa"; C5 has "Falta tiempo: sin semáforo". |
| R14-08 hierarchy and test items | **Mostly done** | The rate is 30sp under the host's 36sp fare (caps 21 vs 26.5dp). C8 is off the pin. §11 R14 now measures how often drivers misread the host's figures as the chip's net rate. **Not done:** C9 (R15-02). |
| R14-09 ruler weight | **Done** | Lines are 2dp at 75% white, labels 15sp/600 at 72%, and the mounted test lists it. |
| R14-10 lineup 6 | **Done** | `180` has a "Round 6" section with the ◆ note. |

**Codex's three items:**
- **Chip hierarchy:** done.
- **C8 pin:** done (16–18dp clear).
- **C10 clearance: not done.** The plate still ends at 456.5 and the card starts at 469.0, a 12.5dp foot. The brief lists this as fixed. The frame placed the plate against a card top of 473, which the render does not have.

**New findings**

| ID | Frame | Sev. | Evidence | Fix |
|---|---|---|---|---|
| R15-01 | C10; all host frames | **Med** | C10's foot is 12.5dp, not 16 (the plate's inline `top:311.4px` assumes a card top of 473; the render has 469). In the standard frames the foot is 19dp, not 16. The invariant holds in none of them. | Set C10's top to 469 − 145.6 − 16 = **307.4**. Correct "473", "545", "313" and "265" in the captions, `_regla-markup.html` and spec 391/393/757, or change the host card to match them. Add a render-measured foot check to the checker. |
| R15-02 | C9 | **Med** | The puck heads east (➤) at y 507. The banner says "↰ Av. Insurgentes Sur". That left turn is at (217.5, 507), inside the plate's area (x 141–381 × y 417–529). So the plate hides the exact manoeuvre the banner announces, while the caption says "the first slot clears everything". | During navigation, add **the next manoeuvre on the active route** to the marker exclusions. C9 then takes slot (3), higher on the right at y ≈ 196–308, which clears the route (y ≥ 380). Correct the caption, or add this to §11 R14. |
| R15-03 | C1–C5, C7, N8–N10 | Low-med | The pickup dot (399–409) sits 8dp above the plate's top edge at 417, about 6dp from its white ring. The plate reads as a map callout hanging off the pin. C8 gives the same pin 16dp. | Specify one marker clearance (16dp, as C8 uses) and re-pan the standard host map. |
| R15-04 | D3b; F4 | Low | D3b, the screen that exists to show the math, prints $16,241 − $4,071 = **$12,169**, but those rows sum to $12,170. The caption claims "It adds up on screen". F4 shows $120 vs $107 as "+13 %", but the printed figures give +12.1% (the unrounded figures give 12.66%). | Put the rounding on the deduction (−$4,072), or carry E1b/E3d's "Redondeado una vez" note on the total. Compute F4's % from the printed figures. Assert ledger sums in the checker. |
| R15-05 | A1 | Low | The legend, which is also the state selector, teaches the row mark (three-zone bars). The chip beside it shows ▲ in a band. R14 then asks "¿Qué quiere decir el amarillo?" after A1. The map is also panned so "the pickup sits behind it" (caption), so the first frame a driver sees demonstrates the last-resort placement. | Put the chip's cue (▲ ◆ ▼ in its band colour) beside each phrase in the legend. Re-pan the slice so the pin shows, as C10 already does. |
| R15-06 | G2; `.regla` | Low | The tray reads "tu meta **$9.50**" next to "tu mínimo **$6**". The lines start at a fixed 104dp. "tu meta $9.50" ends at 96.5dp, so a meta of $10.00 or more with cents would reach about 104 and touch the line. | Format both limits the same way when either has cents. Derive `--rg-x` from the longer label, as `.rg-ph` does. |
| R15-07 | G4 | Low | The head is "Funcionando" in the 24sp title face, with the problem "· batería restringida" in 16sp ink-2. The primary button is the battery fix. | Lead with the problem ("Batería restringida") and demote "funciona por ahora". |
| R15-08 | D1 | Low | The "one-line" timeline is three lines and adds 7 figures (two subtotals, four times, one duration) above the fold, on top of the hero, the count, "23" and three rates. | Drop the time row (the segment positions already show the hours), or drop the subtotals. |
| R15-09 | H3 | Low (privacy) | With "Ocultar montos y horas" on, the bars stay proportional (0.879) to the cohort average of $131, which every user sees on F2. A recipient can recover about $149 per trip. | When amounts are hidden, hide the bars and keep the "78 %" phrase, or rename the toggle to say what it actually hides. |
| R15-10 | `base-v3.css`, checker, §11 R15 | Low (port) | Stale comments: line 313 ("plate top 429"), the `.lector-tab` comment ("same … placement" at top 429), and lines 415–429 ("Delete these selectors there … until the old rules are deleted", in the file where they already were). The checker summary undercounts its guards. There is no greyscale render of the kit's v16 frames, only the proposal compare in `opus-r16-work/compare-grey-small.png`. Verde and rojo bands have relative luminance 0.144 and 0.161 (1.1:1), so in greyscale the state rests entirely on the cue, phrase and marker. | Delete the comments and fix the count. Run the §11 R15 greyscale render on C1–C5 and C7 and archive it. |

## 3. The founder's two questions

**Does the chip still read as the Claude/category template?** No, but it is closer than in Round 14.

- **What it shares with the template:** the top half is now the category's grammar: a coloured block, a large white shape and a bold number. Green ▲ and red ▼ beside a figure is also the stock-ticker idiom.
- **What keeps it out:**
  - no verdict word, check, × or "!";
  - the phrase names where the trip sits relative to *your* numbers;
  - the ink tray draws the driver's own two limits.
  Six judges agreed: template 1/6 against 6/6.
- **What it cost:** distinctiveness 5.8 (generic chips 2.8–4.5, the moving band 7.5) and zero point-of-view votes.

If the founder looks only at the band at arm's length, he may still see "a green badge with an up-arrow". The tray is what makes it Kompara's, so the port must not shrink or drop it. The cheapest way to buy some signature back without touching the glance is a marker sized in the cue's family (about 20dp, not 14×12), tested in R15 and R14.

**Is the product comparable to Uber Driver in use and feel?** In feel, yes on the parked screens. In use, not yet demonstrated, and one gap is now measurable.

- **Where it matches:** B1, D1, E1, F1 and G2 use Uber's grammar: white paper, flat hairline rows, a black primary, uber-5's grey full-width secondary, and three tabs. The whole-product board scored them 5.7–7.7 Uber-class, with coherence at 7.8.
- **Where it falls short:**
  - **Type voice:** a bold 72sp Barlow hero, against uber-5's regular-weight "MXN 645,99" set beside its chart. F1 (5.7) and E1 (6.5) are the weakest screens on the board.
  - **Icons:** stock Material glyphs; Uber draws its own.
  - **The offer geometry is flattering.** In uber-2 the real card starts at about 40% of the screen height (770 of 1920) with the puck centred above it. The kit's standard card starts at 64% (547 of 852). Running uber-2's geometry through the spec's own slot order:
    - the foot slot would be about y 152–264;
    - it hits the puck (about x 159–233 × y 155–229);
    - so does the left slot;
    - neither higher slot fits;
    - so the plate takes the last resort and sits over the puck.

    On Uber the common case is C8 or worse, not C1. Every blind lineup used the C1 crop.
  - **Not yet done:** no timed install, no mounted test, no real accessibility-tree placement, no DiDi host, and no rendered motion.

## 4. Verdict: PASS, at the craft bar exactly

- **Structural 74/80:** 10 · 10 · 10 · 9 · 9 · 9 · 8 · 9.
- **Craft 34/40:** 8 · 8 · 9 · 9.

Every criterion is ≥ 8 and there is no hard fail. Craft is exactly at the pass line, so any one-point loss on criteria 9–12 turns this into NOT_YET.

**Before the port** (about half a day):
1. R15-01: one number in C10, plus the true card tops in captions and spec.
2. R15-02: the next-manoeuvre exclusion in C9.
3. R15-03: one marker clearance.
4. R15-04: ledger sums and a checker assertion.
5. R15-05: the cue in A1's legend and a visible pin.
6. R15-10: comments, checker count, greyscale render.

**To raise criteria 7 and 10 above 8, and to protect 9:**
- Run the §11 timed install.
- Run the mounted R14 test on **two** geometries: C1, and an Uber-proportioned card with the puck centred (draw it as a new frame).
- Add the C9 manoeuvre case and marker size to the test items.

### Critical Files for Implementation
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-c.html
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/_regla-markup.html
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/base-v3.css
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/check-fixture.py
- /Users/panama/pilotea/docs/ux-elevation-spec.md