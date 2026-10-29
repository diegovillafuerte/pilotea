# Round 20 — Opus 5.5

**What I checked.** Frames at full size: B1, B2, B3, B4, B6, B9, C6, C13, D1a, D2b, D2c, D2d, D3a, D3b, E1, E1d, E3, E3d, E8, G2b, G2d, G4, G4c, G4e, G4f, G4g and H6b, plus sheet N. All measurements are on the 786 × 1704 renders (2 px = 1 dp).

Checker results:
- `check-fixture.py`: "199 figures in 70 frames … 66 stale patterns … → OK".
- `make-prototype.py --check`, run with `--out` set to the scratchpad so `prototype.html` was not rewritten: 175 specs, 175 matched, 0 unmatched. Its output is byte-identical to the repo's `prototype.html`, and `index.html` equals the concatenated parts.

I drove the prototype in headless Chrome with real CDP clicks, reusing `cdp.mjs`:
- all eight tasks, plus a second route for each of tasks 6 and 7;
- every Hoy door, B9's rows, G4c → D1b → D2d, and the Mes branch;
- a swipe attempt on C13 (mouse drag and touch);
- a scroll probe;
- a dead-tap audit of every screen a task can reach;
- a phone-width layout (412 × 915).

The scripts are in `/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/r20-opus/`. Nothing was written in the repo.

## 1. My Round 19b items

| # | Status | Evidence |
|---|---|---|
| R1 C6 plate | **Resolved** | 240 × 72 dp plate at 457–529 dp. Text insets: 11.5 dp top, 10.5 bottom, 14.5 left. Three lines (468.5–488.5, 492–503.5, 507–518.5 dp). No line ends on "·". It now says the cause it can actually show: "Dejamos de leer tus ofertas". |
| R2 D3b | **Resolved** | The backdrop reads "158 viajes tomados,". The first row reads "antes de gasolina, desgaste y coche", as D3, D3a and E1d do. No frame contains "según el lector". "lector" survives only off the task paths (E3b "antes del lector", E6, F4). |
| R3 one outage start | **Resolved** | B2 and N2 read "Semáforo apagado · desde ayer 20:31". H6b reads "Kompara · ayer 20:31 · Se apagó el semáforo". |
| P1 money mismatch | **Partly resolved** | **Fixed on the Hoy → Tu día branch.** Driven in task 3: B1 → D1a at 14:40, $548. D2 and D2b sit over D1a (backdrop "$548 · 6 viajes tomados en 4 h 50 min"). D3a reads $979 − $217 − $214 = $548 and foots: 76.3 km × 2.8417 = 216.8; 1,500 ÷ 7 = 214.3.<br>**Still broken one branch over.** Driven in task 4 at 14:40: B1 → Ganancias → Mes → Por semana. E3d reads **"19 oct · hoy · 9 viajes $1,026"**, E3 reads "$12,169 · 158 viajes", and D3b matches them. B1 and E8 say 6 viajes, $548. This is the same defect, three taps from the task-4 start (N2 below). |
| P2 preparado | **Resolved** | B3 → G4e at 9:44: "Semáforo preparado / Tu primera oferta lo confirma", Uber and DiDi "Esperando una oferta". B4 → G4f at 7:30: Uber "Última lectura ayer 20:10". In task 8, B3 → G4e at 12:30. |
| P3 clock jumps | **Resolved inside tasks** | Task 4: E8, E1, E1d, E3, E3d and D3b all read 14:40. Task 7: C13, H6b, B2, G1 and A3 all read 8:01. Task 8: six screens at 12:30.<br>Outside a task, G4d's Reanudar goes from 13:10 to G4 at 14:40, and the G2 screens stay at 9:41. Harmless. |
| P4 D1 scroll | **Changed, not resolved** | D1a's content now scrolls only 40 dp (scrollHeight 704 against 664) and **stops at 10:02**. The list "todas las ofertas de hoy" visibly ends with 4 of the 6 trips taken and neither of the "2 ofertas verdes se fueron sin tomar". This contradicts the fixture's note that the list "visibly runs on toward 08:00". The plan carries no facilitator note for it. |

My §4 conditions:
- **B1's door now opens G4.** Driven; the first tap is logged as "Semáforo funcionando … @B1 -> G4".
- **The first tap is logged for tasks 6 and 7.** Verified.
- **The Uber baseline, task 8 on real Settings and the pre-registered rule are in the plan,** but see N3 and N10.

## 2. New defects and regressions

**Blockers: these contaminate the test as written.**

**N1. The task answers are on screen next to the phone.**
- **Laptop (1400 × 1000):** the facilitator panel sits beside the phone and lists every task's "Éxito" path during the task. While task 7 runs it shows "… → B2 → Volver a dar el permiso → A3".
- **Phone (412 × 915, `?fit=1`):** the panel starts at y 915, the bottom of the viewport. The facilitator has to scroll the participant's device to press Iniciar or Hecho, which exposes the same answers.
- **Fix:** add a participant mode that hides the "Éxito" lines and the frame-ID bar, or move the panel to a second window synced through storage events, with keys for Hecho and Atascado.
- **Contaminates every task.**

**N2. The Mes branch is still at 21:10 under a 14:40 clock** (P1 above).
- **Fix:** draw E3, E3d and D3b at 14:40.
  - Por semana: "19 oct · hoy · 6 viajes $548", and October ≈ **$11,692** (1,417 + 4,747 + 4,980 + 548), 155 viajes.
  - D3b: gross $20,429 (21,040 − 759 + 148); 1,642.0 km, −$4,666; car −$4,071; total $11,692, which foots.
- **Contaminates:** it trips the plan's "no money inconsistency" gate, and it is reachable in task 4.

**N3. The plan has three decision rules that disagree.**
- **The gate** keeps "ease vs Uber ≥ 7.5/10" and "no task below 5.5" as hard conditions.
- **The Uber-baseline paragraph** demotes the 1–10 rating to secondary.
- **The pre-registered rule** makes a single task under 5.5 only a flag.
- **"Comparable within 0.5" has no task mapping:** no Kompara task matches "one trip's detail".
- **Fix:** keep one rule. Map task 1 ↔ today's earnings and task 4 ↔ last week. Add "Abre el detalle de la oferta de las 14:32" (B1 → D2, one tap) ↔ Uber's trip detail. Put Uber first for half the drivers.
- **Contaminates the analysis, not the session.**

**Fix before the test: these bias the measures but do not void them.**

**N4. Task 7's route measure is biased toward the host bar.**
- **Swipe does nothing:** a drag down from C13's status bar logs a miss ("8:01K 100% … @C13 -> undefined"), and a touch swipe has no effect.
- **The host button stands out:** "Abrir Kompara" is the only bright control above the phone.
- **On a real phone,** a swipe from the top opens the test phone's own notification shade.
- **Fix:** make a drag down from the top 24 dp open H6b, and style the host control like "← Atrás".

**N5. Task 7's world leaks the normal day.**

| Route | What it shows | What it contradicts |
|---|---|---|
| B2 → ⚙ → G1 → Semáforo row | G1 "Semáforo · Funcionando"; G4 "Semáforo funcionando · Lectura comprobada · 14:32", at 8:01 | the permission-lost outage |
| B2 → Ganancias | E8 "$548 · 6 viajes · 19–25 oct en curso" | B2 "Ayer te quedaron $1,026" |
| H6b "Octubre" → E3 | "hoy · 9 viajes" | B2 "Aún no registramos viajes hoy" |

- The kit has no Semáforo screen for "apagado".
- **Fix:** draw G4h, "Semáforo apagado · Falta el permiso de Accesibilidad", with primary "Volver a dar el permiso" → A3. Make G1's row read "Apagado" in this world. Give E8 and E3 a 20 oct state, or the neutral note.

**N6. D1a's list ends at 10:02** (P4).
- **Fix:** draw the remaining four rows: the two trips worth $336 over 25.4 km, and the two verdes that left. Or lock the scroll so the list reads as running on.

**N7. Detail sheets keep the old limit after a save.**
- Driven: task 6 via B1 → 12:10 → D2b → "Cambiar tu meta y tu mínimo" → G2b → five taps of + → Guardar → back. D2b still reads "abajo de tu mínimo de **$6.00**/km" while the door reads "tu mínimo $6.50".
- That route is one of the ways in that §9p row 13 lists, so "task 6 does not pass through them" is wrong.
- **Fix:** paint the D2 sheets from the saved pair.

**N8. Silent dead taps on task paths, all rows with a chevron:**
- E8's basis line (task 4 passes it);
- E1 "Tu resumen de Uber dice $34 más";
- F1 "A distintas horas en cada app";
- G4 "Qué lee Kompara".

Other rows answer with the neutral note. **Fix:** give these the same `NO_DETAIL` note.

**N9. D3a opened from B1, B7 or B9 sits over Tu día, not over Hoy.**
- D3a is not in `LIVE.backdrop`, which breaks the brief's own rule that a sheet sits over the screen that opened it.
- **Fix:** add D3a to the backdrop list.

**N10. Task 8's split is unspecified.**
- The panel still runs A1 → B3 in the prototype, while the plan says to run task 8 on a real phone.
- **Fix:** state which steps run on which device, and where the success, the time and the ease rating are recorded.

**Kit nits, no contamination:**
- D2d's "Sin semáforo en pantalla: no cupo en la pantalla (navegación)" says "pantalla" twice. Use "Sin semáforo: no cupo en la pantalla (navegación)".
- D1's heading reads "Ofertas · 23", while D1a and D1b read "Ofertas de hoy".
- "lector" remains in E3b, E6 and F4.

## 3. Final scores

| # | Criterion | R19b | **R20** | Why |
|---|---|---|---|---|
| 1 | One job per screen | 10 | **10** | G4e, G4f, G4g, D1a, D3a and D2d each answer one question. |
| 2 | One primary action | 10 | **10** | G4e has no primary, by design; "Pausar…" is muted. |
| 3 | Navigation | 9 | **10** | Every Hoy door except the off one opens Semáforo in its state. The measured repeat is gone. Task 6 is door → G4 → limits. |
| 4 | Numbers discipline | 9 | **9** | D1a, D3a, D2b, D2c and D2d foot. B1's six figures are an accepted trade. The prototype's Mes branch is N2. |
| 5 | Copy | 9 | **9** | Fixed: G4c is down to 57 words and C6 is clean. Not 10: D2d's double "pantalla", "lector" in E3b/F4, "Te quedan" and "$34 más". |
| 6 | States are designed | 10 | **10** | Preparado (two variants), solo Uber, number entry, the outage without the plate (C13 and H6b). One gap: Semáforo "apagado" (N5). |
| 7 | Time-to-value | 9 | **9** | Structure unchanged. The real Settings hand-off is still unmeasured. |
| 8 | Consistency & feel | 9 | **9** | D1 and D1a headings differ, D3a's backdrop (N9), and the G2 screens sit at 9:41. |
| | **Structural** | 75 | **76 / 80** | Every criterion ≥ 8. |
| 9 | Signature idea | 8 | **8** | Unchanged. |
| 10 | Glance physics | 8 | **8** | The chip is unchanged. There is still no mounted test. |
| 11 | Craft | 9 | **9** | C6's insets are 11.5, 10.5 and 14.5 dp. The D2 sheets keep the 24 dp inset. |
| 12 | Radical simplicity | 9 | **9** | G4c has one action line. |
| | **Craft** | 34 | **34 / 40** | Every criterion ≥ 8. |

**Hard fails: none.**
- No K square: A3b's K is unboxed, and C13's K is a status-bar glyph.
- No platform logos.
- No onboarding step asks for more than one thing.
- The chip is unchanged.
- The one-second glance is still untested: neither a pass nor a fail.

## 4. Verdict

**(a) Rubrics: PASS on both, 76/80 and 34/40, with no hard fail.** R1–R3, P2 and P3 are resolved. P1 is resolved on the branch that was named and survives on the Mes branch. P4 has changed shape.

**(b) Ready to run as written: not yet. Three blockers, each about a day's work at most, none of them design:**
1. **N1:** the participant can see the success paths.
2. **N2:** a 21:10 month under a 14:40 clock ("hoy · 9 viajes $1,026" against $548). It sits on task 4's path and fails the money gate.
3. **N3:** the plan's three decision rules disagree, and the Uber matched-task mapping is undefined.

N4–N10 should go in the same pass, because each biases one task's measure: the shade route, the task-7 leaks, the list ending at 10:02, the stale $6.00, the silent chevrons, and the task-8 split. Once those land, run it.

**(c) Comparable in use and feel to Uber Driver?** **Feel: yes.**
- The kit now behaves like one app at one minute: Hoy → Tu día → each offer's own detail, all at 14:40. That is the same drill Uber uses (today's pill → trip list → trip detail), in the same flat row-and-ledger idiom.
- The outage is told truthfully through the channel Android actually leaves open: the notification, then the off Hoy, then the permission steps.

**Use: still NOT_YET, and still unmeasured.**
- The model walkthroughs plateaued around 5.5 / 7 and cannot settle it.
- On the matched tasks (today's earnings, last week), Kompara is 0–2 taps, which is Uber's distance, so those should compare well.
- Where it can fall behind Uber:
  - the first day's hand-off to Android Settings, where Uber has no equivalent;
  - task 6's two-step route to the limits;
  - task 7's shade gesture.
- The real-driver test is the right instrument, provided it runs on a prototype that neither shows the answers nor contradicts its own money.
