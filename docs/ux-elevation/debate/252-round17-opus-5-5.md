# Round 17 — Opus 5.5

**What I checked**
- **Renders:** all 81 frames in `r17f/frames` at 2×, sheets A–H and N, and uber-1 to uber-6. At full size: B1, B6, C1, C6, C7, D1, D2, D3, D3b, E1, E1b, E2, E3d, E8, F1, G1, G2, G4, G8, A1 and A3.
- **Render diff:** a pixel diff of every frame against `r15cf`. 78 frames changed; C4, C11 and N10 are identical. On C1–C3, C9, C10, C12, N8 and N9 the only change is the tray marker.
- **Text:** spec §4, §5.5 and §5.6, `check-fixture.py`, the walkthrough results (`results-r15c.md`), and papers `240`–`242`.
- **Method:** every figure was recomputed from `fixture.json` at full precision ($2.841667/km; car $214.2857 per day) and rounded once. All sizes are dp, measured from the PNGs.
- **Checker:** `145 figures in 50 frames, 16 chips and 27 offer rows … 61 stale patterns … 5 bar charts → OK`.
- **Disclosure:** I wrote one scratch image by mistake, `/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/r17-opus-uber1346.png` (uber-1, 3, 4 and 6 side by side). It is safe to delete. Nothing in the kit was touched.

## 1. Scores

| # | Criterion | R15c | **R17** | Evidence on the renders |
|---|---|---|---|---|
| 1 | One job per screen | 10 | **10** | The cuts landed: B1's best hour, E1's best-day and standing rows, E3/E3c's best week and D1's timeline are gone. Ganancias is now a figure beside its bars. |
| 2 | One primary action | 10 | **10** | B3's primary is "Abrir Uber para probar". B1, B6 and D1 have no filled button; "Ver tu día" is grey. |
| 3 | Navigation | 10 | **10** | Three tabs with drawn glyphs. Glyph ink areas are 196 / 180 / 180 dp², within ±9 %. The limits row is now "Tu meta y tu mínimo" and the figures are typeable. |
| 4 | Numbers discipline | 9 | **9** | Today now has one figure on every screen, and E1's bars add up to the figure. Bars measure 51 64 60 71 100 58 25 % against the spec's 51 63 60 71 100 58 24 %, and E3's are to scale. Not 10: D3 and D3b open on different bases (R1), and E3d's first row carries two figures (R2). |
| 5 | Copy | 9 | **9** | Gains: one word, "semáforo"; "te deja / te dejó" for a trip; "sin cálculo"; "Lo del coche se resta por día, no por viaje" on D2. Losses: G2's helper grew to 20 words (R7); A2 is still about 100 words; "detenido" sits beside "apagado" (R10). |
| 6 | States are designed | 9 | **9** | The status glyphs are distinct and drawn on B2–B5, A3, A4, G1 and G4. Two states are still undrawn: the negative day that P1 creates (R4), and Hoy when a fix is waiting (R3). |
| 7 | Time-to-value | 8 | **8** | The A3 reassurance is now bold above the steps, and B3 sends the driver to Uber to test. Held at 8: there is still no timed install, and A2 still stands before the permission. |
| 8 | Consistency & feel | 9 | **9** | The icon set is solid geometry; glyph boxes are 16–18 inside a 20 live area. One status-glyph family. Small slips: R6, R8, R9 and R11. |
| | **Structural** | 74 | **74 / 80** | Every criterion is ≥ 8. |
| 9 | Signature idea | 8 | **8** | The tray marker is now a bar in the state's hue, the same object as the row mark's filled slot, so the plate and the mark read as one. Not 9: with the car line retired, the idea is no longer carried into Ganancias or Comparar. |
| 10 | Glance physics | 8 | **8** | The control-like white caret is gone. The blind test cited in `180` gives 0/6 control readings for the bar, against 6/6 for the old triangle. Still held at 8 because there is no mounted one-second test (R14). |
| 11 | Craft | 9 | **9** | E1's figure baseline sits on the $0 line to within 1dp. The plot is 167.5 wide against 168 specified. The amarillo marker is centred to 0dp. Not 10: the marker's gaps are uneven (R8), the "!" ring is a circled glyph (R9), and verde still has three jobs (verdict, status dot, the K). |
| 12 | Radical simplicity | 9 | **9** | Every root can now be said in one sentence. What remains: A2, G2's helper line and E3d's sub-line. |
| | **Craft** | 34 | **34 / 40** | Every criterion is ≥ 8. |

**Hard fails: none on either rubric.**
- The chip is still a 240 × 112 plate: a 56dp band over a 55.5dp tray that carries both named limits and the marker in every coloured state.
- The one-second glance is still unverified until R14.

## 2. Regressions and the money chain

**Money chain: it holds end to end.**

| Figure | Computation | Frames |
|---|---|---|
| **$548** | 648.28 + 113.90 − 214.29 = 547.89 | B1 = E8 = N1. Both say "6 viajes" at 14:40. |
| **$1,026** | 1,590 − 350.09 − 214.29 = 1,025.62 | D1 = D3 (whose ledger adds up) = E3d "19 oct · hoy" = B2 "Ayer" = N2 = N3 |
| **$4,980** | 8,400 − 1,920.40 − 1,500 = 4,979.60 | E1 = E3d's 12–18 row = D3b's bar label. E1b's days (594, 736, 695, 827, 1,166, 677, 284) add to $4,979 and the frame says so. |
| **$284** | E1b's Domingo | = B4 |
| **$12,169** | 1,417.11 + 4,746.95 + 4,979.60 + 1,025.62 | E3 = E3d = D3b. D3b's 16,240.71 − 4,071.43 gives the same figure. |
| **Others** | B5 433.99 · B6 165.71 · D2 113.90 ($285/h) · F1 10.34 − 9.13 = 1.21 · F2 78 %, $149, $131 | All correct. The chip and every rate exclude the car. |

**Regressions**

| # | Frame | Sev. | Evidence | Fix |
|---|---|---|---|---|
| R1 | D3 vs D3b | Low–med (older, now exposed) | The two "Cómo se calculó" sheets open with the same label on two bases. D3 says "9 viajes tomados · antes de gasolina, desgaste y coche $1,590". D3b says "158 viajes tomados · ya sin gasolina ni desgaste $16,241". This is the walkthrough's "one word, two numbers" failure in miniature. | Give D3b D3's four lines. Viajes antes de gasolina, desgaste y coche **$21,040** · Gasolina y desgaste, 1,688.9 km × $2.84, **−$4,799** · Lo del coche, $214.29 × 19 días, **−$4,071** · **$12,169**, with the sub-line "Redondeado una vez · las tres cifras suman $12,170". |
| R2 | E3d, row "1 – 4 oct" | Low–med (older) | The sub-line reads "4 de 7 días del coche · la semana completa dejó $774", which puts a second figure on the row. The $774 takes seven days of car off four days of reading (the reader started 1 oct). H5b's own statement shows 6 Uber trips on 28–30 sep that the reader never saw, so $774 understates the week. The checker asserts this figure (line 79). | Keep "4 de 7 días del coche" and cut the $774, along with its checker assertion. |
| R3 | Hoy (B1, B6) | Low–med (new gap) | The "!" ring ("it works, but a fix waits") appears only on G1 and G4. Spec §4 puts it in the chrome status line, but §5.1 lists only four Hoy states. On a phone with restricted battery, Hoy would still show the green dot with "funcionando". The walkthrough's weakest task (overlay stopped: ease 4.7, 3/6 partial) names exactly this. | Add the state to §5.1 and draw it on Hoy as the "!" ring with "Batería restringida · aún funciona ›", tapping through to G4. Keep the green dot only for when nothing needs the driver. |
| R4 | Not drawn | Low (new) | P1 creates a signed negative day: −$100 after one cheap trip, or −$214 for a past day with no trips. The spec defines it but says "No frame draws a negative day". It is the reading most likely to alarm a renter. | Draw it as an alternate frame, as E3c is: Hoy with the hero "−$100" in ink at the display size, and an E1b row with −$214 and its bar on the $0 line. |
| R5 | H5b | Low (exposed by P1) | The "Así quedaría tu semana" ledger has no car row, although that week carries $1,500 of car from 28 sep. | Add the row "Lo del coche −$1,500". |
| R6 | E1, E1b, E7b, N4 | Low | The kit renders "Tu resumen de Uber dice $34 más". The spec (map, §4, §5.5, §5.6) says "Según tu resumen, Uber te pagó $34 más". The checker missed the drift. | Use the spec's string and add it to the checker's spec strings. |
| R7 | G2 | Low (new) | The helper went from "Suben y bajan de $0.10 en $0.10" (8 words) to 20 words on two lines at 16sp. | Cut it to "Toca la cifra para escribirla." |
| R8 | Chip tray, C1–C3 | Low (new) | Marker gaps: verde sits 4dp above the meta line (bar 962–971px, line 980px); rojo sits 3dp below the mínimo line (line ends 1023px, bar starts 1030px). The markers are tints, not the band hue: #2FB35A on a #187A36 band, and #FF5C5C on #D32F2F. | Move rojo down 1dp. Name the tint as a token instead of calling it "the band's colour". |
| R9 | G1, G4 | Low (record) | The "!" ring is a circled glyph. §6's "Deleted" list still bans circled glyphs, and §5.2 lists "the amber '!'" as removed. | Write the status-ring family into the spec as the exception. |
| R10 | C6 vs B2 | Low | The vocabulary now has five status words, and two are near-synonyms ("detenido" on the overlay tab, "apagado" on Hoy). | Make the tab say "Semáforo apagado". |
| R11 | `check-fixture.py:137` | Low (hygiene) | The comment still says the bars are "before the car cost"; line 138 computes them after the car. | Fix the comment. |

## 3. The founder's two questions

**Is the chip still the Claude/category template? No.**
- The lower half carries the driver's two named limits ("tu meta $8", "tu mínimo $6") on two 2dp lines.
- The marker is now a 22 × 4dp bar in the state's hue. It sits at y 481, 498.5 or 515: above the meta line, exactly between the lines, or below the mínimo line.
- It is the same object as the row mark's filled slot, so plate, list rows and A1's legend now read as one system.
- The white caret that 4 of 6 board judges read as a dropdown is gone.
- **What still reads as category:** the band (a green rounded rectangle, a white ▲ and a bold $9.49) is half the plate's mass, and it is what peripheral vision catches first. The distinctive half has to be learned through A1's legend.

**Is the product now comparable in use and feel to Uber Driver?**
- **Feel: largely yes.**
  - Ganancias is now uber-5's figure beside its bars.
  - Hoy is a big number over rows with a grey secondary button, uber-5's "Estadísticas" pattern.
  - The host mock matches uber-2's card.
  - Each tab is recognisable by its shape.
- **Use: not yet shown.**
  - The one failure all six walkthrough judges hit ("Te quedan" as $762 and $548) is fixed on every screen.
  - Several other named problems are fixed in the frames: one name, typeable limits, bold reassurance, "Abrir Uber para probar", and G8's four causes.
  - But the only measurement of use (ease 5.48/7, 6.5/10 against Uber, 5.7/10 understood) is of the Round 15c kit.

**What still falls short:**
1. **The overlay-stopped task:** Hoy cannot show that a fix is waiting (R3), and a semáforo that did not fit on screen leaves no trace on Hoy.
2. **The first day:** A2 is still three paragraphs, about 100 words. This was the lowest task (ease 4.5).
3. **Changing the limits:** it is now about four taps plus typing, but still through the unlabelled person icon, with no door from Hoy (ease 4.8).
4. **The glance:** still not tested on a windshield mount (R14).
5. **The negative day:** drawn nowhere and not yet read by a driver (R4).

## 4. Verdict: PASS (both rubrics)

- **Structural:** 74/80, every criterion ≥ 8.
- **Craft:** 34/40, every criterion ≥ 8.
- **Hard fails:** none.
- **Round 17 delivered what it promised.** The money chain holds across all six screens the brief named, and the cuts, the display step, the status glyphs, the icon set and the marker are all in the frames.

**The stop condition's "in use" half is still unmeasured.** Shortest path:
1. Fix R1, R2 and R5–R7. These are strings and one ledger, under an hour.
2. Draw R3 and R4 (two frames).
3. Re-run the same walkthrough on these sheets: same six judges, same eight tasks.
   - My proposed gate: mean ease ≥ 6.0/7, ease against Uber ≥ 7.5/10, no task below 5.5, and no judge reporting a money inconsistency.
4. Alongside it, re-run my own `242` gate: the eight-screen board with E1 and F1 in both forms.

### Critical Files for Implementation
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-d.html
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-e.html
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-b.html
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/check-fixture.py
- /Users/panama/pilotea/docs/ux-elevation-spec.md