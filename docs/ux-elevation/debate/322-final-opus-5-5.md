# Round 24: Opus 5.5 final verdict

**What I checked:**
- **Prototype check.** `make-prototype.py --check`, with `--out` pointed at the scratchpad: 306 specs, 306 matched, 0 unmatched. The regenerated file is byte-identical to the repo's `prototype.html` (sha256 starting `c8f175a1`).
- **Fixture check.** `check-fixture.py` passes: 308 figures in 86 frames.
- **Driving.** I drove the participant harness through tasks 8, 3, 4, 5, 6, 7 and 9, with:
  - task 7 taking the full recovery (shade → B2 → A3 → A3s → B2r → A4r);
  - four back gestures at A3, which all stayed inside the prototype;
  - a page reload in the middle of task 9, which put D2 back over B1 at 14:40;
  - the gear on the first day.
- **Where things are.** Scripts are in `/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/r24-opus/`. Sessions are in `/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/r24-review/o1/` and `/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/r24-review/o2/`.
- **The repo.** Nothing in it was written; the same 24 modified files are there as at the start.

## 1. Regressions and defects from Rounds 21–24

| # | Defect | Frame and evidence | Fix | Contaminates the driver test? |
|---|---|---|---|---|
| 1 | **The D2 comprehension probe now tests string matching, not understanding.** | The plan asks «¿Y cuánto llevas hoy, **con el coche**?» over a row that reads «Llevas hoy, **con el coche**: $548». §9u row 2 says the probe "asks in the row's words". Run 8's defect was about which basis each figure uses: j5 doubted $114 was the real number, and j3 found «te deja» used for two bases. A driver can now pass by matching the words without knowing that $114 leaves out the car. My own `312` wording («ya con lo del coche») had the same flaw; Round 24's mirrored labels make it a pure lookup. This probe is the only evidence that will ever confirm readiness criterion 3, which Round 24 marked fixed without a run 9. | Ask three questions:<br>• «¿Cuánto te dejó este viaje?» → $114<br>• «Esos $114, ¿ya tienen descontado lo que pagas del coche?» → no<br>• «¿Y en todo el día, cuánto te queda ya descontando el coche?» → $548<br>Pass at ≥ 8/10 on each; the second question carries rule 4. | **Yes.** It invalidates rule 4's third probe. Plan text only. |
| 2 | **Task 3's success now requires the driver to read out the column header.** | The plan row and the prototype's facilitator line both say «…y nombra la columna «te deja por km, sin el coche»». The prompt already says «con lo que te deja cada una por km», which is the header's own wording. The header is 13sp `--ink-2`, the size j2, j4 and j5 called small. A driver who reads "$9.49 por km" off D1a has done the task. | Success = D1a reached and its rates read. Record separately whether the driver mentions "sin el coche" without being asked. | **Yes.** Success coding will vary by facilitator. Plan and facilitator text only. |
| 3 | **G4's zero-count row sits on task 6's actual path.** | «No cupo el semáforo en ninguna oferta hoy» opens with a failure ("no cupo") that did not happen, in a coined verb. In run 8, 6/6 judges reached the limits through Hoy's door → G4, and 3/6 read and commented on this row. | Draw the row only when the count is 1 or more, as on G4c. G8's help keeps the explanation. | Low: a possible think-aloud detour in task 6. |
| 4 | **The basis is stated three different ways.** | • **Hoy / Tu día:** the header says "te deja por km, sin el coche"; the rates carry no label.<br>• **E1f:** the header says "te deja por km" (present tense, over a past week whose title says "te dejó"); every rate also says "sin el coche"; and the footnote says "Ya restamos gasolina y desgaste…". That is the basis three times on one sheet (rubric 12).<br>• **D2:** "$114 sin el coche", with the $9.49/km and $285/h below it unlabelled.<br>• **Chip, A1, B3, G2:** "/km neto".<br>j3 asked for one label in one place. §4's rule ("any figure that leaves out the car says 'sin el coche' right after the number") has at least five exceptions in the kit. | • E1f takes Hoy's header and drops the per-row suffix.<br>• Cut "Ya restamos gasolina y desgaste;".<br>• Rewrite §4's rule to describe what the kit actually does, or the build will port the rule and the frames inconsistently. | No; the test measures it. |
| 5 | **Two names for one cost.** | "Lo del coche" is used in G1, G5, the D3 rows and the spec's vocabulary table. The new labels use "el coche". | Note it for R17's think-aloud. | No. |
| 6 | **Uneven row spacing in D2 / D2c** (added in Round 23, kept in 24). | The bottom paddings of the three rows are about 16, 30 and 40 dp. The actions are pinned at 660 dp, so about 40 dp sits empty under the day row. | Even out the row spacing, or pin the actions directly under the day row. | No. |
| 7 | **Carried over, untouched by Rounds 21–24.** | • The first day's gear → G1 reads "Semáforo · Funcionando" and "Lo del coche $1,500" (`295` item 7).<br>• B2r reads 8:07 in the kit and 8:01 in the prototype.<br>• **The 14:32 trip is still under way at 14:40,** yet it counts toward "6 viajes" and $548 and is task 9's "último viaje". Uber's matched task will show a completed trip's payout, so task 9 compares an estimate with a paid amount.<br>• **Hidden fixture flaw:** DiDi's $235 at 14:40 is not the sum of any of today's trips, and G4 shows "DiDi · Esperando una oferta". No screen shows which app each trip came from, so no driver can see it. | Record the task-9 mismatch as a limitation of rule 2. Fix the fixture before it becomes build test data. | No. Model-only routes or not visible. |

**No regressions from Rounds 21–24 elsewhere:**
- The limits editor works end to end: + → Guardar → «Guardado: tu mínimo ahora es $6.50/km» → Hoy shows the new pair. Limits reset at tasks 8, 1 and 6.
- B2r → A4r reads "permiso listo"; Round 23's open item is fixed.
- D3a adds up: $979 − $217 − $214 = $548. E1f's rates check against the week's 675.8 km.
- The figure and its basis now sit on one line, in the harness text and on screen.

## 2. Final scores

| # | Criterion | Score | Why |
|---|---|---|---|
| 1 | One job per screen | 10 | D2 is still one trip; its day row is a door, not a second hero. |
| 2 | One primary action | 10 | B2r and A3s are clean. |
| 3 | Navigation | 10 | E1f replaced the silent jump to Comparar. Task 6: 4.0 taps, 0 misses, 6/6 by the same door. |
| 4 | Numbers discipline | 9 | Everything adds up. Holding at 9: the trip still under way counts as taken, plus the hidden DiDi split. |
| 5 | Copy | 9 | "Llevas", dated "ayer" and the basis beside the figure are all better. Holding at 9: "no cupo" in the zero state, two names for the basis (neto / sin el coche) and for the cost (lo del coche / el coche). |
| 6 | States are designed | 10 | The recovery is drawn (A3s, A3n, B2r, A4r). |
| 7 | Time-to-value | 9 | Task 8 is still the lowest task (5.67). The real Settings hand-off is unmeasured. |
| 8 | Consistency & feel | 9 | Three placements of the basis, the first-day G1 leak, and D2's row spacing. |
| | **Structural total** | **76/80** | |
| 9 | Signature idea | 8 | Unchanged. |
| 10 | Glance physics | 8 | "Cumple tu mínimo" is shorter. There is still no mounted test. |
| 11 | Craft | 9 | D2's row spacing is a nit. |
| 12 | Radical simplicity | 9 | E1f's basis said three times is the line to trim. |
| | **Craft total** | **34/40** | |

**Hard fails: none.** No auto-action, no platform logos, no K square, no paywall that blanks the free experience, no onboarding step with more than one ask. The one-second glance remains untested: neither a pass nor a fail.

## 3. Verdict

**(a) Rubrics: pass both, 76/80 and 34/40, with no hard fail.** The scores are unchanged from Round 20b. Rounds 21–24 fixed real findings and added only small craft and copy debt.

**(b) Ready for the real-driver test: yes for the kit and the prototype.** Two plan-text edits must be made before session one; neither needs a kit change:
1. Replace the D2 probe with the neutral three-question version in item 1, so the basis is tested rather than its label.
2. Restore task 3's success line to "D1a reached and its rates read", in both the plan and the prototype's facilitator line.

Recommended but not blocking: hide G4's zero-count row. The plan's existing preconditions still stand:
- the real `android/` build installed with `adb install` and rehearsed on the test phone;
- a 412-wide phone;
- task 7 stopped at A3.

**(c) The goal.** What the evidence supports now:
- **Craft and structure: comparable to the market's benchmarks, as expert review measures them.**
  - Both reviewers pass both rubrics on one version.
  - Model-judged blind tests rank the chip and Hoy first on Uber-class discipline against category templates.
  - On a working prototype, model drivers reached 54 of 54 answer screens with 0 misses.
  - The matched tasks take 0, 2 and 1 taps, as short as Uber Driver's equivalent paths or shorter.
- **"Comparable in use to Uber" cannot yet be claimed.**
  - **No human has used it.** Every judge so far is a model, including the blind-test judges.
  - **The ease scores are compressed and react to the instrument.** Run 8's rise from 5.94 to 6.17 came on tasks 2, 3 and 6, whose prompts, harness or instrument changed. Task 6 had no design change and still went from 5.67 to 6.17.
  - **The wider Uber margins came from the Uber side.** Kompara's own scores on tasks 1, 4 and 9 were identical in runs 7 and 8 (6.83 / 6.17 / 6.00). The margins grew only because the models' guesses about Uber fell (5.83 → 5.17, 4.67 → 4.17, 4.83 → 4.50), and those guesses come from two screenshots.

What remains before the claim needs no qualification:
1. **Real drivers.** The pre-registered test passes: mean ease of 6.0 or higher; Kompara within 0.5 of the same drivers' own Uber ease on tasks 1, 4 and 9; the neutral probes; no money inconsistency. Task 8 is judged on the real Settings hand-off.
2. **The glance.** The mounted one-second test (R14) shows la regla at least as accurate as the generic control, day and night, with no driver answering "abajo" for ◆.
3. **The live app.** On a real phone, the build shows the chip in time and correctly on live Uber and DiDi offers, and install → "preparado" is measured. In this category, "feel like Uber" is mostly whether the overlay shows up right when the offer does, and no kit can show that.

Until then the defensible sentence is: **"designed to the Uber Driver bar and ready to be tested against it."**
