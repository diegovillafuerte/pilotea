**Round 15c — Codex Astra: PASS. Structural 71/80; craft 34/40.** The Round 15b signature blocker is resolved. Two residuals remain.

Measurements below use the supplied 2× renders, excluding shadows.

**1. Blocker resolved.** C12’s compact plate measures **480 × 160px = 240 × 80dp**, at **y492–652px**. Both named limits and their relational marker are visible. The phrase disappears; the distinguishing idea survives. Signature returns **7 → 8**.

The puck ring ends at **y454px** and the host card starts at **y684px**: **19dp above and 16dp below**. Money ink measures approximately **45px on the chip versus 87px on the host fare**; the host retains priority.

The compact colour and greyscale proofs preserve ▲ above, ◆ between and ▼ below the limits. Both grey variants retain dashed lines without a cue or marker, remaining distinct from C6’s lineless stopped-reader tab. This establishes distinct rendered patterns, not one-second recognition.

**2. Regressions and residuals.**

| Frame / evidence | Finding and required follow-through |
|---|---|
| **Compact proof, 1.3×, day and night** | The **312 × 104dp** plate occupies **y222–326dp**; the puck ring ends at **227dp**. It therefore **covers the bottom 5dp of the ring**. This is the explicitly documented last-resort fallback, not a new hard fail, but the enlarged variant does **not** pass marker clearance. Include this exact case in mounted R14; do not describe all compact variants as clear of the puck. |
| **C11 caption** | [part-c.html:89](/Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-c.html:89), also assembled into `index.html`, still says **56 + 32 = 88dp** and suppression below **88dp**. Replace with **80 + 32 = 112dp** and rebuild the index. §§6/12 agree, but the brief’s “C11 says the same” claim is false. The rendered **80dp** gap remains correctly suppressed. |

No other material rendered regression established: **77/81 frames are pixel-identical to Round 15b**. C8’s pickup clearance and C10’s card clearance remain **16dp**. F1/N6 remove the duplicate “más.”

**3. Final scores.**

| # | Criterion | Score |
|---|---|---:|
| 1 | One job per screen | 9 |
| 2 | One primary action | 9 |
| 3 | Navigation | 9 |
| 4 | Numbers discipline | 9 |
| 5 | Copy | 9 |
| 6 | States | 9 |
| 7 | Time-to-value | 8 |
| 8 | Consistency & feel | 9 |
| | **Structural** | **71/80** |
| 9 | Signature idea | **8** |
| 10 | Glance physics | 8 |
| 11 | Craft | 9 |
| 12 | Radical simplicity | 9 |
| | **Craft** | **34/40** |

**Hard-fail check:** no hard fail established in the supplied renders; C12’s previous rounded-colour-rectangle-only failure is resolved. Mounted recognition and timed onboarding remain unverified.

`check-fixture.py` passes **152 figures, 16 chips and 27 offer rows**. It does not validate placement or catch the stale C11 caption.

**4. Verdict: PASS** against both design rubrics, exactly at the craft threshold.