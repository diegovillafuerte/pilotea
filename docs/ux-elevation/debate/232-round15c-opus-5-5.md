# Round 15c — Opus 5.5

**What I checked:**
- **Frames at full size:** C12, C11, C6, F1 and A1.
- **Sheets:** A, C, F, G and N.
- **Proofs:** the colour compact proof and both archived greyscale proofs.
- **Text:** the spec (§6, §9k, §10, §11, §12), `base-v3.css`, `_regla-markup.html` and `part-c.html`.
- **Render diff:** a pixel diff of every frame in `r15cf/frames` against `r15bf/frames`. Only C12, F1 and N6 changed. A1 differs by at most 11 levels, which is not visible.
- **Method:** all numbers are dp, measured from the 2× PNGs.

**Checker:** `152 figures in 50 frames, 16 chips and 27 offer rows … 55 stale patterns (9 of them vocabulary guards), 1 markup guard, 9 caption lints, 5 bar charts → OK`.
- On my first run it reported 6 caption lints. The kit was edited at 15:25 while I was reviewing (`part-c.html`, `index.html`, `part-n.html`, `_frame-contract.md`, `check-fixture.py`). See regression R1.

## 1. Is my Round 15b blocker (G1) resolved? Yes

| Check | Measured | Verdict |
|---|---|---|
| C12 compact plate | x 141–381 × y 246–326, so **240 × 80** | ✓ |
| Band / tray | Band 246–284 (**38**), tray 284–326 (**42**) | ✓ |
| Band content | Cue about 21 × 18 in a 24 box, centred at 265.5 in a band whose centre is 265. "$9.49" caps are about 17, which fits 26sp. "/km neto" is present. The phrase line is gone, and its text is still in `aria-label` ("Arriba de tu meta: 9.49 …"). | ✓ |
| Tray | "tu meta $8" and "tu mínimo $6" at 14sp, starting x 155.5 (a 14dp inset). Two 2dp lines at y 296–298 and 312–314, running x 245–338.5, so they start 104dp from the plate's left edge, the same x as the full plate. ▲ marker at x 349–358.5 × y 287.5–295.5, sitting on the meta line. | ✓ The limits and the relational marker are back. |
| Clearances | Puck ring bottom at 227, so 19 to the plate top. Card top at 342, so a **16** foot. The plate's top 5dp lie over the halo, which ends at 251 (radius 61, centred at 196, 190). The spec says this: "over the halo's soft edge". | ✓ It is the slot I proposed. |
| Compact states (colour proof, 2×) | Marker places: verde ▲ 287.5–295.5, above the meta line. Amarillo ◆ 301–308.5, centred on 305 between lines at 297 and 313. Rojo and rojo − ▼ 314–322, hanging from the mínimo line and 4 above the edge. Both gris states have no cue, no marker, dashed lines and a one-line "Sin lectura · falta la tarifa" or "Falta tiempo · sin semáforo". Night versions are drawn too. The 1.3× plate measures 311 × 104 (band 49.5, tray 54.5). | ✓ All states are drawn. |
| Greyscale | Band luminance: verde 85, rojo 96, amarillo 180, gris 44. Verde and rojo are near-equal in grey, so the ▲/▼ cue and the marker place carry the difference, as on the full plate (by design). The gris compact (band 44, tray 15, dashed lines, 80 tall) no longer twins C6's 15-luminance, lineless 240 × 56 tab (473–528.5). | ✓ The C6 near-twin is gone. |
| Suppression | §6 Compact row, §6 Placement and §12 all say "compact takes any slot the full plate cannot; suppress only below 112 (80 + 32)". C11 measures banner 84–180 and card 260, so 80 < 112 and nothing is drawn. The current C11 caption says 112 (see R1). | ✓ My G2 is closed. |
| G3, §10 row 9 | "…so the port must not shrink or drop the tray; the compact form (Round 15b) keeps both limits and is still open to review." | ✓ |
| G4, F1 and N6 | "+$1.21 / por km en DiDi, / ya sin gasolina ni desgaste" | ✓ |
| `.lector-tab` | Line 342 has `top:473px` | ✓ |

The compact now uses the full plate's layout with only the phrase line removed. The driver's two named limits and the marker are present in the geometry the spec calls common on Uber. This clears the "coloured rectangle with a number and a glyph" reading.

## 2. Regressions

| # | Where | Sev. | Evidence | Fix |
|---|---|---|---|---|
| R1 | C11 caption (render set) | Low, **closed during review** | `r15cf/frames/C11.html` (15:19) still read "compact band's 56 + 32 = 88 … suppression starts below 88dp". That contradicts the brief's claim that C11 matches §6 and §12. At 15:25:03, `part-c.html` and `index.html` were corrected to "80 + 32 = 112", and three caption lints were added (`'56 + 32'`, `'240 × 56 —'`, `'the band alone, ▲'`). The frame PNGs carry no captions, so they are unaffected. | Re-render the sheets so the archived HTML matches the kit. |
| R2 | `blind-test/greyscale-chips-r15b.png` | Low (record) | The file is from 15:07, before the compact rewrite. Its panel 7 still shows the **rejected band-only compact**: a 56dp band reading "▲ $9.49 / Arriba de tu meta" straight over the card, with no tray. Yet the brief cites this file as the full-plate proof. | Re-render panel 7 with the 240 × 80 compact, or label it "superseded R15b". |
| R3 | `blind-test/greyscale-compact-r15b.png` | Low (evidence) | Archived at 900 × 245, about 0.32 px per dp, so the ¾-size marker is about 3 px and the file cannot show the marker places by itself. The 2× colour proof exists only in the scratchpad. | Archive a 2× greyscale (and the colour proof) in `blind-test/`. |
| R4 | Spec wording | Low (record) | §9k row 13 still says "The kit's greyscale render (R15) is still owed" and does not link either proof. §9k row 1 says "even the compact band does not fit". §6 Placement says "only when the band cannot clear the markers either", which is leftover band wording, and it does not say which form takes the last-resort slot. The proof draws the compact. | Say "compact chip" in both places, name the compact as the last-resort form, and link the proofs. |
| R5 | `base-v3.css` lines 465–473 | Low (port) | A patch-file comment sits inside `base-v3.css` itself: "REPLACES in base-v3.css (Round 15b block) — delete: … Until they are deleted this file still wins…". The old selectors are already gone. This is the same class as R15-10. | Delete the two stale sentences. |
| — | Proof panel "verde 1.3×" | Note | The plate is at y 222–326 on the 1× host. It covers the bottom **5dp of the puck ring** (ring black ends at 227; band from 222). The rule allows this (the C12 caption says that at 1.3× it "finds no slot here", so the last resort applies), but the panel is unlabelled and keeps the card at 342. On a real 1.3× host the card would rise, as C10's rose from 545 to 469, so the plate would sit higher over the puck. | Label the panel as the last resort. Add "1.3× on C12's geometry, the plate over the puck" to R14's last-resort item. |
| — | Compact marker | Note | It is ¾ size (≈ 10.5 × 9; ◆ measures about 7.5 × 7.5). The signature's relational element is at its smallest in the common case. | Include the compact in R14's marker-size question. |

No frame regressed visually. Outside C12, F1 and N6, every frame is pixel-identical or within sub-visible noise.

## 3. Scores

| # | Criterion | R15b | **R15c** | Note |
|---|---|---|---|---|
| 1 | One job per screen | 10 | **10** | |
| 2 | One primary action | 10 | **10** | |
| 3 | Navigation | 10 | **10** | |
| 4 | Numbers discipline | 9 | **9** | D1's first viewport still holds more than 3 figures. D3b's −$4,072 (optional) is not applied. |
| 5 | Copy | 9 | **9** | F1 is fixed. "Tu resumen de Uber dice un poco más" and "Cuenta y lo encendido" remain. |
| 6 | States are designed | 9 | **9** | The compact's amarillo, rojo, gris, night and 1.3× states are now drawn (in the proof). H3's empty slot remains. |
| 7 | Time-to-value | 8 | **8** | Held by rule: there is no timed install. |
| 8 | Consistency & feel | 9 | **9** | Suppression is now one rule in §6, §12 and C11. Not 10: R1 was caught only after the render, plus R4 and R5. |
| | **Structural** | 74 | **74 / 80** | Every criterion ≥ 8. |
| 9 | Signature idea | 7 | **8** | **Up 1.** In the Uber-proportioned case the chip keeps "tu meta $8 / tu mínimo $6" and the marker above, between or below them. It is the full plate's object minus the phrase, not the category's badge. Not 9: the marker is ¾ size, and the phrase that named the place is gone in the common case. |
| 10 | Glance physics | 8 | **8** | Host fare leads (26sp rate under C12's fare). Held at 8 by rule: there is no mounted test. |
| 11 | Craft | 9 | **9** | Compact rhythm is exact: content centred to 0.5dp in band and tray, line x shared with the full plate, 16 foot, 19 under the ring. Not 10: verde still has three meanings, plus H3's empty slot and R2/R5 hygiene. |
| 12 | Radical simplicity | 9 | **9** | |
| | **Craft** | 33 | **34 / 40** | Every criterion ≥ 8. |

**Hard fails:**
- **Structural rubric:** none.
- **Craft rubric:** none. The compact is no longer "a rounded coloured rectangle with a number and a glyph"; its lower half carries the driver's two named limits and the relational marker in every coloured state, and dashed limits in gris.
- One-second glance recognition is still unverified until R14.

## 4. Verdict: PASS

- **Structural:** 74/80, every criterion ≥ 8.
- **Craft:** 34/40, every criterion ≥ 8.
- The Round 15b blocker is resolved as I prescribed, and better: the limit labels were kept, not just the lines.
- R1 was fixed in the kit during review. R2–R5 are record and port hygiene and can ride with the next pass:
  - re-render or label the greyscale panel 7;
  - archive a 2× greyscale compact proof;
  - fix the "band" wording in the spec and update §9k row 13;
  - delete the CSS patch comment;
  - label the 1.3× proof panel as the last resort.
- None of them changes a score.

### Critical Files for Implementation
- /Users/panama/pilotea/docs/ux-elevation-spec.md
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/base-v3.css
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-c.html
- /Users/panama/pilotea/docs/ux-elevation/blind-test/greyscale-chips-r15b.png
- /Users/panama/pilotea/docs/ux-elevation/blind-test/greyscale-compact-r15b.png