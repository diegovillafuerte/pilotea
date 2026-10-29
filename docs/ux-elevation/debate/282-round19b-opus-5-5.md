# Round 19b — Opus 5.5

**What I checked**
- **Renders:**
  - Sheets A–H and N.
  - Frames at full size: A1, A3b, B2, C6, D1, D3, D3b, E1c, E1d, E3, G2c, G4b, G4c, G4d, G8 and H6. Before-and-after crops of C6.
- **Pixel diff, `r19bf` against `r19f`:** 2 frames are new (G2c, G4d), 63 are identical and 27 changed. The changes sit where the brief says, except that C6 changed and D3b did not.
- **Measurements:** sizes are in dp, measured on the 786 × 1704 renders (2 px = 1 dp).
- **Checkers:**
  - `check-fixture.py`: `156 figures in 54 frames … 5 bar charts → OK`.
  - `make-prototype.py --check`: `122 specs · 122 matched · 0 unmatched · 0 conflicts`. This command also rewrote `prototype.html`.
- **Walkthrough:** read `results-r19.md` (run 4), `driver-test-plan.md` and the prototype's tap map (`LINKS` in `make-prototype.py`).
- **Files written:** none in the repo. I wrote two crops to the scratchpad by mistake: `/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/opus-r19b-c6-before.png` and `/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/opus-r19b-c6-after.png`.

## 1. Are my Round 19 items resolved?

| # | Status | Evidence on the frames |
|---|---|---|
| M1 D1 "$…/km" | **Resolved** | D1 and N3 now read 17:21 **$11.40/km** and 16:04 **$11.04/km**, ending at x 745, on the gutter. The fixture has $236 over 17.0 km and $255 over 17.9 km. $236 ÷ 17.0 − $2.8417 = $11.040, and $255 ÷ 17.9 − $2.8417 = $11.404. Shift 2 still sums to $759 over 58.9 km (14:32 is $148 over 12 km; 14:50 is $120 over 12 km, which gives $7.16). D3's backdrop row shows $11.40. |
| M2 undrawn G4 states | **Partly resolved** | **G4d is drawn.** The status bar reads 13:10 / 70 %, as on B5. It shows the pause bars and "Semáforo en pausa / Se reanuda mañana". The one primary is "Reanudar el semáforo". The suppression row is correctly absent, and B5 → G4d is wired.<br>**The "preparado" state is still undrawn.** In the prototype, B3 and B4 → G4 land on "Semáforo funcionando · Uber · Lectura comprobada · hace 2 min". On B3 that is a 9:44 first run, before any offer has been read. |
| M3 B1 door → G2b | **Not resolved, and now measured** | In run 4, judges 1, 2, 3 and 5 named it (4 of 6). This is the same family as run 3's 5 of 6. B1 is unchanged, and it is the only Hoy state whose door does not open Semáforo. Task 6 averaged 5.5 taps even though a 1-tap route exists, so the door is neither found for the limits nor expected for the status. |
| M4 G4c action and one cause name | **Resolved** | G4c reads "12:10 · no cupo en la pantalla (navegación)" and "No tienes que hacer nada…". G8 reads "No cupo en la pantalla (navegación, oferta alta): no tienes que hacer nada; queda en Tu día". B9 keeps "sin semáforo en pantalla" as the symptom, which matches its status line and G4c's header, so the split between symptom and cause is acceptable.<br>Nit: G4c's row now says the cause twice and grew from 54 to 66 words. Use G8's wording: "No tienes que hacer nada; quedó en Tu día". |
| M5 A3b K tile | **Resolved** (also Codex's hard gate) | The #F2F2F0 tile at x 79–126, y 755–802 is gone; that area is now 1,831 white pixels plus the verde glyph. The K measures 22 × 31 px (11 × 15.5 dp), unboxed, the same K as H6's small icon. |
| M6 G4b clock | **Resolved** | 14:40 / 82 %. The diff is confined to y 48–69. |
| M7 G4c → D1 | **Accepted at caption level** | The G4c caption says the fixture's 19 oct has no such offer. In the prototype, the row still opens a D1 that shows 12:10 as "Abajo de tu mínimo". |
| Strings | **Done, one miss** | A1 now has "neto: ya restamos gasolina y desgaste". "según el lector" is gone from every basis line (E1, E2, E3, E3c, E3d, E7b, E8, F1, F5, N4–N6). It is kept once in E1d's ledger row and in F4's contrast between sources. The miss is D3b (R2 below). |
| Codex E1c | **Resolved** | Sunday is now a bar below $0, running from y 670 to 693 (11.5 dp = 18 % of Friday's 64 dp, so it is to scale). It has the quiet fill and a 3 px (1.5 dp) ink-2 outline open at the line, with "−$214" under it (x 668–744). The days sum: 594 + 736 + 695 + 827 + 1,166 + 677 − 214 = $4,481. |

## 2. Regressions

| # | Frame | Sev. | Evidence | Fix |
|---|---|---|---|---|
| R1 | **C6** | Low–med (overlay surface) | The cause added a third line inside the same 56 dp plate (y 946–1057).<br>**Insets:** in R19 they were 10.5 dp above and 10 dp below. Now "Semáforo apagado" starts 5 dp under the top edge, the baseline of line 3 sits 3.5 dp above the bottom edge, and the "p" of "Kompara" is 1 dp from it.<br>**Break:** line 2 ends on a dangling "·".<br>**On the sheet,** the 13sp lines render at about 5 px. | Grow the plate upward to about 72 dp, keeping its foot at 529 dp. Accept it when the text has at least 8 dp from the plate edge on every side and no line ends on "·". |
| R2 | **D3b** | Low | D3b is pixel-identical to R19, so it was missed. The quiet line behind the sheet still reads "158 viajes tomados según el lector,", while its parent E3 now reads "158 viajes tomados,". The month ledger's first row also lacks the source, which E1d's week ledger now names. | Make D3b's backdrop match E3. Give D3b's first row E1d's sub-line, or drop the source from both. Add the pattern to the checker's stale-pattern list. |
| R3 | **H6 ↔ B2 / N2** | Low | Unifying the cause makes the H6 notice (19 oct 20:31, "Falta el permiso de Accesibilidad") and B2's "desde las 7:50" (20 oct) read as one outage with two start times. | Change B2 and N2 to "desde ayer 20:31", or give H6's notice a different cause. |

**Prototype seams.** These are not kit regressions, but they would contaminate the driver test.

- **P1 — money mismatch.** This is the most important pre-test fix.
  - B1, B7 and B9 are at 14:40 with $548. Their basis link → **D3**, the rows → D2, and "Ver todas…" → **D1**. Those frames sit over the 21:10 day: "9 viajes tomados $1,590 … $1,026", with rows at 14:50, 16:04 and 17:21.
  - Run 4 judges 3 and 6 already named this. The plan's gate fails on any driver who reports a money inconsistency.
  - **Fix:** draw D3 and D1 at 14:40 from `today_at_1440`. D3 would read: 6 viajes $979; Gasolina y desgaste, 76.3 km, −$217; Lo del coche −$214; Te quedan hoy $548. These lines add up exactly, with no rounding note. Keep the 21:10 pair for H6's narrative.
- **P2 — B3 and B4 land on "funcionando"** (M2 above). Draw a "preparado" head: "Tu primera oferta lo confirma", with Uber and DiDi both "Esperando una oferta".
- **P3 — status-bar clock jumps.** B1 is at 14:40, then G1 and G2b at 9:41, then D1 at 21:10. Normalize the clock per task or hide it.
- **P4 — D1 does not scroll.** Tell the facilitator to accept reaching D1 as success on task 3.

## 3. Final scores

| # | Criterion | R19 | **R19b** | Evidence |
|---|---|---|---|---|
| 1 | One job per screen | 10 | **10** | G4d answers "what is off, how do I resume". G2c answers "what does a higher mínimo change". |
| 2 | One primary action | 10 | **10** | G4d's only button is Reanudar. G2c's Guardar is live and Volver is the muted secondary. |
| 3 | Navigation | 10 | **9** | New evidence from run 4. 4 of 6 judges named B1's door, which repeats run 3. Task 6 averaged 5.5 taps against a 1-tap route. B3 and B4 land on an undrawn state. |
| 4 | Numbers discipline | 9 | **9** | M1 and E1c are fixed and both foot exactly. Not 10: B1 shows 6 figures (an accepted trade), and B1 → D3/D1 reaches $1,026 one tap from $548 (P1). |
| 5 | Copy | 9 | **9** | Fixed: "neto" is defined, "según el lector" is gone from the basis lines, and the outage has one cause. Not 10: G4c says the cause twice, C6 ends a line on "·", D3b is stale, and "Te quedan" and "$34 más" remain. |
| 6 | States are designed | 10 | **10** | G4 now has four states (working, battery, suppression, paused), and G2 has three (at rest, editing meta, editing mínimo). "Preparado" is the last undrawn destination. |
| 7 | Time-to-value | 9 | **9** | The structure is right. The first day measured 4.7 in both run 3 and run 4. |
| 8 | Consistency & feel | 9 | **9** | Fixed: the A3b K and the G4b clock. Not 10: C6's insets, D3b's backdrop, the H6/B2 times, and B1 being the only Hoy door that does not open Semáforo. |
| | **Structural** | 76 | **75 / 80** | Every criterion is ≥ 8. |
| 9 | Signature idea | 8 | **8** | Unchanged. Ganancias and Comparar still carry none of the visual idea. |
| 10 | Glance physics | 8 | **8** | C1–C5 and C7–C12 are pixel-identical. C6 is denser but is not a decision surface. There is still no mounted test. |
| 11 | Craft | 9 | **9** | E1c's negative bar is well made (to scale, 1.5 dp outline open at the line). D1's rates sit on the gutter. The K is unboxed. The offset is C6's cramped plate. |
| 12 | Radical simplicity | 9 | **9** | The basis lines are leaner. G4c grew to 66 words. |
| | **Craft** | 34 | **34 / 40** | Every criterion is ≥ 8. |

**Hard fails: none on either rubric.**
- The K square is gone (M5).
- There are no platform logos.
- No onboarding step asks for more than one thing.
- The chip is unchanged.
- The one-second glance is still untested: neither a pass nor a fail.

## 4. Verdict

**Rubrics: PASS on both, 75/80 and 34/40, with no hard fails.** Codex's A3b gate and E1c are resolved, and so are M1, M4, M5 and M6. M2 is half done, M3 is now a measured repeat, and R1–R3 are small.

**Is it comparable in use and feel to Uber Driver?**
- **Feel: yes.** G2b, G4 and E1d use Uber's flat row-and-ledger idiom. The blind boards still put the chip first on Uber-class among 11 designs, and not a template.
- **Use: NOT_YET, and the model-judged walkthrough can no longer settle it.**
  - **The four runs are flat.** Mean ease was 5.48, 5.50, 5.60 and 5.46: an average of 5.51 with a standard deviation of 0.06, which is noise. Ease against Uber was 6.5 to 6.7 against the 7.5 gate, and the first day scored 4.7 twice.
  - **Run 4 also failed the repeat rule twice.** C2 was named by 4 of 6 judges in both run 3 and run 4. Hoy's diagnosis door was named by 5 of 6, then 4 of 6.
  - **19b fixes the one defect that moved a task** (D1, named by 5 of 6, task 3 at 4.8). It touches neither repeat. My estimate for a run 5 is about 5.6: the same plateau.
- **The driver test is the right instrument, with four changes before it runs:**
  1. **Remove the prototype's money mismatch (P1) and B3's false "funcionando" (P2).** Otherwise the plan's no-money-inconsistency gate fails on an artifact of the prototype, not the design.
  2. **Point B1's door at Semáforo, as every other Hoy state does.** The limits are G4's first row, so task 6 costs one extra tap. Log the first tap on tasks 6 and 7.
  3. **Measure Uber directly.** Have each driver do 2–3 matched tasks in their own Uber Driver app (today's earnings, last week, one trip's detail) and give each the same 1–7 ease rating. Define "comparable" as Kompara within 0.5 of Uber on those matched tasks. Keep the 1–10 rating as secondary, because it is anchored by years of habit.
  4. **Run task 8 on a real Android Settings hand-off,** for example the repo's Android build. The prototype's A3b → B3 skips exactly the step that scored 4.7 in runs 3 and 4.
- **Pre-register the decision for the sample size.** With 10 drivers, the 95 % confidence interval on mean ease is about ±0.6. Pass at ≥ 6.0. From 5.6 to 5.99, add five drivers. Below 5.6, fail. Treat a single task under 5.5 as a flag unless it is also a top-named problem.
- **Run the mounted glance test separately,** with an explicit "¿La tomarías?" probe on C2, the repeated defect.

### Critical Files for Implementation
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/make-prototype.py (B1 door → G4; B1, B7 and B9 → D1/D3 at 14:40; B3 and B4 → the "preparado" state)
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-d.html (D1 and D3 at 14:40 from `today_at_1440`; D3b's backdrop and first row)
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-c.html (C6's plate height and insets)
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-g.html (G4 "preparado" head; G4c row trimmed to G8's wording)
- /Users/panama/pilotea/docs/ux-elevation/driver-test-plan.md (Uber baseline tasks, task 8 on real Settings, pre-registered decision for 10 drivers)