**1. Round 19b items**

Measured the **786 × 1704 renders at 2px = 1dp**. Prototype evidence is a link-map/runtime audit, the brief’s permitted fallback: Chrome UI access was denied. The generated prototype matches the source map; `check-fixture.py` passes. No files written.

| Item | Resolution and evidence |
|---|---|
| Increments, entry, persistence | **Resolved in code.** ±$0.10; selected numeric entry; equality rejected after Listo; Guardar persists; reset changes only the draft; task starts restore $8/$6. |
| Preparado destination/history | **Resolved.** B3 → G4e awaits the first offer; B4 → G4f preserves “Última lectura ayer 20:10.” |
| G4c → history | **Resolved.** G4c → D1b → D2d retains the 12:10 suppression context and its navigation-space explanation. |
| Snapshot and per-offer detail | **Partly resolved.** B1 → D1a/D3a keeps 14:40, six trips, 4 h 50 min and $548; D2/D2b/D2c distinguish green/red/yellow offers. History still contains only six rows, four taken trips, and three morning details return placeholders. Task 3 can establish list findability, not complete-history usability. |

Measured checks: **D1a/D1b row pitch 66dp**; **C6 tab 240 × 72dp**, three unclipped lines; **B2 recovery button 353 × 56dp**; **G2d stepper outlines 48 × 48dp**. Painted dimensions alone do not establish hit areas.

**2. Remaining defects and test contamination**

| Location | Evidence and fix | Test effect |
|---|---|---|
| Participant page | The [panel prints every “Éxito” answer](/Users/panama/pilotea/design-system/ui_kits/android-app-v2/make-prototype.py:884), including task 7’s status-bar route. Hide answers and facilitator controls from participants. | **Blocker:** contaminates ease, success and unprompted first taps. |
| B1 → Ganancias → Mes → Por semana | E8 shows six trips/$548; [E3d shows “hoy · 9 viajes,” $1,026](/Users/panama/pilotea/design-system/ui_kits/android-app-v2/prototype.html:891), while runtime forces 14:40. Give E3/E3d/D3b the same midshift ledger. | **Blocker:** an exploratory task-4 route can manufacture a money-inconsistency failure. |
| Test decision rule | [“No task below 5.5”](/Users/panama/pilotea/docs/ux-elevation/driver-test-plan.md:23) conflicts with the next paragraph’s conditional exception. Specify one rule combining overall, per-task, comparison and comprehension gates. | **Blocker to interpreting results**, rather than participant contamination. |
| Uber baseline | The [three matched tasks](/Users/panama/pilotea/docs/ux-elevation/driver-test-plan.md:13) include trip detail, but no corresponding Kompara detail task/SEQ is specified. Add that paired probe. | **Blocker to the promised comparison.** |
| E8 → car-cost sheet G5 | [Dismissal goes to Ajustes/G1](/Users/panama/pilotea/design-system/ui_kits/android-app-v2/make-prototype.py:210), not E8. Preserve the opener and return destination. | Secondary navigation contaminant during exploration. |

Also clarify the glance scoring: **C7 is genuinely negative; yellow is not.** The current “no state confused with a loss” wording conflates them. No new visible craft regression established.

**3. Final scores**

| # | Criterion | Score |
|---|---|---:|
| 1 | One job per screen | 9 |
| 2 | One primary action | 9 |
| 3 | Navigation that disappears | 9 |
| 4 | Numbers discipline | 9 |
| 5 | Copy | 9 |
| 6 | States are designed | 9 |
| 7 | Time-to-value | 8 |
| 8 | Consistency & feel | 9 |
| | **Structural — PASS** | **71/80** |
| 9 | Signature idea | 8 |
| 10 | Glance physics | 8 |
| 11 | Craft | 9 |
| 12 | Radical simplicity | 9 |
| | **Craft — PASS** | **34/40** |

Scores remain unchanged for the rendered design. **Hard-fail line: none established in the reviewed renders; the one-second driving glance remains unvalidated.**

**4. Verdict**

**Rubrics: PASS / PASS. Kit + prototype ready for the real-driver test as written: NO**, pending the answer-key concealment, month-snapshot repair and protocol corrections above. The main limits, readiness, suppression and recovery paths are materially improved.

**Comparable in use and feel to Uber Driver: NOT_YET.** Record `180` supports a recognizable signature and competitive visual craft, but four model walkthroughs plateaued at **5.48 · 5.50 · 5.60 · 5.46/7**, with the latest comparison **6.5/10**. These repairs improve the instrument; they supply no new human-performance evidence. The corrected paired driver test, actual Android hand-off and mounted-glance test remain the appropriate next evidence.