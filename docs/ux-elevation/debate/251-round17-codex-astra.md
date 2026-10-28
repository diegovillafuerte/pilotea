**Round 17 — NOT_YET. Structural: 67/80. Craft: 34/40.** The money inconsistency is fixed, and Ganancias and Comparar have stronger hierarchy. A1 introduces a measurable target-size regression; discovery and recovery still retain problems identified by the walkthrough.

Measurements use the supplied 786 × 1704 renders: **2px = 1dp**, excluding shadows.

| # | Criterion | Score |
|---|---|---:|
| 1 | One job per screen | 9 |
| 2 | One primary action | 9 |
| 3 | Navigation that disappears | 8 |
| 4 | Numbers discipline | 9 |
| 5 | Copy | 9 |
| 6 | States are designed | 8 |
| 7 | Time-to-value | 8 |
| 8 | Consistency & feel | **7** |
| | **Structural — fails total and criterion-8 floor** | **67/80** |
| 9 | Signature idea | 8 |
| 10 | Glance physics | 8 |
| 11 | Craft | 9 |
| 12 | Radical simplicity | 9 |
| | **Craft — passes at threshold** | **34/40** |

**Hard-fail line:** no enumerated hard fail established in the supplied renders. Mounted one-second recognition and timed onboarding remain unverified.

Relative to `231`, navigation and states each lose one point because the walkthrough exposed unresolved discovery/recovery problems. Consistency loses two for A1’s explicit 48dp-contract regression. The visual craft score holds.

**Regressions and remaining defects**

| Frame | Evidence | Fix |
|---|---|---|
| **A1 — new target regression** | Legend centres are at **y1144, 1224, 1304, 1384px**: **40dp pitch**, versus **48dp** in Round 15c. Four adjacent choices cannot each have non-overlapping 48dp targets. | Restore ≥48dp rows. Recover space from the map and upper whitespace. |
| **A1 — lost example basis** | “Ejemplo” replaces the distance/time explanation, but the sample card still shows only **$148**. Nothing connects that fare to **$9.49/km**. | Restore **“12 km en total (2 para recoger) · 24 min”** inside the example, as requested in `241`. |
| **B1/N1 → G2 — unresolved discovery** | The useful limits link exists on B3, then disappears from regular Hoy. Typing improves editing after arrival; the unlabelled person icon still conceals the route. | Provide an explicitly named settings affordance or a direct limits route. |
| **B1/G4/G8 — unresolved recovery** | G8 explains four causes, but remains behind Cuenta → Ayuda. G4 shows a suppression count without the latest cause/time. There is no rendered Hoy attention/recovery state for these cases. | Make the status actionable and expose the latest cause with its corrective action. |
| **A3 — residual false affordance** | The raised instructional Android dialog still presents convincing **Rechazar / Permitir** buttons alongside the real **Abrir ajustes** action. | Clearly label and frame it as an example of the next screen. |

**Money consistency passes end to end.**

- **B1 ↔ E8:** both **$548** at 14:40, six trips.
- **D1 ↔ D3 ↔ E3d:** **$1,026** at day end. Full precision: $1,590 − $350.093333 − $214.285714 = $1,025.620952.
- **E1 ↔ E1b:** **$4,980**. Displayed daily rows sum to **$4,979**; the rounding explanation is correct.
- **E3 ↔ E3d:** **$12,169**. Displayed period rows sum to **$12,170**; again correctly explained.
- **D2:** “Te dejó $114” preserves the trip contribution; the car stays outside the chip and rates.

Two small residuals: H5’s **“Se suma a septiembre”** should become **“Se guarda”**, since E3c excludes that gross import from the net total. D3b should print **$1,500 ÷ 7 × 19** instead of rounded **$214.29 × 19**, whose displayed arithmetic rounds differently.

`check-fixture.py` passes **145 figures in 50 frames, 16 chips, 27 offer rows and 5 charts**.

**Is the chip still the category template?** No, as a complete object. Its upper band remains conventional; the two named limits and the positioned third line provide the distinguishing idea. C12 preserves that idea.

C1 measures **240 × 112dp**, with a **22 × 5dp** marker and **16dp** clearance above the host card. C12 measures **240 × 80dp**, with **19dp** below the puck ring and **16dp** above the card. The marker change preserves geometry. The model test’s **0/6 control readings** supports removing the false affordance; it does not establish mounted legibility.

**Comparable in use and feel to Uber Driver?** Closer in feel; not yet demonstrated in use. E1’s amount shrinks from **128px to 71px of ink height**, while its body remains about **30px**—a substantial hierarchy improvement. F1 now leads with an intelligible finding. The remaining gap is finding settings, recovering a missing overlay and completing setup confidently.

**Shortest path:** repair A1, expose limits and recovery from Hoy, clarify A3, then repeat the same eight-task walkthrough. Keep the mounted chip test—including compact at 1.3×—as the separate evidence needed for the driving-use claim.