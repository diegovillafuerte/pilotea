**Round 15 — Codex Astra: PASS. Structural 71/80; craft 34/40. C10’s spacing fix remains unfinished.**

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
| 9 | Signature idea | **8** |
| 10 | Glance physics | **8** |
| 11 | Craft | 9 |
| 12 | Radical simplicity | 9 |
| | **Craft** | **34/40** |

**Hard fails: none established in the supplied renders.** Mounted, one-second recognition remains unverified.

**My Round 14 list, re-measured**

Measurements below use the supplied 2× PNGs; opaque boundaries exclude shadows.

| Item | Status | Evidence and fix |
|---|---|---|
| **C1/C2/C10 — competing financial heroes** | **Done** | Chip rate is now **30sp versus the host’s 36sp**; C10 uses **39sp versus 47sp**. Rendered numeral heights confirm the difference: approximately **52px versus 66px**, and **67px versus 86px**. The fare is visibly heavier. Fixed reading order and removal of $/h resolve the original hierarchy problem. |
| **C10 — clearance** | **Not done** | Plate ends at **y914px**; host card starts at **y938px**: **24px = 12dp**, exactly the previous shortfall. The caption assumes a card top of 473dp; the render is **469dp**. Calculate placement from actual bounds. For this render, plate top must be **307.4dp**, replacing 311.4dp, to obtain 16dp. |
| **C8 — covered pickup pin** | **Done, with a spacing residual** | Plate occupies **x24–504, y298–522px**. Pickup at **(436,568)** and destination at **(660,312)** are visible. Clearance to the pickup’s outer stroke is approximately **14.5dp**, rather than the caption’s 16dp. Include the stroke in exclusion bounds; raise this plate another 1.5dp. |

The remaining C10 defect causes no opaque overlap or control obstruction. It deserves correction, but does not independently justify retaining glance physics at 7.

**Regressions and remaining weaknesses**

No new blocking regression established. B1’s navigation, D1’s dated timeline, proportional comparison bars and G2’s equally active controls improve the product. C7’s negative rate remains intact; C9 clears navigation; C11 suppresses; N10 retains its boundary.

**A1 still demonstrates an exception:** its chip hides the pickup pin. The caption acknowledges this, but drivers never see that explanation. Reframe the demo map so it teaches the same marker-preserving placement as C8.

`python3 check-fixture.py` passes: 150 figures, state phrases, date rules and chart checks. That success does **not** validate rendered clearance.

**Does the chip still read as the Claude/category template?** Not as a complete object. The upper band is now conventional; the two named limits and relational marker supply its distinction. Signature drops **9 → 8**. The blind record supports this trade: template votes remain low, but distinctiveness falls and no judge selects the winner as the clearest point of view.

**Comparable to Uber Driver?** Visually, yes—especially B1, E1 and F1. The host fare now retains numerical priority during the offer moment. What remains weaker is the overlay’s footprint at large text—C10 spans **79% of the screen width**—and placement precision. Comparable performance **in use** still requires the mounted driver test and timed onboarding; model rankings cannot establish either.