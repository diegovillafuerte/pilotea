# Round 15b — Opus 5.5

**What I checked:**
- **Frames:** all nine sheets, plus these frames at full size: A1, C1–C5, C7–C12, D1, D3b, F1, F4, G2, G4, H3 and N8–N10.
- **Other inputs:** the greyscale proof, the spec diff (§6, §10, §11, §12), `base-v3.css`, `_regla-markup.html` and the checker source.
- **Method:** every number below was measured from pixels in the 2× PNGs and is given in dp.

**Checker:** `checked 152 figures in 50 frames, 16 chips and 27 offer rows …, 55 stale patterns (9 of them vocabulary guards), 1 markup guard, 6 caption lints, 5 bar charts → OK`.

## 1. My Round 15 items

| ID | Status | Evidence (measured) |
|---|---|---|
| R15-01 foot | **Done** | See the placement table below. Captions and spec now carry 545, 316, 469 and 260. Still missing: a foot check measured from the renders. The checker reads HTML only. |
| R15-02 C9 turn | **Done** | Plate x 141–381 × y 243–355 (slot 3). It is 127dp above the turn (y 482–511.5) and 16dp above the destination stroke (top 371). It is 26dp right of the puck (x 77–115) and 63dp under the banner (bottom 180). |
| R15-03 pin clearance | **Done** | In all nine host frames the pan is identical: the pin's blue is 386.5–397.5 and its white stroke 383.5–400.5, so the plate top at 417 clears it by **16.5**. C8: stroke top 275.5 against plate bottom 259.5, so 16. C10: stroke 273.5–290.5 against 307.5, so 17. |
| R15-04 ledgers | **Mostly done** | D3b's total now says "Redondeado una vez · las dos cifras suman $12,170", the same pattern as E1b and E3d. F4 shows "+12 %" (120/107 = 1.121). F4's bars measure 314/353 = 0.890 against 0.892. The checker asserts both strings. **Residual:** D3b's sub-line "$214.29 por día × 19 días" is $4,071.51, yet the row prints −$4,071. Printing **−$4,072** makes the column add exactly (16,241 − 4,072 = 12,169), and the note is then not needed. There is still no ledger-sum assertion. |
| R15-05 A1 | **Done** | Each legend cell carries the chip's cue in its band colour (▲ ◆ ▼; gris none). The pin is in view: stroke 276.5–293.5 against a plate top of 337, so 43.5dp clear. |
| R15-06 G2 | **Done** | The tray reads "$9.50" and "$6.00". The lines now start 12.5dp past the longer label (123dp from the plate's left edge), matching C1's 12dp past its label (104dp). |
| R15-07 G4 | **Done** | "Batería restringida" is the 24sp head; "· aún funciona" is demoted. |
| R15-08 D1 | **Done** | The timeline has 3 figures (8 h 10 min, $648, $592), down from 7. The times moved into the accessible name. |
| R15-09 H3 | **Done** | With the toggle on, the bars are gone and "78 %" stays. Nit: the 4:5 card leaves an empty slot of about 85dp (y ≈ 437–522) where the bars were. |
| R15-10 port hygiene | **Done** | CSS line 313 now reads "card top 545 → 417–529". The "Delete these selectors" block is gone, the guards are counted, and the proof is archived (10 panels). **Residual:** the proof shows the compact chip only in verde. `.lector-tab`'s default `top:429px` (line 342) is a dead value, since C6 sets 473 inline. |

**Placement as rendered (spec: 16dp foot):**

| Frame | Plate | Host card top | Foot |
|---|---|---|---|
| C10 | x 69–381 × y 307.5–453 | 469 | 16 |
| C1–C5, C7, N8–N10 | 417–529 | 545.0 | 16 |
| C8 | 147.5–259.5 | 316 | — (rises clear of the pin) |
| C12 | 270–326 | 342 | 16 |
| A1 | 337–449 | 465 | 16 |

Codex's C10 item is closed by the same C10 fix.

## 2. Regressions introduced by the pass

| # | Frame | Sev. | Evidence | Fix |
|---|---|---|---|---|
| G1 | **C12, spec §6** | **High: it decides the verdict** | See "G1 in detail" below. | Keep both zones in the compact form; see below. |
| G2 | spec §6 / §12, C11 | Med-low (port) | Suppression is stated three ways: (1) the §6 "Compact" row says "It answers markers, not height: suppression is still measured against the full plate (C11)"; (2) the §6 Placement paragraph and the C11 caption say the compact takes the band and suppression starts **below 88dp**; (3) the §12 build summary still says "suppression when that band is shorter than the plate + 32dp (**144dp at 1×**)". This is the R14-02 class again, in the section engineers read. | Write one sentence and use it in all three places. Add a lint against "144dp at 1×" in §12. |
| G3 | spec §10, row 9 | Low (record) | The row now records Round 15 as "the port must not shrink or drop the tray **except as the compact fallback**". Neither reviewer wrote that exception; mine reads "must not shrink or drop it". | Restore the wording, and log the compact as a Round 15b change still open to review. |
| G4 | F1 | Nit | "+$1.21" over "más por km en DiDi" says "more" twice (the sign is new this pass). | Drop the sign or the word. |
| — | C9 | Note | The plate now sits 63dp under the dark banner, which raises the grouping risk the spec already sends to R14. | None now; the risk is already in R14. |

**G1 in detail**

*Evidence:*
- In C12 the compact chip (x 141–381 × y 270–326) is a green rounded band with ▲, $9.49 and the phrase. It has no lines and no marker.
- The C12 caption and spec §6 both say "On Uber this is the common case, not C1".
- So in the geometry drivers will mostly see, the chip is the template form. In Round 15 I said the band alone "matches those words literally" and that "the tray is what makes it Kompara's, so the port must not shrink or drop it".
- There is no blind vote for the compact (`180` has no entry). The 1/6 template vote covered the full plate only.
- The greyscale proof shows only the verde compact.
- A gris compact would be a #2A2C2F 240×56 band reading "Sin lectura / falta la tarifa". That is a near-twin of C6's #0E0F10 240×56 "Lector detenido" tab in the same spot. Until now the tray's dashed lines told them apart.

*Fix:*
- Keep both zones in the compact: **240 × 80** = the 56dp band plus a 24dp tray holding the two 2dp lines and the marker. Drop the labels, since the phrase names the limits. Gris keeps dashed lines.
- In C12 this fits the foot slot at **246–326**: 19dp under the puck's ring (227), with the 16dp foot. C12's own foot-slot arithmetic measures the ring, not the halo.
- If the halo must count, only about 59dp is free. In that case, draw the lines and marker as a 28dp glyph at the band's right end and widen the band; the slot under the puck is 369dp wide.
- Redraw C12. Add the amarillo, rojo and gris compact states beside C6 to the greyscale proof.
- The threshold becomes 80 + 32 = 112dp; C11 at 80dp stays suppressed.

## 3. Scores

| # | Criterion | R15 | R15b | Note |
|---|---|---|---|---|
| 1 | One job per screen | 10 | **10** | |
| 2 | One primary action | 10 | **10** | |
| 3 | Navigation | 10 | **10** | |
| 4 | Numbers discipline | 9 | **9** | D1 drops from 7 to 3 timeline figures. The first viewport still holds more than 3. |
| 5 | Copy | 9 | **9** | G4 is fixed. "Tu resumen de Uber dice un poco más", "Cuenta y lo encendido" and F1's double "more" remain. |
| 6 | States are designed | 9 | **9** | A1 is fixed. The compact's amarillo, rojo, gris and 1.3× states are specified but not drawn. |
| 7 | Time-to-value | 8 | **8** | Held by rule: there is still no timed install. |
| 8 | Consistency & feel | 9 | **9** | The foot is a true 16 everywhere. Not 10: suppression is stated three ways (G2), and the gris compact is a near-twin of C6. |
| | **Structural** | 74 | **74 / 80** | Every criterion ≥ 8. |
| 9 | Signature idea | 8 | **7** | **Down 1.** In the case the spec itself calls common on Uber, the chip is the band alone. That is the category's grammar plus a phrase, with no limits and no marker (G1). The row marks, G2, A1 and E1's car line still carry the idea elsewhere. |
| 10 | Glance physics | 8 | **8** | All three Round 15 defects are fixed (C10 foot, C9 turn, pin clearance). Held at 8 by rule: there is no mounted test. |
| 11 | Craft | 9 | **9** | Placement is now precise in every frame. Not 10: verde still has three meanings, the C6 near-twin, and H3's empty slot. |
| 12 | Radical simplicity | 9 | **9** | |
| | **Craft** | 34 | **33 / 40** | Criterion 9 is below 8. |

**Hard fails:**
- **Structural rubric:** none.
- **Craft rubric:** none triggered on the component as specified. But C12's compact band matches "a rounded coloured rectangle with a number and a glyph" word for word. It is held off only because the full plate is still the specified chip and the phrase names the driver's own limit. The G1 fix clears it.

## 4. Verdict: NOT_YET

The structural rubric passes at 74/80. Craft is at **33/40, with criterion 9 at 7**. The pass closed all ten of my items; what costs the point is the new compact chip, not anything left undone.

**Shortest path (about half a day):**
1. **G1:** the two-zone compact (240 × 80, unlabelled lines plus marker, gris dashed), C12 redrawn at 246–326, and the four compact states beside C6 in the greyscale proof.
2. **G2:** one suppression sentence across the §6 table, §6 Placement, §12 and C11.
3. **G3:** restore the Round 15 wording in §10.
4. Optional: D3b's −$4,072, and a foot check measured from the renders.

With step 1 done, criterion 9 returns to 8 and craft to 34/40, which is a PASS on the same evidence.

### Critical Files for Implementation
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/base-v3.css
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-c.html
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/_regla-markup.html
- /Users/panama/pilotea/docs/ux-elevation-spec.md
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/check-fixture.py