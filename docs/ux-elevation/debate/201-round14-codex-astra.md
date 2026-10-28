**Round 14 — Codex Astra: NOT_YET. Structural 71/80; craft 34/40; hard-fail check: none established from static evidence; criterion 10 remains below 8.**

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
| | **Structural** | **71/80** |
| 9 | Signature idea | 9 |
| 10 | Glance physics | **7** |
| 11 | Craft | 9 |
| 12 | Radical simplicity | 9 |
| | **Craft** | **34/40** |

The downgrade from Round 13b is for the new overlay’s competing hierarchy and placement—not simply because driver testing remains outstanding.

| Frame | Evidence | Fix |
|---|---|---|
| **C1/C2/C10 — hierarchy regression** | The new plate places a saturated second hero immediately above the host’s fare. Its primary figure is 40sp against the host’s 36sp; C10 grows these to 52sp and 47sp, with a plate spanning **76% of screen width**. The labelled limits distinguish the information, but do not resolve the visual competition. | Keep the threshold grammar. Prototype a band showing only the configured primary rate, moving the secondary rate to detail; reduce the number’s optical dominance while retaining readable labels and system scaling. Recheck against the host at both scales. |
| **C10 — measurable spacing defect** | The rendered plate ends at y914px; the host card starts at y938px. At 2×, clearance is **12dp**, not the specified 16dp. Nothing overlaps, but the claimed placement invariant does not hold. | Calculate placement from the host card’s measured bounds after layout. For this render, plate top should be **323dp**, not 327dp. |
| **C8 — carried visibility problem** | The chip completely covers the pickup pin. This is **not newly lost visibility**: the previous render hid that pin under the host card. The new placement still fails to solve it. | Include pickup, destination and driver markers in placement exclusions. Try another valid slot; suppress when none fits. A perception test can assess the impact, but cannot establish whether the pin is visibly covered—it is. |

**Checks that passed:** no rendered `$8 = tu mínimo` regression found; G2 correctly changes the $9.49 example to yellow against a $9.50 target. No larger-type overflow established. C7 retains both negative signs, C9 clears the navigation banner, C11 suppresses correctly, and N8–N10 retain visible plate boundaries. E2/F3 preserve free value behind their Premium invitations. `python3 check-fixture.py` passes all 154 figure assertions.

**Does the chip still look like the Claude/category template?** No, as a complete design. “The trip sits above, between or below your two limits” is a specific visual idea, carried into the offer marks and reference lines. Its rounded floating silhouette remains familiar, but the information structure exceeds a coloured rectangle containing a number and glyph; I do not trigger that hard fail. The blind results support distinctiveness, not proven driving comprehension.

**Is it comparable to Uber Driver in use and feel?** The parked surfaces—especially **B1, E1 and F2**—now credibly match its hierarchy and restraint. The offer moment remains weaker: competing financial heroes, unresolved map visibility, and unmeasured first-encounter recognition. The blind record’s glance ranking remains **6.2/11 versus 2.0** for the leading generic chip; those are model predictions, not driver outcomes.

**Shortest path:** address the overlay hierarchy, anchor its clearance to actual layout, and resolve C8’s marker exclusion. Re-score criterion 10 on those renders; retain the mounted driver test as the separate evidence gate already specified.