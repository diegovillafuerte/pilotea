# Round 13b — Codex Astra

**PASS — 71/80 structural, 35/40 craft.** Every criterion remains ≥8. Two residuals are complete; one wording residual remains partly complete. One cosmetic synchronization regression needs correction.

## 1. My Round 13 residuals

| ID | Status | Evidence |
|---|---|---|
| **R13-01 — G2 target bounds** | **Done in the mock** | G2 preserves the visible label placement. Each label now has a separate ≥48×48dp wrapper; their vertical bounds are −7–41 and 46–94 relative to the plate, leaving 5dp between them. Native accessibility bounds remain unverified. |
| **R13-02 — duplicate car-cost amount** | **Done** | E1/E7b/N4 render the qualifier on one line without −$1,500 and retain **$214 por día** beside the chart. E1b visibly retains **Lo del coche −$1,500**; the checker now asserts it there. |
| **R13-03 — perception claims** | **Partly** | C3 now says the mounted test **must confirm** the distinction; specification §6 identifies the gris and gross/net distinctions as hypotheses. However, [D1’s caption](/Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-d.html:28) still says its rate “cannot be read as the host’s fare per km”; [N3’s caption](/Users/panama/pilotea/design-system/ui_kits/android-app-v2/part-n.html:65) repeats that assertion. Replace remaining absolute comprehension claims with intended distinctions pending testing. |

## 2. Regression check

| Frame | Severity | Evidence | Fix |
|---|---|---|---|
| **E7** | **Low — cosmetic** | The loading qualifier still draws two placeholder lines, whereas E1/E7b now resolve to one. Its caption also explicitly describes two lines. | Use one placeholder and update the caption, preserving the 48dp row. No layout jump is established. |

No other rendered regression established:

- **E3/E3c/N5:** narrower bars preserve the totals, baseline and unavailable-period distinction. **E4:** the empty band is gone.
- **F2/F3/N7:** the context selector and plain standing sentence fit cleanly; F3 retains the free standing and driver’s figure.
- **H3:** “Tú” and its mark above “Promedio” communicate position without amounts. The reference has a subject; the former orphan-reference defect has not returned.
- **C11/G4:** suppression preserves host controls; G4 displays the daily count. The individual suppression-history treatment is specified in captions, not demonstrated by a populated history row.

**Fixture checker: PASS** — 154 figures in 50 frames, 23 specification strings, one date rule, 46 stale patterns, six caption checks and five bar charts. This does not validate native behaviour.

## 3. Re-score

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
| | **Structural total** | **71/80** |
| 9 | Signature idea | 9 |
| 10 | Glance physics | 8 |
| 11 | Craft | 9 |
| 12 | Radical simplicity | 9 |
| | **Craft total** | **35/40** |

**Hard-fail check:** none established in either rubric from the supplied static evidence; one-second driving recognition remains untested.

**Verdict: PASS.** The residual pass preserves the design’s strengths. Finish the remaining hypothesis wording and E7 skeleton correction; time-to-value and glance scores remain at 8 pending measured evidence.