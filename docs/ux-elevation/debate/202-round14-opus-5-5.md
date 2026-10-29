# Round 14 — Opus 5.5

**What I reviewed:** the brief, `180`, `190` and my Round 13b report (`172`). I opened all nine contact sheets and these frames at 2×: A1, A2, B1, B3–B6, C1–C5, C7–C11, D1, D2, E1, E3, E5, F1, F2, F4, F5, G1, G2, G4, H3, N1, N8 and N10. I compared them with Uber references uber-1 to uber-6. I took pixel measurements from the PNGs and read `base-v3.css`, `_regla-markup.html`, `_head.html`, the part-c captions and the spec.

**Checker:** `python3 check-fixture.py` prints `checked 154 figures in 50 frames, 23 spec strings, 1 date rule, 46 stale patterns, 6 caption lints, 5 bar charts → OK`. These counts are the same as in Round 13b (see R14-04).

**Placement measured** (dp, from the 2× PNGs):
- **C1–C5, C7, C9 and N8–N10:** plate at 429–528, host card top at 547.5.
- **C8:** plate at 197–296 above the tall card.
- **C10:** plate at 327–456 above a card at about 469.
- **C11:** suppressed. The banner ends at 180 and the card starts at 268, leaving 88dp.
- **C6:** the 240×55 sibling plate in the same slot.

Every plate sits above its card. None touches the fare, the countdown or Aceptar, and none overlaps the control row or the banner.

**Vocabulary:** every rendered frame, aria-label and caption uses "tu meta" for $8 and "tu mínimo" for $6. No frame calls $8 "tu mínimo". The old pair survives only in source (R14-03).

**Overflow:** I found no clipping at 30px titles, 72px heroes, 24px sentence heroes or C10's 1.3× scale. The longest hero (`$12,169`, E3) uses 230 of 353dp. The two-line basis lines on B5, B6, E1, E3 and F1 are the deliberate `.sub2.two` variant.

## 1. Scores

| # | Criterion | R13b | R14 | Why (not 10) |
|---|---|---|---|---|
| 1 | One job per screen | 10 | **10** | Hoy v5's 72px hero wins every root screen. The regla is an overlay on the host's job. |
| 2 | One primary action | 10 | **10** | One filled button or none; Hoy's grey "Ver tu día" is a true secondary. |
| 3 | Navigation | 10 | **10** | Unchanged: three tabs. |
| 4 | Numbers discipline | 9 | **9** | At the decision moment the chip now carries **4 figures** ($9.49, $285/h, $8, $6), up from 2. F2's first viewport still has four. |
| 5 | Copy | 9 | **9** | Rows now state their meaning in the chip's words, and the vocabulary is consistent. Not 10: G2 says "rojo **debajo** de" where everywhere else says "**Abajo** de tu mínimo". C5 has labels with no figures. G6's "Cuenta y lo encendido" is still there. |
| 6 | States are designed | 9 | **9** | Gris regla, C6, C11 and N10 are drawn. Not 10: G1 and G4 show the green "it works" dot on a degraded state (R14-01). The gris plate has no accessible name (R14-07). The D1 suppressed row is still undrawn. |
| 7 | Time-to-value | 8 | **8** | Held by rule: no timed install yet. |
| 8 | Consistency & feel | 9 | **9** | The rendered kit looks like one hand made it. Not 10: two suppression thresholds, dead chip CSS that still uses the old vocabulary, a stale kit lede and legend (R14-02, R14-03), and R13b-03 still open. |
| | **Structural** | 74 | **74 / 80** | Every criterion ≥ 8. |
| 9 | Signature idea | 9 | **9** | La regla is a sentence a driver could draw: "your two limits as lines, the trip lands above, between or below." It travels to the row marks, G2, E1's car line and the Comparar line. Not 10: the lines are too faint to carry it at distance (R14-09), and the ▲◆▼ cue lives only on the chip. |
| 10 | Glance physics | 8 | **8** | Better channels than R13: a 56dp colour band, position, and a solid shape. Unrecorded lineup6 has kit-r14 at glance rank 3.5/11, best in its field. Held at 8 by rule: no human mounted test. There is also a new risk: the chip now sits 20dp from the fare and is larger than it (R14-08). |
| 11 | Craft | 10 | **9** | Deliberate type scale (30/24/72/22/18/14), clean plates, a credible host. Not 10: verde now has three meanings (brand K, reader status, verdict) and lands on a degraded state; C5's empty labels look unfinished; the ruler lines are 1.5dp at 42 % white. |
| 12 | Radical simplicity | 9 | **9** | Not 10: every chip spells out its scale, and every row pairs the colour with words. Both were accepted on blind evidence. F2/F3 still restate their scope, and A2 runs about 100 words. |
| | **Craft** | 36 | **35 / 40** | Every criterion ≥ 8. |

**Hard fails:** none on either rubric. The closest call is "a rounded coloured rectangle with a number and a glyph". In verde, seen from a distance, the plate collapses to a green rounded-top block with ▲ and a bold white number (R14-09). It is not a fail: the plate is ink, the colour is a slot in a labelled ruler, each state has its own silhouette, and blind template votes were 0/6 (r5a) and 1/6 (kit-r14), against 6/6 for the category.

## 2. Regressions the propagation introduced

| ID | Frame / file | Sev. | Evidence | Fix |
|---|---|---|---|---|
| R14-01 | G1, G4 | Med | Hoy v5's green dot (`.status` at base-v3.css line 364) now leads "Funcionando · batería restringida". That is a warning state: G4's primary is "Quitar restricción de batería" and the sub-line says "Tu teléfono puede apagar el lector". Green says "all good" while the screen asks for a fix. Spec line 273 itself says the funcionando dot "would read as a verdict". | Show the dot only when nothing needs action. "Batería restringida" takes the ink segment. In G1, "Funcionando · batería restringida" moves to the segment form. |
| R14-02 | C11 caption; spec lines 391, 393, 718, 745 | Med (port) | One rule, two numbers. Suppression applies when the gap is shorter than "plate + 32 (16dp foot + 16dp clear)" (spec 391, 718, C11 caption sentence 2) or "plate + 24" (spec 393, 745, C11 caption's last sentence). Captions also put the card top at 545 (C1) and 265 (C11), but the renders show 547.5 and 268, so the real foot is 18.5dp, not 16. | Keep +32, which matches the 16dp foot stated everywhere else. Correct the four spec lines and the caption, and align the host card's top with the captions or the captions with the render. |
| R14-03 | `base-v3.css` lines 168–186, 230–232, 262, 306+; `_head.html` line 30 | Med (port) | The retired `.chip` and `.lane-edit` rules are still in the stylesheet, unused by any frame, under a comment that says "the upper rule is **tu mínimo**, the lower is **tu rojo**". That is the only place in the live kit where $8 is still "tu mínimo", and it is in the file the Compose port reads. The kit lede is also stale in four places: it calls the signature "**la raya con carril**"; the legend says verdict colours appear "only on verdicts: the chip and the mark", which the dot now contradicts; it says "night set (N1–N8)", but N8–N10 exist; and it says "Sí, así es", but B4's button says "Sí, son mis costos". | Delete the dead rules and the comment. Rewrite the lede around la regla. Add "and the reader's dot" to the legend. Change N1–N8 to N1–N10 and fix B4's button label. |
| R14-04 | `check-fixture.py` | Low-med | The checker counts are identical to Round 13b (154 figures, 50 frames, 46 stale patterns). The vocabulary flip, the riskiest change of the round, has no guard. The state-to-phrase mapping on rows (for example, 14:50 → "Entre tu meta y tu mínimo") is also unasserted. I verified the mapping by eye on B1, B5, B6, D1, N3 and all regla aria-labels; it is correct today. | Add stale patterns: `tu rojo`, `mínimo $8`, `mínimo de $8`, `Verde desde $`, `Rojo debajo`, `▬`, `class="chip`, `carril`. Assert that each `.regla` and each row mark's phrase matches its fixture state. |
| R14-05 | C5 | Low | The gris plate shows "tu meta - - -" and "tu mínimo - - -" with no figures. Next to C4's "$8 / $6" it reads as a missing value. The caption explains "the fixture has no per-hour limits", but C5's own premise is a driver who chose Por hora, and such a driver has hourly limits. | Add illustrative per-hour limits to the fixture (marked Ejemplo) and print them, or draw C5 for a per-km driver. |
| R14-06 | G2 | Low | "Tu mínimo · rojo **debajo** de" against "**Abajo** de tu mínimo" in B1, D1, G8, aria-labels and the fixture ("rojo abajo de"). Separately, "Arriba de tu meta" is used for ≥ $8.00, so a trip at exactly $8.00 is called "above". | Change to "rojo abajo de". Spec the boundary wording, or accept it, since rounding makes exactly $8.00 rare. |
| R14-07 | C4, C5, N10, `_regla-markup.html` | Low (a11y) | The verde, amarillo and rojo bands have `role="img"` and a sentence. The gris band has neither, so TalkBack reads "tu meta 8, tu mínimo 6, Sin lectura, falta la tarifa" in DOM order. | Add `role="img" aria-label="Sin lectura: falta la tarifa"` to the gris band in the markup file. |
| R14-08 | C1–C3, C8, C9 | Risk (mounted test) | **R13→R14 shift:** the chip moved from the bezel, about 300dp from the fare, to 20dp above it, and its 40sp `$9.49` now outsizes the host's ~36sp `$148.00` (criterion 10: "nothing competes with the host's fare"). **C8:** the host's `$12.33/km` gross sits about 70dp from `$9.49 /km neto`, and the plate covers the pickup dot (the caption admits it). **C9:** the plate hides the next corner of the active route; the caption says "clear of the puck", which is true but incomplete. | Add to §11: the rate at which drivers misread the fare and the net figure, A/B of 40 vs 34sp, and whether covering the route matters in motion. Correct C9's caption. |
| R14-09 | C1, N8, A1, B3 | Low (signature) | The ruler that makes la regla Kompara's is its quietest element: 1.5dp lines at 42 % white (#6B6B6B on #0E0F10) and 14sp labels at 72 %. At 60cm they vanish, and verde's silhouette becomes a green block with ▲ and a bold number. | Lines 2dp at about 75 %, labels 15sp/600. Test in the mounted test against the current weight. |
| R14-10 | `180` | Record | `scratchpad/blind/lineup6-tally.md` with `blind-key-lineup6.json` is a sixth round with the propagated regla (kit-r14, still with ▬). It scored glance rank 3.5/11 (best), Uber-class rank 2.2 (best), template 1/6 and point of view 4. It is not in `180`, whose residual-risk paragraph still says the template wins glance. | Record it, with the model-judge caveat and the ▬→◆ change that followed. |

From Round 13b: **R13b-01** is closed (C11 now uses D1's second-line anatomy). **R13b-02** (F2 and F3 state their scope twice) and **R13b-03** are still open: there is no `.spark.wk` and no `role="tablist"`, and the `.lane-edit` that R13b-03 named is now dead code (R14-03).

## 3. The founder's two questions

**Does the chip still read as the Claude/category template? No, with one caveat.** The category template is a verdict word, a check-in-circle, a red-amber-green gradient slider with a thumb, and a "CONVIENE" pill. I set the lineup6 board beside C1, and la regla has none of these. It is also the only design in that field that draws the driver's own numbers as the scale. That is the product's point of view, and six context-free judges agreed: 0/6 template votes on r5a, 1/6 on kit-r14, against 6/6 for the category's chips.

The caveat is verde, the state a working driver will see most. From the windshield, with the 1.5dp grey lines gone, it is a green rounded block with a white ▲ and a bold number. Green ▲ and red ▼ beside a figure is also the stock-ticker idiom, which is a template of its own. If the founder judges C1 alone at arm's length, he may still see "a green badge". The fix is cheap and keeps the idea: make the ruler visible at distance (R14-09). Amarillo and rojo are unambiguous already, because the ink lines above the colour change the silhouette.

**Is the product now comparable to Uber Driver in use and feel? In feel, yes on the static in-app screens. In use, not demonstrated.**
- **Where it matches:** Hoy, Ganancias, Comparar and Cuenta now use Uber's grammar: white surface, black type, flat rows with hairlines, a black primary, a grey full-width secondary (uber-5's "Estadísticas"), three tabs, and a host mock that is a credible Uber offer card. Five of six judges ranked Hoy v5 first against real Uber screens.
- **Type voice:** Barlow Semi Condensed 700 with a 72px hero under a 30px title reads like a scoreboard. Uber's Ganancias sets a ~38sp geometric title above a ~33sp regular-weight figure beside its chart. Kompara's hierarchy is louder and inverted. This is a defensible point of view, but it is the clearest "not Uber" signal left.
- **Iconography:** stock Material glyphs (home, bars, swap, person, clock, calendar, bank). Uber draws its own.
- **Use:**
  - No timed install (criterion 7) and no mounted glance test (criterion 10).
  - The overlay has been drawn over one host mock only: no DiDi card, no real-build variance.
  - The placement rule (16dp above the host card's top, recomputed on every layout change) is unproven against real Uber and DiDi accessibility trees.
- **Motion and haptics:** the state swap, the 5s detenido toast and the A1 auto-step exist only in captions.

## 4. Verdict: **PASS**

Structural **74/80** (10 · 10 · 10 · 9 · 9 · 9 · 8 · 9). Craft **35/40** (9 · 8 · 9 · 9). Every criterion is ≥ 8 and there is no hard fail. Craft drops one point from Round 13b because of R14-01, R14-05 and R14-09.

**Before the port** (about half a day): R14-01 (dot rule), R14-02 (one threshold, true geometry), R14-03 (delete the dead CSS, fix the lede and legend), R14-04 (vocabulary guards), R14-05 to R14-07 (C5 figures, "abajo", gris aria) and R14-10 (record lineup6).

**To raise criteria 7 and 10 above 8:** the §11 timed install, and the mounted driver test with R14-08 and R14-09 added as test items.

### Critical Files for Implementation
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/base-v3.css
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-g.html
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-c.html
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/check-fixture.py
- /Users/panama/pilotea/docs/ux-elevation-spec.md