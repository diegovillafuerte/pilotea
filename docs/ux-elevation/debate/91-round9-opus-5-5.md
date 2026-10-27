# Kompara v2, Round 9 (joint confirmation), Opus: re-score from scratch

**Judged:**
- `docs/ux-elevation-spec.md` (23 oct 16:49, §9e row 12)
- `design-system/ui_kits/android-app-v2/index.html` (16:49, 61 frames)
- `base.css` (chip `width:200px`, 48 px controls)
- `fixture.json`, `check-fixture.py`, `rubric-uber-bar.md`

**The checker passes.** `python3 check-fixture.py` → "checked 95 figures in 33 frames, 23 spec strings, 1 date rule, 42 stale patterns → OK".

**Figures re-verified by hand:**

| Check | Result |
|---|---|
| H5b (28 sep – 4 oct) | 150.6 km × $2.8417 = $427.96 → "−$428"; 14 + 6 = 20 trips |
| F2 promedio | $162,440 ÷ 1,240 = $131; percentile 78 % by interpolation (median $126 is never called promedio) |
| E3 hero | 1,025.71 + 4,979.58 + 4,746.93 + 1,417.10 = 12,169.31 → $12,169; rows rounded individually add to $12,170 |
| E5 | $12,608 × 0.94 = $11,852; $9,582 − $7,926 = $1,656 |
| E2 date | 5 – 11 oct leaves "this week + 3" on lunes 2 nov ("hace 4 semanas") |
| B1 best hour | Shift-1 hourly nets 120 / 185 / 150 / 130 / 63 sum to $648; the best is 9–10 h |

**Scratch-copy test of the checker.** I edited six frames in a scratch copy:

| Edit | Caught? |
|---|---|
| Removed B1's "$762" | yes |
| Changed B3's sample chip to $7.20 | yes, only via a stale pattern |
| Changed A1's demo chip to "$9.94 · $258/h" | **no** |
| Changed G1's cost to "$3.84" | **no** |
| Changed H2c's grace date to "26 de diciembre" | **no** |
| Changed H6's "62 viajes" to "26 viajes" | **no** |
| Changed D1's "8 h 10 min" to "6 h 10 min" | **no** |

---

## 1. Scorecard

| # | Criterion | Score | Justification |
|---|---|---|---|
| 1 | One job per screen | **9** | Each root has one answer: B1 "$762 · 6 viajes tomados", E1 "$4,980 · 62 viajes tomados" (its Comparar card carries no figure: "Tu lugar entre los de Uber en CDMX"), E3 "$12,169 · 158 viajes tomados" over four unlabelled bars, F1 "$1.21 más por km en DiDi". |
| 2 | One primary action | **9** | One verb per screen: B2 "Encender lector", F5 "Confirmar con mi resumen de Uber", H2b "Suscribirme por $79 al mes" with the explicit alternative "Mejor avísame el 9 de noviembre", H5e "Reintentar". H2d demands nothing ("No tienes que hacer nada"). |
| 3 | Navigation that disappears | **9** | 3 tabs + avatar, depth ≤ 2. Every control is ≥ 48dp (status pill, Semana/Mes, the "Uber ▾ CDMX ▾" pills). E6 names the reader-less period and has no locks on absent data. |
| 4 | Numbers discipline | **9** | The root budget holds (answer plus ≤ 2 measures). "Promedio" is a true mean, and the median is never called promedio. One record per import scenario: H5/H5d/E3b = 29 viajes · 26 h; H5b binds to its week, and the checker asserts it. Residual: E3's hero ($12,169) and its visible rows ($12,170) differ by one peso on the same screen. H2c's grace date no longer follows H2b's "cobra hoy" (R9-04). |
| 5 | Copy | **8** | One estimate word ("aprox."), sentence case, `tú` throughout. H2b now leads with the trade-off ("Si te suscribes hoy, Google Play cobra hoy… Si prefieres esperar, te avisamos el 9 de noviembre"). Residual: E1's scope line runs to four clauses ("Con lo que leyó el lector · tu resumen de Uber dice un poco más · después de gasolina, desgaste y lo del coche · aprox."), and nothing has been comprehension-tested. |
| 6 | States designed | **9** | 61 frames cover the failed upload (H5e), Corregir on the right import (H5d), offline with its card (E7b), the import-only month (E3b, with the car cost named "tampoco se resta"), launch vs post-gate IMSS (E4/E5), pago en proceso / atención (H2d/H2c) and the open week (E8). One state is undefined: a boundary week merged with "Usar el total del PDF" (R9-05). |
| 7 | Time-to-value | **8** | A true verdict on screen 1 (A1). The permission at step 3, with Android's "control total" dialog drawn and explained (A3). Battery only when detected (A4), costs after the cliff (A5), the account optional (A6). Not measured on a device. |
| 8 | Consistency and feel | **8** | The frames are one hand, and the docs now carry the verde #187A36 (tokens, README, principles). Residual: the spec's governing §4 contract still prescribes scope lines that no frame uses (R9-01). Several captions contradict their frames (R9-03). |
| | **Total** | **69 / 80** | **PASS.** Every criterion is ≥ 8 and the total is 69, one point of margin, up from zero in Rounds 6–8. It passes as specified. The driving-time claims still need the device and driver evidence in §6. |

---

## 2. Hard fails

- **Auto-action proposed: none.**
  - Spec §6: "Drag only… Tap does nothing… Long-press does nothing either."
  - A2: "Kompara solo lee: nunca acepta ni rechaza por ti."
  - A3: "Kompara solo lee, nunca toca nada."
- **Platform logos: none.**
  - Platform names appear as plain text only.
  - The A1/G2 samples are neutral; the C1–C6 host card is "internal only".
- **A driving-time surface failing the 1-second glance: none.**
  - Fixed 200dp chip: glyph, colour and one rate, second line at 15sp.
  - C5 gris carries no peso figure ("Falta tiempo · sin semáforo · ve el detalle al estacionarte").
  - C6 lasts 5 s and lets touches pass through.
- **A paywall that blanks the free product: none.**
  - E2 keeps "Te quedaron esa semana · aprox. $4,747" with a flat placeholder.
  - F3 masks only the average.
  - F4 and F5 show no purchase prompt.
- **An onboarding screen with more than one ask: none.**
  - A1–A7 each have one primary.
  - A3's drawn dialog is an illustration.

---

## 3. Round 8 defects: status

| R8 id | Status | Evidence |
|---|---|---|
| R8-01 (spec/captions vs frames) | **fixed as listed; new drift elsewhere** | §5.1 item 4 now reads "each a verdict glyph · colour word and outcome · time, never a rate". E7b draws "Sin conexión · Tus números están en tu teléfono y no necesitan internet… Reintentar". E2: "hace 4 semanas", caption "lunes 2 nov". New drift sits in §4 and in captions (R9-01, R9-03). |
| R8-02 (page-wide presence check) | **partly** | Now frame-scoped: removing B1's "$762" fails with "MISSING in B1: '$762'". Not covered: 28 of 61 frames have no figure assertions, captions are excluded entirely, and no prose is compared. My scratch edits to A1, G1, H2c, H6 and D1 all passed (R9-02). |
| R8-03 (import records contradict) | fixed | H5 "Viajes 29 · Horas en línea (según Uber) 26 h · Comisión de la app 24 %". E3b "29 viajes · solo 14 – 20 sep". H5d "Viajes · 14 – 20 sep · 29". H5b "28 sep – 4 oct · Viajes 14 · 20 · $1,480 aprox. · $2,210 · Kilómetros 150.6". The checker asserts H5b binds to its week. |
| R8-04 (share hours shown with switch on) | fixed | H3: "Viajes en Uber 34 · Semana confirmada con mi resumen" under "Ocultar montos y horas" on. No hours on the card. |
| R8-05 (placeholder DOF citation in E4) | fixed | E4: "(imss.gob.mx/vigencia · la regla oficial se enlaza cuando esté confirmada)". |
| R8-06 (E3b IMSS assertion) | fixed | E3b: "IMSS de septiembre · Este pago está en tu mes · cómo consultar tu vigencia". |
| R8-07 ("después" vs "cobra hoy") | fixed | H2: "Suscribirme ahora". H2b: "Si te suscribes hoy, Google Play cobra hoy · Tus 21 días gratis no se pierden… Si prefieres esperar, te avisamos el 9 de noviembre" + "Mejor avísame el 9 de noviembre". |
| R8-08 (H5d on the wrong import) | fixed | H5d: "Viajes · 14 – 20 sep · 29 · Te pagó · se leyó $5,340 · Horas en línea 26 h". |
| R8-09 (September car cost unstated) | fixed | E3b: "Lo del coche de 28 – 30 sep ($643) tampoco se resta: sin km no hay neto." |
| R8-10 (E5 repeats, tip note on one app) | fixed | E5: one headline per app ("Para el IMSS cuentan $11,852 · aprox.") and one shared note ("Sin propinas: el lector no las ve y de un resumen se descuentan"). |
| R8-11 (offline frame identical to online) | fixed | E7b replaces the city card with the "Sin conexión" card. |
| R8-12 (verde token only in CSS/spec) | fixed in the design docs | `design-system/tokens/colors.css` `--verde: #187a36`; README "Verde `#187A36`"; design principles "Verde `#187A36` on white". Code still differs (R9-06). |
| R8-13 ("compartes a Kompara") | fixed | H1: "cada semana le compartes a Kompara el PDF que te manda Uber". |
| R8-14 (free-window date) | fixed | E2 caption "lunes 2 nov, the first Monday it leaves the this-week-plus-three window"; the checker's date rule asserts it. |

---

## 4. Remaining defects (ranked by damage)

**Counts:** 0 critical · 0 high · 1 medium · 7 low.

| id | Screen / § | Sev. | Defect | Exact evidence | Concrete fix |
|---|---|---|---|---|---|
| R9-01 | Spec §4 (vocabulary table and scope-line rule), §5.5 vs B1, B5, D1, D3, E1 | medium | The spec's governing contract prescribes hero sub-lines that no frame uses. An engineer building from §4 would ship the old wording. | §4 "Period hero" row: scope line "**Estimado de viajes tomados**, después de gasolina y desgaste". §4 Scope line rule: "one of: 'Después de gasolina y desgaste · aprox.' · '…y lo del coche · aprox.' · 'Con costos típicos · aprox.'". §5.5: "**Estimado de 9 viajes tomados** (lo que decía la oferta) $1,590". The frames say B1/B5/D1 "Viajes tomados según la oferta, después de gasolina y desgaste · aprox.", E1 "Con lo que leyó el lector · tu resumen de Uber dice un poco más · …", D3 "9 viajes tomados · lo que decía la oferta $1,590". The checker forbids "Estimado de" in frames but not in the spec. | Rewrite the §4 row and the scope-line rule as the four strings the frames use (day, closed week with statement, open week, typical costs), and §5.5 as D3. Add "Estimado de" to the checker's `stale_spec`. Add a short prose check: every quoted string in §4/§5 must appear in its frame. |
| R9-02 | `check-fixture.py` | low | The checker is now frame-scoped but covers only 33 of 61 frames, so the onboarding demo and several money and date screens can drift undetected. | My scratch edits all passed: A1 demo chip "$9.94 · $258/h", G1 "$3.84 por km", H2c "26 de diciembre", H6 "26 viajes", D1 "6 h 10 min". The docstring says it "does not compare spec prose with frame prose", and captions are excluded entirely. | Add A1/B3/G2 sample chips (from the verde chip), G1 (cost, floor, car cost), D1/B5 hours (from the shifts), H6 counts, the H2/H2b/H2c/H2d dates (from `plan`). Add a caption lint that fails on known-stale caption phrases. |
| R9-03 | Captions B1, H2b, E3b | low | Three captions contradict their frames. Reviewers and engineers read captions as the rationale. | B1: "the last three offers (**one number each**…)" vs rows "✓ Verde · la tomaste 14:32 · aprox." with no rate. H2b: "the start date (**after the free access, never before**)" vs "Si te suscribes hoy, Google Play cobra hoy". E3b: "the IMSS entry **states that the payment counts**" vs "Este pago está en tu mes". | Rewrite the three captions. Lint captions in the checker (R9-02). |
| R9-04 | H2c vs H2b, `fixture.json` `plan` | low | The grace-period example was computed for the old 10 nov start, so it no longer follows the new billing date. | H2b: "Si te suscribes hoy, Google Play cobra hoy" (h2b_viewed_on 19 oct, so renewals on the 19th). H2c: "Google Play no pudo cobrar el mes. Conservas tu acceso hasta el **16 de diciembre**". `grace_until_example: "2026-12-16"` is consistent with a 10 dic renewal, not 19 nov or 19 dic. | Derive the grace end in the fixture from the purchase date plus Play's configured grace period (e.g. 19 nov + 7 = "26 de noviembre"), and assert it in H2c. |
| R9-05 | H5b; spec §12 period accounting | low | Choosing "Usar el total del PDF" on a week that crosses the month boundary leads to an undefined month view. | H5b: a 7-day statement for "28 sep – 4 oct" ($2,210, 20 viajes) over reader days 1–4 oct. §12: "boundary-crossing [undated imports] stay unallocated". E3's October row "1 – 4 oct · 27 viajes · … $1,417" is built from reader data, and no frame shows what it becomes after the PDF choice. | State the rule in H5b's preview ("En octubre seguimos contando lo que leyó el lector del 1 al 4; el total del PDF queda en la semana, sin repartirse") and add an E3 variant showing it. |
| R9-06 | `docs/design-principles.md` vs `android/ui/…/theme/Color.kt` | low | The principles doc says it "describes what the code already does", but it now lists a verdict green the code doesn't have. | Principles: "Verde `#187A36` on white". `Color.kt:20`: `val VerdictGreen = Color(0xFF1B8A3A)`. | Mark it "target (B-xxx)" until the token lands in Compose, or change `Color.kt` and the overlay palette in the same PR. |
| R9-07 | E3 | low | The month hero and the rows directly under it differ by one peso on screen, and the explanation is in a month sheet that isn't drawn. | Hero "$12,169"; rows "$1,026 · $4,980 · $4,747 · $1,417" = **$12,170**. Spec: "which Cómo se calculó states". No month "Cómo se calculó" frame exists. | Draw the month sheet with the line "Las semanas, redondeadas una por una, suman $12,170; el total redondeado una vez es $12,169". Or show the hero as the sum of the displayed rows and note the cent-level remainder in the sheet. |
| R9-08 | H2 vs H2b; appendix | low | One step has two names. | H2 row: "**Suscribirme ahora** · $79 al mes". H2b title: "← **Seguir con Premium**". Appendix: "H2b Seguir con Premium". | Title H2b "Suscribirme" (and the appendix) so the row and the screen use one verb. |

---

## 5. What a driver on a windshield mount gets from each tab root in one second

- **Hoy (B1):** "Está leyendo; hoy llevo unos $762 de 6 viajes; mi mejor hora fue de 9 a 10, y la última verde la tomé."
- **Ganancias (E1):** "La semana pasada me quedaron unos $4,980 ya con la renta; mi lugar en Uber lo veo aquí abajo."
- **Comparar (F1, Tus apps):** "DiDi me dejó $1.21 más por kilómetro que Uber."
- **Chip verde (C1):** "Verde, $9.49 el kilómetro: pasa mi mínimo."
- **Chip amarillo (C2):** "Amarillo, $7.16: entre mi mínimo y mi rojo; piénsale."
- **Chip rojo (C3):** "Rojo, $2.20: no llega."
- **Chip gris (C4 / C5):** "Gris: le falta un dato para el semáforo; la decido yo y veo el detalle al estacionarme."

---

## 6. What only evidence can settle

| Claim | Test that settles it |
|---|---|
| The chip reads correctly in one second | 1-second exposures at 60 cm on a mount, day and night, 10–12 CDMX drivers including ≥ 3 with colour-vision deficiency. Pass if take/pass decisions are accurate against their stated floor and gris is never read as "no" (R1). |
| A fixed 200dp chip doesn't hide what the driver needs | On real Uber and DiDi offer layouts, measure the overlap with fare, pickup and destination text, and the share of offers where no safe position exists. |
| The new verde (#187A36) stays distinct from rojo at night | The same test, under glare, with the ✓ / ✕ glyphs. |
| "neto aprox." and the second rate earn their space | Chip variants in the 1-second test, measuring accuracy and time (R11, R3). |
| The reader is fast and correct | On-device runs of the silent screenshot + OCR lane (TD-040): parse→chip ≤ 150 ms and the fare/km/time read rate on real cards. |
| Install → "preparado" is quick, and A3's drawn "control total" dialog cuts drop-off | Closed beta on Xiaomi, Samsung and Motorola: median ≤ 75 s; abandonment at the system dialog with vs without the mock. |
| The reader survives a shift | Full-shift soak on restricted-battery phones: incidents per shift after A4/G4. |
| Drivers understand the money words | A 10-driver comprehension test of "Te quedan · aprox.", "viajes tomados según la oferta", "gris", "por viaje", "según el lector / según tu resumen". |
| Drivers keep a weekly PDF routine | The share of active drivers importing most weeks. The standing, the share card and Premium's one new datum depend on it. |
| Premium is worth $79 | A price test at $59 / $79 / $99 when it adds one city average ($131) + history detail + the PDF. |
| The subscription model passes Play and consumer review | Play policy and a Profeco-style read of H2b: charging today while appending 21 server-granted days "cuando canceles". |
| The IMSS rule and citations are right | Legal memo (B-038): the statutory base, factor and tips, the 2026 minimum $9,582, the DOF reference. E5 and any rule link wait for it. |
| Play accepts the disclosure | A pre-flight of A2 ("toma una imagen de toda la pantalla"), the accessibility declaration, the data-safety form and kompara.mx/borrar. |
| Three tabs are findable | Tree test and first click on "¿Dónde ves si te pagan más que a otros de tu ciudad?" and "¿Dónde ves lo del IMSS?" (R9). |

---

## 7. Verdict

**PASS, 69/80.** As specified, this version meets "comparable in use and feel to the Uber Driver app" for its job: a status-first home, one answer per screen, an honest chip, money labelled with its source that reconciles frame by frame, and every state drawn. It has the first point of margin since Round 5, with no hard fails and no critical or high defects.

**Biggest residual risk.** The promises that matter while driving are still unproven on a real mounted phone: the 1-second read of a now 200dp chip, OCR speed and accuracy on live cards, and surviving a full shift. On paper the only notable defect is that the spec's §4 contract still prescribes wording the frames dropped.

**Build first.** The chip and the readiness model, on device:
- engine changes: gris never red, no unit swap, no rate on gris, preparado / funcionando
- the silent-screenshot lane
- the C1–C6 states

Run them through the 1-second mounted test. Meanwhile, regenerate §4/§5.5 from the frames and extend the checker to the remaining 28 frames and the captions.
