**P1 — AGREE WITH CHANGES. Reverse the Round 7 decision.** A driver should not need to remember which screen deducts the car. The distinction between a trip’s contribution and a period’s result is useful; giving both the same name is the problem.

The rule I would write:

> “Te quedan / quedaron” means recorded income minus gasolina, desgaste and the configured car cost attributable to that period. Deduct the car once per local calendar date, from its effective start date, including today; never per trip, shift or app, and never for future dates. Day, week and month use the same dated calculation. Calculate at full precision and round only for display. These results remain before taxes.

The common basis line becomes **“ya sin gasolina, desgaste ni coche.”** “All your costs” should mean the costs actually configured, not imply that Kompara knows every expense.

| Frame | Revised amount |
|---|---:|
| B1, and E8 at 14:40 | $548 |
| D1/D3 at day end; B2’s yesterday | $1,026 |
| B4’s Sunday | **$284** |
| B5 | $434 |
| B6, under the explicit partial-data rule below | $166 |

**The brief’s $285 for B4 is incorrect.** The [fixture](/Users/panama/pilotea/design-system/ui_kits/android-app-v2/fixture.json) gives `600 − 35.7 × (24.50/12 + 0.80) − 1500/7 = 284.2668`. Subtracting rounded figures creates the error.

This requires four accompanying changes:

- **D2 must stop saying “Te quedaron” above $114.** Use **“Aporte del viaje · aprox.”** Keep $114, $9.49/km and the verdict unchanged. Explain in the detail: **“Lo del coche se resta por día.”** Otherwise the supposedly unified language still changes meaning inside the offer sheet.
- **D3 gains the car row:** $1,590 income, −$350 gasolina/desgaste, −$214 lo del coche, **$1,026** result. Replace the existing sentence excluding the car.
- **Weekly bars and daily rows must use the same result.** E1’s Friday becomes **$1,166**; E8’s Monday becomes **$548**. E1b’s Monday–Sunday values become **$594, $736, $695, $827, $1,166, $677, $284**. The week remains **$4,980**; the rounded daily figures sum to $4,979, which the existing rounding explanation should acknowledge. Remove the car reference line from these already-deducted bars and use a zero baseline with negative bars below it. Do not subtract $1,500 again in E1b; show that deduction in Cómo se calculó.
- **G5 must explain the new contract:** “Se resta una vez por día: $214. Se incluye en tu día, tu semana y tu mes. No cambia el semáforo.” Propagate the changes to the night frames.

**Before the first trip:** preserve B3/B4’s useful readiness or cost-confirmation surface. “Aún no registramos viajes hoy” does not establish that income was zero, so it should not automatically produce a −$214 earnings headline. The fixed cost still accrues in the calculation. Once a result is supported, show its real sign: a known zero-income day is **−$214**; one $114 example trip leaves **−$100**. For negative results use **“Balance de hoy · aprox.”**, with the signed amount. Never clamp to zero or postpone the deduction until earnings cover it.

**B6:** explicitly define the fixture’s $380 as contribution after running costs. Deduct the daily car cost once from the recorded-day aggregate, yielding **$166**, labelled **“Te quedan de lo leído hoy · aprox.”** Keep **“Solo Uber”** visible and retain **“Por ahora no leemos DiDi.”** This is a partial recorded-day result, not an allocation of the entire car expense to Uber. Do not invent gross receipts or kilometres to flesh out its ledger.

Unknown costs or missing distance must remain unknown; an explicitly confirmed zero car cost is different from an unset one.

**P2 — AGREE WITH CHANGES. Cut repeated conclusions, while preserving clear routes to useful detail.**

The rule I would write:

> A root has one hero, at most one primary evidence group, and one short group of supporting actions. Every supporting item must add a fact, explain an exception or open a distinct task. A chart is optional. A drill-in may contain the full list its task requires.

“One hero, one visual, one short list” is too mechanical if it forces a visual into B2 or truncates D1’s history.

| Frame | Exact decision |
|---|---|
| **B1** | Delete **“Tu mejor hora hoy: 9 a 10 h.”** At 14:40 it adds little to the immediate task. The fixture supports a retrospective fact, not a forecast; do not turn it into advice. Keep the hero’s route to **Cómo se calculó** and **“Ver tu día”** to D1. Those destinations answer different questions. |
| **D1** | Replace the shift graphic and its $648/$592 figures with **“En turno · 8 h 10 min”** beneath the hero. Pull **“Ofertas · 23”** upward instead of preserving its old position with padding. Keep the missed-offers filter within the offers section: it opens a useful subset and is not merely another summary. |
| **E1** | Delete **“Tu mejor día fue el viernes.”** Give the chart an explicit **“Por día ›”** heading with a 48dp target opening E1b; bars retain that interaction. Change the discrepancy to **“Tu resumen de Uber registra $34 más.”** Its sheet shows $5,062 versus $5,028 and makes clear that the hero still uses reader data. Place this exception with the supporting rows so the hero leads directly into the chart. |
| **A1** | Keep the interactive four-state legend. Remove the standalone summary line only after moving **“Ejemplo”** onto the demo and putting **“12 km en total (2 para recoger) · 24 min”** into the sample offer card. Remove the duplicate $148, which the card already shows. Otherwise the cut removes the only visible distance context behind $9.49/km. |

Apply E1’s treatment to **E3/E3c** too: replace the sentence repeating the highlighted week with **“Por semana ›”** attached to the chart. Preserve the IMSS route. The chart should communicate the conclusion and visibly offer its breakdown.

**P3 — AGREE WITH CHANGES. Draw the set, but retain familiar meanings.**

A coherent custom set is worthwhile at this stage. It will improve finish across every screen. I would reject **three parallel lines as the Hoy icon**: that silhouette is easily read as a menu, and the existing offer mark already has a specific meaning.

The rule I would write:

> Draw the named icons on a 24dp canvas, with a nominal 20dp optical footprint and 2dp strokes. Use square terminals on straight strokes, consistent joins and optically balanced curves. Preserve recognizable silhouettes: home, bars, comparison, person, share and back. Borrow the chip’s line rhythm rather than turning every control into its mark.

Specific constraints:

- Keep **Hoy** recognizably a home; its baseline can carry the raya.
- Keep the offer mark exclusive to offer states. Navigation must not suggest a verdict.
- Use monochrome tokens, persistent nav labels and the existing active-tab indicator. Selection must remain understandable without colour.
- Retain **48dp minimum targets** for actionable icons; the drawn glyph remains 24dp.
- Judge the set together at actual size in day and night. Do not approve isolated enlarged SVGs while leaving inconsistent chevrons, close controls or locks elsewhere.

This is a finishing improvement. It does not resolve the larger hierarchy and accounting issues.

**The ONE change I would add is a typography rebalance.**

In the supplied Uber references, titles, amounts and actionable text all have substantial presence. In B1/D1/E1, the **72sp condensed amount dominates the 13–16sp text needed to interpret it**. Removing rows without correcting that relationship risks producing a large number surrounded by small, quiet instructions.

The rule I would write:

> Set app money heroes at **60/64sp**; primary row labels and buttons at **18/24sp**; essential supporting text at **16/22sp**. Reserve 13sp for genuinely tertiary material. Use normal-width Barlow for prose headings, keeping Semi Condensed for money and the chip.

Keep root titles at **30/36**, prose headlines at **24/30**, and section headings at **22/28**, using the normal-width face. Apply the shared hierarchy first to B1, D1 and E1, including their night equivalents. Let long offer meanings wrap and rows grow at enlarged text settings; preserve the chip’s existing geometry.

That would improve both reading and visual confidence across the product more than making the icons more distinctive.