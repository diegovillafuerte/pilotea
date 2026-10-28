# Round 19 — Opus 5.5

**What I checked**
- **Renders:** I looked at sheets A–H and N, and at uber-all (uber-1 to uber-6). I read these frames at full size: A1, A3, A3b, B3, B4, B9, D1, D2, D3b, E1d, G1, G2b, G4c and H5b.
- **Pixel diff, `r19f` against `r18f`:**
  - 6 frames are new: A3b, B9, E1d, G2b, G4b and G4c.
  - 53 are identical, including all of C1–C12, A2, E1b and E1c.
  - 31 changed. Most changes are confined to the basis line or the status row.
- **Measurements:** sizes are in dp, measured on the 786 × 1704 PNGs (2 px = 1 dp). Money was recomputed from `fixture.json` at full precision.
- **Checker:** `154 figures in 54 frames, 17 chips and 42 offer rows … 5 bar charts → OK`.
- **Walkthrough:** there is no `results-r19.md`. The spec says Round 19 is "not yet re-scored", and run 4 is the next step. The last measured run is still run 3 on R18: 5.60/7, 6.7/10 against Uber.
- **Files:** none written.

## 1. Scores

| # | Criterion | R18 | **R19** | Evidence on the renders |
|---|---|---|---|---|
| 1 | One job per screen | 10 | **10** | Each new frame answers one question. A3b: "what will I see in Ajustes". G2b: "what are my limits". G4c: "why no semáforo on that offer". E1d: "how the week was computed". The three G4 states share one anatomy and one head each. |
| 2 | One primary action | 10 | **10** | A3b repeats A3's "Abrir ajustes". G2b's Guardar is drawn inactive at rest. G4 and G4c have no filled button because nothing waits. B9 has only the grey secondary. |
| 3 | Navigation | 10 | **10** | The doors now open what they name. B1 → G2b (the limits) is 1 tap. B7 → G4b, B9 → G4c, and every G4 state → G8. Caveats: M2 and M3. |
| 4 | Numbers discipline | 9 | **9** | **E1d:** 675.8 km × $2.8417 = $1,920.4, and 8,400 − 1,920.4 − 1,500 = $4,979.6 → $4,980. **H5b:** 150.6 × 2.8417 = −$428, and the car is off the single-app ledger. **D3/D3b** are km-only. **Not 10:** D1's two newest rows print "$…/km" under the "neto por km" header (M1). B1's first viewport now holds 6 figures ($8, $6, $548, 6, $9.49, $2.20) against the rubric's 3; that is the right trade for task 3, but it counts. |
| 5 | Copy | 9 | **9** | Fixed: "ya restamos gasolina, desgaste y coche" (the "ni coche" misread is gone), A1's "ve cuánto te deja", "Recuperar mis datos", "Ofertas sin semáforo · ninguna hoy", "de $0.10 en $0.10" and H5's "Se guarda". **Not 10:** "según el lector" is still on E1, E3, E7b, E8, F1 and F5 (third round running). "neto" is never defined. "Te quedan" and "$34 más" are still there. There are three wordings of one cause: B9 "sin semáforo en pantalla", G4c "no cupo en la pantalla (navegación)", G8 "No cupo (navegación, oferta alta)". |
| 6 | States are designed | 10 | **10** | Newly drawn: the suppression state on Hoy (B9) and on Semáforo (G4c), the healthy and battery states of G4, and G2 at rest. Caveat: G4 "en pausa" and "preparado" are still undrawn destinations (M2). |
| 7 | Time-to-value | 9 | **9** | A3b draws the hand-off as "Así se verá" with the return ("Al volver, verás «Permiso listo»"). B3 confirms "✓ Permiso listo" and discloses "Usamos costos típicos: $2.84 por km · Ajustar". The bar reads 1/2 on A2 and 2/2 on A3. **Not 10:** no timed install, and the first day measured 4.7 in run 3. |
| 8 | Consistency & feel | 9 | **9** | Doors are on every Hoy state except B2, which has its own primary. G2b's rings measure 96 px = 48 dp, with 17 px ≈ 8 dp between them. A1's legend pitch is still 96 px = 48 dp. **Not 10:** G4b is drawn at 9:41 / 100 % while its source B7 is at 14:40 / 82 % (G4c was matched to B9). A3b's K tile contradicts the spec (M5). B9 keeps the rojo mark beside "sin semáforo en pantalla". |
| | **Structural** | 76 | **76 / 80** | Every criterion is ≥ 8. |
| 9 | Signature idea | 8 | **8** | The chip's limits language now reaches G2b's preview and B3's example. Ganancias and Comparar still carry none of the visual idea. |
| 10 | Glance physics | 8 | **8** | The C frames are pixel-identical to R18. There is still no mounted test. |
| 11 | Craft | 9 | **9** | A3b's highlight ring is a 4 px (2 dp) stroke on a 56 dp row, inset 13 px each side inside the 1.5 dp dashed frame. In D1, "neto por km" sits on the baseline of "Ofertas · 23" (y 771–772 px), and the sixth row is cut by the nav bar at y 1544 under the fade. B1's rows are on a 66 dp pitch and the rates end at x 743–744, on the 746 gutter. **Not 10:** the "$…/km" placeholder; A2's no-break ragged line (unchanged); A3b's K tile; verde still does three jobs. |
| 12 | Radical simplicity | 9 | **9** | The new frames are lean: G4c is 54 words and A3b is 21. G4 still has five groups, and "según el lector" is still repeated. |
| | **Craft** | 34 | **34 / 40** | Every criterion is ≥ 8. |

**Hard fails: none on either rubric.**
- The chip is unchanged (the 240 × 112 plate with both named limits and the marker).
- There are no platform logos: A3b uses grey placeholders and G4 names the apps in words.
- No onboarding step asks for more than one thing.
- **Watch:** A3b draws Kompara's launcher icon as a green K on a 48 px (24 dp) #F2F2F0 tile. The spec's brand line (`ux-elevation-spec.md`:84) says "the K square is gone", and rubric 03 lists "a 'K' square as the brand" as a hard fail. As the OS-list icon slot it is not the in-app brand, but it should not stay.
- The one-second glance is still unverified.

## 2. Round 18 items

| # | Status | Evidence |
|---|---|---|
| N1 G4 state mismatch | **Done**, partly by another route | G4 is drawn healthy, G4b battery and G4c suppression. B1's door now opens G2b; Semáforo is reached by the gear. B3, B4 and B5 still open undrawn G4 states (M2). |
| N2 D1 order and continuation | **Done, with a regression** | Newest-first; the sixth row (11:48) is cut under the nav bar. The two top rows print "$…/km" (M1). |
| N3 G2 at rest | **Done** | G2b: $8.00 / $6.00, verde preview, Guardar inactive, and the steps labelled. |
| N4 G4 wording and route to G8 | **Done** | "Ofertas sin semáforo · ninguna hoy"; "¿No ves el semáforo? ›" on all three states. |
| N5 B3/B4 doors | **Done** | Both have the chevron. Their destination state is undrawn (M2). |
| N6 H5b car scope | **Done** | "Lo del coche se resta de la semana, no de una app." is now a note line. −$428 = 150.6 km × $2.8417. |
| N7 D3b rate | **Done** | "1,688.9 km" only; D3 reads "123.2 km". D2 keeps the 2-decimal "× $2.84", which is fine. |
| N8 progress bar | **Done** | The bar ends at x 406 on A2 (1/2) and at 746 on A3 (2/2). A3b and A4 have none. |
| N9 B8 label and spec count | **Done** | B8 is labelled in the kit, and §9m's count is corrected. The sheets show only ids, so the alternates (B8, B9, G4b, G4c, E1c) rely on the screen list. |

**New items**

| # | Frame | Sev. | Evidence | Fix |
|---|---|---|---|---|
| M1 | D1, N3 (and behind D2/D3) | **Med** (task 3) | The 17:21 and 16:04 rows show "$…/km" in ink-3. These are the newest rows of the list that task 3 asks for ("what each one paid per km"). The fixture gives these two trips only as a combined $491 over 34.9 km. B1 sits at 14:40, so both rows are also in its future. | Split them in the fixture. 16:04: $236 over 17.0 km → $11.04/km. 17:21: $255 over 17.9 km → $11.40/km. Both are verde, the sums are unchanged and $1,026 holds. In SCREENS-r19, describe D1 as "the same day at 21:10". |
| M2 | B3, B4, B5 → G4 | Low–med (task 7) | The captions say the door "opens Semáforo in that state", but G4 "preparado" and "en pausa" are not drawn. B5 → G4 lands on "Semáforo funcionando", the same contradiction as R18-N1. | Draw G4d (pause bars, "Semáforo en pausa · se reanuda mañana", primary "Reanudar el semáforo"). For "preparado", use a one-line head variant. |
| M3 | B1 → G2b | Low (watch) | The healthy row's title is "Semáforo funcionando", and it opens the limits. G2b has no route to Semáforo. A driver whose overlay vanished undetected reaches G8 by gear → Ayuda (2 taps). | Accept for now, and check in run 4 whether judges on task 7 tap the status row first. |
| M4 | G4c, G8 | Low (task 7) | The cause is given ("no cupo en la pantalla"), but not what to do, and the task asks "why and what to do". G8's line has no action either. | Add one line: "No tienes que hacer nada; pasa cuando la navegación llena la pantalla." Use one name for the cause on B9, G4c and G8. |
| M5 | A3b | Low (rubric-sensitive) | The K tile, as described in the hard-fail line above. | Draw the unboxed verde K (as on H6's notification) or a neutral placeholder until fork §7.3 is closed. |
| M6 | G4b | Low | 9:41 / 100 %, against its source B7 at 14:40 / 82 %. | Use 14:40 / 82 %, as G4c does. |
| M7 | G4c → D1 | Low | G4c's row opens D1 at the 12:10 offer, but the drawn D1 shows 12:10 as "Abajo de tu mínimo", not "sin semáforo en pantalla". | Note it in SCREENS-r19 as an alternate destination. |

## 3. The founder's two questions

**Is the chip still the Claude/category template? No, unchanged.**
- C1–C12 are pixel-identical to R18.
- The lower half is still the driver's two named limits with a positioned marker; the band is still the conventional half.

**Is it comparable in use and feel to Uber Driver?**
- **Feel: yes.**
  - G2b and G4 are uber-5's flat list idiom.
  - E1d's "Cómo se calculó · semana" is uber-5's own "Cómo calculamos las estadísticas".
  - Ganancias still pairs a figure with bars as uber-5 does, and the host mock matches uber-2.
- **Use: not shown.**
  - Run 4 has not been done, so the last measurement is run 3 on R18: 5.60 / 6.7 / 5.8.
  - **The biggest gate risk is untouched.** In run 3, 4 of 6 judges named C2 (judges 1, 4, 5 and 6), and C2 is unchanged in R19. The gate fails a version on any defect named by ≥ 4 of 6 judges that the previous run also named. So if four judges name C2 again in run 4, the version fails regardless of the means.
  - **Part of the C2 complaint comes from the test medium.** The tray labels measure 26 px on the 786 px frame (about 13 dp). On the 260 px sheet column that becomes about 8.6 px, which is what the judges reported as "8pt".
  - Run 3's other ≥ 4-of-6 finding (5 of 6 judges: "funcionando" while the overlay was hidden) is answered by B9, G4c and the G8 links.
- My **prediction**, calibrated for my R18 optimism: I predicted 5.9 and run 3 measured 5.6. The fixes I counted on for tasks 6–8 came in 0.5–0.7 under.

| Task | Run 3 | Predicted run 4 | Why |
|---|---|---|---|
| 1 Hoy | 6.2 | 6.3 | The basis line now names what was taken off, and the rows carry their rates. |
| 2 Offer | 5.5 | 5.5 | C2 is unchanged and "neto" is undefined. This task is at the floor. |
| 3 Every offer | 5.7 | 5.8 (6.1 with M1 fixed) | The order and the cue are fixed; two rows print "$…/km". |
| 4 Last week | 6.0 | 6.1 | E1d. |
| 5 Which app | 6.3 | 6.3 | Unchanged. |
| 6 Change mínimo | 5.3 | 5.9 | One tap to G2b at rest; the steps are labelled. |
| 7 Overlay stopped | 5.2 | 5.6 | B9, G4c and G8 help. M2, M3 and M4 hurt. |
| 8 First day | 4.7 | 5.1 | A3b and B3 confirm the steps; there are still about 8 taps through the OS. |

- **Mean ≈ 5.8** (range 5.6–6.0).
- Ease against Uber ≈ 7.0.
- The gate would most likely miss on the first day (below 5.5) and on ease against Uber (below 7.5).

## 4. Verdict: PASS on both rubrics; NOT_YET on "comparable in use"

**Rubrics:**
- Structural 76/80 and craft 34/40, every criterion ≥ 8, no hard fails.
- All nine Round 18 items landed. M1 is the one visible regression.

**In use:** NOT_YET. It has not been measured, and my prediction is below the gate. Shortest path:
1. **Before run 4, about 2 hours:**
   - M1: split the fixture.
   - M2: draw G4d (pause).
   - M4: G4c's "what to do" line and one name for the cause.
   - M5 (the icon) and M6 (the G4b clock).
   - Strings: define "neto" once on A1 under "Ejemplo del semáforo" ("neto: ya sin gasolina ni desgaste"), and drop "según el lector" from the basis lines.
   - Write SCREENS-r19: A3b, B9 (alternate), E1d, G2b, G4 / G4b / G4c, and D1 as "21:10, the same day".
2. **Test fidelity, without changing the gate:** add C1–C3 at phone size to the judges' folder. The task is a phone glance, and the sheet shrinks the tray to about 9 px. Keep the sheets for everything else.
3. **Run 4:** same six judges, same eight tasks, same gate:
   - mean ease ≥ 6.0/7;
   - ease against Uber ≥ 7.5/10;
   - no task below 5.5;
   - no repeat of a defect named by ≥ 4 of 6 judges.
4. **If run 4 misses only on the first day or on ease against Uber,** stop adding static frames. The next evidence should be a clickable first run or real drivers (R16 "later, real drivers"), plus the mounted one-second glance test, which is still open.

### Critical Files for Implementation
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/fixture.json (split $491 / 34.9 km into the 16:04 and 17:21 trips)
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-d.html (D1, D2 and D3 rows: the rates replace "$…/km")
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-g.html (G4d pause state, G4b clock, G4c "what to do" line, one cause name with G8)
- /Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-a.html (A3b icon, "neto" defined on A1)
- /Users/panama/pilotea/docs/ux-elevation/blind-test/walkthrough/SCREENS-r18.md (template for SCREENS-r19 with the new ids and alternate labels)