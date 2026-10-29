# Kompara v2, Round 10 (joint confirmation), Opus: re-score from scratch

**Judged:**
- `docs/ux-elevation-spec.md` (23 oct 17:03, §9e rows 13–14)
- `design-system/ui_kits/android-app-v2/index.html` (17:02, 64 frames)
- `base.css`, `fixture.json`, `check-fixture.py`
- `rubric-uber-bar.md`

**The checker:** `python3 check-fixture.py` → "checked 120 figures in 45 frames, 23 spec strings, 1 date rule, 46 stale patterns, 6 caption lints, 3 bar charts → OK".

**Re-verified by hand:**

| Item | Check |
|---|---|
| 12–18 oct days | Sum to 62 viajes / $8,400 / 675.8 km |
| E1 bars | Day nets $808 · $950 · $910 · $1,041 · $1,380 · $891 · $499 → 59/69/66/75/100/65/36 % |
| E3 bars | $1,417 · $4,747 · $4,980 · $1,026 → 28/95/100/21 % (unasserted, but right) |
| E3c bars | 0/100/25 % (unasserted, but right) |
| B4 | Sunday 18 oct = $600 − 35.7 km × $2.8417 = $499 |
| C7 | $22 − 34.95 = −$12.95 → −$1.05/km · −$26/h |
| D3b | $16,240.65 − $4,071.43 = $12,169 |
| E3c | $5,723 + ($2,089 − 3 × $214.29) = $7,170 |
| H2c | 19 nov + 7 days = "26 de noviembre" |

**Scratch-copy test (29 edits across uncovered frames).** The checker caught 14 of 29.

| Result | Frames and edits |
|---|---|
| Caught | A1 $9.49, B3 $9.49, G1, H2c, H6, D1 hours, C7, D3b, E3c, B6, G9, F4, F1 $1.21, H2b "21 días" |
| Missed | A1 / B3 / G2 sample-chip "$285/h"; A5 "$2.84" and "$34"; H5c "$3,100"; H5e "1.2 MB"; E2 "hace 4 semanas"; E6 period labels; H1 "$79"; G5 "28 sep" |

---

## 1. Scorecard

| # | Criterion | Score | Justification |
|---|---|---|---|
| 1 | One job per screen | **9** | The roots carry one answer and words. E1: "$4,980 · 62 viajes tomados", then "Ver cada día · Tu mejor día fue el viernes ›". E3: "$12,169 · 158 viajes tomados" over four bars, then "Ver cada semana · La mejor fue 12 – 18 oct ›". E8 states the deduction once. |
| 2 | One primary action | **9** | One verb per screen. B2 "Encender lector", G4 "Quitar restricción de batería", F5 "Confirmar con mi resumen de Uber", H2b "Suscribirme por $79 al mes" with "Mejor avísame el 9 de noviembre", H5e "Reintentar". H2d asks nothing. |
| 3 | Navigation that disappears | **9** | 3 tabs + avatar; E4/E5 are now drill-ins without the bottom bar. Scope lines and "Ver tu día ›" / "Elegir mi ciudad" are 48dp. Residual: the per-day and per-week amounts are now more than one tap from Ganancias (R10-01). |
| 4 | Numbers discipline | **9** | Every displayed figure I re-ran reconciles, and the bars are fixture-derived (asserted on E1/E7b/E8). B4 matches the fixture's Sunday. The peso between $12,169 and $12,170 is explained in D3b. The roots list no amounts. |
| 5 | Copy | **8** | One estimate word, sentence case, `tú` throughout. The §4 vocabulary row and scope-line rule now quote the frames verbatim. Residual: E1's scope line runs to four clauses, and nothing has been comprehension-tested with drivers. |
| 6 | States designed | **9** | 64 frames, including C7 (negative chip, 232dp), D3b (month sheet), E3c (mixed month), H5b's boundary rule ("En octubre seguimos contando lo que leyó el lector del 1 al 4…"), H5c/H5e ("No añadimos esta semana a Ganancias hasta que revises y guardes"), and H2c's derived grace date. |
| 7 | Time-to-value | **8** | A true verdict on screen 1 (A1); the permission at step 3 with Android's dialog drawn (A3); battery only when detected (A4); costs after the cliff (A5); the account optional (A6). Not measured on a device. |
| 8 | Consistency and feel | **8** | Frames, spec §4 and the captions now agree (all three R9 caption contradictions fixed). Residual: E3c prints a mock annotation inside the phone screen ("Escenario alterno…"), C7's clock puts an offer on the fixture day that Tu día doesn't list, and the checker still misses ~40 % of my edits. |
| | **Total** | **69 / 80** | **PASS.** Every criterion is ≥ 8 and the total is 69, the same one-point margin as Round 9. This is a pass for the design as specified; the driving-time claims still need the evidence in §6. |

---

## 2. Hard fails

- **Auto-action proposed: none.**
  - Spec §6: "Drag only… Tap does nothing… Long-press does nothing either."
  - A2: "Kompara solo lee: nunca acepta ni rechaza por ti."
  - A3: "Kompara solo lee, nunca toca nada."
- **Platform logos: none.**
  - Platform names are plain text.
  - The A1/G2 samples are neutral; the host card in C1–C7 is "internal only".
- **A driving-time surface that fails the 1-second glance: none.**
  - Chip: a fixed 200dp footprint (232dp only for negative or two-digit amounts, C7), with glyph, colour and one rate.
  - C5 gris carries no peso figure ("Falta tiempo · sin semáforo · ve el detalle al estacionarte").
  - C6 lasts 5 s and lets touches pass through.
- **A paywall that blanks the free product: none.**
  - E2 keeps "Te quedaron esa semana · aprox. $4,747" with a flat placeholder.
  - F3 masks only the average.
  - F4/F5 have no purchase prompt.
- **An onboarding screen with more than one ask: none.** A1–A7 each have one primary.

---

## 3. Round 9 defects: status

| R9 id | Status | Evidence |
|---|---|---|
| R9-01 (§4 contract prescribes old wording) | fixed | §4 Period hero row: "the scope line 'Viajes tomados según la oferta, después de gasolina y desgaste · aprox.'". §4 Scope line rule: "one of exactly these: day '…' (B1, B5, D1) · closed week with a statement '…' (E1) · open week '…' (E8) · month '…' (E3)". "Estimado de" appears only in fix-log row 13. |
| R9-02 (checker covers 33 of 61 frames) | **partly** | Now 120 figures in 45 frames, with caption lints and three bar charts. My deletions in A1, G1, H2c, H6, D1, C7, D3b, E3c, B6, G9, F4, F1 and H2b were all caught. Still missed: the sample chips' "$285/h" (A1/B3/G2), A5 "$2.84 · $34", H5c "$3,100", H5e "1.2 MB", E2 "hace 4 semanas", E6 period labels, the price "$79" (H1/H2/H2b) and G5 "28 sep". No prose comparison (docstring) (R10-02). |
| R9-03 (captions contradict frames) | fixed | B1 caption: "the last three offers (colour, outcome and time, no rate…)". H2b caption: "the trade-off stated first (subscribing today bills today…)". E3b caption: "the IMSS entry places the payment in the month without an IMSS outcome ('Este pago está en tu mes')". |
| R9-04 (H2c grace date) | fixed | H2c: "Conservas tu acceso hasta el 26 de noviembre (periodo de gracia)". `fixture.json` `plan`: "grace_until = first renewal (purchase + 1 month) + grace_days". |
| R9-05 (boundary-week PDF choice undefined) | fixed | H5b: "faltan los km de 6 viajes. El lector está activo desde el 1 de octubre · En octubre seguimos contando lo que leyó el lector del 1 al 4; el total del PDF queda en la semana, sin repartirse por día". |
| R9-06 (principles vs `Color.kt`) | fixed | Design principles: "Verde `#187A36` on white (target per the UX elevation spec §12; Compose `Color.kt` still ships `#1B8A3A` until the chip is rebuilt)". |
| R9-07 (E3 $1 with no drawn explanation) | fixed | D3b: "Te quedan en octubre · aprox. $12,169 · Las semanas, redondeadas una por una, suman $12,170 · La diferencia es de centavos…". |
| R9-08 (H2b two names) | fixed | H2b's title is now "← Suscribirme", matching H2's row "Suscribirme ahora". |

---

## 4. Remaining defects (ranked by damage)

**Counts:** 0 critical · 0 high · 1 medium · 6 low.

| id | Screen / § | Sev. | Defect | Exact evidence | Concrete fix |
|---|---|---|---|---|---|
| R10-01 | E1, E3; spec §5.6, §5.7 | medium | Taking every amount off the roots pushed the week's and month's breakdowns more than one tap away, which weakens the "earnings as a timeline" job the Uber pattern is about. | E1: "Ver cada día · Tu mejor día fue el viernes ›". Spec: "the per-day amounts live on Tu día", which is one day at a time behind the "‹ ›" stepper. E3: "Ver cada semana · La mejor fue 12 – 18 oct ›" → "Elegir periodo → that week" (E6 lists weeks with no amounts). The spec no longer says a bar reveals its value. Seeing seven daily amounts takes seven steps; four weekly amounts take four round trips. | Keep the roots as they are, but put the breakdown one tap away. **Either** a tap on a bar shows its value in place ("Vie 16 · $1,380 · antes de lo del coche"), **or** "Ver cada día" / "Ver cada semana" opens a sheet listing the seven days or four weeks with their amounts (same scope labels as today). The rubric's bar is "one big number, one chart, breakdown behind a tap". |
| R10-02 | `check-fixture.py` | low | The checker now covers 45 of 64 frames but still misses several money and date strings. | Missed in my scratch test: A1/B3/G2 "$285/h", A5 "Manejar te cuesta $2.84 por km · … unos $34", H5c "$3,100 · 29 viajes", H5e "1.2 MB", E2 "hace 4 semanas", E6 labels "19 – 25 oct · en curso", H1/H2/H2b "$79", G5 "Se resta desde lunes 28 sep". The E3/E3c bars are unasserted. | Assert the sample chips' $/h (from `chips.verde`), A5 (from `costs`), H5c (from the 5–11 DiDi record), the E2 relative label and E6 labels (from the date rule), `plan.price_monthly` in H1/H2/H2b, `car_cost.from` in G5, and the E3/E3c bars. |
| R10-03 | E3c | low | A mock annotation is drawn inside the phone screen, as screen text an engineer could ship. | E3c screen text: "**Escenario alterno · lector activo desde el 21 sep**" above "Te quedan en septiembre · aprox. $7,170". | Move the scenario note to the caption, or to a badge outside the phone frame. The screen starts at "Te quedan en septiembre". |
| R10-04 | C7 vs D1, fixture | low | C7's clock puts a fourth shown offer on the fixture day that Tu día doesn't list. | C7 status bar "**12:14**" ($22 · 12.3 km, inside the 08:00–12:30 shift). The fixture's "shown ones" and D1 list only 11:48, 12:10, 14:32 and 14:50. `fixture.json` keeps C7 as `chips.rojo_negativo`, a chip example. | Caption C7 "illustrative offer, not in the 19 oct timeline" (as B6 does), or add it to D1's day (it would be among the "12 se fueron sin tomar"). |
| R10-05 | E8 | low | The open week's bars are scaled to last week's best day, so they jump when a day this week beats it. | E8 bar heights `['55','0',…]` = $762 ÷ last Friday's $1,380. The checker comment: "E8's today bar is relative to that same best day". Nothing on screen says so. | Scale the open week to its own best day (or to a fixed cap that only grows). State the rule in §5.6. |
| R10-06 | D3b vs E3 | low | The month sheet now explains a peso the driver can no longer see. E3 has no week rows, so "$12,170" appears nowhere else. | D3b: "Las semanas, redondeadas una por una, suman $12,170 · La diferencia es de centavos…". E3 shows only "$12,169" and four unlabelled bars. | Keep the line only in the "Ver cada semana" sheet proposed in R10-01 (where the rows are), or drop it from D3b. |
| R10-07 | Spec §5.7 | low | A regeneration seam is left in the month section. | "…("Lo que te pagó cada app y cómo consultar tu vigencia") · "**Semanas ··** one row **"Ver cada semana · La mejor fue 12 – 18 oct ›"**…". | Delete the orphaned "'Semanas ··". |

---

## 5. What a driver on a windshield mount gets from each tab root in one second

- **Hoy (B1):** "Está leyendo; hoy llevo unos $762 de 6 viajes; mi mejor hora fue de 9 a 10, y la última verde la tomé."
- **Ganancias (E1):** "La semana pasada me quedaron unos $4,980 ya con la renta; el viernes fue el bueno."
- **Comparar (F1, Tus apps):** "DiDi me dejó $1.21 más por kilómetro que Uber."
- **Chip verde (C1):** "Verde, $9.49 el kilómetro: pasa mi mínimo."
- **Chip amarillo (C2):** "Amarillo, $7.16: entre mi mínimo y mi rojo; piénsale."
- **Chip rojo (C3 / C7):** "Rojo: no llega; y si sale en menos, perdería dinero."
- **Chip gris (C4 / C5):** "Gris: le falta un dato para el semáforo; la decido yo y veo el detalle al estacionarme."

---

## 6. What only evidence can settle

| Claim | Test |
|---|---|
| The chip reads correctly in one second | 1-second exposures at 60 cm on a mount, day and night, 10–12 CDMX drivers including ≥ 3 with colour-vision deficiency. Pass if take/pass decisions are accurate against their stated floor and gris is never read as "no" (R1). |
| A 200dp chip (232dp when negative) doesn't hide what the driver needs | Overlap with fare, pickup and destination text on real Uber and DiDi layouts, and the share of offers with no safe position. |
| The new verde (#187A36) stays distinct from rojo at night | The same test, under glare, with the ✓ / ✕ glyphs. |
| "neto aprox." and the second rate earn their space | Chip variants in the 1-second test: accuracy and time (R11, R3). |
| The reader is fast and correct | On-device silent-screenshot + OCR runs (TD-040): parse→chip ≤ 150 ms and the fare/km/time read rate on real cards. |
| Install → "preparado" is quick, and A3's drawn dialog cuts drop-off | Closed beta on Xiaomi, Samsung and Motorola: median ≤ 75 s; abandonment at the system dialog with vs without the mock. |
| The reader survives a shift | Full-shift soak on restricted-battery phones: incidents per shift after A4/G4. |
| Drivers find a day's or a week's amount | First-click and time-on-task for "¿Cuánto hiciste el martes?" and "¿Cuál fue tu mejor semana de octubre y cuánto?" (R10-01). |
| Drivers understand the money words | A 10-driver comprehension test of "Te quedan · aprox.", "viajes tomados según la oferta", "gris", "por viaje", "según el lector / según tu resumen". |
| Drivers keep a weekly PDF routine | The share of active drivers importing most weeks. The standing, the share card and Premium's one new datum depend on it. |
| Premium is worth $79 | A price test at $59 / $79 / $99 for one city average ($131) + history detail + the PDF. |
| Subscription and IMSS claims pass review | Play policy and a Profeco-style read of H2b ("cobra hoy" + 21 appended days). Legal memo B-038 for E5's base, factor and the DOF reference. |
| Play accepts the disclosure | A pre-flight of A2 ("toma una imagen de toda la pantalla"), the accessibility declaration, the data-safety form and kompara.mx/borrar. |
| Three tabs are findable | Tree test and first click on "¿Dónde ves si te pagan más que a otros de tu ciudad?" and "¿Dónde ves lo del IMSS?" (R9). |

---

## 7. Verdict

**PASS, 69/80.** As specified, this version meets "comparable in use and feel to the Uber Driver app" for its job: a status-first home, one answer per screen, an honest chip that grows rather than truncates, and money labelled by source that reconciles frame by frame. There are no hard fails and no critical or high defects. The one medium, R10-01, is a usability cost of the "no amounts on roots" fix: the daily and weekly breakdowns should come back one tap away.

**Biggest residual risk.** The driving-time promises are still unproven on a real mounted phone: the 1-second read of a 200–232dp chip, OCR speed and accuracy on live cards, and surviving a full shift.

**Build first.** The chip and the readiness model, on device:
- gris never red, no unit swap, no rate on gris
- preparado / funcionando
- the silent-screenshot lane
- C1–C7

Run them through the 1-second mounted test. Fix R10-01 before Ganancias is built.
