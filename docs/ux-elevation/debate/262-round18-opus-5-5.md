# Round 18 — Opus 5.5

**What I checked**
- **Renders:** sheets A–H and N, and uber-1, 3, 5 and 6. I read these frames at full size: A1–A4, B1, B3, B4, B6–B8, D1–D3b, E1c, E3d, G1, G2, G4, H5b, and G2 from R17.
- **Pixel diff, `r18f` against `r17f`:** three frames are new (B7, B8, E1c). 35 frames are identical, including C1, C2, D1, D3 and H5. On the other tab roots the only change is the gear (x 678–718, y 140–180). C3 and C7 differ only by the rojo marker.
- **Measurements:** sizes are in dp, measured on 2× PNGs. Money figures were recomputed from `fixture.json` at full precision.
- **Checker:** `148 figures in 52 frames, 16 chips and 31 offer rows … 23 spec strings … 5 bar charts → OK`.
- **Walkthrough:** it was not re-run on these sheets. There is no `results-r18.md`, and spec §9m says "This version has not been re-scored".
- **Files:** none written.

## 1. Scores

| # | Criterion | R17 | **R18** | Evidence on the renders |
|---|---|---|---|---|
| 1 | One job per screen | 10 | **10** | B7 and B8 each answer one question. G4 now holds five groups: status, limits, the per-app rows, suppression and "Qué lee". That is acceptable for a drill-in, but it is the screen to watch. |
| 2 | One primary action | 10 | **10** | B7 has no filled button; the fix is G4's primary. B8 has no button. A3's primary is "Abrir ajustes". B4's is "Sí, son mis costos". |
| 3 | Navigation | 10 | **10** | The gear is on all three roots, and G1 is titled "Ajustes". The limits are 2 taps from Hoy: B1 → G4 → G2 through the door, or B1 → G1 → G2 through the gear. Before, it was 6.8 mean taps. Caveat: N1. |
| 4 | Numbers discipline | 9 | **9** | R1 and R2 are fixed. D3b: 21,040 − 4,799.29 − 4,071.43 = 12,169.28. B8: 113.90 − 214.29 = −100.39. E1c: the days add to exactly $4,481. Not 10: D3b prints a four-decimal rate, "× $2.8417" (N7). B1's first viewport now has four numbers ($8, $6, $548, 6), above the rubric's three; that is a fair price for the door, but it counts. H5b has a scope problem (N6). |
| 5 | Copy | 9 | **9** | A2 went from about 100 words to 59. G2's helper went from 20 words to 5 ("Toca la cifra para escribirla."). There is now one word for off, and D2's third name for the limits is gone. Not 10: "según el lector" still appears on E1, E3, E8, F1, F4 and F5. "Te quedan" and "$34 más" were flagged by the judges and remain. G4 says "Sin semáforo en pantalla hoy / Ninguna hoy" (N4). |
| 6 | States are designed | 9 | **10** | Both R17 gaps are drawn: the fix-waiting state (B7) and the negative day (B8, E1c). Each degraded state has one line and one fix, and none uses red or amber. Caveat: two default destinations are undrawn (N1, N3). That is a walkthrough risk, not a gap in state design. |
| 7 | Time-to-value | 8 | **9** | The first run is now A1 → A2 → A3 (→ A4) → B3. A5, A6 and A7, including the OTP keypad, are off the path. A1 shows a simulated verdict before any ask. A2 is Play's required disclosure. A3's dialog is an inert, dashed example labelled "Así se verá". Not 10: Android's settings hand-off and the return are still undrawn (3 of 6 judges asked for them), the bar stops at 75 % on the common path (N8), and there is no timed install. |
| 8 | Consistency & feel | 9 | **9** | R8, R9 and R11 are fixed. New slips: the status row is a door on only 5 of 8 Hoy states (N5); the progress bar (N8); B8 is not labelled as an alternate (N9). |
| | **Structural** | 74 | **76 / 80** | Every criterion is ≥ 8. |
| 9 | Signature idea | 8 | **8** | The chip's limits wording ("tu meta $8 · tu mínimo $6") now also appears on Hoy's door, on G4 and on G1. The visual idea still stops at the row marks; Ganancias and Comparar carry none of it. |
| 10 | Glance physics | 8 | **8** | The chip is pixel-identical on C1 and C2. There is still no mounted one-second test. |
| 11 | Craft | 9 | **9** | The marker is now symmetric. Verde runs y 962–971 against the meta line at 980–983, a 4dp gap. Rojo starts at 1032 under the mínimo line at 1020–1023, also 4dp. Amarillo has 13 px above and below. The tray margins are 8/8dp. The tints are tokens: #2FB35A, #F2B705, #FF5C5C. Not 10: A2's `captura&nbsp;de&nbsp;pantalla` makes line 2 end 94dp short; D3b's four-decimal rate; verde still does three jobs. |
| 12 | Radical simplicity | 9 | **9** | The cuts landed: A2, the G2 helper and E3d's sub-line. G4's growth and the repeated "según el lector" hold it at 9. |
| | **Craft** | 34 | **34 / 40** | Every criterion is ≥ 8. |

**Hard fails: none on either rubric.**
- The chip is still the 240 × 112 plate: band over tray, both named limits, and the marker.
- No platform logos: G4 names the apps in words.
- No onboarding step asks for more than one thing.
- The one-second glance is still unverified.

## 2. Round 17 items

| # | Status | Frame evidence |
|---|---|---|
| R1 | **Done** | D3b now opens on D3's four lines: $21,040 · −$4,799 · "$1,500 ÷ 7 × 19 días" −$4,071 · $12,169, with "las tres cifras suman $12,170". New nit: N7. |
| R2 | **Done** | E3d reads "4 de 7 días del coche" only. The checker no longer asserts $774. |
| R3 | **Done** | B7 shows the ring with "!" and "Batería restringida · aún funciona ›", and spec §4 and §5.1 cover it. G1 and G4 use the same words. Gap: N1. |
| R4 | **Done** | B8 shows −$100 in ink at 72sp. E1c shows Domingo at −$214 with no bar above $0. Gap: B8 is unlabelled (N9). |
| R5 | **Done, but my fix was mis-scoped** | H5b now has "Lo del coche −$1,500", but the ledger is Uber-only ("28 sep – 4 oct · Uber"). DiDi's 13 trips ($1,470) are absent, so the whole week's car is charged to one app. That breaks B6's own rule, "never charged to one app". See N6. |
| R6 | **Done, by the reverse route** | The kit's "Tu resumen de Uber dice $34 más" is now canonical; the spec matches it and the checker asserts it. The judges' complaint about the wording is still open. |
| R7 | **Done** | G2 has the 5-word helper and the label "Decides por:". |
| R8 | **Done** | Gaps are 4/4dp, as measured in §1. The tints are tokens. |
| R9 | **Done** | Spec §4 now names the status-ring family as the exception to "no circled glyphs". |
| R10 | **Done** | C6 says "Semáforo apagado". The diff is confined to that text. |
| R11 | **Done** | `check-fixture.py:141` now reads "day bars after the car share". |

**New items**

| # | Frame | Sev. | Evidence | Fix |
|---|---|---|---|---|
| N1 | G4 | **Med** (walkthrough) | G4 is drawn only in the battery-restricted state. It is still the destination of the door on every Hoy state: B1, B6, B8 and N1 ("funcionando") and B5 ("en pausa"). A judge going B1 → G4 sees "Batería restringida", which contradicts B1. | Draw G4 healthy (green dot, "Semáforo funcionando", no filled primary) as B1's destination, and keep the current frame as B7's (G4b). |
| N2 | D1 | **Med** (task 3, ease 5.5) | D1 is pixel-identical to R17: "Ofertas · 23" over 4 rows with empty space below. The 21:10 list starts at 14:50, although the second shift ran until 18:00. The renamed button promises "todas" and lands on 4 rows. | Make the list newest-first and let it run under the nav bar with a cut row, or draw a scrolled D1. |
| N3 | G2 | Low–med (task 6) | G2 is drawn only mid-edit: meta $9.50, amarillo preview, Guardar live. This is the R17 judge's exact complaint. | Draw G2 at rest ($8.00 / $6.00, verde preview, Guardar inactive) as the destination of the door and the gear. |
| N4 | G4 | Low (task 7) | "Sin semáforo en pantalla hoy / Ninguna hoy" reads as a double negative with "hoy" twice. There is no route from G4 to G8's causes; the spec lists this as open. | Change to "Ofertas sin semáforo · ninguna hoy" and add "¿No ves el semáforo? ›" → G8, open on that answer. |
| N5 | B2–B4 | Low | The status row is a door on B1 and B5–B8 but a plain label on B2, B3 and B4. On B4 the gear is the only route to Semáforo or the limits. | Add the chevron on B3 and B4 (→ G4). B2 may keep its primary. |
| N6 | H5b | Low (my R5) | The whole week's car is set against one app's statement (see R5). It prints no wrong figure only because "Te quedaron" is "—". | Drop the car row and keep "Lo del coche se resta de la semana, no de una app" as a sub-line. Alternatively, label the ledger for both apps and add DiDi's row. |
| N7 | D3b | Low | "1,688.9 km × $2.8417" puts four-decimal pesos in user copy; D3 prints "× $2.84". | Show "1,688.9 km" only; the rate lives in Tus costos. Do the same on D3 for symmetry. |
| N8 | A2–A4 | Low | The bar reads 50 % on A2 and 75 % on A3, and A4 has none. On the common path (no battery restriction) the bar never completes. | Make the bar 1/2 and 2/2 over A2–A3, and leave A4 unbarred as an extra step. |
| N9 | B8, spec | Low (record) | E1c and E3c carry "Escenario alterno, no es pantalla" above the phone; B8 does not, although §9m row 4 says both are labelled. Unlabelled, B8 at 14:40 contradicts B1 at 14:40. §9m also says the checker reports "146 figures in 51 frames", but it prints 148 in 52. | Add the label to B8 and to the walkthrough's screen list. Fix the count in §9m. |

## 3. The founder's two questions

**Is the chip still the Claude/category template? No, unchanged from R17.**
- The lower half carries the driver's two named limits and the marker bar. The marker is now optically exact (4/4dp, centred amarillo).
- Those limits now appear on Hoy's status door too.
- The band (a green rounded rectangle, ▲ and a bold $9.49) is still half the plate's mass. Peripheral vision catches it first.

**Is it comparable in use and feel to Uber Driver?**
- **Feel: yes.**
  - The filled gear matches uber-5's filled "?".
  - Ganancias is uber-5's figure beside its bars.
  - Hoy's grey full-width secondary is uber-5's "Estadísticas" button.
  - The host mock matches uber-2.
- **Use: not shown. The walkthrough was not re-run on R18.**
  - The R18 doors fix five named failures: Ajustes behind a gear, the limits on Hoy, the «control total» line, "Ver tu día", and D2's third name.
  - Four named failures are still on the frames: D1 (N2), G2 mid-edit (N3), the G4 state mismatch (N1), and the undrawn Android hand-off.
- My **prediction**, which is not a measurement:

| Task | R17 ease | Predicted R18 | Why |
|---|---|---|---|
| 1 Hoy | 6.2 | 6.3 | Unchanged, plus the door. |
| 2 Offer | 5.8 | 5.8 | C2 is unchanged. |
| 3 Every offer | 5.5 | 5.8 | The button label is fixed; D1 is not (N2). |
| 4 Last week | 5.8 | 5.8 | Unchanged; Ganancias still opens on the week in progress. |
| 5 Which app | 6.3 | 6.3 | Unchanged. |
| 6 Change mínimo | 5.0 | 6.0 | About 3 taps to G2, then type and Guardar. N3 remains. |
| 7 Overlay stopped | 5.2 | 5.8 | The door and B7 help; N1 and N4 hurt. |
| 8 First day | 4.2 | 5.2 | Three fewer screens and a shorter A2. The Settings hand-off is still undrawn. |

- **Mean ≈ 5.9.** That would miss the gate (≥ 6.0, and no task below 5.5) on the first day.

## 4. Verdict: PASS on both rubrics; NOT_YET on "comparable in use"

**Rubrics:**
- Structural 76/80 and craft 34/40, every criterion ≥ 8, no hard fails.
- Every Round 17 item landed. R5 needs re-scoping (N6).

**In use:** NOT_YET, because it has not been measured. Shortest path:
1. **Frames before run 3** (about half a day):
   - G4 healthy as B1's destination (N1).
   - G2 at rest (N3).
   - D1 with a scroll cue and newest-first order (N2).
   - An "Así se verá" frame of Android's Accessibility list with Kompara marked, plus A3's return tick (task 8).
   - The alternate label on B8, and a screen list for the R18 walkthrough (N9).
2. **Strings, under an hour:** N4, N5, N6, N7 and N8.
3. **Run 3:** same six judges, same eight tasks, gate unchanged:
   - mean ease ≥ 6.0/7;
   - ease against Uber ≥ 7.5/10;
   - no task below 5.5;
   - no judge reporting a money inconsistency.
4. **Still open:** the mounted one-second glance test.

### Critical Files for Implementation
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-g.html (G4 healthy, G4b, G2 at rest, the G4 suppression row)
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-d.html (D1 scroll and order, D3b rate)
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-b.html (B8 label, B3/B4 door chevrons)
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-a.html (progress bar, the Accessibility-list example, the A2 line break)
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-h.html (H5b car scope)